-- 000001_init_schema.sql
-- Create extension for PostGIS spatial features if enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table for storing field georeferenced records
CREATE TABLE IF NOT EXISTS georef_records (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT DEFAULT '',
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    elevation DOUBLE PRECISION DEFAULT 0.0,
    accuracy DOUBLE PRECISION DEFAULT 0.0,
    metadata_json JSONB DEFAULT '{}'::jsonb,
    client_updated_at BIGINT NOT NULL,
    server_updated_at BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_georef_records_server_updated_at ON georef_records (server_updated_at);
CREATE INDEX IF NOT EXISTS idx_georef_records_client_id ON georef_records (client_id);

-- Idempotency table to store sync batch results
CREATE TABLE IF NOT EXISTS sync_idempotency_logs (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    processed_count INT NOT NULL,
    response_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sync_idempotency_client ON sync_idempotency_logs (client_id);
