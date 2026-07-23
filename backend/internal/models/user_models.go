package models

// User represents an authenticated account in GeoRef
type User struct {
	ID           string `json:"id"`
	Email        string `json:"email"`
	Name         string `json:"name"`
	PasswordHash string `json:"-"`
	CreatedAt    string `json:"created_at,omitempty"`
	UpdatedAt    string `json:"updated_at,omitempty"`
}

// RegisterRequest contains payload for creating a new user account
type RegisterRequest struct {
	Name     string `json:"name"`
	Email    string `json:"email"`
	Password string `json:"password"`
}

// LoginRequest contains payload for user login
type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

// AuthResponse returns token and user info on auth success
type AuthResponse struct {
	Success bool   `json:"success"`
	User    *User  `json:"user,omitempty"`
	Token   string `json:"token,omitempty"`
	Message string `json:"message,omitempty"`
}
