# 📋 GeoRef Roadmap & TODOs

Este documento descreve o plano de melhorias e tarefas pendentes (TODOs) para o ecossistema **GeoRef** (Backend Go + Mobile Kotlin Multiplatform).

---

## ⚙️ 1. Backend & Engenharia GIS (Go + PostGIS)

- [ ] **Suporte a Múltiplos Formatos Geoespaciais**
  - Implementar endpoints no backend Go para exportação e importação de **GeoJSON**, **KML**, **Shapefile (.shp)** e **GeoPackage (.gpkg)**.
- [ ] **Validação Topológica de Geometrias**
  - Integrar rotinas de validação PostGIS (`ST_IsValid`, `ST_MakeValid`) no fluxo de salvamento de feições para evitar geometrias inválidas.
- [ ] **Suporte e Reprojeção de CRS / SRID**
  - Adicionar reprojeção automática entre **EPSG:4326** (WGS 84) e sistemas projetados locais/UTM (ex: **EPSG:31983** - SIRGAS 2000 UTM Zone 23S).
- [ ] **Documentação OpenAPI / Swagger**
  - Configurar geração automática de especificação OpenAPI (Swagger) no backend em Go para integrar com ferramentas GIS externas (QGIS, ArcGIS).

---

## 📱 2. Mobile & Experiência no Campo (Kotlin Multiplatform / React Native)

- [ ] **Fila de Sincronização Offline-First Robusta**
  - Aprimorar o motor de sincronização com suporte a fila persistente (`Queue Sync`) e resolução configurável de conflitos ao reconectar à internet.
- [ ] **Gerenciamento Avançado de Cache de Mapas (Tiles)**
  - Adicionar visualização de progresso e estimativa de tamanho em MB para o download de tiles offline de uma região geográfica.
- [ ] **Captura de Mídia Georreferenciada**
  - Implementar captura de fotos com gravação automática de coordenadas GPS e azimute/direção nos metadados EXIF.
- [ ] **Média de Coordenadas GPS (Amostragem de Sinal)**
  - Adicionar modo de alta precisão no campo que coleta múltiplas amostras de sinal GPS durante um intervalo de tempo e calcula a coordenada média ajustada pela acurácia.

---

## 🚀 3. CI/CD & Deploy

- [ ] **Pipeline de CI/CD via GitHub Actions**
  - Configurar workflow para execução de testes automatizados do backend Go (`go test ./...`) e validação da build do Docker image.
  - Configurar workflow para compilação automatizada dos artefatos mobile: Android App Bundle (`.aab`) e iOS Framework (`XCFramework`).
- [ ] **Política de Privacidade & Termos**
  - Criar documento de Política de Privacidade de Dados Geográficos exigido para submissão no Google Play Console e Apple App Store Connect.
