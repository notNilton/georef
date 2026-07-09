-- 000002_gis_layers.sql
-- Enable PostGIS extension for spatial GIS calculations
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Table for storing imported region maps and GIS spatial layers
CREATE TABLE IF NOT EXISTS gis_layers (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL, -- GEOPDF, GEOJSON, KML, GEOTIFF
    min_lat DOUBLE PRECISION NOT NULL,
    min_lng DOUBLE PRECISION NOT NULL,
    max_lat DOUBLE PRECISION NOT NULL,
    max_lng DOUBLE PRECISION NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL,
    -- PostGIS spatial geometry column (SRID 4326 - WGS84)
    bbox_geom GEOMETRY(Polygon, 4326),
    center_geom GEOMETRY(Point, 4326),
    geojson_payload JSONB DEFAULT '{}'::jsonb,
    client_updated_at BIGINT NOT NULL,
    server_updated_at BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- PostGIS Spatial GIST Indexes for fast spatial intersection queries
CREATE INDEX IF NOT EXISTS idx_gis_layers_bbox_gist ON gis_layers USING GIST (bbox_geom);
CREATE INDEX IF NOT EXISTS idx_gis_layers_center_gist ON gis_layers USING GIST (center_geom);
CREATE INDEX IF NOT EXISTS idx_gis_layers_server_updated_at ON gis_layers (server_updated_at);
