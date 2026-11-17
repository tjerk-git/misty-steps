import SwiftUI
import Shared

/// Map screen with the fog. The status strip shows the pack and map state and the fog's spot count.
struct ContentView: View {
    @StateObject private var location = LocationModel()
    @StateObject private var packs = PackStore()
    @State private var mapStatus = "loading"
    @State private var following = true
    @State private var showAbout = UserDefaults.standard.integer(forKey: "composeAbout") > 0
    @State private var showOnboarding = UserDefaults.standard.integer(forKey: "composeOnboarding") > 0
    @State private var showConsent = UserDefaults.standard.integer(forKey: "composeConsent") > 0
    @StateObject private var fog = FogModel(store: SQLiteTrailStore(path: ContentView.databaseURL))

    private static var databaseURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("trails.db")
    }

    init() {
        let spots = UserDefaults.standard.integer(forKey: "spots")
        if spots > 0 { fog.addStressTrail(spots: spots) }
    }

    private var styleJSON: String? {
        if case .ready(let json) = packs.state { return json }
        return nil
    }

    private var packText: String {
        switch packs.state {
        case .idle: return "idle"
        case .downloading: return "downloading"
        case .ready: return "ready"
        case .failed(let why): return "failed (\(why))"
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            MapView(coordinate: location.coordinate, styleJSON: styleJSON, fogModel: fog, status: $mapStatus, following: $following)
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
                if UserDefaults.standard.integer(forKey: "composeHello") > 0 {
                    SharedHelloView().frame(height: 190).clipShape(RoundedRectangle(cornerRadius: 12))
                }
                HStack {
                    Button(fog.session == nil ? "Start wandering" : "Stop") {
                        if fog.session == nil { fog.beginWalk(); following = true } else { fog.endWalk() }
                    }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                    if !following {
                        Button { following = true } label: { Image(systemName: "location.fill") }
                            .buttonStyle(.bordered).controlSize(.large)
                    }
                }
                .padding(.bottom, 28)
            }
            .padding(.horizontal, 8)
        }
        .overlay {
            if showAbout {
                AboutHostView(onClose: { showAbout = false }).ignoresSafeArea()
            }
            if showOnboarding {
                OnboardingHostView(onDone: { showOnboarding = false }, onSkip: { showOnboarding = false })
                    .ignoresSafeArea()
            }
            if showConsent {
                ConsentHostView { allowed in
                    fputs("consent answer: \(allowed)\n", stderr)
                    showConsent = false
                }
                .ignoresSafeArea()
            }
        }
        .onAppear {
            location.onFix = { fog.onLocation($0) }
            location.start()
            // Test hooks: -autostart 1 begins a walk at launch, -autostop N ends it after N seconds.
            if UserDefaults.standard.integer(forKey: "autostart") > 0 { fog.beginWalk() }
            let stop = UserDefaults.standard.integer(forKey: "autostop")
            if stop > 0 { DispatchQueue.main.asyncAfter(deadline: .now() + Double(stop)) { fog.endWalk() } }
        }
        .task { await packs.prepare() }
    }
}
