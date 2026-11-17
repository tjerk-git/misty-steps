import CoreLocation
import Foundation
import Shared

/// The cleared spots, in the shared PointIndex. Main thread only. `onChange` tells the fog to redraw.
final class FogModel {
    let index = PointIndex()
    private(set) var version: Int32 = 0
    var onChange: (() -> Void)?
    private var last: CLLocationCoordinate2D?

    var count: Int { Int(index.count) }

    /// A fix from Core Location: clears a spot, unless it is right next to the last one.
    func record(_ c: CLLocationCoordinate2D) {
        if let last, hypot((c.latitude - last.latitude) * 111_000, (c.longitude - last.longitude) * 67_000) < 15 { return }
        last = c
        index.add(lat: c.latitude, lon: c.longitude, radiusMeters: FogConstants.shared.CLEAR_RADIUS_M)
        version += 1
        onChange?()
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
        onChange?()
    }
}
