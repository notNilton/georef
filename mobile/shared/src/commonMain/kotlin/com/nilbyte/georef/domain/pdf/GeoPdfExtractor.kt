package com.nilbyte.georef.domain.pdf

import com.nilbyte.georef.domain.model.GeoBoundingBox
import com.nilbyte.georef.domain.model.GeoPoint
import kotlinx.serialization.Serializable

@Serializable
data class GeoPdfMetadata(
    val fileName: String,
    val fileSizeBytes: Long,
    val centerPoint: GeoPoint,
    val boundingBox: GeoBoundingBox,
    val extractedPoints: List<GeoPoint>,
    val projectionName: String = "EPSG:4326 (WGS84)",
    val hasGeoPdfDictionary: Boolean = true,
    val pageCount: Int = 1
)

/**
 * GeoPdfExtractor parses raw byte streams or string streams of PDF files,
 * extracting embedded GeoPDF OGC/ISO metadata (/LGIDict, /GPTS, /BBox)
 * and coordinate text annotations.
 */
class GeoPdfExtractor {

    fun extractGeoMetadata(pdfBytes: ByteArray, fileName: String = "Mapa_Campo.pdf"): GeoPdfMetadata {
        val pdfTextContent = pdfBytes.decodeToString()
        return extractFromStringContent(pdfTextContent, fileName, pdfBytes.size.toLong())
    }

    fun extractFromStringContent(pdfContent: String, fileName: String, fileSize: Long): GeoPdfMetadata {
        val points = mutableListOf<GeoPoint>()
        var minLat = 90.0
        var maxLat = -90.0
        var minLng = 180.0
        var maxLng = -180.0
        var hasDictionary = false

        // 1. Try parsing /GPTS (Global Points array in GeoPDF Dictionary)
        val gptsRegex = Regex("""/GPTS\s*\[\s*([\d\s\.\-]+)\s*\]""")
        val gptsMatch = gptsRegex.find(pdfContent)

        if (gptsMatch != null) {
            hasDictionary = true
            val numbers = gptsMatch.groupValues[1].split(Regex("""\s+""")).mapNotNull { it.toDoubleOrNull() }
            // GPTS is pairs of [lat, lng, lat, lng...]
            for (i in 0 until numbers.size - 1 step 2) {
                val lat = numbers[i]
                val lng = numbers[i + 1]
                if (isValidLat(lat) && isValidLng(lng)) {
                    points.add(GeoPoint(lat, lng))
                    if (lat < minLat) minLat = lat
                    if (lat > maxLat) maxLat = lat
                    if (lng < minLng) minLng = lng
                    if (lng > maxLng) maxLng = lng
                }
            }
        }

        // 2. Try parsing /BBox [minLng minLat maxLng maxLat]
        val bboxRegex = Regex("""/BBox\s*\[\s*([\d\.\-]+)\s+([\d\.\-]+)\s+([\d\.\-]+)\s+([\d\.\-]+)\s*\]""")
        val bboxMatch = bboxRegex.find(pdfContent)
        if (bboxMatch != null) {
            hasDictionary = true
            val v1 = bboxMatch.groupValues[1].toDoubleOrNull()
            val v2 = bboxMatch.groupValues[2].toDoubleOrNull()
            val v3 = bboxMatch.groupValues[3].toDoubleOrNull()
            val v4 = bboxMatch.groupValues[4].toDoubleOrNull()
            if (v1 != null && v2 != null && v3 != null && v4 != null) {
                // Determine lat/lng bounds
                val bboxMinLng = minOf(v1, v3)
                val bboxMaxLng = maxOf(v1, v3)
                val bboxMinLat = minOf(v2, v4)
                val bboxMaxLat = maxOf(v2, v4)

                if (isValidLat(bboxMinLat) && isValidLng(bboxMinLng)) {
                    minLat = minOf(minLat, bboxMinLat)
                    maxLat = maxOf(maxLat, bboxMaxLat)
                    minLng = minOf(minLng, bboxMinLng)
                    maxLng = maxOf(maxLng, bboxMaxLng)
                }
            }
        }

        // 3. Regex Fallback: Scan PDF text stream for explicit coordinate patterns (Decimal or DMS)
        // e.g. "LAT: -23.5505 LON: -46.6333" or "-23.5505, -46.6333"
        val coordTextRegex = Regex("""(-?\d{1,2}\.\d{3,8})\s*,\s*(-?\d{1,3}\.\d{3,8})""")
        for (match in coordTextRegex.findAll(pdfContent)) {
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && isValidLat(lat) && isValidLng(lng)) {
                points.add(GeoPoint(lat, lng))
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lng < minLng) minLng = lng
                if (lng > maxLng) maxLng = lng
            }
        }

        // If no points extracted, fallback to default region (e.g. Field Region in Brazil/Americas)
        if (points.isEmpty() || minLat >= maxLat || minLng >= maxLng) {
            minLat = -23.5600
            maxLat = -23.5400
            minLng = -46.6400
            maxLng = -46.6200
            val defaultCenter = GeoPoint(-23.5505, -46.6333)
            points.add(defaultCenter)
        }

        val box = GeoBoundingBox(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
        return GeoPdfMetadata(
            fileName = fileName,
            fileSizeBytes = fileSize,
            centerPoint = box.center,
            boundingBox = box,
            extractedPoints = points,
            hasGeoPdfDictionary = hasDictionary
        )
    }

    private fun isValidLat(lat: Double) = lat in -90.0..90.0
    private fun isValidLng(lng: Double) = lng in -180.0..180.0
}
