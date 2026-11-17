import SwiftUI
import Shared

/// Map screen with the fog. The status strip shows the pack and map state and the fog's spot count.
struct ContentView: View {
    @StateObject private var location = LocationModel()
    @StateObject private var packs = PackStore()
    @State private var mapStatus = "loading"
    private let fog = FogModel()

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
            MapView(coordinate: location.coordinate, styleJSON: styleJSON, fogModel: fog, status: $mapStatus)
                .ignoresSafeArea()
            Text("pack \(packText) | spots \(fog.count) | \(mapStatus)")
                .font(.system(size: 10)).padding(8)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, 8)
        }
        .onAppear { location.start() }
        .onChange(of: location.coordinate?.latitude) { _, _ in
            if let c = location.coordinate { fog.record(c) }
        }
        .task { await packs.prepare() }
    }
}
