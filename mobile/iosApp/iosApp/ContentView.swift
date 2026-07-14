import SwiftUI
import WebKit
import shared

struct ContentView: View {
    @StateObject private var viewModel = GeoRefViewModel()
    @State private var selectedTab: Int = 1

    var body: some View {
        TabView(selection: $selectedTab) {
            // TAB 0 (Esquerda): Camadas Salvas
            NavigationView {
                VStack(alignment: .leading) {
                    Text("Camadas Salvas (\(viewModel.gisLayers.count))")
                        .font(.headline)
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
                                        .background(Color.gray.opacity(0.3))
                                        .foregroundColor(.white)
                                        .cornerRadius(4)
                                }
                                Text("Lat: \(layer.centerLat) | Lng: \(layer.centerLng)")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                viewModel.selectLayer(layer)
                                selectedTab = 1
                            }
                        }
                    }
                }
                .navigationTitle("Camadas")
            }
            .tabItem {
                Label("Camadas", systemImage: "square.3.layers.3d")
            }
            .tag(0)

            // TAB 1 (Centro): Mapa
            NavigationView {
                VStack(spacing: 8) {
                    HStack {
                        Text("Visualizador GIS")
                            .font(.subheadline)
                            .bold()
                        Spacer()
                        Button(action: { viewModel.isSatellite.toggle() }) {
                            Text(viewModel.isSatellite ? "Satélite" : "Vetor")
                                .font(.caption)
                                .padding(6)
                                .background(Color.gray.opacity(0.3))
                                .foregroundColor(.white)
                                .cornerRadius(6)
                        }
                    }
                    .padding(.horizontal)

                    OSMWebView(
                        activeLayer: viewModel.selectedLayer,
                        isSatellite: viewModel.isSatellite
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .cornerRadius(8)
                    .padding(.horizontal)

                    if let active = viewModel.selectedLayer {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(active.name)
                                .font(.caption)
                                .bold()
                            Text("Lat: \(active.centerLat) | Lng: \(active.centerLng)")
                                .font(.caption2)
                                .foregroundColor(.secondary)

                            Button(action: { viewModel.downloadRegionalTiles() }) {
                                Text("Baixar Tiles Offline")
                                    .font(.caption)
                                    .bold()
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 6)
                                    .background(Color.green)
                                    .foregroundColor(.black)
                                    .cornerRadius(6)
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .navigationTitle("Mapa")
            }
            .tabItem {
                Label("Mapa", systemImage: "map")
            }
            .tag(1)

            // TAB 2 (Direita): Importar
            NavigationView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Importar Dados")
                        .font(.headline)

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Arquivos").font(.subheadline).bold()

                        Button(action: {
                            viewModel.importMockGeoPdf()
                            selectedTab = 1
                        }) {
                            Text("GeoPDF (.pdf)")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(Color.gray.opacity(0.3))
                                .foregroundColor(.white)
                                .cornerRadius(8)
                        }

                        Button(action: {
                            viewModel.importMockGeoJson()
                            selectedTab = 1
                        }) {
                            Text("GeoJSON (.geojson)")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(Color.gray.opacity(0.3))
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
                .navigationTitle("Importar")
            }
            .tabItem {
                Label("Importar", systemImage: "square.and.arrow.down")
            }
            .tag(2)
        }
        .preferredColorScheme(.dark)
    }
}

struct OSMWebView: UIViewRepresentable {
    let activeLayer: GisLayer?
    let isSatellite: BooleanLiteralType

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        let lat = activeLayer?.centerLat ?? -23.5505
        let lng = activeLayer?.centerLng ?? -46.6333
        val minLat = activeLayer?.minLat ?? (lat - 0.02)
        val minLng = activeLayer?.minLng ?? (lng - 0.02)
        val maxLat = activeLayer?.maxLat ?? (lat + 0.02)
        val maxLng = activeLayer?.maxLng ?? (lng + 0.02)
        let name = activeLayer?.name ?? "Mapa"

        let tileUrl = isSatellite ? "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" : "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        let hasActive = activeLayer != nil ? "true" : "false"

        let html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #121212; }</style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([\(lat), \(lng)], \(hasActive == "true" ? 14 : 4));
                L.tileLayer('\(tileUrl)', { maxZoom: 19 }).addTo(map);

                if (\(hasActive)) {
                    var marker = L.marker([\(lat), \(lng)]).addTo(map);
                    marker.bindPopup("<b>\(name)</b>").openPopup();
                    var bounds = [[\(minLat), \(minLng)], [\(maxLat), \(maxLng)]];
                    L.rectangle(bounds, { color: "#00E676", weight: 2, fillColor: "#00E676", fillOpacity: 0.2 }).addTo(map);
                    map.fitBounds(bounds, { padding: [20, 20] });
                }
            </script>
        </body>
        </html>
        """

        uiView.loadHTMLString(html, baseURL: URL(string: "https://openstreetmap.org"))
    }
}

class GeoRefViewModel: ObservableObject {
    @Published var isSatellite: Bool = false
    @Published var syncStatusMessage: String = "Pronto"
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
            let layer = try await syncEngine.importGisDocument(fileBytes: pdfString.data(using: .utf8) ?? Data(), fileName: "Mapa.pdf")
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
            let layer = try await syncEngine.importGisDocument(fileBytes: geoJsonStr.data(using: .utf8) ?? Data(), fileName: "Dados.geojson")
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
        self.syncStatusMessage = "Sincronizando..."
        syncEngine.syncNow(batchId: batchId)
    }
}
