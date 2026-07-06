package main

import (
	"context"
	"fmt"
	"log"
	"net/http"

	"georef/backend/internal/api"
	"georef/backend/internal/config"
	"georef/backend/internal/db"
	"georef/backend/internal/repository"
	"georef/backend/internal/sync"
)

func main() {
	log.Println("Starting georef Backend Service...")

	cfg := config.LoadConfig()

	// Connect PostgreSQL Pool
	database, err := db.ConnectDB(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}
	defer database.Close()

	// Initialize DB schema automatically if migration file exists
	migrationPath := "db/migrations/000001_init_schema.sql"
	if err := database.InitSchema(context.Background(), migrationPath); err != nil {
		log.Printf("Warning: Schema migration skipped/deferred: %v", err)
	}

	repo := repository.NewPostgresRepository(database.Pool)
	syncService := sync.NewSyncService(repo)
	server := api.NewServer(syncService, repo)

	mux := http.NewServeMux()
	server.RegisterRoutes(mux)

	addr := fmt.Sprintf(":%s", cfg.ServerPort)
	log.Printf("Server listening on HTTP port %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("Server crashed: %v", err)
	}
}
