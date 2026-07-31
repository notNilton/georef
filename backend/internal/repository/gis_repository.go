package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/nilbyte/georef/backend/internal/models"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type GisRepository interface {
	UpsertGisLayer(ctx context.Context, layer *models.GisLayerRecord) (*models.SyncItemStatus, error)
	GetGisLayersSince(ctx context.Context, clientID string, sinceServer int64, limit int) ([]models.GisLayerRecord, int64, bool, error)
	GetAllGisLayers(ctx context.Context, limit int) ([]models.GisLayerRecord, error)
	GetLayersIntersectingPoint(ctx context.Context, lat, lng float64) ([]models.GisLayerRecord, error)
}

type PostgresGisRepository struct {
	pool *pgxpool.Pool
}

func NewPostgresGisRepository(pool *pgxpool.Pool) *PostgresGisRepository {
	return &PostgresGisRepository{pool: pool}
}

func (r *PostgresGisRepository) UpsertGisLayer(ctx context.Context, layer *models.GisLayerRecord) (*models.SyncItemStatus, error) {
	nowMs := time.Now().UnixMilli()

	queryCheck := `SELECT version, server_updated_at FROM gis_layers WHERE id = $1`
	var existingVersion int
	var existingServerUpdated int64
	err := r.pool.QueryRow(ctx, queryCheck, layer.ID).Scan(&existingVersion, &existingServerUpdated)

	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return nil, fmt.Errorf("failed to check existing GIS layer: %w", err)
	}

	payloadJSON := layer.GeoJSONPayload
	if len(payloadJSON) == 0 {
		payloadJSON = json.RawMessage("{}")
	}

	if errors.Is(err, pgx.ErrNoRows) {
		newVersion := layer.Version
		if newVersion < 1 {
			newVersion = 1
		}
		// PostGIS ST_MakeEnvelope(min_lng, min_lat, max_lng, max_lat, 4326) creates Polygon
		// ST_SetSRID(ST_MakePoint(center_lng, center_lat), 4326) creates Point
		insertQuery := `
			INSERT INTO gis_layers (
				id, client_id, name, file_type, min_lat, min_lng, max_lat, max_lng, center_lat, center_lng,
				bbox_geom, center_geom, geojson_payload, client_updated_at, server_updated_at, version, is_deleted, created_at
			) VALUES (
				$1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
				ST_MakeEnvelope($6, $5, $8, $7, 4326),
				ST_SetSRID(ST_MakePoint($10, $9), 4326),
				$11, $12, $13, $14, $15, CURRENT_TIMESTAMP
			)
		`
		_, err := r.pool.Exec(ctx, insertQuery,
			layer.ID, layer.ClientID, layer.Name, string(layer.FileType),
			layer.MinLat, layer.MinLng, layer.MaxLat, layer.MaxLng, layer.CenterLat, layer.CenterLng,
			payloadJSON, layer.ClientUpdatedAt, nowMs, newVersion, layer.IsDeleted,
		)
		if err != nil {
			return nil, fmt.Errorf("failed to insert PostGIS layer: %w", err)
		}

		return &models.SyncItemStatus{
			ID:              layer.ID,
			Status:          "ACCEPTED",
			ServerVersion:   newVersion,
			ServerUpdatedAt: nowMs,
			Message:         "GIS Spatial layer created in PostGIS",
		}, nil
	}

	if layer.Version < existingVersion {
		return &models.SyncItemStatus{
			ID:              layer.ID,
			Status:          "IGNORED_STALE",
			ServerVersion:   existingVersion,
			ServerUpdatedAt: existingServerUpdated,
			Message:         "Stale GIS layer update ignored",
		}, nil
	}

	newVersion := existingVersion + 1
	updateQuery := `
		UPDATE gis_layers SET
			client_id = $2, name = $3, file_type = $4,
			min_lat = $5, min_lng = $6, max_lat = $7, max_lng = $8, center_lat = $9, center_lng = $10,
			bbox_geom = ST_MakeEnvelope($6, $5, $8, $7, 4326),
			center_geom = ST_SetSRID(ST_MakePoint($10, $9), 4326),
			geojson_payload = $11, client_updated_at = $12, server_updated_at = $13,
			version = $14, is_deleted = $15
		WHERE id = $1
	`
	_, err = r.pool.Exec(ctx, updateQuery,
		layer.ID, layer.ClientID, layer.Name, string(layer.FileType),
		layer.MinLat, layer.MinLng, layer.MaxLat, layer.MaxLng, layer.CenterLat, layer.CenterLng,
		payloadJSON, layer.ClientUpdatedAt, nowMs, newVersion, layer.IsDeleted,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to update PostGIS layer: %w", err)
	}

	return &models.SyncItemStatus{
		ID:              layer.ID,
		Status:          "ACCEPTED",
		ServerVersion:   newVersion,
		ServerUpdatedAt: nowMs,
		Message:         "GIS Spatial layer updated in PostGIS",
	}, nil
}

