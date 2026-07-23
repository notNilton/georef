package main

import (
	"context"
	"fmt"
	"log"
	"net/http"

	"github.com/nilbyte/georef/backend/internal/api"
	"github.com/nilbyte/georef/backend/internal/config"
	"github.com/nilbyte/georef/backend/internal/db"
	"github.com/nilbyte/georef/backend/internal/repository"
	"github.com/nilbyte/georef/backend/internal/sync"
)

func main() {
	log.Println("Starting georef PostGIS Backend Service...")

	cfg := config.LoadConfig()

	// Connect PostgreSQL Pool
	database, err := db.ConnectDB(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}
	defer database.Close()

	// Initialize DB schema automatically
	_ = database.InitSchema(context.Background(), "db/migrations/000001_init_schema.sql")
	_ = database.InitSchema(context.Background(), "db/migrations/000002_gis_layers.sql")
	_ = database.InitSchema(context.Background(), "db/migrations/000003_users.sql")

	repo := repository.NewPostgresRepository(database.Pool)
	gisRepo := repository.NewPostgresGisRepository(database.Pool)
	userRepo := repository.NewUserRepository(database.Pool)
	syncService := sync.NewSyncService(repo)

	server := api.NewServer(syncService, repo, gisRepo, userRepo)

	mux := http.NewServeMux()
	server.RegisterRoutes(mux)

	addr := fmt.Sprintf(":%s", cfg.ServerPort)
	log.Printf("Server listening on HTTP port %s (PostGIS spatial & User Auth ready)", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("Server crashed: %v", err)
	}
}
