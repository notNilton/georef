package exportimport

import (
	"encoding/json"
	"fmt"

	"github.com/nilbyte/georef/backend/internal/models"
)

type GeoJSONFeature struct {
	Type       string                 `json:"type"`
	Geometry   map[string]interface{} `json:"geometry"`
	Properties map[string]interface{} `json:"properties"`
}

type GeoJSONFeatureCollection struct {
	Type     string           `json:"type"`
	Features []GeoJSONFeature `json:"features"`
}

// ExportToGeoJSON converts GIS layers into a standard GeoJSON FeatureCollection
func ExportToGeoJSON(layers []models.GisLayerRecord) ([]byte, error) {
	features := make([]GeoJSONFeature, 0, len(layers))

	for _, layer := range layers {
		var geom map[string]interface{}
		if len(layer.GeoJSONPayload) > 0 {
			_ = json.Unmarshal(layer.GeoJSONPayload, &geom)
		}
		if geom == nil {
			// Fallback bounding box geometry
			geom = map[string]interface{}{
				"type": "Polygon",
				"coordinates": [][][]float64{
					{
						{layer.MinLng, layer.MinLat},
						{layer.MaxLng, layer.MinLat},
						{layer.MaxLng, layer.MaxLat},
						{layer.MinLng, layer.MaxLat},
						{layer.MinLng, layer.MinLat},
					},
				},
			}
		}

		props := map[string]interface{}{
			"id":         layer.ID,
			"layer_name": layer.Name,
			"file_type":  layer.FileType,
			"client_id":  layer.ClientID,
			"updated_at": layer.ServerUpdatedAt,
		}

		features = append(features, GeoJSONFeature{
			Type:       "Feature",
			Geometry:   geom,
			Properties: props,
		})
	}

	fc := GeoJSONFeatureCollection{
		Type:     "FeatureCollection",
		Features: features,
	}

	return json.MarshalIndent(fc, "", "  ")
}

// ExportToKML converts GIS layers into a basic KML XML structure
func ExportToKML(layers []models.GisLayerRecord) string {
	kml := `<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>GeoRef GIS Export</name>
`

	for _, layer := range layers {
		kml += fmt.Sprintf(`    <Placemark>
      <name>%s</name>
      <description>File Type: %s | ID: %s</description>
      <ExtendedData>
        <Data name="client_id"><value>%s</value></Data>
      </ExtendedData>
    </Placemark>
`, layer.Name, layer.FileType, layer.ID, layer.ClientID)
	}

	kml += `  </Document>
</kml>`

	return kml
}
