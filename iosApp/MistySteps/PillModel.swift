import CoreLocation
import Foundation
import Shared

/// The text of the pill at the top of the map: buckets of mist cleared in your city or town. Counted off the main thread by the
/// shared `PillSource`, at most every `PillRefresh.INTERVAL_MS` and only when the cleared ground or your spot changed. Nothing is
/// counted while a page covers the map or the app is not in front (a walk with the screen off); a failed count keeps the old text.
final class PillModel: ObservableObject {
    @Published var content: PillContent?
    /// False while a full-screen page covers the map.
    var mapShown = true
    /// False while the app is in the background.
    var foreground = true

    private var source: PillSource?
    private var version: (() -> Int32)?
    private var here: (() -> CLLocationCoordinate2D?)?
    private var timer: Timer?

    private var active: Bool { mapShown && foreground }

    func start(store: SQLiteTrailStore, version: @escaping () -> Int32, here: @escaping () -> CLLocationCoordinate2D?) {
        guard source == nil else { return }
        source = PillSource(points: store, sessions: store)
        self.version = version
        self.here = here
        refresh()
        let seconds = Double(PillRefresh.shared.INTERVAL_MS) / 1000
        let t = Timer.scheduledTimer(withTimeInterval: seconds, repeats: true) { [weak self] _ in self?.refresh() }
        t.tolerance = 2
        timer = t
    }

    func refresh() {
        guard active, let source else { return }
        let c = here?()
        source.refresh(version: Int64(version?() ?? 0), lat: c.map { KotlinDouble(value: $0.latitude) }, lon: c.map { KotlinDouble(value: $0.longitude) }) { [weak self] content in
            self?.content = content
        }
    }

    deinit { timer?.invalidate() }
}
