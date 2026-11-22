import CoreGraphics
import CoreLocation
import MapLibre
import Shared

/// The shared eraser engine asks the map where things are through this: [MapProjection] over a MapLibre map. Positions are in
/// points, like the touches that come from the screen.
final class MapLibreProjection: NSObject, MapProjection {
    private weak var map: MLNMapView?

    init(map: MLNMapView) { self.map = map }

    var width: Int32 { Int32(map?.bounds.width ?? 0) }
    var height: Int32 { Int32(map?.bounds.height ?? 0) }
    var zoom: Double { map?.zoomLevel ?? 0 }
    /// The whole world is 512 points wide at zoom 0 and doubles with every zoom step.
    var worldSizePx: Double { 512 * pow(2, map?.zoomLevel ?? 0) }

    func toScreen(lat: Double, lon: Double) -> ScreenPoint {
        guard let map else { return ScreenPoint(x: 0, y: 0) }
        let p = map.convert(CLLocationCoordinate2D(latitude: lat, longitude: lon), toPointTo: map)
        return ScreenPoint(x: Float(p.x), y: Float(p.y))
    }

    func fromScreen(x: Float, y: Float) -> LatLon {
        guard let map else { return LatLon(lat: 0, lon: 0) }
        let c = map.convert(CGPoint(x: CGFloat(x), y: CGFloat(y)), toCoordinateFrom: map)
        return LatLon(lat: c.latitude, lon: c.longitude)
    }

    func metersToPixels(meters: Double, lat: Double) -> Float {
        guard let map else { return 0 }
        let perPoint = map.metersPerPoint(atLatitude: lat)
        return perPoint > 0 ? Float(meters / perPoint) : 0
    }
}

/// Where the eraser puts its spots and takes them back from: the fog model.
final class FogErasedStore: NSObject, ErasedStore {
    private let model: FogModel

    init(model: FogModel) { self.model = model }

    func add(points: [TrailPoint]) { model.addErased(points) }

    func remove(strokeId: Int64, points: [TrailPoint]) { model.removeErased(strokeId: strokeId, points: points) }
}
