# Makefile para GeoRef (Podman/Docker + Go Backend + Kotlin Multiplatform Mobile)

.PHONY: all help db-backend health mobile install-adb clean logs

# Target padrão
all: db-backend health install-adb
	@echo "🚀 Tudo pronto! Banco de Dados rodando, Backend Go no ar e App instalado no celular!"

help:
	@echo "Comandos disponíveis:"
	@echo "  make all         - Sobe banco + backend, compila KMP e instala no Android via ADB"
	@echo "  make db-backend  - Sobe containers do Postgres e Backend Go (Podman/Docker)"
	@echo "  make health      - Testa a saúde da API do Backend Go (http://localhost:8080/health)"
	@echo "  make mobile      - Compila o aplicativo Android em modo Debug"
	@echo "  make install-adb - Instala o app compilado em TODOS os dispositivos ADB conectados"
	@echo "  make logs        - Exibe os logs do banco de dados e backend em tempo real"
	@echo "  make clean       - Para os containers e limpa os arquivos de build"

# 1. Subir Banco de Dados e Backend Go
db-backend:
	@echo "🐘 Subindo PostgreSQL (PostGIS) e Go Backend..."
	@if command -v podman-compose > /dev/null 2>&1; then \
		podman-compose up --build -d; \
	elif command -v docker-compose > /dev/null 2>&1; then \
		docker-compose up --build -d; \
	elif docker compose version > /dev/null 2>&1; then \
		docker compose up --build -d; \
	else \
		echo "❌ Nem podman-compose nem docker-compose encontrados!"; exit 1; \
	fi

# 2. Testar Saúde da API
health:
	@echo "⏳ Verificando saúde do servidor Go HTTP (http://localhost:8080/health)..."
	@sleep 2
	@curl -s http://localhost:8080/health || (echo "\n❌ Servidor Go ainda não está pronto." && exit 1)
	@echo "\n✅ Servidor Go respondendo com sucesso!"

# 3. Compilar APK Android
mobile:
	@echo "📦 Compilando módulo KMP e APK Android (:androidApp:assembleDebug)..."
	@cd mobile && ./gradlew :androidApp:assembleDebug

# 4. Instalar em todos os dispositivos ADB conectados
install-adb: mobile
	@echo "📱 Detectando dispositivos Android via ADB..."
	@DEVICES=$$(adb devices | grep -v 'List' | grep 'device$$' | awk '{print $$1}'); \
	if [ -z "$$DEVICES" ]; then \
		echo "⚠️  Nenhum dispositivo Android conectado via ADB!"; \
		exit 1; \
	fi; \
	for dev in $$DEVICES; do \
		echo "📲 Instalando no dispositivo ADB: $$dev..."; \
		adb -s $$dev install -r mobile/androidApp/build/outputs/apk/debug/androidApp-debug.apk; \
		echo "🚀 Abrindo GeoRef no dispositivo: $$dev..."; \
		adb -s $$dev shell am start -n com.nilbyte.georef.android/.MainActivity > /dev/null 2>&1 || true; \
	done; \
	echo "✅ Instalação via ADB concluída em todos os dispositivos!"

# Exibir logs
logs:
	@if command -v podman-compose > /dev/null 2>&1; then \
		podman-compose logs -f; \
	else \
		docker-compose logs -f; \
	fi

# Limpeza
clean:
	@echo "🧹 Parando containers e limpando build..."
	@if command -v podman-compose > /dev/null 2>&1; then \
		podman-compose down; \
	else \
		docker-compose down; \
	fi
	@cd mobile && ./gradlew clean
