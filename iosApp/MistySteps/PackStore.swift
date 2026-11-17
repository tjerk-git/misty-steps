import Foundation

/// Downloads a province map pack and builds the map style for it. Spike version: one hard-coded
/// province (Fryslân), no resume, no checksum; the real pack manager comes with the shared pack logic.
@MainActor
final class PackStore: ObservableObject {
    enum State { case idle, downloading(Double), ready(String), failed(String) }

    @Published var state: State = .idle

    private let base = "https://maps.potloodgum.com/"
    private let packPath = "v2/nl/2026/fr/map.pmtiles"
    private let userAgent = "com.potloodgum.mistysteps.ios/spike"

    private var packFile: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("maps/nl/2026/fr/map.pmtiles")
    }

    func prepare() async {
        if FileManager.default.fileExists(atPath: packFile.path) { finish(); return }
        do {
            try FileManager.default.createDirectory(at: packFile.deletingLastPathComponent(), withIntermediateDirectories: true)
            var request = URLRequest(url: URL(string: base + packPath)!)
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
            state = .downloading(0)
            let (tmp, response) = try await URLSession.shared.download(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                state = .failed("download failed: HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0)"); return
            }
            try FileManager.default.moveItem(at: tmp, to: packFile)
            finish()
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    private func finish() {
        guard let templateURL = Bundle.main.url(forResource: "misty-style", withExtension: "json", subdirectory: "maps"),
              let template = try? Data(contentsOf: templateURL),
              let style = try? StyleBuilder.build(template: template, pack: packFile) else {
            state = .failed("style template missing"); return
        }
        state = .ready(style)
    }
}

/// Turns the style template into a style for one installed pack. Port of the Android MapStyle.build.
enum StyleBuilder {
    static func build(template: Data, pack: URL) throws -> String {
        var style = try JSONSerialization.jsonObject(with: template) as! [String: Any]
        var layers = style["layers"] as! [[String: Any]]
        let background = layers.removeFirst()
        style["sources"] = ["pack-0": ["type": "vector", "url": "pmtiles://file://\(pack.path)"]]
        style["layers"] = [background] + layers.map { layer in
            var l = layer; l["id"] = "\(layer["id"] as! String)-0"; l["source"] = "pack-0"; return l
        }
        if let glyphs = Bundle.main.resourceURL?.appendingPathComponent("maps/glyphs") {
            style["glyphs"] = "file://\(glyphs.path)/{fontstack}/{range}.pbf"
        }
        let data = try JSONSerialization.data(withJSONObject: style)
        return String(decoding: data, as: UTF8.self)
    }
}
