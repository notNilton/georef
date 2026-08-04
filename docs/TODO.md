# 📋 GeoRef Roadmap & TODOs

Roadmap and pending tasks for the **GeoRef** ecosystem (Go Backend + Kotlin Multiplatform Mobile).

---

## 🎯 Minimalist Field Support Requirements (GeoRef Lite Concept)

To evolve **GeoRef** into an essential, lightweight field support tool for topographers, agronomists, and environmental researchers, the following core capabilities are defined:

1. **High-Precision GPS Sampling (Averaging Mode)**
   - Collect multiple GPS fixes over 5-10 seconds and compute accuracy-weighted average coordinates.
2. **On-Map Spatial Measurements**
   - Instant calculation of linear distances (meters/km), polygon perimeters, and polygon areas in **Hectares (`ha`)** and **Square Meters (`m²`)**.
3. **Georeferenced Photo Evidence**
   - Capture photos with embedded EXIF metadata (Latitude, Longitude, Compass Azimuth, Timestamp) attached to GIS markers.
4. **Native Mobile Export & Share**
   - Direct one-click sharing of `.kml` and `.geojson` files via mobile intent (WhatsApp, Email, Drive) for instant field-to-office communication.

---

## ⚙️ 1. Backend & GIS Engineering (Go + PostGIS)
- [x] **Support for Multiple Geospatial Formats**
  - Added Go endpoints `/api/v1/gis/export/geojson` and `/api/v1/gis/export/kml` for exporting GIS layers.
- [ ] **Topological Geometry Validation**
  - Integrate PostGIS routines (`ST_IsValid`, `ST_MakeValid`) into feature saving workflows to prevent invalid geometries.
- [x] **CRS / SRID Reprojection**
  - Added `crs` package with mathematical WGS84 to UTM (Zone 1-60) coordinate reprojection algorithm.
- [ ] **OpenAPI / Swagger Documentation**
  - Configure automatic OpenAPI spec generation in Go to integrate with external GIS software (QGIS, ArcGIS).

---

## 📱 2. Mobile & Field Experience (Kotlin Multiplatform / React Native)
- [ ] **GPS Sampling & Coordinate Averaging**
  - Add high-precision field mode that samples multiple GPS points over a time window to calculate accuracy-weighted average coordinates.
- [ ] **Minimalist Map Measurement Tool (Distance & Area in Hectares)**
  - Interactive map drawing tool for measuring linear distances and polygon areas in `m²` and `ha`.
- [ ] **Georeferenced Media Capture**
  - Implement photo capture with automatic GPS coordinate and compass azimuth metadata embedded in EXIF.
- [ ] **Direct Mobile KML/GeoJSON File Sharing**
  - Native share intent integration to send `.kml`/`.geojson` files via WhatsApp, Email, or Drive directly from field.
- [ ] **Robust Offline-First Sync Queue**
  - Enhance sync engine with persistent queue support (`Queue Sync`) and configurable conflict resolution upon reconnecting.
- [ ] **Advanced Map Tile Caching**
  - Add download progress tracking and estimated MB size for offline tile caching of a geographic region.

---

## 🚀 3. CI/CD & Deployment
- [x] **GitHub Actions CI/CD Pipeline**
  - Created `.github/workflows/ci.yml` workflow for automated backend testing and mobile Gradle validation.
- [x] **Privacy Policy & Terms**
  - Created `PRIVACY_POLICY.md` required for Google Play Console and Apple App Store submissions.
