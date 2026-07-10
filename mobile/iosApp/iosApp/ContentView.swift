import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var viewModel = GeoRefViewModel()
    @State private var selectedTab: Int = 1 // Default: Center Tab (Mapa Mundi)

    var body: some View {
        TabView(selection: $selectedTab) {
            // TAB 0 (Esquerda): Todos os Mapas Importados
            NavigationView {
                VStack(alignment: .leading) {
                    Text("📁 Meus Mapas Importados (\(viewModel.gisLayers.count))")
                        .font(.headline)
                        .padding(.horizontal)

                    Text("Toque em qualquer mapa para centralizá-lo e exibi-lo sobreposto no Mapa Mundi.")
                        .font(.caption)
                        .foregroundColor(.gray)
                        .padding(.horizontal)

                    List {
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
                            .contentShape(Rectangle())
                            .onTapGesture {
                                viewModel.selectLayer(layer)
                                selectedTab = 1 // Switch to World Map tab automatically!
                            }
                        }
                    }
                }
                .navigationTitle("Importações")
            }
            .tabItem {
                Label("Importações", systemImage: "folder.fill")
            }
            .tag(0)

            // TAB 1 (Centro/Principal): Mapa Mundi Global Interactive View
            NavigationView {
                VStack(spacing: 12) {
                    Text("🌍 VISUALIZADOR GLOBAL GIS (iOS)")
                        .font(.caption)
                        .bold()
                        .foregroundColor(.blue)

                    ZStack {
                        Rectangle()
                            .fill(Color.blue.opacity(0.15))
                            .cornerRadius(12)
                        
                        VStack(spacing: 8) {
                            Image(systemName: "mappin.and.ellipse")
                                .font(.largeTitle)
                                .foregroundColor(viewModel.selectedLayer != nil ? .red : .gray)

                            if let active = viewModel.selectedLayer {
                                Text("SOBREPOSIÇÃO ATIVA: \(active.name)")
                                    .font(.subheadline)
                                    .bold()
                                    .padding(6)
                                    .background(Color.green)
                                    .foregroundColor(.white)
                                    .cornerRadius(6)

                                Text("📍 Centro: \(active.centerLat), \(active.centerLng)")
                                    .font(.caption)
                                    .bold()

                                Text("Formato DMS: \(active.centerPoint.toDmsString())")
                                    .font(.caption2)

                                Text("BBox: [\(active.minLat), \(active.minLng)] à [\(active.maxLat), \(active.maxLng)]")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)

                                Button(action: { viewModel.downloadRegionalTiles() }) {
                                    HStack {
                                        Image(systemName: "square.and.arrow.down")
                                        Text("Salvar Tiles da Região Offline")
                                    }
                                    .font(.caption)
                                    .bold()
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(Color.blue)
                                    .foregroundColor(.white)
                                    .cornerRadius(6)
                                }
                            } else {
                                Text("Nenhum mapa sobreposto selecionado.")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                Text("Selecione um mapa na aba 'Importações' para posicionar sobreposto.")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding()
                    }
                    .padding(.horizontal)

                    Spacer()
                }
                .navigationTitle("Mapa Mundi")
            }
            .tabItem {
                Label("Mapa Mundi", systemImage: "globe")
            }
            .tag(1)

            // TAB 2 (Direita): Importar Dados
            NavigationView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("📥 Importar Novos Dados e Arquivos GIS")
                        .font(.headline)

                    VStack(alignment: .leading, spacing: 10) {
                        Text("📄 Selecionar Arquivos do Dispositivo").font(.subheadline).bold()

                        Button(action: {
                            viewModel.importMockGeoPdf()
                            selectedTab = 1
                        }) {
                            HStack {
                                Image(systemName: "doc.richtext")
                                Text("Importar GeoPDF (.pdf)")
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.red)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                        }

                        Button(action: {
                            viewModel.importMockGeoJson()
                            selectedTab = 1
                        }) {
                            HStack {
                                Image(systemName: "map")
                                Text("Importar GeoJSON (.geojson)")
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.teal)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                        }
                    }
                    .padding()
                    .background(Color(UIColor.tertiarySystemBackground))
                    .cornerRadius(10)

                    Spacer()
                }
                .padding()
                .navigationTitle("Importar Dados")
            }
            .tabItem {
                Label("Importar Dados", systemImage: "square.and.arrow.down.fill")
            }
            .tag(2)
        }
    }
}

class GeoRefViewModel: ObservableObject {
    @Published var syncStatusMessage: String = "PostGIS Pronto"
    @Published var selectedLayer: GisLayer? = nil
    @Published var gisLayers: [GisLayer] = []

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
