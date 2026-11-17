import CoreLocation
import Foundation
import Shared

/// The cleared spots (in the shared PointIndex) and the walk being recorded (shared WalkRecorder).
/// Main thread only. `onChange` tells the fog to redraw.
final class FogModel: ObservableObject {
    let index = PointIndex()
    /// The lines of every walk, for drawing.
    let trails = Trails()
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
        writer.async { [store] in store.endSession(s: done) }
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
