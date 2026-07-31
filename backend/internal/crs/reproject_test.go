package crs

import (
	"math"
	"testing"
)

func TestWGS84ToUTM(t *testing.T) {
	// Coordenadas de São Paulo: -23.5505, -46.6333 (UTM Zone 23S)
	ll := LatLng{Lat: -23.5505, Lng: -46.6333}
	utm := WGS84ToUTM(ll, 23)

	if utm.X <= 0 || utm.Y <= 0 {
		t.Errorf("Invalid UTM coordinate generated: %+v", utm)
	}

	// Verification: Easting should be around 333,370m and Northing around 7,395,000m
	if math.Abs(utm.X-333370) > 10000 {
		t.Errorf("UTM Easting out of expected range: %f", utm.X)
	}
}
