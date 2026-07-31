package crs

import (
	"math"
)

// Point2D represents a 2D coordinate pair
type Point2D struct {
	X float64 `json:"x"`
	Y float64 `json:"y"`
}

// LatLng represents geographic latitude/longitude in WGS84 (EPSG:4326)
type LatLng struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

const (
	WGS84SemiMajorAxis = 6378137.0         // meters
	WGS84Flattening    = 1.0 / 298.257223563
)

// WGS84ToUTM converts geographic WGS84 (EPSG:4326) to UTM projected coordinates.
// zone parameter specifies the UTM zone (1 to 60).
func WGS84ToUTM(ll LatLng, zone int) Point2D {
	latRad := ll.Lat * math.Pi / 180.0
	lngRad := ll.Lng * math.Pi / 180.0

	centralMeridian := float64((zone-1)*6-180+3) * math.Pi / 180.0

	k0 := 0.9996
	e := math.Sqrt(2*WGS84Flattening - WGS84Flattening*WGS84Flattening)

	N := WGS84SemiMajorAxis / math.Sqrt(1-e*e*math.Sin(latRad)*math.Sin(latRad))
	T := math.Tan(latRad) * math.Tan(latRad)
	C := (e * e / (1 - e * e)) * math.Cos(latRad) * math.Cos(latRad)
	A := (lngRad - centralMeridian) * math.Cos(latRad)

	M := WGS84SemiMajorAxis * ((1 - e*e/4 - 3*e*e*e*e/64 - 5*e*e*e*e*e*e/256)*latRad -
		(3*e*e/8 + 3*e*e*e*e/32 + 45*e*e*e*e*e*e/1024)*math.Sin(2*latRad) +
		(15*e*e*e*e/256 + 45*e*e*e*e*e*e/1024)*math.Sin(4*latRad) -
		(35*e*e*e*e*e*e/3072)*math.Sin(6*latRad))

	easting := k0*N*(A+(1-T+C)*A*A*A/6.0+(5-18*T+T*T+72*C-58*(e*e/(1-e*e)))*A*A*A*A*A/120.0) + 500000.0

	northing := k0 * (M + N*math.Tan(latRad)*(A*A/2.0+(5-T+9*C+4*C*C)*A*A*A*A/24.0+(61-58*T+T*T+600*C-330*(e*e/(1-e*e)))*A*A*A*A*A*A/720.0))
	if ll.Lat < 0 {
		northing += 10000000.0 // False northing for southern hemisphere
	}

	return Point2D{X: easting, Y: northing}
}
