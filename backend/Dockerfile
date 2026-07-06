# Build stage
FROM docker.io/library/golang:1.26-alpine AS builder

WORKDIR /app

COPY go.mod go.sum* ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /app/georef-backend main.go

# Final light container stage
FROM docker.io/library/alpine:3.19

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app
COPY --from=builder /app/georef-backend .
COPY --from=builder /app/db/migrations ./db/migrations

EXPOSE 8080

CMD ["./georef-backend"]
