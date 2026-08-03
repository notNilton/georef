package models

import (
	"encoding/json"
	"testing"
)

func TestGeorefRecordSerialization(t *testing.T) {
	rec := GeorefRecord{
		ID:           "rec-test",
		ClientID:     "client-1",
		Name:         "Ponto Teste",
		Latitude:     -23.55,
		Longitude:    -46.63,
		MetadataJSON: json.RawMessage(`{"accuracy":5.2}`),
	}

	data, err := json.Marshal(rec)
	if err != nil {
		t.Fatalf("Failed to marshal GeorefRecord: %v", err)
	}

	var unmarshaled GeorefRecord
	if err := json.Unmarshal(data, &unmarshaled); err != nil {
		t.Fatalf("Failed to unmarshal GeorefRecord: %v", err)
	}

	if unmarshaled.Name != rec.Name || unmarshaled.Latitude != rec.Latitude {
		t.Errorf("Mismatch in serialization data")
	}
}

func TestGisLayerRecordSerialization(t *testing.T) {
	gis := GisLayerRecord{
		ID:             "layer-test",
		ClientID:       "client-1",
		Name:           "Regiao A",
		FileType:       FileTypeGeoJSON,
		MinLat:         -23.6,
		MaxLat:         -23.5,
		GeoJSONPayload: json.RawMessage(`{"type":"Polygon"}`),
	}

	data, err := json.Marshal(gis)
	if err != nil {
		t.Fatalf("Failed to marshal GisLayerRecord: %v", err)
	}

	var unmarshaled GisLayerRecord
	if err := json.Unmarshal(data, &unmarshaled); err != nil {
		t.Fatalf("Failed to unmarshal GisLayerRecord: %v", err)
	}

	if unmarshaled.FileType != FileTypeGeoJSON {
		t.Errorf("Expected FileType GEOJSON, got %s", unmarshaled.FileType)
	}
}
