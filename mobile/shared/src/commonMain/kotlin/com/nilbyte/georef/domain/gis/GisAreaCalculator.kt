package com.nilbyte.georef.domain.gis

import com.nilbyte.georef.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision WGS84 Geodesic Area and Distance Calculator for Agricultural & Field GIS.
 */
object GisAreaCalculator {

    private const val EARTH_RADIUS_METERS = 6371008.8

    /**
     * Calculates polygon surface area in Hectares (ha) using WGS84 spherical projection.
     */
    fun calculateAreaHectares(coords: List<GeoPoint>): Double {
        val areaSquareMeters = calculateAreaSquareMeters(coords)
        return areaSquareMeters / 10000.0
    }

    /**
     * Calculates polygon surface area in Square Meters (m²).
     */
    fun calculateAreaSquareMeters(coords: List<GeoPoint>): Double {
        if (coords.size < 3) return 0.0

        var total = 0.0
        val numPoints = coords.size

        for (i in 0 until numPoints) {
            val p1 = coords[i]
            val p2 = coords[(i + 1) % numPoints]

            val lat1 = p1.latitude * (PI / 180.0)
            val lat2 = p2.latitude * (PI / 180.0)
            val lng1 = p1.longitude * (PI / 180.0)
            val lng2 = p2.longitude * (PI / 180.0)

            total += (lng2 - lng1) * (2.0 + sin(lat1) + sin(lat2))
        }

        total = total * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2.0
        return abs(total)
    }

    /**
     * Calculates total perimeter length in Kilometers (km).
     */
    fun calculatePerimeterKm(coords: List<GeoPoint>): Double {
        if (coords.size < 2) return 0.0

        var totalMeters = 0.0
        for (i in 0 until coords.size - 1) {
            totalMeters += distanceMeters(coords[i], coords[i + 1])
        }
        if (coords.size >= 3) {
            totalMeters += distanceMeters(coords.last(), coords.first())
        }

        return totalMeters / 1000.0
    }

    /**
     * Haversine distance in meters between two coordinates.
     */
    fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val dLat = (p2.latitude - p1.latitude) * (PI / 180.0)
        val dLng = (p2.longitude - p1.longitude) * (PI / 180.0)

        val lat1 = p1.latitude * (PI / 180.0)
        val lat2 = p2.latitude * (PI / 180.0)

        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                sin(dLng / 2.0) * sin(dLng / 2.0) * cos(lat1) * cos(lat2)

        val c = 2.0 * kotlin.math.atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }
}
