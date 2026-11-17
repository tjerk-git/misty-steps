import SwiftUI
import Shared

/// Map screen with the fog. The status strip shows the pack and map state and the fog's spot count.
struct ContentView: View {
    @StateObject private var location = LocationModel()
    @StateObject private var packs = PackStore()
    @State private var mapStatus = "loading"
    @State private var following = true
    @StateObject private var app = AppModel()
    @StateObject private var fog = FogModel(store: ContentView.store)
    @StateObject private var pins = PinsModel(storage: ContentView.store)
    @State private var pinSheet: PinSheetTarget?
    @StateObject private var postcards = PostcardPainterImpl(store: ContentView.store)
    @State private var postcardWalk: Int64?
    @State private var backupHost: IosBackupHost?
    @State private var hint: String?

    /// One database file for the spots, walks and pins.
    private static let store = SQLiteTrailStore(path: ContentView.databaseURL)

    private static var databaseURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("trails.db")
    }

    private var packText: String { "\(packs.manager.mapFiles().count) packs" }

    /// "Leave a pin" while walking: at the newest good fix, if it is fresh.
    private func dropPin() {
        if let fix = fog.lastGoodFix, Date().timeIntervalSince(fix.timestamp) <= 30 {
            pinSheet = .new(lat: fix.coordinate.latitude, lon: fix.coordinate.longitude, sessionId: fog.session?.id)
        } else {
            showHint(IosTextsKt.pinLookingHint())
        }
    }

    private func showHint(_ text: String) {
        hint = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { if hint == text { hint = nil } }
    }

    var body: some View {
        ZStack(alignment: .top) {
            MapView(coordinate: location.coordinate, locationModel: location, styleJSON: packs.styleJSON, fogModel: fog, pinsModel: pins,
                    onOpenPin: { pinSheet = .open(id: $0) },
                    onNewPin: { pinSheet = .new(lat: $0.latitude, lon: $0.longitude, sessionId: nil) },
                    onFogPress: { showHint(IosTextsKt.pinFogHint()) },
                    status: $mapStatus, following: $following)
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
                if let hint {
                    Text(hint).font(.footnote).padding(10)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                }
                Spacer()
                HStack(spacing: 12) {
                    Button { app.route = .characters } label: { Image(systemName: "pawprint.fill") }
                        .buttonStyle(.bordered).controlSize(.large)
                    if fog.session != nil {
                        Button { dropPin() } label: { Image(systemName: "mappin.and.ellipse") }
                            .buttonStyle(.bordered).controlSize(.large)
                    }
                    Button(fog.session == nil ? "Start wandering" : "Stop") {
                        if fog.session == nil { fog.beginWalk(); following = true } else { fog.endWalk() }
                    }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                    if !following {
                        Button { following = true } label: { Image(systemName: "location.fill") }
                            .buttonStyle(.bordered).controlSize(.large)
                    }
                    Button { app.route = .stats } label: { Image(systemName: "chart.bar.fill") }
                        .buttonStyle(.bordered).controlSize(.large)
                    Button { app.route = .about } label: { Image(systemName: "info.circle") }
                        .buttonStyle(.bordered).controlSize(.large)
                }
                .padding(.bottom, 28)
            }
            .padding(.horizontal, 8)
        }
        .overlay {
            if let id = postcardWalk {
                PostcardHostView(walkId: id, painter: postcards, onClose: { postcardWalk = nil }).id(id).ignoresSafeArea()
            }
            if let target = pinSheet {
                PinSheetHostView(book: pins.book, target: target, onDone: { pinSheet = nil }).ignoresSafeArea()
            }
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
            case .stats where backupHost != nil:
                StatsHostView(store: fog.store, here: location.coordinate ?? fog.lastKnown, pins: pins.book, backup: backupHost!, packs: packs.manager, provinces: packs.provinces,
                              onOpenPicker: { app.route = .picker }, onOpenAbout: { app.route = .about },
                              onOpenWalk: { id in app.close(); postcardWalk = id },
                              onClose: { app.close() })
                    .id(packs.provinces?.count ?? -1)
                    .ignoresSafeArea()
            case .about:
                AboutHostView(onClose: { app.close() }).ignoresSafeArea()
            case .characters:
                CharacterHostView(onClose: { app.close() }).ignoresSafeArea()
            default:
                EmptyView()
            }
        }
        .onAppear {
            app.pickerAvailable = true
            postcards.pins = pins.book
            let backup = BackupPlatform(store: fog.store, fog: fog, pins: pins)
            backupHost = IosBackupHost(files: backup, data: backup, versionName: appVersion)
            postcards.packs = packs
            // Test hooks: -backuptest export|import -backuppath <file> runs the backup host without the file dialogs.
            if let mode = UserDefaults.standard.string(forKey: "backuptest"), let host = backupHost {
                if mode == "export" {
                    host.export { counts, error in
                        fputs("BACKUP export: \(counts.map { "\($0.points) points, \($0.walks) walks, \($0.pins) pins" } ?? "none") \(error.map { "error \($0)" } ?? "")\n", stderr)
                    }
                } else if mode == "import" {
                    host.pickImport { pending, error in
                        guard let pending else { fputs("BACKUP import: nothing to import \(error.map { "error \($0)" } ?? "")\n", stderr); return }
                        fputs("BACKUP import: file has \(pending.counts.points) points, \(pending.counts.walks) walks, \(pending.counts.pins) pins\n", stderr)
                        host.confirmImport(pending: pending) { added, error in
                            fputs("BACKUP import: added \(added.map { "\($0.points) points, \($0.walks) walks, \($0.pins) pins" } ?? "none") \(error.map { "error \($0)" } ?? "")\n", stderr)
                        }
                    }
                }
            }
            // Test hook: -postcard last opens the postcard of the newest walk.
            if UserDefaults.standard.string(forKey: "postcard") == "last",
               let newest = (fog.store.loadSessions(limit: KotlinInt(value: 1)) as? [Session])?.first { postcardWalk = newest.id }
            // The anonymous open count: release builds only. A test can point it at a local server (-pingurl, -pingrelease 1).
            #if DEBUG
            if UserDefaults.standard.integer(forKey: "pingrelease") > 0, let url = UserDefaults.standard.string(forKey: "pingurl") {
                OpenPingSender.pingIfAllowed(release: true, url: url)
            }
            #else
            OpenPingSender.pingIfAllowed(release: true)
            #endif
            // Test hook: -spots N adds a stress trail of N cleared spots.
            let spots = UserDefaults.standard.integer(forKey: "spots")
            if spots > 0 { fog.addStressTrail(spots: spots) }
            // Test hooks: -demopins 1 adds a few pins near Leeuwarden; -pinsheet new|open opens a sheet.
            if UserDefaults.standard.integer(forKey: "demopins") > 0 && pins.all.isEmpty {
                for (i, key) in ["sit", "view", "crow", "cat", "path"].enumerated() {
                    _ = pins.book.add(lat: 53.2012 + 0.0004 * Double(i), lon: 5.7936 + 0.0006 * Double(i), labelKey: key, note: i == 2 ? "Loud one" : nil,
                                      sessionId: nil, nowMs: Int64(Date().timeIntervalSince1970 * 1000))
                }
            }
            switch UserDefaults.standard.string(forKey: "pinsheet") {
            case "new": pinSheet = .new(lat: 53.2012, lon: 5.7936, sessionId: nil)
            case "open": if let first = pins.all.first { pinSheet = .open(id: first.id) }
            default: break
            }
            app.start(hasTrailPoints: fog.count > 0)
            location.onFix = { fog.onLocation($0) }
            location.start()
            // Test hooks: -autostart 1 begins a walk at launch, -autostop N ends it after N seconds.
            if UserDefaults.standard.integer(forKey: "autostart") > 0 { fog.beginWalk() }
            let stop = UserDefaults.standard.integer(forKey: "autostop")
            if stop > 0 { DispatchQueue.main.asyncAfter(deadline: .now() + Double(stop)) { fog.endWalk() } }
        }
        .onChange(of: fog.session?.id) { _, _ in backupHost?.setRecording(value: fog.session != nil) }
        .onChange(of: fog.finished?.id) { _, id in
            // A postcard when a walk ends (not for tiny ones, like a start-stop by mistake).
            if let walk = fog.finished, let id, walk.distanceM >= StatsScreenKt.MIN_WALK_M { postcardWalk = id }
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
