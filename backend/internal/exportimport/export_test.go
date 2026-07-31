package exportimport

import (
	"testing"

	"github.com/nilbyte/georef/backend/internal/models"
)

func TestExportToGeoJSON(t *testing.T) {
	layers := []models.GisLayerRecord{
		{
			ID:             "layer-1",
			Name:           "Campo Ponto A",
			FileType:       models.FileTypeGeoJSON,
			GeoJSONPayload: []byte(`{"type":"Point","coordinates":[-46.6333,-23.5505]}`),
			ClientID:       "client-test",
		},
	}

	data, err := ExportToGeoJSON(layers)
	if err != nil {
		t.Fatalf("Failed to export GeoJSON: %v", err)
	}

	if len(data) == 0 {
		t.Errorf("Expected non-empty GeoJSON output")
	}
}

func TestExportToKML(t *testing.T) {
	layers := []models.GisLayerRecord{
		{
			ID:       "layer-1",
			Name:     "Campo Ponto A",
			FileType: models.FileTypeGeoJSON,
			ClientID: "client-test",
		},
	}

	kml := ExportToKML(layers)
	if kml == "" {
		t.Errorf("Expected non-empty KML output")
	}
}
