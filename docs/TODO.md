# 📋 GeoRef Roadmap & TODOs

Roadmap and pending tasks for the **GeoRef** ecosystem (Go Backend + Kotlin Multiplatform Mobile).

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
- [ ] **Robust Offline-First Sync Queue**
  - Enhance sync engine with persistent queue support (`Queue Sync`) and configurable conflict resolution upon reconnecting.
- [ ] **Advanced Map Tile Caching**
  - Add download progress tracking and estimated MB size for offline tile caching of a geographic region.
- [ ] **Georeferenced Media Capture**
  - Implement photo capture with automatic GPS coordinate and compass azimuth metadata embedded in EXIF.
- [ ] **GPS Sampling & Coordinate Averaging**
  - Add high-precision field mode that samples multiple GPS points over a time window to calculate accuracy-weighted average coordinates.

---

## 🚀 3. CI/CD & Deployment
- [x] **GitHub Actions CI/CD Pipeline**
  - Created `.github/workflows/ci.yml` workflow for automated backend testing and mobile Gradle validation.
- [x] **Privacy Policy & Terms**
  - Created `PRIVACY_POLICY.md` required for Google Play Console and Apple App Store submissions.
