# 📱 GeoRef Lite: Minimalist Field Support Specification

This document details the functional specifications for turning **GeoRef** into a lightweight, high-utility mobile application for field georeferencing, agricultural surveying, and environmental inspection.

---

## 1. Core Use Cases & Field Workflows

```
┌──────────────────┐    ┌─────────────────────────┐    ┌──────────────────────┐
│  GPS Averaging   │───>│ On-Map Measurements     │───>│ Georeferenced Photo  │
│ (High Precision) │    │ (Distance & Hectares)   │    │ (EXIF Metadata)      │
└──────────────────┘    └─────────────────────────┘    └──────────────────────┘
                                                                  │
                                                                  ▼
                                                       ┌──────────────────────┐
                                                       │ Direct KML/GeoJSON   │
                                                       │ Mobile Share Intent  │
                                                       └──────────────────────┘
```

### A. High-Precision GPS Point Averaging
- **Problem**: Instantaneous mobile GPS readings have multipath noise (error range 5m - 15m under trees/clouds).
- **Solution**:
  - Sample GPS positions at **1 Hz** over a **5 to 10-second window** (5-10 samples).
  - Calculate accuracy-weighted centroid position:
    $$\bar{Lat} = \frac{\sum (Lat_i \cdot w_i)}{\sum w_i}, \quad w_i = \frac{1}{Accuracy_i^2}$$
  - Display real-time estimated error radius in meters.

### B. Minimalist On-Map Measurement (Distance & Area in Hectares)
- **Linear Measurement**: Compute Haversine/geodesic distance between tapped waypoints in **meters (m)** and **kilometers (km)**.
- **Polygon Area Measurement**:
  - Compute enclosed surface area using Shoelace formula on projected coordinates or Spherical Area Formula.
  - Automatically format output in **Square Meters ($m^2$)** and **Hectares ($ha$)** ($1 \, ha = 10,000 \, m^2$).
  - Display perimeter length in meters.

### C. Georeferenced Field Photo Evidence
- **EXIF Metadata Tags**:
  - `GPSLatitude` & `GPSLongitude`
  - `GPSImgDirection` (Compass Azimuth 0°-360°)
  - `DateTimeOriginal`
- **Linking**: Automatically attach photos as attachments to GeoJSON points/features.

### D. Instant Field File Sharing
- **Zero-Cloud Requirement**: Enable instant export of single points or layers into `.kml` or `.geojson`.
- **Native Share Intent**: Trigger OS native share menu (WhatsApp, Email, Telegram, Google Drive, AirDrop) to send files directly from field.

---

## 2. Technical Stack & Implementation Guidelines

- **Mobile Client**: Kotlin Multiplatform / React Native (MapLibre / Google Maps SDK).
- **Backend Service**: Go 1.22+ with PostGIS / Spatial SQL.
- **Export Engine**: Lightweight JSON/XML generators.
