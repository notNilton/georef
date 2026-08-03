package config

import (
	"os"
	"testing"
)

func TestLoadConfigDefaults(t *testing.T) {
	os.Unsetenv("DB_HOST")
	os.Unsetenv("SERVER_PORT")

	cfg := LoadConfig()
	if cfg.DBHost != "localhost" {
		t.Errorf("Expected default DBHost localhost, got %s", cfg.DBHost)
	}

	if cfg.ServerPort != "8080" {
		t.Errorf("Expected default ServerPort 8080, got %s", cfg.ServerPort)
	}

	dsn := cfg.DatabaseDSN()
	expected := "postgres://georef_user:georef_password@localhost:5432/georef_db?sslmode=disable"
	if dsn != expected {
		t.Errorf("Expected DSN %s, got %s", expected, dsn)
	}
}

func TestLoadConfigCustomEnv(t *testing.T) {
	os.Setenv("DB_HOST", "postgres-prod")
	os.Setenv("SERVER_PORT", "9090")

	defer func() {
		os.Unsetenv("DB_HOST")
		os.Unsetenv("SERVER_PORT")
	}()

	cfg := LoadConfig()
	if cfg.DBHost != "postgres-prod" {
		t.Errorf("Expected DBHost postgres-prod, got %s", cfg.DBHost)
	}

	if cfg.ServerPort != "9090" {
		t.Errorf("Expected ServerPort 9090, got %s", cfg.ServerPort)
	}
}
