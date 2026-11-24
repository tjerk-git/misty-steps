import CoreLocation
import Foundation
import Shared

/// The text of the pill at the top of the map: buckets of mist cleared in your city or town. Counted off the main thread by the
/// shared `PillSource` every `PillRefresh.INTERVAL_MS` while the map is on screen; a failed count keeps the old text.
final class PillModel: ObservableObject {
    @Published var content: PillContent?
    /// False while a full-screen page covers the map: no counting then.
    var active = true

    private var source: PillSource?
    private var here: (() -> CLLocationCoordinate2D?)?
    private var timer: Timer?

    func start(store: SQLiteTrailStore, here: @escaping () -> CLLocationCoordinate2D?) {
        guard source == nil else { return }
        source = PillSource(points: store, sessions: store)
        self.here = here
        refresh()
        let seconds = Double(PillRefresh.shared.INTERVAL_MS) / 1000
        timer = Timer.scheduledTimer(withTimeInterval: seconds, repeats: true) { [weak self] _ in self?.refresh() }
    }

    func refresh() {
        guard active, let source else { return }
        let c = here?()
        source.refresh(lat: c.map { KotlinDouble(value: $0.latitude) }, lon: c.map { KotlinDouble(value: $0.longitude) }) { [weak self] content in
            self?.content = content
        }
    }

    deinit { timer?.invalidate() }
}
