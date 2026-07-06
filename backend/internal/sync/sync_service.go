package sync

import (
	"context"
	"fmt"
	"time"

	"georef/backend/internal/models"
	"georef/backend/internal/repository"
)

type SyncService struct {
	repo repository.Repository
}

func NewSyncService(repo repository.Repository) *SyncService {
	return &SyncService{repo: repo}
}

// ProcessSyncPush processes a client batch idempotently.
func (s *SyncService) ProcessSyncPush(ctx context.Context, req *models.SyncPushRequest) (*models.SyncPushResponse, error) {
	// 1. Idempotency Check: if batchID was already processed, return existing cached response
	if cachedResp, found, err := s.repo.GetIdempotencyLog(ctx, req.BatchID); err == nil && found {
		return cachedResp, nil
	}

	statuses := make([]models.SyncItemStatus, 0, len(req.Records))

	// 2. Process each incoming record idempotently
	for idx := range req.Records {
		rec := &req.Records[idx]
		if rec.ClientID == "" {
			rec.ClientID = req.ClientID
		}

		status, err := s.repo.UpsertGeorefRecord(ctx, rec)
		if err != nil {
			statuses = append(statuses, models.SyncItemStatus{
				ID:      rec.ID,
				Status:  "FAILED",
				Message: fmt.Sprintf("Error processing record: %v", err),
			})
			continue
		}
		statuses = append(statuses, *status)
	}

	// 3. Fetch server changes since client's last sync timestamp for bi-directional sync delta
	serverChanges, maxServerTimestamp, _, err := s.repo.GetChangesSince(ctx, req.ClientID, req.LastSyncServer, 100)
	if err != nil {
		serverChanges = []models.GeorefRecord{}
		maxServerTimestamp = time.Now().UnixMilli()
	}

	if maxServerTimestamp <= req.LastSyncServer {
		maxServerTimestamp = time.Now().UnixMilli()
	}

	resp := &models.SyncPushResponse{
		BatchID:           req.BatchID,
		ProcessedCount:    len(statuses),
		Statuses:          statuses,
		ServerChanges:     serverChanges,
		NewLastSyncServer: maxServerTimestamp,
	}

	// 4. Save result to idempotency log
	_ = s.repo.SaveIdempotencyLog(ctx, req.BatchID, req.ClientID, len(statuses), resp)

	return resp, nil
}

// ProcessSyncPull returns server delta changes since client's last sync.
func (s *SyncService) ProcessSyncPull(ctx context.Context, req *models.SyncPullRequest) (*models.SyncPullResponse, error) {
	records, maxTimestamp, hasMore, err := s.repo.GetChangesSince(ctx, req.ClientID, req.SinceServer, req.Limit)
	if err != nil {
		return nil, err
	}

	return &models.SyncPullResponse{
		Records:        records,
		LastSyncServer: maxTimestamp,
		HasMore:        hasMore,
	}, nil
}
