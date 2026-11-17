import SwiftUI
import Shared

/// Map screen with the fog. The status strip shows the pack and map state and the fog's spot count.
struct ContentView: View {
    @StateObject private var location = LocationModel()
    @StateObject private var packs = PackStore()
    @State private var mapStatus = "loading"
    @State private var following = true
    @StateObject private var app = AppModel()
    @StateObject private var fog = FogModel(store: SQLiteTrailStore(path: ContentView.databaseURL))

    private static var databaseURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("trails.db")
    }

    init() {
        let spots = UserDefaults.standard.integer(forKey: "spots")
        if spots > 0 { fog.addStressTrail(spots: spots) }
    }

    private var packText: String { "\(packs.manager.mapFiles().count) packs" }

    var body: some View {
        ZStack(alignment: .top) {
            MapView(coordinate: location.coordinate, styleJSON: packs.styleJSON, fogModel: fog, status: $mapStatus, following: $following)
                .ignoresSafeArea()
            VStack(spacing: 6) {
                Text("pack \(packText) | spots \(fog.count) | \(mapStatus)")
                    .font(.system(size: 10)).padding(6)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 10))
                if fog.session != nil {
                    Text(fog.inVehicle ? "In a vehicle: the fog stays" : String(format: "%.0f m walked", fog.distanceM))
                        .font(.headline).padding(.horizontal, 14).padding(.vertical, 6)
                        .background(.thinMaterial, in: Capsule())
                }
                Spacer()
                HStack(spacing: 12) {
                    Button { app.route = .characters } label: { Image(systemName: "pawprint.fill") }
                        .buttonStyle(.bordered).controlSize(.large)
                    Button(fog.session == nil ? "Start wandering" : "Stop") {
                        if fog.session == nil { fog.beginWalk(); following = true } else { fog.endWalk() }
                    }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                    if !following {
                        Button { following = true } label: { Image(systemName: "location.fill") }
                            .buttonStyle(.bordered).controlSize(.large)
                    }
                    Button { app.route = .about } label: { Image(systemName: "info.circle") }
                        .buttonStyle(.bordered).controlSize(.large)
                }
                .padding(.bottom, 28)
            }
            .padding(.horizontal, 8)
        }
        .overlay {
            switch app.route {
            case .onboarding:
                OnboardingHostView(onDone: { app.finishOnboarding(hasTrailPoints: fog.count > 0) },
                                   onSkip: { app.finishOnboarding(hasTrailPoints: fog.count > 0) }).ignoresSafeArea()
            case .consent:
                ConsentHostView { allowed in app.answerConsent(allowed, hasTrailPoints: fog.count > 0) }.ignoresSafeArea()
            case .picker:
                PickerHostView(provinces: packs.provinces, packs: packs.manager, preselected: [], existingUser: fog.count > 0,
                               onDone: { app.finishPicker(hasTrailPoints: fog.count > 0) })
                    .id(packs.provinces?.count ?? -1) // rebuilt when the catalog arrives
                    .ignoresSafeArea()
            case .stats:
                StatsDemoHostView(onClose: { app.close() }).ignoresSafeArea()
            case .about:
                AboutHostView(onClose: { app.close() }).ignoresSafeArea()
            case .characters:
                CharacterHostView(onClose: { app.close() }).ignoresSafeArea()
            case nil:
                EmptyView()
            }
        }
        .onAppear {
            app.pickerAvailable = true
            app.start(hasTrailPoints: fog.count > 0)
            location.onFix = { fog.onLocation($0) }
            location.start()
            // Test hooks: -autostart 1 begins a walk at launch, -autostop N ends it after N seconds.
            if UserDefaults.standard.integer(forKey: "autostart") > 0 { fog.beginWalk() }
            let stop = UserDefaults.standard.integer(forKey: "autostop")
            if stop > 0 { DispatchQueue.main.asyncAfter(deadline: .now() + Double(stop)) { fog.endWalk() } }
        }
        .task {
            await packs.prepare()
            // Test hook: -autopick <id> downloads that province.
            if let id = UserDefaults.standard.string(forKey: "autopick"), let entry = packs.provinces?.first(where: { $0.id == id }) {
                packs.manager.download(entry: entry, unmeteredOnly: false)
            }
        }
    }
}
