package sync

import (
	"context"
	"testing"
	"time"

	"github.com/nilbyte/georef/backend/internal/models"
	"github.com/nilbyte/georef/backend/internal/repository"
)

type MockRepo struct {
	repository.Repository
	idempotentMap map[string]*models.SyncPushResponse
}

func (m *MockRepo) GetIdempotencyLog(ctx context.Context, batchID string) (*models.SyncPushResponse, bool, error) {
	if m.idempotentMap != nil {
		if resp, ok := m.idempotentMap[batchID]; ok {
			return resp, true, nil
		}
	}
	return nil, false, nil
}

func (m *MockRepo) SaveIdempotencyLog(ctx context.Context, batchID, clientID string, count int, resp *models.SyncPushResponse) error {
	if m.idempotentMap == nil {
		m.idempotentMap = make(map[string]*models.SyncPushResponse)
	}
	m.idempotentMap[batchID] = resp
	return nil
}

func (m *MockRepo) UpsertGeorefRecord(ctx context.Context, record *models.GeorefRecord) (*models.SyncItemStatus, error) {
	return &models.SyncItemStatus{
		ID:              record.ID,
		Status:          "ACCEPTED",
		ServerVersion:   1,
		ServerUpdatedAt: time.Now().UnixMilli(),
	}, nil
}

func (m *MockRepo) GetChangesSince(ctx context.Context, clientID string, sinceServer int64, limit int) ([]models.GeorefRecord, int64, bool, error) {
	return []models.GeorefRecord{}, time.Now().UnixMilli(), false, nil
}

func TestProcessSyncPushIdempotency(t *testing.T) {
	mockRepo := &MockRepo{}
	svc := NewSyncService(mockRepo)

	req := &models.SyncPushRequest{
		BatchID:  "batch-123",
		ClientID: "device-1",
		Records: []models.GeorefRecord{
			{ID: "rec-1", Name: "Point A", Latitude: -23.5, Longitude: -46.6},
		},
	}

	// First execution
	resp1, err := svc.ProcessSyncPush(context.Background(), req)
	if err != nil {
		t.Fatalf("ProcessSyncPush failed: %v", err)
	}

	if resp1.BatchID != "batch-123" || resp1.ProcessedCount != 1 {
		t.Errorf("Unexpected response: %+v", resp1)
	}

	// Second execution with same BatchID (Idempotent replay)
	resp2, err := svc.ProcessSyncPush(context.Background(), req)
	if err != nil {
		t.Fatalf("ProcessSyncPush replay failed: %v", err)
	}

	if resp2.BatchID != resp1.BatchID {
		t.Errorf("Idempotency failed, expected same batch response")
	}
}

func TestProcessSyncPull(t *testing.T) {
	mockRepo := &MockRepo{}
	svc := NewSyncService(mockRepo)

	req := &models.SyncPullRequest{
		ClientID:    "device-1",
		SinceServer: 0,
		Limit:       10,
	}

	resp, err := svc.ProcessSyncPull(context.Background(), req)
	if err != nil {
		t.Fatalf("ProcessSyncPull failed: %v", err)
	}

	if resp.HasMore {
		t.Errorf("Expected HasMore to be false")
	}
}
