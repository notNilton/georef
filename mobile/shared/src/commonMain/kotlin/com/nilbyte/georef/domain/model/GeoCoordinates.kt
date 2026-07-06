package com.nilbyte.georef.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double = 0.0
) {
    /**
     * Convert decimal degrees to Degrees Minutes Seconds (DMS) format.
     * Example: 23° 33' 1.80" S, 46° 37' 59.88" W
     */
    fun toDmsString(): String {
        fun formatSingle(deg: Double, isLat: Boolean): String {
            val absDeg = abs(deg)
            val degrees = absDeg.toInt()
            val minutesDouble = (absDeg - degrees) * 60.0
            val minutes = minutesDouble.toInt()
            val seconds = (minutesDouble - minutes) * 60.0

            val direction = if (isLat) {
                if (deg >= 0) "N" else "S"
            } else {
                if (deg >= 0) "E" else "W"
            }

            return "$degrees° $minutes' ${seconds.format(2)}\" $direction"
        }

        return "${formatSingle(latitude, true)}, ${formatSingle(longitude, false)}"
    }

    /**
     * Distance to another point in meters using the Haversine formula.
     */
    fun distanceToInMeters(other: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = (other.latitude - latitude).toRadians()
        val dLng = (other.longitude - longitude).toRadians()

        val a = sin(dLat / 2).pow(2) +
                cos(latitude.toRadians()) * cos(other.latitude.toRadians()) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun Double.toRadians(): Double = this * (PI / 180.0)
    private fun Double.format(digits: Int): String {
        val factor = 10.0.pow(digits)
        return ((this * factor).roundToInt() / factor).toString()
    }
}

@Serializable
data class GeoBoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    val center: GeoPoint
        get() = GeoPoint((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)

    fun contains(point: GeoPoint): Boolean {
        return point.latitude in minLat..maxLat && point.longitude in minLng..maxLng
    }

    /**
     * Calculate tile coordinate grid (Z/X/Y) covering this bounding box for given zoom levels.
     */
    fun getTileGrid(minZoom: Int, maxZoom: Int): List<GeoTile> {
        val tiles = mutableListOf<GeoTile>()
        for (zoom in minZoom..maxZoom) {
            val minTile = GeoTile.fromGeoPoint(GeoPoint(maxLat, minLng), zoom)
            val maxTile = GeoTile.fromGeoPoint(GeoPoint(minLat, maxLng), zoom)

            for (x in minTile.x..maxTile.x) {
                for (y in minTile.y..maxTile.y) {
                    tiles.add(GeoTile(zoom = zoom, x = x, y = y))
                }
            }
        }
        return tiles
    }
}

@Serializable
data class GeoTile(
    val zoom: Int,
    val x: Int,
    val y: Int
) {
    val tileId: String
        get() = "$zoom/$x/$y"

    val tileUrl: String
        get() = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

    companion object {
        fun fromGeoPoint(point: GeoPoint, zoom: Int): GeoTile {
            val latRad = point.latitude * (PI / 180.0)
            val n = 2.0.pow(zoom)
            val xtile = ((point.longitude + 180.0) / 360.0 * n).toInt()
            val ytile = ((1.0 - sinh(asinh(tan(latRad))) / PI) / 2.0 * n).toInt()
            return GeoTile(zoom = zoom, x = xtile, y = ytile)
        }
    }
}
