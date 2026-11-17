import SwiftUI
import Shared

/// First iOS build: proves the shared Kotlin logic runs on iOS. The map and fog come next.
struct ContentView: View {
    private let x = Mercator.shared.x(lon: 5.7936)
    private let y = Mercator.shared.y(lat: 53.2012)
    private let name = CharacterNames.shared.clean(raw: "  Misty   the\nfox  ") ?? "(none)"

    var body: some View {
        VStack(spacing: 12) {
            Text("Misty Steps").font(.largeTitle)
            Text("Leeuwarden in Mercator units")
            Text(String(format: "x %.5f  y %.5f", x, y)).monospaced()
            Text("Cleaned name: \(name)")
        }
        .padding()
    }
}
