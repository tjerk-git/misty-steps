import CoreLocation
import SwiftUI
import Shared

/// Map screen with the fog. The status strip shows the pack and map state and the fog's spot count.
struct ContentView: View {
    @StateObject private var location = LocationModel()
    @StateObject private var packs = PackStore()
    @State private var mapStatus = "loading"
    @State private var following = true
    /// Held, not observed: the pointer moves with every map frame and only `OffscreenPointerLayer` should redraw for it.
    @State private var pointerModel = PointerModel()
    @StateObject private var app = AppModel()
    @StateObject private var fog = FogModel(store: ContentView.store)
    @StateObject private var pins = PinsModel(storage: ContentView.store)
    @StateObject private var eraser = EraserModel()
    @State private var pinSheet: PinSheetTarget?
    @StateObject private var postcards = PostcardPainterImpl(store: ContentView.store)
    @State private var postcardWalk: Int64?
    @State private var backupHost: IosBackupHost?
    @State private var hint: String?
    @State private var showPinTip = false
    @State private var pickerPreselect: Set<String> = []
    @State private var offerDismissed = Set(UserDefaults.standard.stringArray(forKey: "offer_dismissed") ?? [])
    @State private var dialog: LocationDialog?
    @State private var afterGrant: AfterGrant = .wander
    /// The pin tip shows once, after the first real walk (same as Android).
    @AppStorage("pinTipSeen") private var pinTipSeen = false
    /// The old status strip (pack count, spots, map state) is for testing: launch with -debugstrip 1.
    private let debugStrip = UserDefaults.standard.integer(forKey: "debugstrip") > 0

    enum LocationDialog: Identifiable {
        case rationale, settings
        var id: Int { self == .rationale ? 0 : 1 }
    }
    enum AfterGrant { case wander, locate }

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

    /// "No map of <province> yet": once per province, while the app is open inside it and not walking (same rules as Android).
    private var offer: (province: Province, entry: PackEntry)? {
        guard app.route == nil, fog.session == nil, let index = packs.provinceIndex,
              let place = location.coordinate ?? fog.lastKnown,
              let province = index.provinceAt(lat: place.latitude, lon: place.longitude),
              let entry = packs.provinces?.first(where: { $0.id == province.id }),
              OfferRules.shared.shouldOffer(here: province, state: packs.manager.state(entry: entry), dismissed: offerDismissed)
        else { return nil }
        return (province, entry)
    }

    private func dismissPinTip() { showPinTip = false; pinTipSeen = true }

    /// Start or stop wandering. Starting asks for the location first, with the reason, like Android.
    private func record() {
        if fog.session != nil { fog.endWalk(); return }
        askThen(.wander)
    }

    /// The locate button: fly back to where you are.
    private func locate() {
        askThen(.locate)
    }

    private func askThen(_ what: AfterGrant) {
        afterGrant = what
        if location.isAllowed { grantedAction(what) } else if location.needsAsking { dialog = .rationale } else { dialog = .settings }
    }

    private func grantedAction(_ what: AfterGrant) {
        location.start()
        following = true
        if what == .wander { fog.beginWalk() } else if location.coordinate == nil { showHint(IosTextsKt.pinLookingHint()) }
    }

    private func showHint(_ text: String) {
        hint = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { if hint == text { hint = nil } }
    }

