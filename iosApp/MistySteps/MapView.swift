import CoreLocation
import MapLibre
import SwiftUI

/// The map, drawn from the Misty style over a downloaded province pack, with the fog on top.
struct MapView: UIViewRepresentable {
    var coordinate: CLLocationCoordinate2D?
    var styleJSON: String?
    let fogModel: FogModel
    @Binding var status: String
    @Binding var following: Bool

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero)
        map.delegate = context.coordinator
        map.setCenter(CLLocationCoordinate2D(latitude: 53.2012, longitude: 5.7936), zoomLevel: 15, animated: false)
        map.showsUserLocation = true
        map.logoView.isHidden = true
        map.allowsRotating = false
        map.allowsTilting = false
        let fog = FogOverlayView(map: map, model: fogModel)
        fog.enabled = UserDefaults.standard.string(forKey: "fog") != "0"
        map.addSubview(fog)
        context.coordinator.fog = fog
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

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var centered = false
        var styleJSON: String?
        var status: Binding<String>?
        var following: Binding<Bool>?
        var fog: FogOverlayView?
        var perfRunning = false
        private var link: CADisplayLink?
        private var stamps: [CFTimeInterval] = []
        private var perfStart: CFTimeInterval = 0
        private weak var perfMap: MLNMapView?

        func mapViewRegionIsChanging(_ mapView: MLNMapView) { fog?.mapMoved() }
        func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) { fog?.mapMoved() }

        /// Dragging or pinching the map stops following; the recentre button starts it again.
        func mapView(_ mapView: MLNMapView, regionWillChangeWith reason: MLNCameraChangeReason, animated: Bool) {
            if reason.contains(.gesturePan) || reason.contains(.gesturePinch) || reason.contains(.gestureZoomIn) || reason.contains(.gestureZoomOut) {
                DispatchQueue.main.async { self.following?.wrappedValue = false }
            }
        }

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            DispatchQueue.main.async { self.status?.wrappedValue = "style ok" }
            fog?.mapMoved()
            if UserDefaults.standard.integer(forKey: "perf") > 0 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 6) { self.runPerf(mapView) }
            }
        }

        func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
            DispatchQueue.main.async { self.status?.wrappedValue = "map failed: \(error.localizedDescription)" }
        }

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