func (r *PostgresGisRepository) GetGisLayersSince(ctx context.Context, clientID string, sinceServer int64, limit int) ([]models.GisLayerRecord, int64, bool, error) {
	if limit <= 0 || limit > 500 {
		limit = 100
	}

	query := `
		SELECT id, client_id, name, file_type, min_lat, min_lng, max_lat, max_lng, center_lat, center_lng,
		       geojson_payload, client_updated_at, server_updated_at, version, is_deleted,
		       TO_CHAR(created_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
		FROM gis_layers
		WHERE server_updated_at > $1 AND client_id != $2
		ORDER BY server_updated_at ASC
		LIMIT $3
	`
	rows, err := r.pool.Query(ctx, query, sinceServer, clientID, limit+1)
	if err != nil {
		return nil, sinceServer, false, err
	}
	defer rows.Close()

	var layers []models.GisLayerRecord
	var maxServerTimestamp = sinceServer

	for rows.Next() {
		var l models.GisLayerRecord
		var fileTypeStr string
		err := rows.Scan(
			&l.ID, &l.ClientID, &l.Name, &fileTypeStr, &l.MinLat, &l.MinLng, &l.MaxLat, &l.MaxLng, &l.CenterLat, &l.CenterLng,
			&l.GeoJSONPayload, &l.ClientUpdatedAt, &l.ServerUpdatedAt, &l.Version, &l.IsDeleted, &l.CreatedAt,
		)
		if err != nil {
			return nil, sinceServer, false, err
		}
		l.FileType = models.GisFileType(fileTypeStr)
		layers = append(layers, l)
		if l.ServerUpdatedAt > maxServerTimestamp {
			maxServerTimestamp = l.ServerUpdatedAt
		}
	}

	hasMore := false
	if len(layers) > limit {
		hasMore = true
		layers = layers[:limit]
	}

	return layers, maxServerTimestamp, hasMore, nil
}

func (r *PostgresGisRepository) GetAllGisLayers(ctx context.Context, limit int) ([]models.GisLayerRecord, error) {
	if limit <= 0 {
		limit = 100
	}
	query := `
		SELECT id, client_id, name, file_type, min_lat, min_lng, max_lat, max_lng, center_lat, center_lng,
		       geojson_payload, client_updated_at, server_updated_at, version, is_deleted,
		       TO_CHAR(created_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
		FROM gis_layers
		WHERE is_deleted = FALSE
		ORDER BY server_updated_at DESC
		LIMIT $1
	`
	rows, err := r.pool.Query(ctx, query, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var layers []models.GisLayerRecord
	for rows.Next() {
		var l models.GisLayerRecord
		var fileTypeStr string
		err := rows.Scan(
			&l.ID, &l.ClientID, &l.Name, &fileTypeStr, &l.MinLat, &l.MinLng, &l.MaxLat, &l.MaxLng, &l.CenterLat, &l.CenterLng,
			&l.GeoJSONPayload, &l.ClientUpdatedAt, &l.ServerUpdatedAt, &l.Version, &l.IsDeleted, &l.CreatedAt,
		)
		if err != nil {
			return nil, err
		}
		l.FileType = models.GisFileType(fileTypeStr)
		layers = append(layers, l)
	}

	return layers, nil
}

// GetLayersIntersectingPoint uses PostGIS ST_Intersects spatial query.
func (r *PostgresGisRepository) GetLayersIntersectingPoint(ctx context.Context, lat, lng float64) ([]models.GisLayerRecord, error) {
	query := `
		SELECT id, client_id, name, file_type, min_lat, min_lng, max_lat, max_lng, center_lat, center_lng,
		       geojson_payload, client_updated_at, server_updated_at, version, is_deleted,
		       TO_CHAR(created_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
		FROM gis_layers
		WHERE is_deleted = FALSE AND ST_Intersects(bbox_geom, ST_SetSRID(ST_MakePoint($2, $1), 4326))
		ORDER BY server_updated_at DESC
	`
	rows, err := r.pool.Query(ctx, query, lat, lng)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var layers []models.GisLayerRecord
	for rows.Next() {
		var l models.GisLayerRecord
		var fileTypeStr string
		err := rows.Scan(
			&l.ID, &l.ClientID, &l.Name, &fileTypeStr, &l.MinLat, &l.MinLng, &l.MaxLat, &l.MaxLng, &l.CenterLat, &l.CenterLng,
			&l.GeoJSONPayload, &l.ClientUpdatedAt, &l.ServerUpdatedAt, &l.Version, &l.IsDeleted, &l.CreatedAt,
		)
		if err != nil {
			return nil, err
		}
		l.FileType = models.GisFileType(fileTypeStr)
		layers = append(layers, l)
	}

	return layers, nil
}
