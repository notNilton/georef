package models

import "encoding/json"

type GisFileType string

const (
	FileTypeGeoPDF  GisFileType = "GEOPDF"
	FileTypeGeoJSON GisFileType = "GEOJSON"
	FileTypeKML     GisFileType = "KML"
	FileTypeGeoTIFF GisFileType = "GEOTIFF"
)

type GisLayerRecord struct {
	ID              string          `json:"id"`
	ClientID        string          `json:"client_id"`
	Name            string          `json:"name"`
	FileType        GisFileType     `json:"file_type"`
	MinLat          float64         `json:"min_lat"`
	MinLng          float64         `json:"min_lng"`
	MaxLat          float64         `json:"max_lat"`
	MaxLng          float64         `json:"max_lng"`
	CenterLat       float64         `json:"center_lat"`
	CenterLng       float64         `json:"center_lng"`
	GeoJSONPayload  json.RawMessage `json:"geojson_payload"`
	ClientUpdatedAt int64           `json:"client_updated_at"`
	ServerUpdatedAt int64           `json:"server_updated_at"`
	Version         int             `json:"version"`
	IsDeleted       bool            `json:"is_deleted"`
	CreatedAt       string          `json:"created_at,omitempty"`
}

type GisSyncPushRequest struct {
	BatchID        string           `json:"batch_id"`
	ClientID       string           `json:"client_id"`
	LastSyncServer int64            `json:"last_sync_server"`
	Layers         []GisLayerRecord `json:"layers"`
}

type GisSyncPushResponse struct {
	BatchID          string           `json:"batch_id"`
	ProcessedCount   int              `json:"processed_count"`
	Statuses         []SyncItemStatus `json:"statuses"`
	ServerChanges    []GisLayerRecord `json:"server_changes"`
	NewLastSyncServer int64           `json:"new_last_sync_server"`
}
