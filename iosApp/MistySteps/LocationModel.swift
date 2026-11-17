import CoreLocation
import Foundation

/// Where you are, from Core Location. The iOS counterpart of Android's LocationFeed.
final class LocationModel: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    /// Every fix, for the recorder (the published coordinate only drives the camera).
    var onFix: ((CLLocation) -> Void)?
    /// More listeners for every fix (the character on the map).
    private var observers: [(CLLocation) -> Void] = []
    func observeFixes(_ f: @escaping (CLLocation) -> Void) { observers.append(f) }
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func start() {
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        manager.startUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for l in locations { onFix?(l); observers.forEach { $0(l) } }
        coordinate = locations.last?.coordinate
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.startUpdatingLocation()
        }
    }
}
