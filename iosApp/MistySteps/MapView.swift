import CoreLocation
import MapLibre
import SwiftUI

/// The map, drawn from the Misty style over a downloaded province pack.
struct MapView: UIViewRepresentable {
    var coordinate: CLLocationCoordinate2D?
    var styleJSON: String?
    @Binding var status: String

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero)
        map.delegate = context.coordinator
        map.setCenter(CLLocationCoordinate2D(latitude: 53.2012, longitude: 5.7936), zoomLevel: 13, animated: false)
        map.showsUserLocation = true
        map.logoView.isHidden = true
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.status = $status
        if let styleJSON, context.coordinator.styleJSON != styleJSON {
            context.coordinator.styleJSON = styleJSON
            let file = FileManager.default.temporaryDirectory.appendingPathComponent("misty-style.json")
            try? styleJSON.write(to: file, atomically: true, encoding: .utf8)
            map.styleURL = file
        }
        guard let coordinate, !context.coordinator.centered else { return }
        context.coordinator.centered = true
        map.setCenter(coordinate, zoomLevel: 15, animated: true)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var centered = false
        var styleJSON: String?
        var status: Binding<String>?

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            DispatchQueue.main.async { self.status?.wrappedValue = "style ok: \(style.layers.count) layers" }
        }

        func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
            DispatchQueue.main.async { self.status?.wrappedValue = "map failed: \(error.localizedDescription)" }
        }
    }
}
