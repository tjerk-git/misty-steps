import Combine
import Foundation
import Shared

/// The map catalog, the installed packs and the map style built from them. The packs themselves are handled by the
/// shared-module `ApplePackManager`; this class loads the catalog and keeps the style in step with what is installed.
@MainActor
final class PackStore: ObservableObject {
    /// The provinces in the catalog; nil while there is no catalog (offline and never fetched).
    @Published var provinces: [PackEntry]?
    /// The map style for the installed packs (just the mist-coloured background when there are none).
    @Published var styleJSON: String?

    /// The province downloading now, for the progress card over the map; nil when none is.
    @Published var active: ActiveDownload?

    struct ActiveDownload: Equatable {
        let id: String
        let name: String
        let fraction: Double
        let detail: String
    }

    let manager: ApplePackManager
    private var meters: [String: DownloadMeter] = [:]

    /// Which province a coordinate is in (the same asset as Android), for the "No map of ... yet" offer.
    let provinceIndex: ProvinceIndex? = {
        guard let url = Bundle.main.url(forResource: "nl-provinces", withExtension: "json"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        return try? ProvinceIndex.Companion.shared.parse(text: text)
    }()

    private let base = "https://maps.potloodgum.com/"
    private let userAgent: String
    private let root: URL
    private var installedKey = ""
    private var ticker: AnyCancellable?

    init() {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"
        userAgent = "com.potloodgum.mistysteps.ios/\(version)"
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        root = support.appendingPathComponent("maps/nl")
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        manager = ApplePackManager(root: root.path, userAgent: userAgent)
    }

    private var catalogFile: URL { root.deletingLastPathComponent().appendingPathComponent("catalog.json") }

    /// Loads the cached catalog at once, refreshes it from the network once a week, and keeps the style current.
    func prepare() async {
        rebuildStyle()
        ticker = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect().sink { [weak self] _ in self?.rebuildStyleIfPacksChanged(); self?.updateActive() }
        if let cached = try? Data(contentsOf: catalogFile) { apply(cached) }
        let age = (try? catalogFile.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate)
            .map { Date().timeIntervalSince($0) } ?? .infinity
        guard provinces == nil || age > 7 * 24 * 3600 else { return }
        var request = URLRequest(url: URL(string: base + "v2/catalog.json")!)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if let (data, response) = try? await URLSession.shared.data(for: request), (response as? HTTPURLResponse)?.statusCode == 200,
           apply(data) {
            try? data.write(to: catalogFile, options: .atomic)
        }
    }

    /// Parses a catalog; false when it can't be used (and then nothing changes). A newer format asks for an app update.
    @discardableResult
    private func apply(_ data: Data) -> Bool {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
        guard (json["format"] as? Int) == 2 else {
            manager.setNeedsAppUpdate(value: true)
            return false
        }
        manager.setNeedsAppUpdate(value: false)
        let year = json["year"] as? Int ?? 0
        let list = (json["provinces"] as? [[String: Any]] ?? []).compactMap { o -> PackEntry? in
            guard let id = o["id"] as? String, let name = o["name"] as? String, let map = fileRef(o["map"] as? [String: Any]) else { return nil }
            let bbox = (o["bbox"] as? [Double] ?? []).map { KotlinDouble(value: $0) }
            return PackEntry(id: id, name: name, year: Int32(o["year"] as? Int ?? year), bbox: bbox, map: map, stats: fileRef(o["stats"] as? [String: Any]))
        }
        provinces = list
        return true
    }

    private func fileRef(_ o: [String: Any]?) -> FileRef? {
        guard let o, let url = o["url"] as? String, let bytes = o["bytes"] as? Int64 ?? (o["bytes"] as? Int).map(Int64.init),
              let sha = o["sha256"] as? String, bytes >= 0, sha.count == 64 else { return nil }
        return FileRef(url: url.hasPrefix("https://") ? url : base + url.drop(while: { $0 == "/" }), bytes: bytes, sha256: sha)
    }

    /// Reads the download progress twice a second: the card shows the percentage, speed and time left.
    private func updateActive() {
        var found: ActiveDownload?
        for entry in provinces ?? [] {
            guard let d = manager.state(entry: entry) as? PackState.Downloading else { continue }
            let meter = meters[entry.id] ?? DownloadMeter()
            meters[entry.id] = meter
            meter.update(nowMs: Int64(ProcessInfo.processInfo.systemUptime * 1000), bytes: d.bytes)
            let left = meter.secondsLeft(bytes: d.bytes, total: d.total)?.int32Value ?? -1
            found = ActiveDownload(
                id: entry.id, name: entry.name,
                fraction: d.total > 0 ? min(1, Double(d.bytes) / Double(d.total)) : 0,
                detail: IosTextsKt.downloadDetailText(bytes: d.bytes, total: d.total, bytesPerSecond: meter.bytesPerSecond, secondsLeft: left))
            break
        }
        if found != active { active = found }
        if found == nil { meters.removeAll() }
    }

    private func rebuildStyleIfPacksChanged() {
        if manager.mapFiles().joined(separator: "|") != installedKey { rebuildStyle() }
    }

    private func rebuildStyle() {
        let files = manager.mapFiles() as? [String] ?? []
        installedKey = files.joined(separator: "|")
        guard let url = Bundle.main.url(forResource: "misty-style", withExtension: "json", subdirectory: "maps"),
              let template = try? Data(contentsOf: url),
              let style = try? StyleBuilder.build(template: template, packs: files) else { return }
        styleJSON = style
    }
}

/// Turns the style template into a style for the installed packs. Port of the Android MapStyle.build: a MapLibre source
/// reads one PMTiles file, so every pack gets its own source and its own copy of the template's layers.
enum StyleBuilder {
    static func build(template: Data, packs: [String]) throws -> String {
        var style = try JSONSerialization.jsonObject(with: template) as! [String: Any]
        let templateLayers = style["layers"] as! [[String: Any]]
        let background = templateLayers[0]
        var layers: [[String: Any]] = [background]
        var sources: [String: Any] = [:]
        for (n, file) in packs.enumerated() {
            sources["pack-\(n)"] = ["type": "vector", "url": "pmtiles://file://\(file)"]
            for layer in templateLayers.dropFirst() {
                var l = layer
                l["id"] = "\(layer["id"] as! String)-\(n)"
                l["source"] = "pack-\(n)"
                layers.append(l)
            }
        }
        style["sources"] = sources
        style["layers"] = layers
        if let glyphs = Bundle.main.resourceURL?.appendingPathComponent("maps/glyphs") {
            style["glyphs"] = "file://\(glyphs.path)/{fontstack}/{range}.pbf"
        }
        let data = try JSONSerialization.data(withJSONObject: style)
        return String(decoding: data, as: UTF8.self)
    }
}
