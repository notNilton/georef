package models

import "encoding/json"

// GeorefRecord represents a single offline-capable georeferenced item.
type GeorefRecord struct {
	ID              string          `json:"id"`
	ClientID        string          `json:"client_id"`
	Name            string          `json:"name"`
	Description     string          `json:"description"`
	Latitude        float64         `json:"latitude"`
	Longitude       float64         `json:"longitude"`
	Elevation       float64         `json:"elevation"`
	Accuracy        float64         `json:"accuracy"`
	MetadataJSON    json.RawMessage `json:"metadata_json"`
	ClientUpdatedAt int64           `json:"client_updated_at"`
	ServerUpdatedAt int64           `json:"server_updated_at"`
	Version         int             `json:"version"`
	IsDeleted       bool            `json:"is_deleted"`
	CreatedAt       string          `json:"created_at,omitempty"`
}

// SyncPushRequest encapsulates a batch of local client mutations sent for sync.
type SyncPushRequest struct {
	BatchID        string         `json:"batch_id"`        // Unique client batch/idempotency key
	ClientID       string         `json:"client_id"`       // Device identifier
	LastSyncServer int64          `json:"last_sync_server"`// Timestamp of last successful server pull
	Records        []GeorefRecord `json:"records"`         // Pending local records
}

// SyncItemStatus reflects server outcome for each item in batch
type SyncItemStatus struct {
	ID              string `json:"id"`
	Status          string `json:"status"` // "ACCEPTED", "CONFLICT_RESOLVED", "IGNORED_STALE"
	ServerVersion   int    `json:"server_version"`
	ServerUpdatedAt int64  `json:"server_updated_at"`
	Message         string `json:"message,omitempty"`
}

// SyncPushResponse returns batch result with server status and pulled server updates.
type SyncPushResponse struct {
	BatchID          string           `json:"batch_id"`
	ProcessedCount   int              `json:"processed_count"`
	Statuses         []SyncItemStatus `json:"statuses"`
	ServerChanges    []GeorefRecord   `json:"server_changes"`
	NewLastSyncServer int64           `json:"new_last_sync_server"`
}

// SyncPullRequest for client incremental delta fetch.
type SyncPullRequest struct {
	ClientID   string `json:"client_id"`
	SinceServer int64 `json:"since_server"`
	Limit      int    `json:"limit"`
}

// SyncPullResponse returns updated items since client's last sync.
type SyncPullResponse struct {
	Records        []GeorefRecord `json:"records"`
	LastSyncServer int64          `json:"last_sync_server"`
	HasMore        bool           `json:"has_more"`
}
