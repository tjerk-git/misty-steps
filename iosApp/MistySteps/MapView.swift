import CoreLocation
import MapLibre
import Shared
import SwiftUI

/// The map, drawn from the Misty style over a downloaded province pack, with the fog on top.
struct MapView: UIViewRepresentable {
    var coordinate: CLLocationCoordinate2D?
    let locationModel: LocationModel
    var styleJSON: String?
    let fogModel: FogModel
    let pinsModel: PinsModel
    var onOpenPin: (Int64) -> Void
    var onNewPin: (CLLocationCoordinate2D) -> Void
    var onFogPress: () -> Void
    @Binding var status: String
    @Binding var following: Bool

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero)
        map.delegate = context.coordinator
        map.setCenter(CLLocationCoordinate2D(latitude: 53.2012, longitude: 5.7936), zoomLevel: 15, animated: false)
        map.showsUserLocation = false // the character shows where you are
        map.logoView.isHidden = true
        map.allowsRotating = false
        map.allowsTilting = false
        let fog = FogOverlayView(map: map, model: fogModel)
        fog.enabled = UserDefaults.standard.string(forKey: "fog") != "0"
        map.addSubview(fog)
        let character = CharacterOverlayView(map: map)
        map.addSubview(character)
        context.coordinator.fog = fog
        context.coordinator.character = character
        locationModel.observeFixes { [weak character] in character?.offer($0) }
        context.coordinator.model = fogModel
        context.coordinator.pinsModel = pinsModel
        fogModel.observe { [weak coordinator = context.coordinator] in coordinator?.refreshTrail() }
        pinsModel.observe { [weak coordinator = context.coordinator] in coordinator?.refreshPins() }
        // Long-press on cleared ground leaves a pin; a tap on a pin opens it.
        let press = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.longPressed(_:)))
        press.minimumPressDuration = 0.6
        map.addGestureRecognizer(press)
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        tap.delegate = context.coordinator
        map.addGestureRecognizer(tap)
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.status = $status
        context.coordinator.onOpenPin = onOpenPin
        context.coordinator.onNewPin = onNewPin
        context.coordinator.onFogPress = onFogPress
        if let styleJSON, context.coordinator.styleJSON != styleJSON {
            context.coordinator.styleJSON = styleJSON
            let file = FileManager.default.temporaryDirectory.appendingPathComponent("misty-style.json")
            try? styleJSON.write(to: file, atomically: true, encoding: .utf8)
            map.styleURL = file
        }
        context.coordinator.following = $following
        guard let coordinate, !context.coordinator.perfRunning else { return }
        if !context.coordinator.centered {
            context.coordinator.centered = true
            map.setCenter(coordinate, zoomLevel: 16, animated: false)
        } else if following {
            map.setCenter(coordinate, animated: true)
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MLNMapViewDelegate, UIGestureRecognizerDelegate {
        var centered = false
        var styleJSON: String?
        var status: Binding<String>?
        var following: Binding<Bool>?
        var fog: FogOverlayView?
        var character: CharacterOverlayView?
        var model: FogModel?
        var pinsModel: PinsModel?
        var onOpenPin: (Int64) -> Void = { _ in }
        var onNewPin: (CLLocationCoordinate2D) -> Void = { _ in }
        var onFogPress: () -> Void = {}
        private var pinSource: MLNShapeSource?
        private static let pinLayerIds = ["pins-icons", "pins-dots-REST", "pins-dots-SENSES", "pins-dots-ANIMALS", "pins-dots-FINDS", "pins-dots-OTHER"]
        private var pastSource: MLNShapeSource?
        private var nowSource: MLNShapeSource?
        private var lastTrailRefresh: CFTimeInterval = 0
        private var trailRefreshQueued = false
        var perfRunning = false
        private var link: CADisplayLink?
        private var stamps: [CFTimeInterval] = []
        private var perfStart: CFTimeInterval = 0
        private weak var perfMap: MLNMapView?

        func mapViewRegionIsChanging(_ mapView: MLNMapView) { fog?.mapMoved(); character?.refresh() }
        func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) { fog?.mapMoved(); character?.refresh() }

        /// Dragging or pinching the map stops following; the recentre button starts it again.
        func mapView(_ mapView: MLNMapView, regionWillChangeWith reason: MLNCameraChangeReason, animated: Bool) {
            if reason.contains(.gesturePan) || reason.contains(.gesturePinch) || reason.contains(.gestureZoomIn) || reason.contains(.gestureZoomOut) {
                DispatchQueue.main.async { self.following?.wrappedValue = false }
            }
        }

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            DispatchQueue.main.async { self.status?.wrappedValue = "style ok" }
            installTrail(style)
            installPins(style)
            fog?.mapMoved()
            if UserDefaults.standard.integer(forKey: "perf") > 0 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 6) { self.runPerf(mapView) }
            }
        }

        func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
            DispatchQueue.main.async { self.status?.wrappedValue = "map failed: \(error.localizedDescription)" }
        }

        /// Two dotted lines: bright for the walk you are on, faint for earlier walks (same look as Android).
        private func installTrail(_ style: MLNStyle) {
            func layer(_ id: String, _ source: MLNShapeSource, _ color: UIColor, width: Double, gap: Double, opacity: Double) -> MLNLineStyleLayer {
                let l = MLNLineStyleLayer(identifier: id, source: source)
                l.lineColor = NSExpression(forConstantValue: color)
                l.lineWidth = NSExpression(forConstantValue: width)
                l.lineOpacity = NSExpression(forConstantValue: opacity)
                l.lineCap = NSExpression(forConstantValue: NSValue(mlnLineCap: .round))
                l.lineJoin = NSExpression(forConstantValue: NSValue(mlnLineJoin: .round))
                // A near-zero dash with round caps draws a row of dots; dash lengths are in line widths.
                l.lineDashPattern = NSExpression(forConstantValue: [0.01, gap])
                return l
            }
            let empty = MLNShapeCollectionFeature(shapes: [])
            let past = MLNShapeSource(identifier: "trail-past", shape: empty, options: nil)
            let now = MLNShapeSource(identifier: "trail-now", shape: empty, options: nil)
            style.addSource(past); style.addSource(now)
            style.addLayer(layer("trail-past-line", past, UIColor(red: 0x5B / 255, green: 0x6B / 255, blue: 0x8C / 255, alpha: 1), width: 3, gap: 8.0 / 3, opacity: 0.55))
            style.addLayer(layer("trail-now-line", now, UIColor(red: 0xB4 / 255, green: 0x53 / 255, blue: 0x6A / 255, alpha: 1), width: 4.5, gap: 2, opacity: 1))
            pastSource = past; nowSource = now
            lastTrailRefresh = 0
            refreshTrail()
        }

        /// Rebuilds the trail lines from the shared Trails; at most about 2.5 times a second.
        func refreshTrail() {
            guard pastSource != nil, let model else { return }
            let wait = 0.4 - (CACurrentMediaTime() - lastTrailRefresh)
            if wait > 0 {
                if !trailRefreshQueued {
                    trailRefreshQueued = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + wait) { self.trailRefreshQueued = false; self.refreshTrail() }
                }
                return
            }
            lastTrailRefresh = CACurrentMediaTime()
            let segments = model.trails.segments as! [Trails.Segment]
            let pair = TrailGeoJson.shared.build(segments: segments, currentSession: model.session.map { KotlinLong(value: $0.id) }, minStepM: 3.0)
            func shape(_ json: String?) -> MLNShape? {
                json.flatMap { try? MLNShape(data: Data($0.utf8), encoding: String.Encoding.utf8.rawValue) }
            }
            if let s = shape(pair.first as String?) { nowSource?.shape = s }
            if let s = shape(pair.second as String?) { pastSource?.shape = s }
        }

        // MARK: pins

        /// The pin icons (pixel art, drawn at 3x so MapLibre keeps them crisp) and the layers: dots when zoomed out, icons close up.
        private func installPins(_ style: MLNStyle) {
            for label in PinLabel.entries {
                if let image = Coordinator.pinImage(label) { style.setImage(image, forName: "pin-\(label.key)") }
            }
            let source = MLNShapeSource(identifier: "pins", shape: MLNShapeCollectionFeature(shapes: []), options: nil)
            style.addSource(source)
            let groups: [(String, UIColor)] = PinGroup.entries.map { ($0.name, Coordinator.color(argb: $0.dotColor)) }
                + [("OTHER", UIColor(red: 0x5B / 255, green: 0x6B / 255, blue: 0x8C / 255, alpha: 1))]
            for (name, color) in groups {
                let dots = MLNCircleStyleLayer(identifier: "pins-dots-\(name)", source: source)
                dots.predicate = NSPredicate(format: "group == %@", name)
                dots.circleRadius = NSExpression(forConstantValue: 5)
                dots.circleColor = NSExpression(forConstantValue: color)
                dots.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
                dots.circleStrokeWidth = NSExpression(forConstantValue: 1.5)
                dots.minimumZoomLevel = 10
                dots.maximumZoomLevel = 14
                style.addLayer(dots)
            }
            let icons = MLNSymbolStyleLayer(identifier: "pins-icons", source: source)
            icons.iconImageName = NSExpression(forKeyPath: "icon")
            icons.iconAnchor = NSExpression(forConstantValue: NSValue(mlnIconAnchor: .bottom))
            icons.iconAllowsOverlap = NSExpression(forConstantValue: true)
            icons.iconScale = NSExpression(forConstantValue: 0.87)
            icons.minimumZoomLevel = 14
            style.addLayer(icons)
            pinSource = source
            refreshPins()
        }

        func refreshPins() {
            guard let pinSource, let pinsModel else { return }
            let features: [MLNPointFeature] = pinsModel.all.map { pin in
                let f = MLNPointFeature()
                f.coordinate = CLLocationCoordinate2D(latitude: pin.lat, longitude: pin.lon)
                f.attributes = ["id": pin.id, "icon": "pin-\(pin.labelKey)", "group": pin.label.group?.name ?? "OTHER"]
                return f
            }
            pinSource.shape = MLNShapeCollectionFeature(shapes: features)
        }

        private static func color(argb: Int32) -> UIColor {
            let v = UInt32(bitPattern: argb)
            return UIColor(red: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255, blue: CGFloat(v & 255) / 255, alpha: 1)
        }

        private static func pinImage(_ label: PinLabel) -> UIImage? {
            PixelImage.make(pixels: PinSprites.shared.pixels(label: label))
        }

        @objc func longPressed(_ g: UILongPressGestureRecognizer) {
            guard g.state == .began, let map = g.view as? MLNMapView, let model else { return }
            let coordinate = map.convert(g.location(in: map), toCoordinateFrom: map)
            if PinRules.shared.isCleared(index: model.index, lat: coordinate.latitude, lon: coordinate.longitude) {
                onNewPin(coordinate)
            } else {
                onFogPress()
            }
        }

        @objc func tapped(_ g: UITapGestureRecognizer) {
            guard let map = g.view as? MLNMapView else { return }
            let p = g.location(in: map)
            let hit = CGRect(x: p.x - 22, y: p.y - 40, width: 44, height: 56) // icons sit above the spot
            let found = map.visibleFeatures(in: hit, styleLayerIdentifiers: Set(Coordinator.pinLayerIds))
            if let id = (found.first?.attribute(forKey: "id") as? NSNumber)?.int64Value { onOpenPin(id) }
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        /// Pans the map in a circle (~1 km round) for 12 s and records every frame's duration.
        func runPerf(_ map: MLNMapView) {
            guard !perfRunning else { return }
            perfRunning = true; perfMap = map; stamps = []; perfStart = CACurrentMediaTime()
            link = CADisplayLink(target: self, selector: #selector(tick(_:)))
            link?.add(to: .main, forMode: .common)
        }

        @objc private func tick(_ l: CADisplayLink) {
            guard let map = perfMap else { return }
            let t = CACurrentMediaTime() - perfStart
            stamps.append(l.timestamp)
            let a = t * 0.8
            map.setCenter(CLLocationCoordinate2D(latitude: 53.2012 + 0.004 * sin(a), longitude: 5.7936 + 0.007 * cos(a)),
                          zoomLevel: 15 + 0.8 * sin(t * 0.5), animated: false)
            if t >= 12 { finishPerf() }
        }

        private func finishPerf() {
            link?.invalidate(); link = nil; perfRunning = false
            let dts = zip(stamps.dropFirst(), stamps).map { ($0 - $1) * 1000 }.sorted()
            guard !dts.isEmpty else { return }
            func pct(_ p: Double) -> Double { dts[min(dts.count - 1, Int(Double(dts.count) * p))] }
            let slow = Double(dts.filter { $0 > 20 }.count) / Double(dts.count) * 100
            let s = String(format: "PERF fog=%@ spots=%d frames=%d p50=%.1fms p95=%.1fms p99=%.1fms max=%.1fms slow(>20ms)=%.1f%% maskBuilds=%d lastBuild=%.1fms",
                           (fog?.enabled ?? false) ? "on" : "off", fog?.spotCount ?? 0, dts.count, pct(0.5), pct(0.95), pct(0.99), dts.last!, slow,
                           fog?.builds ?? 0, fog?.lastBuildMs ?? 0)
            fputs(s + "\n", stderr)
            DispatchQueue.main.async { self.status?.wrappedValue = s }
        }
    }
}
