package sync

import (
	"context"
	"errors"
	"testing"

	"github.com/nilbyte/georef/backend/internal/models"
)

type FailingMockRepo struct {
	MockRepo
}

func (m *FailingMockRepo) UpsertGeorefRecord(ctx context.Context, record *models.GeorefRecord) (*models.SyncItemStatus, error) {
	return nil, errors.New("database disk full")
}

func TestProcessSyncPushFailureHandling(t *testing.T) {
	failingRepo := &FailingMockRepo{}
	svc := NewSyncService(failingRepo)

	req := &models.SyncPushRequest{
		BatchID:  "batch-fail-1",
		ClientID: "device-fail",
		Records: []models.GeorefRecord{
			{ID: "rec-err-1", Name: "Failed Point"},
		},
	}

	resp, err := svc.ProcessSyncPush(context.Background(), req)
	if err != nil {
		t.Fatalf("ProcessSyncPush should return response with item status FAILED, got error: %v", err)
	}

	if len(resp.Statuses) != 1 {
		t.Fatalf("Expected 1 item status, got %d", len(resp.Statuses))
	}

	if resp.Statuses[0].Status != "FAILED" {
		t.Errorf("Expected item status FAILED, got %s", resp.Statuses[0].Status)
	}

	if resp.Statuses[0].Message == "" {
		t.Errorf("Expected error message in item status")
	}
}

func TestProcessSyncPushDefaultClientID(t *testing.T) {
	mockRepo := &MockRepo{}
	svc := NewSyncService(mockRepo)

	req := &models.SyncPushRequest{
		BatchID:  "batch-default-client",
		ClientID: "device-fallback-123",
		Records: []models.GeorefRecord{
			{ID: "rec-no-client", ClientID: ""},
		},
	}

	resp, err := svc.ProcessSyncPush(context.Background(), req)
	if err != nil {
		t.Fatalf("ProcessSyncPush failed: %v", err)
	}

	if resp.ProcessedCount != 1 {
		t.Errorf("Expected 1 processed record")
	}
}
