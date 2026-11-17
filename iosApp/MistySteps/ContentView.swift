import SwiftUI
import Shared

/// Map screen. The title strip still shows output of the shared Kotlin logic, to prove it runs on iOS.
struct ContentView: View {
    @StateObject private var location = LocationModel()

    private let x = Mercator.shared.x(lon: 5.7936)
    private let name = CharacterNames.shared.clean(raw: "  Misty   the\nfox  ") ?? "(none)"

    var body: some View {
        ZStack(alignment: .top) {
            MapView(coordinate: location.coordinate).ignoresSafeArea()
            Text("Misty Steps  x \(String(format: "%.4f", x))  \(name)")
                .font(.footnote).padding(8)
                .background(.thinMaterial, in: Capsule())
        }
        .onAppear { location.start() }
    }
}
