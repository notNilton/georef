import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var viewModel = GeoRefViewModel()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 14) {
                    // PostGIS Status Header
                    HStack {
                        VStack(alignment: .leading) {
                            Text("PostGIS & Sincronização em Campo")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(viewModel.syncStatusMessage)
                                .font(.subheadline)
                                .bold()
                        }
                        Spacer()
                        Button(action: { viewModel.syncNow() }) {
                            Text("Sincronizar PostGIS")
                                .font(.footnote)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.blue)
                                .foregroundColor(.white)
                                .cornerRadius(6)
                        }
                    }
                    .padding()
                    .background(Color(UIColor.secondarySystemBackground))
                    .cornerRadius(10)

                    // Global GIS Map View Widget with Overlay
                    VStack(alignment: .leading, spacing: 8) {
                        Text("🌍 VISUALIZADOR GLOBAL GIS (iOS)")
                            .font(.caption)
                            .bold()
                            .foregroundColor(.blue)

                        if let active = viewModel.selectedLayer {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Sobreposição Ativa: \(active.name) (\(active.fileType.name))")
                                    .font(.subheadline)
                                    .bold()
                                Text("Centro: \(active.centerLat), \(active.centerLng)")
                                    .font(.caption)
                                Text("BBox: [\(active.minLat), \(active.minLng)] à [\(active.maxLat), \(active.maxLng)]")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)

                                ZStack {
                                    Rectangle()
                                        .fill(Color.blue.opacity(0.2))
                                        .frame(height: 100)
                                        .cornerRadius(8)
                                    VStack {
                                        Text("🗺️ Visualizador GIS Sobreposto")
                                            .font(.caption)
                                            .bold()
                                        Text("Pin: \(active.centerLat), \(active.centerLng)")
                                            .font(.caption2)
                                            .padding(4)
                                            .background(Color.red)
                                            .foregroundColor(.white)
                                            .cornerRadius(4)
                                    }
                                }

                                Button(action: { viewModel.downloadRegionalTiles() }) {
                                    HStack {
                                        Image(systemName: "arrow.down.doc")
                                        Text("Salvar Tiles da Região Offline")
                                    }
                                    .font(.caption)
                                    .bold()
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(Color.green)
                                    .foregroundColor(.white)
                                    .cornerRadius(6)
                                }
                            }
                        } else {
                            Text("Nenhum mapa selecionado. Toque em um mapa importado abaixo.")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                    .padding()
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(10)

                    // GIS File Import Section
                    VStack(alignment: .leading, spacing: 8) {
                        Text("📂 Importar Exportação GIS (iOS)").font(.headline)
                        HStack(spacing: 8) {
                            Button(action: { viewModel.importMockGeoPdf() }) {
                                Text("GeoPDF")
                                    .font(.caption)
                                    .padding(8)
                                    .background(Color.red)
                                    .foregroundColor(.white)
                                    .cornerRadius(6)
                            }
                            Button(action: { viewModel.importMockGeoJson() }) {
                                Text("GeoJSON")
                                    .font(.caption)
                                    .padding(8)
                                    .background(Color.teal)
                                    .foregroundColor(.white)
                                    .cornerRadius(6)
                            }
                            Button(action: { viewModel.importMockKml() }) {
                                Text("KML")
                                    .font(.caption)
                                    .padding(8)
                                    .background(Color.purple)
                                    .foregroundColor(.white)
                                    .cornerRadius(6)
                            }
                        }
                    }
                    .padding()
                    .background(Color(UIColor.tertiarySystemBackground))
                    .cornerRadius(10)

                    // Saved Layers List
                    VStack(alignment: .leading) {
                        Text("🗺️ Mapas de Região Salvos (\(viewModel.gisLayers.count))")
                            .font(.headline)

                        ForEach(viewModel.gisLayers, id: \.id) { layer in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(layer.name).font(.subheadline).bold()
                                    Spacer()
                                    Text(layer.fileType.name)
                                        .font(.caption2)
                                        .padding(4)
                                        .background(Color.blue)
                                        .foregroundColor(.white)
                                        .cornerRadius(4)
                                }
                                Text("Centro: \(layer.centerLat), \(layer.centerLng)")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            .padding()
                            .background(viewModel.selectedLayer?.id == layer.id ? Color.blue.opacity(0.2) : Color(UIColor.secondarySystemBackground))
                            .cornerRadius(8)
                            .onTapGesture {
                                viewModel.selectLayer(layer)
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("GeoRef GIS iOS")
        }
    }
}

class GeoRefViewModel: ObservableObject {
    @Published var syncStatusMessage: String = "PostGIS Pronto (Offline)"
    @Published var selectedLayer: GisLayer? = nil
    @Published var gisLayers: [GisLayer] = []
    @Published var records: [GeorefRecord] = []

    private let syncEngine: IdempotentSyncEngine

    init() {
        let clientId = "ios-field-" + UUID().uuidString.prefix(8)
        self.syncEngine = IdempotentSyncEngine(clientId: String(clientId), localDatabase: LocalDatabase(), apiClient: KtorSyncApiClient(baseUrl: "http://localhost:8085"))
    }

    func importMockGeoPdf() {
        let pdfString = "%PDF-1.7 /BBox [-46.6400 -23.5600 -46.6200 -23.5400] /GPTS [-23.5505 -46.6333]"
        Task {
            let layer = try await syncEngine.importGisDocument(fileBytes: pdfString.data(using: .utf8) ?? Data(), fileName: "Mapa_GeoPDF_iOS.pdf")
            DispatchQueue.main.async {
                self.selectedLayer = layer
            }
        }
    }

    func importMockGeoJson() {
        let geoJsonStr = """
        {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-43.1820,-22.8950]}}]}
        """
        Task {
            let layer = try await syncEngine.importGisDocument(fileBytes: geoJsonStr.data(using: .utf8) ?? Data(), fileName: "Talhao_Agro.geojson")
            DispatchQueue.main.async {
                self.selectedLayer = layer
            }
        }
    }

    func importMockKml() {
        let kmlStr = "<kml><Placemark><coordinates>-47.8828,-15.7939</coordinates></Placemark></kml>"
        Task {
            let layer = try await syncEngine.importGisDocument(fileBytes: kmlStr.data(using: .utf8) ?? Data(), fileName: "Fazenda_KML.kml")
            DispatchQueue.main.async {
                self.selectedLayer = layer
            }
        }
    }

    func selectLayer(_ layer: GisLayer) {
        syncEngine.selectGisLayerForMapOverlay(layer: layer)
        self.selectedLayer = layer
    }

    func downloadRegionalTiles() {
        syncEngine.downloadMapTilesForPdfRegion(minZoom: 12, maxZoom: 14)
    }

    func syncNow() {
        let batchId = "ios-batch-" + UUID().uuidString
        self.syncStatusMessage = "Sincronizando PostGIS..."
        syncEngine.syncNow(batchId: batchId)
    }
}
