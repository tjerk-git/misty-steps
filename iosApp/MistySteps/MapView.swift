import CoreLocation
import MapLibre
import SwiftUI

/// The map. For the spike it loads MapLibre's online demo style; the offline province packs come later.
struct MapView: UIViewRepresentable {
    var coordinate: CLLocationCoordinate2D?

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: URL(string: "https://demotiles.maplibre.org/style.json"))
        map.setCenter(CLLocationCoordinate2D(latitude: 53.2012, longitude: 5.7936), zoomLevel: 13, animated: false)
        map.showsUserLocation = true
        map.logoView.isHidden = true
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        guard let coordinate, !context.coordinator.centered else { return }
        context.coordinator.centered = true
        map.setCenter(coordinate, zoomLevel: 15, animated: true)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator { var centered = false }
}
