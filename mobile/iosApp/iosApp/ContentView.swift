import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var viewModel = GeoRefViewModel()

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                // Sync status banner
                HStack {
                    VStack(alignment: .leading) {
                        Text("Status de Sincronização")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(viewModel.syncStatusMessage)
                            .font(.subheadline)
                            .bold()
                    }
                    Spacer()
                    Button(action: {
                        viewModel.syncNow()
                    }) {
                        Text("Sincronizar")
                            .font(.footnote)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                    }
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(10)

                // Add record section
                VStack(alignment: .leading, spacing: 8) {
                    Text("Novo Ponto em Campo (iOS)").font(.headline)
                    TextField("Nome do Ponto", text: $viewModel.name)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    TextField("Descrição", text: $viewModel.desc)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    HStack {
                        TextField("Lat", text: $viewModel.lat)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                        TextField("Lng", text: $viewModel.lng)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                    }

                    Button(action: {
                        viewModel.saveLocalRecord()
                    }) {
                        Text("Salvar Localmente (Offline)")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.green)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                    }
                }
                .padding()

                List {
                    Section(header: Text("Pontos Guardados no iOS")) {
                        ForEach(viewModel.records, id: \.id) { record in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(record.name).font(.headline)
                                    Spacer()
                                    Text(record.syncStatus.name)
                                        .font(.caption2)
                                        .padding(4)
                                        .background(record.syncStatus == .synced ? Color.green : Color.orange)
                                        .foregroundColor(.white)
                                        .cornerRadius(4)
                                }
                                if !record.description_.isEmpty {
                                    Text(record.description_).font(.subheadline)
                                }
                                Text("Lat: \(record.latitude), Lng: \(record.longitude)")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                        }
                    }
                }
            }
            .padding(.top)
            .navigationTitle("GeoRef iOS")
        }
    }
}

class GeoRefViewModel: ObservableObject {
    @Published var name: String = ""
    @Published var desc: String = ""
    @Published var lat: String = "-23.5505"
    @Published var lng: String = "-46.6333"
    @Published var syncStatusMessage: String = "Pronto para campo"
    @Published var records: [GeorefRecord] = []

    private let syncEngine: IdempotentSyncEngine

    init() {
        let clientId = "ios-device-" + UUID().uuidString.prefix(8)
        self.syncEngine = IdempotentSyncEngine(clientId: String(clientId), localDatabase: LocalDatabase(), apiClient: KtorSyncApiClient(baseUrl: "http://localhost:8080"))
    }

    func saveLocalRecord() {
        guard !name.isEmpty else { return }
        let recordId = UUID().uuidString
        Task {
            _ = try await syncEngine.createFieldRecord(
                id: recordId,
                name: name,
                description: desc,
                latitude: Double(lat) ?? -23.5505,
                longitude: Double(lng) ?? -46.6333,
                elevation: 0.0,
                accuracy: 0.0
            )
            DispatchQueue.main.async {
                self.name = ""
                self.desc = ""
                self.loadRecords()
            }
        }
    }

    func syncNow() {
        let batchId = "ios-batch-" + UUID().uuidString
        self.syncStatusMessage = "Sincronizando..."
        syncEngine.syncNow(batchId: batchId)
    }

    private func loadRecords() {
        // Updated via flow binding or async call
    }
}
