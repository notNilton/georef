import SwiftUI
import WebKit
import shared

struct ContentView: View {
    @StateObject private var viewModel = GeoRefViewModel()
    @State private var selectedTab: Int = 1 // Default: Center Tab (Mapa Mundi OSM)

    var body: some View {
        TabView(selection: $selectedTab) {
            // TAB 0 (Esquerda): Todos os Mapas Importados
            NavigationView {
                VStack(alignment: .leading) {
                    Text("📁 Meus Mapas Importados (\(viewModel.gisLayers.count))")
                        .font(.headline)
                        .padding(.horizontal)

                    Text("Toque em qualquer mapa para centralizá-lo e exibi-lo sobreposto no OpenStreetMap.")
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
                                selectedTab = 1
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

            // TAB 1 (Centro/Principal): OpenStreetMap Interactive View
            NavigationView {
                VStack(spacing: 8) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("🗺️ Mapa Mundi OpenStreetMap")
                                .font(.subheadline)
                                .bold()
                            Text("Sem Chave de API • Gratuito & Offline Ready")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Button(action: { viewModel.isSatellite.toggle() }) {
                            Text(viewModel.isSatellite ? "🛰️ Satélite" : "🗺️ Ruas")
                                .font(.caption)
                                .padding(6)
                                .background(Color.blue)
                                .foregroundColor(.white)
                                .cornerRadius(6)
                        }
                    }
                    .padding(.horizontal)

                    // Interactive OpenStreetMap WKWebView
                    OSMWebView(
                        activeLayer: viewModel.selectedLayer,
                        isSatellite: viewModel.isSatellite
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .cornerRadius(12)
                    .padding(.horizontal)

                    if let active = viewModel.selectedLayer {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Camada Sobreposta: \(active.name)")
                                .font(.caption)
                                .bold()
                            Text("BBox: [\(active.minLat), \(active.minLng)] à [\(active.maxLat), \(active.maxLng)]")
                                .font(.caption2)
                                .foregroundColor(.secondary)

                            Button(action: { viewModel.downloadRegionalTiles() }) {
                                HStack {
                                    Image(systemName: "arrow.down.doc")
                                    Text("Salvar Tiles OpenStreetMap Offline")
                                }
                                .font(.caption)
                                .bold()
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                                .background(Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(6)
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .navigationTitle("Mapa Mundi OSM")
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

// SwiftUI WKWebView OpenStreetMap Leaflet Engine
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
        let name = activeLayer?.name ?? "Mapa Mundi"

        let tileUrl = isSatellite ? "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" : "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        let attr = isSatellite ? "Esri World Imagery" : "&copy; OpenStreetMap contributors"

        let hasActive = activeLayer != nil ? "true" : "false"

        let html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; }</style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([\(lat), \(lng)], \(hasActive == "true" ? 14 : 4));
                L.tileLayer('\(tileUrl)', { maxZoom: 19, attribution: '\(attr)' }).addTo(map);

                if (\(hasActive)) {
                    var marker = L.marker([\(lat), \(lng)]).addTo(map);
                    marker.bindPopup("<b>\(name)</b>").openPopup();
                    var bounds = [[\(minLat), \(minLng)], [\(maxLat), \(maxLng)]];
                    L.rectangle(bounds, { color: "#d32f2f", weight: 2, fillColor: "#ff7961", fillOpacity: 0.25 }).addTo(map);
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
