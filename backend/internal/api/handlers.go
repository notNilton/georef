package api

import (
	"encoding/json"
	"net/http"
	"strconv"

	"georef/backend/internal/models"
	"georef/backend/internal/repository"
	"georef/backend/internal/sync"
)

type Server struct {
	syncService *sync.SyncService
	repo        repository.Repository
}

func NewServer(syncService *sync.SyncService, repo repository.Repository) *Server {
	return &Server{
		syncService: syncService,
		repo:        repo,
	}
}

func (s *Server) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("/health", s.HandleHealth)
	mux.HandleFunc("/api/v1/sync/push", s.HandleSyncPush)
	mux.HandleFunc("/api/v1/sync/pull", s.HandleSyncPull)
	mux.HandleFunc("/api/v1/records", s.HandleGetRecords)
}

func (s *Server) HandleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{
		"status": "UP",
		"service": "georef-backend",
	})
}

func (s *Server) HandleSyncPush(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req models.SyncPushRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request payload: "+err.Error(), http.StatusBadRequest)
		return
	}

	if req.BatchID == "" || req.ClientID == "" {
		http.Error(w, "Missing required batch_id or client_id", http.StatusBadRequest)
		return
	}

	resp, err := s.syncService.ProcessSyncPush(r.Context(), &req)
	if err != nil {
		http.Error(w, "Sync processing error: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(resp)
}

func (s *Server) HandleSyncPull(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	clientID := r.URL.Query().Get("client_id")
	sinceStr := r.URL.Query().Get("since_server")
	limitStr := r.URL.Query().Get("limit")

	var since int64 = 0
	if sinceStr != "" {
		sVal, err := strconv.ParseInt(sinceStr, 10, 64)
		if err == nil {
			since = sVal
		}
	}

	limit := 100
	if limitStr != "" {
		lVal, err := strconv.Atoi(limitStr)
		if err == nil && lVal > 0 {
			limit = lVal
		}
	}

	req := models.SyncPullRequest{
		ClientID:    clientID,
		SinceServer: since,
		Limit:       limit,
	}

	resp, err := s.syncService.ProcessSyncPull(r.Context(), &req)
	if err != nil {
		http.Error(w, "Error executing pull sync: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
}

func (s *Server) HandleGetRecords(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	records, err := s.repo.GetAllRecords(r.Context(), 100)
	if err != nil {
		http.Error(w, "Database error: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(records)
}
