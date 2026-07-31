package api

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/nilbyte/georef/backend/internal/models"
	"github.com/nilbyte/georef/backend/internal/repository"
	"github.com/nilbyte/georef/backend/internal/sync"
)

type MockGisRepo struct {
	repository.GisRepository
}

func (m *MockGisRepo) GetAllGisLayers(ctx context.Context, limit int) ([]models.GisLayerRecord, error) {
	return []models.GisLayerRecord{
		{
			ID:             "layer-mock-1",
			ClientID:       "client-mock",
			Name:           "Fazenda Parana",
			FileType:       models.FileTypeGeoJSON,
			MinLat:         -23.55,
			MinLng:         -46.63,
			MaxLat:         -23.50,
			MaxLng:         -46.60,
			GeoJSONPayload: json.RawMessage(`{"type":"Point","coordinates":[-46.63,-23.55]}`),
		},
	}, nil
}

func TestExportHandlers(t *testing.T) {
	mockRepo := &MockGisRepo{}
	syncSvc := sync.NewSyncService(nil)
	server := NewServer(syncSvc, nil, mockRepo, nil)

	mux := http.NewServeMux()
	server.RegisterRoutes(mux)

	// 1. Test GeoJSON Export HTTP Handler
	reqGeoJSON := httptest.NewRequest(http.MethodGet, "/api/v1/gis/export/geojson", nil)
	rrGeoJSON := httptest.NewRecorder()
	mux.ServeHTTP(rrGeoJSON, reqGeoJSON)

	if rrGeoJSON.Code != http.StatusOK {
		t.Errorf("Expected status 200 OK for GeoJSON export, got %d", rrGeoJSON.Code)
	}

	contentTypeGeoJSON := rrGeoJSON.Header().Get("Content-Type")
	if contentTypeGeoJSON != "application/geo+json" {
		t.Errorf("Expected Content-Type application/geo+json, got %s", contentTypeGeoJSON)
	}

	// 2. Test KML Export HTTP Handler
	reqKML := httptest.NewRequest(http.MethodGet, "/api/v1/gis/export/kml", nil)
	rrKML := httptest.NewRecorder()
	mux.ServeHTTP(rrKML, reqKML)

	if rrKML.Code != http.StatusOK {
		t.Errorf("Expected status 200 OK for KML export, got %d", rrKML.Code)
	}

	contentTypeKML := rrKML.Header().Get("Content-Type")
	if contentTypeKML != "application/vnd.google-earth.kml+xml" {
		t.Errorf("Expected Content-Type application/vnd.google-earth.kml+xml, got %s", contentTypeKML)
	}
}
