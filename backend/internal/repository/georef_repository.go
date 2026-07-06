package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"georef/backend/internal/models"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Repository interface {
	GetIdempotencyLog(ctx context.Context, batchID string) (*models.SyncPushResponse, bool, error)
	SaveIdempotencyLog(ctx context.Context, batchID, clientID string, count int, resp *models.SyncPushResponse) error
	UpsertGeorefRecord(ctx context.Context, record *models.GeorefRecord) (*models.SyncItemStatus, error)
	GetChangesSince(ctx context.Context, clientID string, sinceServer int64, limit int) ([]models.GeorefRecord, int64, bool, error)
	GetAllRecords(ctx context.Context, limit int) ([]models.GeorefRecord, error)
}

type PostgresRepository struct {
	pool *pgxpool.Pool
}

func NewPostgresRepository(pool *pgxpool.Pool) *PostgresRepository {
	return &PostgresRepository{pool: pool}
}

// GetIdempotencyLog checks if a batch request with the given batchID was already processed.
func (r *PostgresRepository) GetIdempotencyLog(ctx context.Context, batchID string) (*models.SyncPushResponse, bool, error) {
	query := `SELECT response_payload FROM sync_idempotency_logs WHERE idempotency_key = $1`
	var payloadBytes []byte
	err := r.pool.QueryRow(ctx, query, batchID).Scan(&payloadBytes)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, false, nil
		}
		return nil, false, fmt.Errorf("error querying idempotency log: %w", err)
	}

	var resp models.SyncPushResponse
	if err := json.Unmarshal(payloadBytes, &resp); err != nil {
		return nil, false, fmt.Errorf("failed to unmarshal idempotency response: %w", err)
	}

	return &resp, true, nil
}

// SaveIdempotencyLog saves the sync push result under batchID.
func (r *PostgresRepository) SaveIdempotencyLog(ctx context.Context, batchID, clientID string, count int, resp *models.SyncPushResponse) error {
	payloadBytes, err := json.Marshal(resp)
	if err != nil {
		return fmt.Errorf("failed to marshal idempotency response: %w", err)
	}

	query := `
		INSERT INTO sync_idempotency_logs (idempotency_key, client_id, processed_count, response_payload, created_at)
		VALUES ($1, $2, $3, $4, CURRENT_TIMESTAMP)
		ON CONFLICT (idempotency_key) DO UPDATE SET response_payload = EXCLUDED.response_payload
	`
	_, err = r.pool.Exec(ctx, query, batchID, clientID, count, payloadBytes)
	return err
}

// UpsertGeorefRecord performs an idempotent conflict-resilient upsert.
// If client version/timestamp is equal or greater than server, it accepts the update.
// If server version is higher, it rejects the update (keeping server state) and notifies client.
func (r *PostgresRepository) UpsertGeorefRecord(ctx context.Context, record *models.GeorefRecord) (*models.SyncItemStatus, error) {
	nowMs := time.Now().UnixMilli()

	// Check existing server record version
	queryCheck := `SELECT version, server_updated_at, client_updated_at FROM georef_records WHERE id = $1`
	var existingVersion int
	var existingServerUpdated, existingClientUpdated int64
	err := r.pool.QueryRow(ctx, queryCheck, record.ID).Scan(&existingVersion, &existingServerUpdated, &existingClientUpdated)

	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return nil, fmt.Errorf("failed to check existing record: %w", err)
	}

	metaJSON := record.MetadataJSON
	if len(metaJSON) == 0 {
		metaJSON = json.RawMessage("{}")
	}

	// Record does not exist -> Insert fresh
	if errors.Is(err, pgx.ErrNoRows) {
		newVersion := record.Version
		if newVersion < 1 {
			newVersion = 1
		}
		insertQuery := `
			INSERT INTO georef_records (
				id, client_id, name, description, latitude, longitude, elevation, accuracy,
				metadata_json, client_updated_at, server_updated_at, version, is_deleted, created_at
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, CURRENT_TIMESTAMP)
		`
		_, err := r.pool.Exec(ctx, insertQuery,
			record.ID, record.ClientID, record.Name, record.Description,
			record.Latitude, record.Longitude, record.Elevation, record.Accuracy,
			metaJSON, record.ClientUpdatedAt, nowMs, newVersion, record.IsDeleted,
		)
		if err != nil {
			return nil, fmt.Errorf("failed to insert new record: %w", err)
		}

		return &models.SyncItemStatus{
			ID:              record.ID,
			Status:          "ACCEPTED",
			ServerVersion:   newVersion,
			ServerUpdatedAt: nowMs,
			Message:         "Record created on server",
		}, nil
	}

	// Record exists: Compare versions/timestamps for Last-Write-Wins with Versioning
	if record.Version < existingVersion || (record.Version == existingVersion && record.ClientUpdatedAt < existingClientUpdated) {
		// Stale client update -> Server state wins
		return &models.SyncItemStatus{
			ID:              record.ID,
			Status:          "IGNORED_STALE",
			ServerVersion:   existingVersion,
			ServerUpdatedAt: existingServerUpdated,
			Message:         "Client update is stale compared to server version",
		}, nil
	}

	// Client update accepted: Bump version
	newVersion := existingVersion + 1
	updateQuery := `
		UPDATE georef_records SET
			client_id = $2,
			name = $3,
			description = $4,
			latitude = $5,
			longitude = $6,
			elevation = $7,
			accuracy = $8,
			metadata_json = $9,
			client_updated_at = $10,
			server_updated_at = $11,
			version = $12,
			is_deleted = $13
		WHERE id = $1
	`
	_, err = r.pool.Exec(ctx, updateQuery,
		record.ID, record.ClientID, record.Name, record.Description,
		record.Latitude, record.Longitude, record.Elevation, record.Accuracy,
		metaJSON, record.ClientUpdatedAt, nowMs, newVersion, record.IsDeleted,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to update record: %w", err)
	}

	return &models.SyncItemStatus{
		ID:              record.ID,
		Status:          "ACCEPTED",
		ServerVersion:   newVersion,
		ServerUpdatedAt: nowMs,
		Message:         "Record updated successfully",
	}, nil
}

