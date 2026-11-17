import SwiftUI
import Shared

/// Map screen. The title strip still shows output of the shared Kotlin logic, to prove it runs on iOS.
struct ContentView: View {
    @StateObject private var location = LocationModel()
    @StateObject private var packs = PackStore()
    @State private var mapStatus = "loading"

    private let x = Mercator.shared.x(lon: 5.7936)
    private let name = CharacterNames.shared.clean(raw: "  Misty   the\nfox  ") ?? "(none)"

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
            MapView(coordinate: location.coordinate, styleJSON: styleJSON, status: $mapStatus).ignoresSafeArea()
            Text("\(name)  x \(String(format: "%.4f", x))  pack: \(packText)  \(mapStatus)")
                .font(.footnote).padding(8)
                .background(.thinMaterial, in: Capsule())
        }
        .onAppear { location.start() }
        .task { await packs.prepare() }
    }
}
