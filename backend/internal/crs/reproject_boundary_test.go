package crs

import (
	"math"
	"testing"
)

func TestWGS84ToUTMEquatorAndPolar(t *testing.T) {
	// Test Point at Equator & Central Meridian of Zone 31 (lng = 3.0 E)
	eq := LatLng{Lat: 0.0, Lng: 3.0}
	utmEq := WGS84ToUTM(eq, 31)

	if math.Abs(utmEq.X-500000.0) > 1.0 {
		t.Errorf("Expected Easting near 500,000 at Central Meridian, got %f", utmEq.X)
	}

	if math.Abs(utmEq.Y-0.0) > 1.0 {
		t.Errorf("Expected Northing near 0 at Equator in Northern Hemisphere, got %f", utmEq.Y)
	}

	// Test Southern Hemisphere Equator Offset (-0.0001, 0.0)
	eqSouth := LatLng{Lat: -0.0001, Lng: 0.0}
	utmSouth := WGS84ToUTM(eqSouth, 31)

	if utmSouth.Y < 9999000.0 {
		t.Errorf("Expected Southern Hemisphere false northing (~10,000,000m), got %f", utmSouth.Y)
	}
}

func TestWGS84ToUTMZoneBoundaries(t *testing.T) {
	// Test Zone 1 (Longitude -177 W)
	z1Point := LatLng{Lat: -12.0, Lng: -177.0}
	utmZ1 := WGS84ToUTM(z1Point, 1)

	if utmZ1.X <= 0 || utmZ1.Y <= 0 {
		t.Errorf("Invalid coordinates for Zone 1: %+v", utmZ1)
	}
}