// GetChangesSince fetches updated records for client delta sync.
func (r *PostgresRepository) GetChangesSince(ctx context.Context, clientID string, sinceServer int64, limit int) ([]models.GeorefRecord, int64, bool, error) {
	if limit <= 0 || limit > 500 {
		limit = 100
	}

	query := `
		SELECT id, client_id, name, description, latitude, longitude, elevation, accuracy,
		       metadata_json, client_updated_at, server_updated_at, version, is_deleted,
		       TO_CHAR(created_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
		FROM georef_records
		WHERE server_updated_at > $1 AND client_id != $2
		ORDER BY server_updated_at ASC
		LIMIT $3
	`
	rows, err := r.pool.Query(ctx, query, sinceServer, clientID, limit+1)
	if err != nil {
		return nil, sinceServer, false, err
	}
	defer rows.Close()

	var records []models.GeorefRecord
	var maxServerTimestamp = sinceServer

	for rows.Next() {
		var rec models.GeorefRecord
		err := rows.Scan(
			&rec.ID, &rec.ClientID, &rec.Name, &rec.Description,
			&rec.Latitude, &rec.Longitude, &rec.Elevation, &rec.Accuracy,
			&rec.MetadataJSON, &rec.ClientUpdatedAt, &rec.ServerUpdatedAt,
			&rec.Version, &rec.IsDeleted, &rec.CreatedAt,
		)
		if err != nil {
			return nil, sinceServer, false, err
		}
		records = append(records, rec)
		if rec.ServerUpdatedAt > maxServerTimestamp {
			maxServerTimestamp = rec.ServerUpdatedAt
		}
	}

	hasMore := false
	if len(records) > limit {
		hasMore = true
		records = records[:limit]
	}

	return records, maxServerTimestamp, hasMore, nil
}

// GetAllRecords returns all non-deleted records for inspection.
func (r *PostgresRepository) GetAllRecords(ctx context.Context, limit int) ([]models.GeorefRecord, error) {
	if limit <= 0 {
		limit = 100
	}
	query := `
		SELECT id, client_id, name, description, latitude, longitude, elevation, accuracy,
		       metadata_json, client_updated_at, server_updated_at, version, is_deleted,
		       TO_CHAR(created_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
		FROM georef_records
		WHERE is_deleted = FALSE
		ORDER BY server_updated_at DESC
		LIMIT $1
	`
	rows, err := r.pool.Query(ctx, query, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var records []models.GeorefRecord
	for rows.Next() {
		var rec models.GeorefRecord
		err := rows.Scan(
			&rec.ID, &rec.ClientID, &rec.Name, &rec.Description,
			&rec.Latitude, &rec.Longitude, &rec.Elevation, &rec.Accuracy,
			&rec.MetadataJSON, &rec.ClientUpdatedAt, &rec.ServerUpdatedAt,
			&rec.Version, &rec.IsDeleted, &rec.CreatedAt,
		)
		if err != nil {
			return nil, err
		}
		records = append(records, rec)
	}

	return records, nil
}
