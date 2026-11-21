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
        // A walk is movement on foot or by bike: tell iOS, and never let it pause the updates on its own (it would guess we stopped).
        manager.activityType = .fitness
        manager.pausesLocationUpdatesAutomatically = false
    }

    /// A walk is on or off. Only during a walk may location updates continue with the screen locked or the app in the background
    /// (the blue status-bar indicator shows then); outside a walk the app goes quiet like any other. The counterpart of Android's
    /// foreground service. Needs the `location` background mode in Info.plist (project.yml), or this would crash.
    func setRecording(_ on: Bool) {
        manager.allowsBackgroundLocationUpdates = on
        manager.showsBackgroundLocationIndicator = on
        if on && isAllowed { manager.startUpdatingLocation() }
    }

    /// Whether the app may use the location now.
    var isAllowed: Bool { manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways }
    /// The person has not been asked yet (the rationale shows first, like Android).
    var needsAsking: Bool { manager.authorizationStatus == .notDetermined }
    /// Set by the hub: runs when the answer to the system prompt arrives.
    var onAnswer: ((Bool) -> Void)?

    /// Starts listening if allowed. It does not ask: Android asks only when you tap Start wandering or the locate button.
    func start() {
        if isAllowed { manager.startUpdatingLocation() }
    }

    /// Shows the system prompt.
    func ask() { asking = true; manager.requestWhenInUseAuthorization() }
    /// True between asking and the answer, so the callback at launch (permission already given) does nothing.
    private var asking = false

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for l in locations { onFix?(l); observers.forEach { $0(l) } }
        coordinate = locations.last?.coordinate
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if isAllowed { manager.startUpdatingLocation() }
        if asking && manager.authorizationStatus != .notDetermined { asking = false; onAnswer?(isAllowed) }
    }
}
