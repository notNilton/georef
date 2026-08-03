package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/nilbyte/georef/backend/internal/models"
	"github.com/nilbyte/georef/backend/internal/sync"
)

func TestHandleHealth(t *testing.T) {
	syncSvc := sync.NewSyncService(nil)
	server := NewServer(syncSvc, nil, nil, nil)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rr := httptest.NewRecorder()

	server.HandleHealth(rr, req)

	if rr.Code != http.StatusOK {
		t.Errorf("Expected status 200, got %d", rr.Code)
	}

	var resp map[string]string
	if err := json.Unmarshal(rr.Body.Bytes(), &resp); err != nil {
		t.Fatalf("Failed to parse JSON response: %v", err)
	}

	if resp["status"] != "UP" || resp["service"] != "georef-backend" {
		t.Errorf("Unexpected health response payload: %+v", resp)
	}
}

func TestHandleRegisterValidation(t *testing.T) {
	server := NewServer(nil, nil, nil, nil)

	// Missing email and password
	payload := models.RegisterRequest{Name: "Nilton"}
	data, _ := json.Marshal(payload)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register", bytes.NewBuffer(data))
	rr := httptest.NewRecorder()

	server.HandleRegister(rr, req)

	if rr.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400 Bad Request on missing fields, got %d", rr.Code)
	}
}

func TestHandleGisIntersectsValidation(t *testing.T) {
	server := NewServer(nil, nil, nil, nil)

	// Invalid lat/lng params
	req := httptest.NewRequest(http.MethodGet, "/api/v1/gis/intersects?lat=invalid&lng=12.3", nil)
	rr := httptest.NewRecorder()

	server.HandleGisIntersects(rr, req)

	if rr.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400 Bad Request on invalid coordinates, got %d", rr.Code)
	}
}
