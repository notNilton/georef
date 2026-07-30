# 📋 GeoRef Roadmap & TODOs

Roadmap and pending tasks for the **GeoRef** ecosystem (Go Backend + Kotlin Multiplatform Mobile).

---

## ⚙️ 1. Backend & GIS Engineering (Go + PostGIS)
- [ ] **Support for Multiple Geospatial Formats**
  - Add endpoints in Go for exporting and importing **GeoJSON**, **KML**, **Shapefile (.shp)**, and **GeoPackage (.gpkg)**.
- [ ] **Topological Geometry Validation**
  - Integrate PostGIS routines (, ) into feature saving workflows to prevent invalid geometries.
- [ ] **CRS / SRID Reprojection**
  - Add automatic reprojection between **EPSG:4326** (WGS 84) and local projected UTM systems (e.g., **EPSG:31983** - SIRGAS 2000 UTM Zone 23S).
- [ ] **OpenAPI / Swagger Documentation**
  - Configure automatic OpenAPI spec generation in Go to integrate with external GIS software (QGIS, ArcGIS).

---

## 📱 2. Mobile & Field Experience (Kotlin Multiplatform / React Native)
- [ ] **Robust Offline-First Sync Queue**
  - Enhance sync engine with persistent queue support () and configurable conflict resolution upon reconnecting.
- [ ] **Advanced Map Tile Caching**
  - Add download progress tracking and estimated MB size for offline tile caching of a geographic region.
- [ ] **Georeferenced Media Capture**
  - Implement photo capture with automatic GPS coordinate and compass azimuth metadata embedded in EXIF.
- [ ] **GPS Sampling & Coordinate Averaging**
  - Add high-precision field mode that samples multiple GPS points over a time window to calculate accuracy-weighted average coordinates.

---

## 🚀 3. CI/CD & Deployment
- [ ] **GitHub Actions CI/CD Pipeline**
  - Configure workflows for Go backend tests (FAIL	./... [setup failed]
FAIL) and Docker build validations.
  - Configure workflows for automated mobile artifact builds: Android App Bundle () and iOS Framework ().
- [ ] **Privacy Policy & Terms**
  - Create GIS Data Privacy Policy document required for Google Play Console and Apple App Store Connect submissions.
