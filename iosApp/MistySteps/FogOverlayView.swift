import CoreLocation
import MapLibre
import Shared
import UIKit

/// The mist above the map. A veil layer is cut by a mask layer: the mask is built off the main thread at half
/// resolution (planned by the shared MaskPlan) and, while the map moves, only repositioned and scaled, so panning
/// costs no CPU drawing. Same idea as Android's FogPainter, on Core Graphics and Core Animation.
final class FogOverlayView: UIView {
    private weak var map: MLNMapView?
    private let model: FogModel
    private let resolution = 0.5

    private var front: CGImage?
    private var frontSpec: MaskSpec?
    private var requested: MaskSpec?
    private var building = false
    private var waiting: (MaskSpec, Data)?
    private let worker = DispatchQueue(label: "fog-mask", qos: .userInitiated)
    private let maskLayer = CALayer()
    private let brush = FogOverlayView.makeBrush()

    /// Wall time of the last mask build in ms, for the perf readout.
    private(set) var lastBuildMs = 0.0
    private(set) var builds = 0
    var spotCount: Int { model.count }
    var enabled = true { didSet { isHidden = !enabled } }

    init(map: MLNMapView, model: FogModel) {
        self.map = map
        self.model = model
        super.init(frame: map.bounds)
        isUserInteractionEnabled = false
        autoresizingMask = [.flexibleWidth, .flexibleHeight]
        layer.backgroundColor = UIColor(white: 0.96, alpha: 0.92).cgColor
        maskLayer.anchorPoint = .zero
        maskLayer.magnificationFilter = .linear
        updatePalette()
        model.observe { [weak self] in self?.mapMoved() }
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() { super.layoutSubviews(); mapMoved() }

    /// Re-tints the veil by the sun's height at the map centre.
    func updatePalette() {
        guard let c = map?.centerCoordinate else { return }
        let colors = MistPalette.shared.at(epochMs: Int64(Date().timeIntervalSince1970 * 1000), lat: c.latitude, lon: c.longitude)
        let argb = UInt32(bitPattern: colors.base)
        layer.backgroundColor = UIColor(
            red: CGFloat((argb >> 16) & 255) / 255, green: CGFloat((argb >> 8) & 255) / 255,
            blue: CGFloat(argb & 255) / 255, alpha: CGFloat(argb >> 24) / 255
        ).cgColor
    }

    /// Call whenever the camera or the spots change.
    func mapMoved() {
        guard enabled, let map, bounds.width > 0 else { return }
        let view = currentView(map)
        if MaskPlan.shared.needsRebuild(built: requested, now: view) { request(view) }
        place(view)
    }

    /// What the mask has to show: fog pixel = mercator * k + off, measured through the live map projection.
    private func currentView(_ map: MLNMapView) -> MaskView {
        let c = map.centerCoordinate
        let p0 = map.convert(c, toPointTo: map)
        let c1 = CLLocationCoordinate2D(latitude: c.latitude, longitude: c.longitude + 0.01)
        let p1 = map.convert(c1, toPointTo: map)
        let m = Mercator.shared
        let k = Double(p1.x - p0.x) / (m.x(lon: c1.longitude) - m.x(lon: c.longitude)) * resolution
        let offX = Double(p0.x) * resolution - m.x(lon: c.longitude) * k
        let offY = Double(p0.y) * resolution - m.y(lat: c.latitude) * k
        return MaskView(offX: offX, offY: offY, k: k, w: Int32((bounds.width * resolution).rounded(.up)),
                        h: Int32((bounds.height * resolution).rounded(.up)), version: model.version)
    }

    private func place(_ view: MaskView) {
        guard let front, let spec = frontSpec else { return }
        let p = MaskPlan.shared.place(built: spec, now: view)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if layer.mask == nil { layer.mask = maskLayer }
        if maskLayer.contents == nil || (maskLayer.contents as! CGImage) !== front { maskLayer.contents = front }
        maskLayer.frame = CGRect(x: p.tx / resolution, y: p.ty / resolution,
                                 width: Double(spec.w) * p.scale / resolution, height: Double(spec.h) * p.scale / resolution)
        CATransaction.commit()
    }

    private func request(_ view: MaskView) {
        let spec = MaskPlan.shared.specFor(v: view)
        requested = spec
        // Pad the lookup so spots just off-screen still bleed in.
        let pad = Mercator.shared.metersToUnits(meters: FogConstants.shared.CLEAR_RADIUS_M * 2, lat: 80.0) + 4 / spec.k
        let minX = -spec.offX / spec.k - pad
        let minY = -spec.offY / spec.k - pad
        let maxX = (Double(spec.w) - spec.offX) / spec.k + pad
        let maxY = (Double(spec.h) - spec.offY) / spec.k + pad
        let spots = model.index.snapshotData(minX: minX, minY: minY, maxX: maxX, maxY: maxY)
        if building { waiting = (spec, spots); return }
        start(spec, spots)
    }

    private func start(_ spec: MaskSpec, _ spots: Data) {
        building = true
        worker.async { [brush] in
            let t0 = CACurrentMediaTime()
            let image = FogOverlayView.paintMask(spec: spec, spots: spots, brush: brush)
            let ms = (CACurrentMediaTime() - t0) * 1000
            DispatchQueue.main.async {
                self.building = false
                if let image { self.front = image; self.frontSpec = spec; self.lastBuildMs = ms; self.builds += 1 }
                if let next = self.waiting { self.waiting = nil; self.start(next.0, next.1) }
                self.mapMoved()
            }
        }
    }

    /// Worker thread: opaque fog, with every spot cut out through the soft brush.
    private static func paintMask(spec: MaskSpec, spots: Data, brush: CGImage) -> CGImage? {
        let w = Int(spec.w), h = Int(spec.h)
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return nil }
        ctx.translateBy(x: 0, y: CGFloat(h)); ctx.scaleBy(x: 1, y: -1)
        ctx.setFillColor(red: 0, green: 0, blue: 0, alpha: 1)
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        ctx.setBlendMode(.destinationOut)
        var seen = Set<UInt64>()
        spots.withUnsafeBytes { raw in
            let d = raw.bindMemory(to: Double.self)
            for i in 0..<(d.count / 3) {
                let px = d[3 * i] * spec.k + spec.offX, py = d[3 * i + 1] * spec.k + spec.offY
                let r = max(d[3 * i + 2] * spec.k, 1.2)
                if px + r < 0 || py + r < 0 || px - r > Double(w) || py - r > Double(h) { continue }
                let rect = CGRect(x: px - r, y: py - r, width: 2 * r, height: 2 * r)
                if r < 3 {
                    if !seen.insert((UInt64(UInt32(truncatingIfNeeded: Int(px))) << 32) | UInt64(UInt32(truncatingIfNeeded: Int(py)))).inserted { continue }
                    ctx.fillEllipse(in: rect)
                } else {
                    ctx.draw(brush, in: rect)
                }
            }
        }
        return ctx.makeImage()
    }

    /// Soft round brush: fully clear in the middle, feathering out to the edge.
    private static func makeBrush() -> CGImage {
        let size = 128
        let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        let colors = [0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 160.0 / 255, 0, 0, 0, 0] as [CGFloat]
        let gradient = CGGradient(colorSpace: CGColorSpaceCreateDeviceRGB(), colorComponents: colors,
                                  locations: [0, 0.72, 0.88, 1], count: 4)!
        let c = CGFloat(size) / 2
        ctx.drawRadialGradient(gradient, startCenter: CGPoint(x: c, y: c), startRadius: 0,
                               endCenter: CGPoint(x: c, y: c), endRadius: c, options: [])
        return ctx.makeImage()!
    }
}