    var body: some View {
        ZStack(alignment: .top) {
            MapView(coordinate: location.coordinate, locationModel: location, styleJSON: packs.styleJSON, fogModel: fog, pinsModel: pins, eraser: eraser,
                    onOpenPin: { pinSheet = .open(id: $0) },
                    onNewPin: { pinSheet = .new(lat: $0.latitude, lon: $0.longitude, sessionId: nil) },
                    onFogPress: { showHint(IosTextsKt.pinFogHint()) },
                    status: $mapStatus, following: $following, pointerModel: pointerModel)
                .ignoresSafeArea()
            OffscreenPointerLayer(model: pointerModel, onTap: locate)
            if debugStrip {
                Text("pack \(packText) | spots \(fog.count) | \(mapStatus)")
                    .font(.system(size: 10)).padding(6)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 10))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom).padding(.bottom, 90).allowsHitTesting(false)
            }
            HubView(fog: fog, showPinTip: showPinTip, onDismissPinTip: dismissPinTip,
                    onStats: { app.route = .stats }, onYou: { app.route = .you },
                    onLocate: locate, onPin: dropPin, onRecord: record, eraser: eraser)
            if let d = packs.active {
                Button { pickerPreselect = []; app.route = .picker } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(IosTextsKt.downloadPillText(name: d.name)).font(.system(size: 14)).foregroundStyle(Misty.ink)
                        ProgressView(value: d.fraction).tint(Misty.ink)
                        Text(d.detail).font(.system(size: 12)).foregroundStyle(Misty.dusk)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(Misty.frost, in: RoundedRectangle(cornerRadius: 20))
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.horizontal, 84).padding(.bottom, 112)
            }
            if let offer {
                VStack(alignment: .leading, spacing: 4) {
                    Text(IosTextsKt.offerCardText(province: offer.province.name, bytes: offer.entry.map.bytes))
                        .font(.system(size: 15)).foregroundStyle(Misty.ink)
                    HStack(spacing: 4) {
                        Spacer()
                        Button(IosTextsKt.notNowText()) {
                            offerDismissed.insert(offer.province.id)
                            UserDefaults.standard.set(Array(offerDismissed), forKey: "offer_dismissed")
                        }.foregroundStyle(Misty.dusk)
                        Button(IosTextsKt.dataDownloadText()) { pickerPreselect = [offer.province.id]; app.route = .picker }
                            .foregroundStyle(Misty.ember)
                    }.font(.system(size: 14, weight: .medium)).padding(.horizontal, 4)
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(Misty.frost, in: RoundedRectangle(cornerRadius: 20))
                .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.horizontal, 84).padding(.bottom, 112)
            }
            if let hint {
                Text(hint).font(.system(size: 13)).foregroundStyle(Misty.ink).padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Misty.frost, in: Capsule()).shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom).padding(.bottom, 100).allowsHitTesting(false)
            }
        }
        .overlay {
            if let id = postcardWalk {
                PostcardHostView(walkId: id, painter: postcards, onClose: { postcardWalk = nil }).id(id).ignoresSafeArea()
            }
            if let target = pinSheet {
                PinSheetHostView(book: pins.book, target: target, onDone: { pinSheet = nil }).ignoresSafeArea()
            }
            if app.ratCardDue {
                RatCardHostView(onTry: { app.finishRatCard(tryIt: true) }, onLater: { app.finishRatCard(tryIt: false) }).ignoresSafeArea()
            }
            switch app.route {
            case .onboarding:
                OnboardingHostView(onDone: { app.finishOnboarding(hasTrailPoints: fog.count > 0) },
                                   onSkip: { app.finishOnboarding(hasTrailPoints: fog.count > 0) }).ignoresSafeArea()
            case .picker:
                PickerHostView(provinces: packs.provinces, packs: packs.manager, preselected: pickerPreselect, existingUser: fog.count > 0,
                               onDone: { app.finishPicker(hasTrailPoints: fog.count > 0) })
                    .id(packs.provinces?.count ?? -1) // rebuilt when the catalog arrives
                    .ignoresSafeArea()
            case .stats:
                StatsHostView(store: fog.store, here: location.coordinate ?? fog.lastKnown, pins: pins.book,
                              onOpenWalk: { id in app.close(); postcardWalk = id },
                              onClose: { app.close() })
                    .ignoresSafeArea()
            case .you where backupHost != nil:
                YouHostView(backup: backupHost!, packs: packs.manager, provinces: packs.provinces,
                            onOpenPicker: { pickerPreselect = []; app.route = .picker }, onOpenAbout: { app.route = .about },
                            onClose: { app.close() })
                    .id(packs.provinces?.count ?? -1)
                    .ignoresSafeArea()
            case .about:
                AboutHostView(onClose: { app.close() }).ignoresSafeArea()
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
            // Test hook: -droppin 1 places a pin five seconds after launch, to watch it drop in.
            if UserDefaults.standard.integer(forKey: "droppin") > 0 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                    let c = location.coordinate ?? CLLocationCoordinate2D(latitude: 53.2012, longitude: 5.7936)
                    _ = pins.book.add(lat: c.latitude + 0.0008, lon: c.longitude, labelKey: "cat", note: nil, sessionId: nil, nowMs: Int64(Date().timeIntervalSince1970 * 1000))
                }
            }
            switch UserDefaults.standard.string(forKey: "pinsheet") {
            case "new": pinSheet = .new(lat: 53.2012, lon: 5.7936, sessionId: nil)
            case "open": if let first = pins.all.first { pinSheet = .open(id: first.id) }
            default: break
            }
            app.start(hasTrailPoints: fog.count > 0)
            location.onFix = { fog.onLocation($0) }
            location.onAnswer = { allowed in
                if allowed { grantedAction(afterGrant) } else { dialog = .settings }
            }
            location.start()
            // Test hooks: -autostart 1 begins a walk at launch, -autostop N ends it after N seconds.
            if UserDefaults.standard.integer(forKey: "autostart") > 0 { fog.beginWalk() }
            // Test hook: -pointerdemo 1 starts with the camera not following you, so moving the simulated location off-screen shows the pointer.
            if UserDefaults.standard.integer(forKey: "pointerdemo") > 0 { following = false }
            // Test hook (demo video): -demochar <id> picks that character after -demochardelay seconds (default 3), so the picker shows the choice move.
            if let id = UserDefaults.standard.string(forKey: "demochar") {
                let delay = UserDefaults.standard.object(forKey: "demochardelay") != nil ? UserDefaults.standard.double(forKey: "demochardelay") : 3
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                    if let c = (Characters.shared.all as? [Character])?.first(where: { $0.id == id }) { Characters.shared.select(c: c) }
                }
            }
            let stop = UserDefaults.standard.integer(forKey: "autostop")
            if stop > 0 { DispatchQueue.main.asyncAfter(deadline: .now() + Double(stop)) { fog.endWalk() } }
        }
        .task { eraser.purchases.start() }
        .sheet(isPresented: $eraser.showSheet) { EraserSheet(eraser: eraser, purchases: eraser.purchases) }
        .onChange(of: eraser.hint) { _, h in if let h { showHint(h); eraser.hint = nil } }
        .onChange(of: app.route == nil) { _, closed in if !closed { eraser.mapCovered() } }
        .alert(item: $dialog) { d in
            switch d {
            case .rationale:
                return Alert(title: Text(IosTextsKt.dialogRationaleTitleText()), message: Text(IosTextsKt.dialogRationaleTextText()),
                             primaryButton: .default(Text(IosTextsKt.dialogContinueText())) { location.ask() },
                             secondaryButton: .cancel(Text(IosTextsKt.notNowText())))
            case .settings:
                return Alert(title: Text(IosTextsKt.dialogSettingsTitleText()), message: Text(IosTextsKt.iosDialogSettingsTextText()),
                             primaryButton: .default(Text(IosTextsKt.openSettingsText())) {
                                 if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                             },
                             secondaryButton: .cancel(Text(IosTextsKt.notNowText())))
            }
        }
        .onChange(of: fog.session?.id) { _, _ in
            backupHost?.setRecording(value: fog.session != nil)
            location.setRecording(fog.session != nil)
        }
        .onChange(of: fog.finished?.id) { _, id in
            // A postcard when a walk ends (not for tiny ones, like a start-stop by mistake).
            if let walk = fog.finished, let id, walk.distanceM >= StatsScreenKt.MIN_WALK_M {
                postcardWalk = id
                if !pinTipSeen { showPinTip = true }
            }
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
