import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var viewModel = GeoRefViewModel()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 14) {
                    // Sync Header Banner
                    HStack {
                        VStack(alignment: .leading) {
                            Text("Status da Conexão / Campo")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(viewModel.syncStatusMessage)
                                .font(.subheadline)
                                .bold()
                        }
                        Spacer()
                        Button(action: { viewModel.syncNow() }) {
                            Text("Sincronizar")
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

                    // GeoPDF Input Section
                    VStack(alignment: .leading, spacing: 8) {
                        Text("📄 Processar Documento GeoPDF (iOS)")
                            .font(.headline)
                        Text("Extrai geocoordenadas (/GPTS, /BBox) do PDF e exibe o mapa.")
                            .font(.caption)
                            .foregroundColor(.gray)

                        Button(action: {
                            viewModel.processMockGeoPdf()
                        }) {
                            HStack {
                                Image(systemName: "doc.richtext")
                                Text("Importar GeoPDF de Campo")
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.purple)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                        }
                    }
                    .padding()
                    .background(Color(UIColor.tertiarySystemBackground))
                    .cornerRadius(10)

                    // GeoPDF Metadata & Coordinate Display Card
                    if let pdf = viewModel.selectedPdf {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("📍 Geocoordenadas do GeoPDF")
                                .font(.subheadline)
                                .bold()
                                .foregroundColor(.blue)

                            Text("Arquivo: \(pdf.fileName)")
                                .font(.footnote)
                            Text("• Centro Decimal: \(pdf.centerPoint.latitude), \(pdf.centerPoint.longitude)")
                                .font(.caption)
                            Text("• Formato DMS: \(pdf.centerPoint.toDmsString())")
                                .font(.caption)
                                .bold()
                            Text("• Bounding Box: [\(pdf.boundingBox.minLat) à \(pdf.boundingBox.maxLat)]")
                                .font(.caption2)
                                .foregroundColor(.secondary)

                            // Map Preview Widget
                            ZStack {
                                Rectangle()
                                    .fill(Color.blue.opacity(0.2))
                                    .frame(height: 90)
                                    .cornerRadius(8)
                                VStack {
                                    Text("🗺️ Visualizador de Mapa (iOS)")
                                        .font(.caption)
                                        .bold()
                                    Text("Pin: \(pdf.centerPoint.latitude), \(pdf.centerPoint.longitude)")
                                        .font(.caption2)
                                        .padding(4)
                                        .background(Color.red)
                                        .foregroundColor(.white)
                                        .cornerRadius(4)
                                }
                            }

                            // Regional Map Tile Saver Button
                            Button(action: {
                                viewModel.downloadRegionalTiles()
                            }) {
                                HStack {
                                    Image(systemName: "square.and.arrow.down")
                                    Text("Salvar Mapas da Região Offline")
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
                        .padding()
                        .background(Color.blue.opacity(0.1))
                        .cornerRadius(10)
                    }

                    // Saved Points List
                    VStack(alignment: .leading) {
                        Text("📍 Pontos de Campo Registrados (\(viewModel.records.count))")
                            .font(.headline)

                        ForEach(viewModel.records, id: \.id) { record in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(record.name).font(.subheadline).bold()
                                    Spacer()
                                    Text(record.syncStatus.name)
                                        .font(.caption2)
                                        .padding(4)
                                        .background(record.syncStatus == .synced ? Color.green : Color.orange)
                                        .foregroundColor(.white)
                                        .cornerRadius(4)
                                }
                                Text("Coordenadas: \(record.latitude), \(record.longitude)")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            .padding()
                            .background(Color(UIColor.secondarySystemBackground))
                            .cornerRadius(8)
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("GeoRef GeoPDF iOS")
        }
    }
}

class GeoRefViewModel: ObservableObject {
    @Published var syncStatusMessage: String = "Operando em Campo (Offline)"
    @Published var selectedPdf: GeoPdfMetadata? = nil
    @Published var records: [GeorefRecord] = []

    private let syncEngine: IdempotentSyncEngine

    init() {
        let clientId = "ios-field-" + UUID().uuidString.prefix(8)
        self.syncEngine = IdempotentSyncEngine(clientId: String(clientId), localDatabase: LocalDatabase(), apiClient: KtorSyncApiClient(baseUrl: "http://localhost:8080"))
    }

    func processMockGeoPdf() {
        let pdfString = """
        %PDF-1.7
        /BBox [-46.6400 -23.5600 -46.6200 -23.5400]
        /GPTS [-23.5505 -46.6333]
        """
        Task {
            let meta = try await syncEngine.processGeoPdfFile(pdfBytes: pdfString.data(using: .utf8) ?? Data(), fileName: "Mapa_GeoPDF_iOS.pdf")
            DispatchQueue.main.async {
                self.selectedPdf = meta
            }
        }
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
