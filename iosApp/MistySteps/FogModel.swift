import CoreLocation
import Foundation
import Shared

/// The cleared spots (in the shared PointIndex) and the walk being recorded (shared WalkRecorder).
/// Main thread only. `onChange` tells the fog to redraw.
final class FogModel: ObservableObject {
    private(set) var index = PointIndex()
    /// The lines of every walk, for drawing.
    private(set) var trails = Trails()
    private(set) var version: Int32 = 0
    private var observers: [() -> Void] = []

    /// Calls [f] whenever spots, trails or the walk change.
    func observe(_ f: @escaping () -> Void) { observers.append(f) }
    private func notify() { observers.forEach { $0() } }

    let store: SQLiteTrailStore
    private let writer = DispatchQueue(label: "fog-store")
    private let recorder = WalkRecorder()

    /// The walk being recorded, or nil.
    @Published private(set) var session: Session?
    @Published private(set) var distanceM = 0.0
    @Published private(set) var inVehicle = false
    /// The walk that just ended, for the postcard.
    @Published private(set) var finished: Session?
    private var sessionPoints: Int32 = 0

    var count: Int { Int(index.count) }

    /// The last place a spot was cleared, for the stats when there is no fix yet.
    private(set) var lastKnown: CLLocationCoordinate2D?

    /// The newest fix that was good enough to use, for dropping a pin where you stand.
    private(set) var lastGoodFix: CLLocation?

    /// Opens the store and puts every saved spot back into the index.
    init(store: SQLiteTrailStore) {
        self.store = store
        for p in store.loadAll() {
            index.add(lat: p.lat, lon: p.lon, radiusMeters: p.radius)
            trails.add(p: p, newSegment: false)
            lastKnown = CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
        }
        version += 1
    }

    /// Reads every spot from the store again (after an import).
    func reload() {
        index = PointIndex()
        trails = Trails()
        for p in store.loadAll() {
            index.add(lat: p.lat, lon: p.lon, radiusMeters: p.radius)
            trails.add(p: p, newSegment: false)
            lastKnown = CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
        }
        version += 1
        notify()
    }

    /// "Start wandering": a new walk, its own line, logged in the store.
    func beginWalk() {
        guard session == nil else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let s = Session(id: now, startTime: now, endTime: nil, distanceM: 0, points: 0,
                        batteryStart: nil, batteryEnd: nil, newAreaM2: nil)
        recorder.begin()
        session = s; distanceM = 0; sessionPoints = 0; inVehicle = false
        writer.async { [store] in store.startSession(s: s) }
        notify()
    }

    /// Ends the walk and saves its log entry.
    func endWalk() {
        guard let s = session else { return }
        recorder.end()
        let done = Session(id: s.id, startTime: s.startTime, endTime: KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000)),
                           distanceM: distanceM, points: sessionPoints, batteryStart: s.batteryStart, batteryEnd: nil, newAreaM2: nil)
        session = nil; inVehicle = false
        writer.async { [store] in
            store.endSession(s: done)
            // Count the ground nobody had cleared before this walk, like Android does; then the postcard can say so.
            let area = FogModel.newGround(store: store, walk: done.id)
            let final = Session(id: done.id, startTime: done.startTime, endTime: done.endTime, distanceM: done.distanceM, points: done.points,
                                batteryStart: done.batteryStart, batteryEnd: done.batteryEnd, newAreaM2: area.map { KotlinDouble(value: $0) })
            if area != nil { store.endSession(s: final) }
            DispatchQueue.main.async { self.finished = final }
        }
        version += 1
        notify()
    }

    /// A fix from Core Location. Only walks clear fog.
    func onLocation(_ l: CLLocation) {
        lastKnown = l.coordinate
        if l.horizontalAccuracy >= 0 && l.horizontalAccuracy <= 35 { lastGoodFix = l }
        guard let s = session else { return }
        let ms = Int64(l.timestamp.timeIntervalSince1970 * 1000)
        let fix = RecordedFix(lat: l.coordinate.latitude, lon: l.coordinate.longitude,
                              accuracyM: l.horizontalAccuracy >= 0 ? KotlinFloat(value: Float(l.horizontalAccuracy)) : nil,
                              timeMs: ms, elapsedMs: ms)
        guard recorder.accepts(fix: fix) else { return }
        let step = recorder.onFix(fix: fix, sessionId: KotlinLong(value: s.id))
        if recorder.inVehicle != inVehicle { inVehicle = recorder.inVehicle }
        guard let step else { return }
        for (i, p) in step.points.enumerated() {
            index.add(lat: p.lat, lon: p.lon, radiusMeters: p.radius)
            trails.add(p: p, newSegment: step.newSegment && i == 0)
        }
        sessionPoints += Int32(step.points.count)
        distanceM += step.distanceM
        let points = step.points
        writer.async { [store] in store.insert(points: points) }
        version += 1
        notify()
    }

    /// Area (m²) this walk cleared that no other walk had; nil when it has no spots.
    private static func newGround(store: SQLiteTrailStore, walk id: Int64) -> Double? {
        let mine = store.loadSessionPoints(id: id)
        guard let first = mine.first else { return nil }
        let pad = 0.01 // about 1 km of latitude: plenty for 100 m circles
        let padLon = pad / max(cos(first.lat * .pi / 180), 0.05)
        let others = store.loadOtherPointsIn(
            id: id,
            minLat: mine.map { $0.lat }.min()! - pad, minLon: mine.map { $0.lon }.min()! - padLon,
            maxLat: mine.map { $0.lat }.max()! + pad, maxLon: mine.map { $0.lon }.max()! + padLon)
        return ClearedCells.Companion.shared.from(points: mine).areaNotIn(other: ClearedCells.Companion.shared.from(points: others))
    }

    /// Test data: random walks of cleared spots around Leeuwarden, like a long-time user's trail.
    func addStressTrail(spots: Int) {
        var rng = SystemRandomNumberGenerator()
        var made = 0
        while made < spots {
            var lat = 53.2012 + Double.random(in: -0.012...0.012, using: &rng)
            var lon = 5.7936 + Double.random(in: -0.02...0.02, using: &rng)
            var heading = Double.random(in: 0..<(2 * .pi), using: &rng)
            for _ in 0..<min(2000, spots - made) {
                heading += Double.random(in: -0.35...0.35, using: &rng)
                let step = Double.random(in: 15...40, using: &rng)
                lat += cos(heading) * step / 111_000
                lon += sin(heading) * step / 67_000
                index.add(lat: lat, lon: lon, radiusMeters: FogConstants.shared.CLEAR_RADIUS_M)
                made += 1
            }
        }
        version += 1
        notify()
    }
}
