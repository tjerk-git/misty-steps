import CoreLocation
import MapLibre
import Shared
import UIKit

/// Draws a walk as a little pixel-style postcard, like the Android one: the map with this walk's clearing cut out of the mist,
/// the trail, the pins, your character where you stopped, and the walk's numbers.
@MainActor
final class PostcardRenderer {
    private let width: CGFloat = 1080, height: CGFloat = 1440, margin: CGFloat = 54, mapHeight: CGFloat = 1010, border: CGFloat = 8
    private let paper = UIColor(red: 246 / 255, green: 243 / 255, blue: 236 / 255, alpha: 1)
    private let ink = UIColor(red: 46 / 255, green: 53 / 255, blue: 80 / 255, alpha: 1)
    private let dusk = UIColor(red: 91 / 255, green: 107 / 255, blue: 140 / 255, alpha: 1)
    private let ember = UIColor(red: 180 / 255, green: 83 / 255, blue: 106 / 255, alpha: 1)

    private var snapshotter: MLNMapSnapshotter?

    /// [styleJSON]: the map style of the installed packs, or nil to leave the map plain paper colour.
    func render(walk: Session, points: [TrailPoint], pins: [Pin], character: Character?, customName: String?, styleJSON: String?) async -> UIImage? {
        guard !points.isEmpty else { return nil }
        let mapRect = CGRect(x: margin, y: margin, width: width - 2 * margin, height: mapHeight)
        let view = PostcardView.Companion.shared.fit(points: points, w: Int32(mapRect.width), h: Int32(mapRect.height))
        let base = await snapshot(view: view, styleJSON: styleJSON)
        let last = points.last!
        let palette = MistPalette.shared.at(epochMs: walk.endTime?.int64Value ?? walk.startTime, lat: last.lat, lon: last.lon)

        let mapImage = UIGraphicsImageRenderer(size: mapRect.size, format: format()).image { r in
            let ctx = r.cgContext
            UIColor(red: 242 / 255, green: 239 / 255, blue: 233 / 255, alpha: 1).setFill()
            ctx.fill(CGRect(origin: .zero, size: mapRect.size))
            base?.draw(in: CGRect(origin: .zero, size: mapRect.size))
            fog(points: points, view: view, palette: palette, size: mapRect.size).draw(at: .zero)
            drawTrail(ctx, points: points, view: view)
            drawPins(ctx, pins: pins, view: view)
            if let character { drawCharacter(ctx, character: character, last: last, view: view) }
        }

        let parts = PostcardTexts.shared.parts(distanceM: walk.distanceM,
                                               durationMs: walk.endTime.map { KotlinLong(value: $0.int64Value - walk.startTime) },
                                               newAreaM2: walk.newAreaM2)
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format()).image { r in
            let ctx = r.cgContext
            paper.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
            mapImage.draw(in: mapRect)
            pixelFrame(ctx, mapRect)
            drawTexts(walk: walk, parts: parts as? [String] ?? [], customName: customName, mapRect: mapRect)
        }
    }

    private func format() -> UIGraphicsImageRendererFormat {
        let f = UIGraphicsImageRendererFormat()
        f.scale = 1
        return f
    }

    /// The map under the postcard: a MapLibre snapshot at exactly the scale the view uses, so trail, clearing and pins land on the
    /// right streets. nil without packs.
    private func snapshot(view: PostcardView, styleJSON: String?) async -> UIImage? {
        guard let styleJSON else { return nil }
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("postcard-style.json")
        guard (try? styleJSON.write(to: file, atomically: true, encoding: .utf8)) != nil else { return nil }
        // MapLibre counts 512-point tiles: the world is 512 * 2^zoom points wide at scale 1.
        let zoom = log2(view.scale / 512)
        let center = CLLocationCoordinate2D(latitude: Mercator.shared.lat(y: view.cy), longitude: Mercator.shared.lon(x: view.cx))
        let options = MLNMapSnapshotOptions(styleURL: file, camera: MLNMapCamera(lookingAtCenter: center, altitude: 1000, pitch: 0, heading: 0),
                                            size: CGSize(width: Int(view.w), height: Int(view.h)))
        options.zoomLevel = zoom
        options.scale = 1
        let shooter = MLNMapSnapshotter(options: options)
        snapshotter = shooter
        return await withCheckedContinuation { cont in
            shooter.start { snap, _ in cont.resume(returning: snap?.image) }
        }
    }

    /// Mist over the map, with this walk's clearing cut out.
    private func fog(points: [TrailPoint], view: PostcardView, palette: MistPalette.Colors, size: CGSize) -> UIImage {
        let shadow = FogTextures.tile(seed: 7, tint: 0xB0B8CE, palette: UInt32(bitPattern: palette.shadowTint), maxAlpha: 0.55, contrast: 1.6)
        let light = FogTextures.tile(seed: 23, tint: 0xFFFFFF, palette: UInt32(bitPattern: palette.lightTint), maxAlpha: 0.85, contrast: 1.8)
        let brush = FogOverlayView.makeBrush()
        return UIGraphicsImageRenderer(size: size, format: format()).image { r in
            let ctx = r.cgContext
            let argb = UInt32(bitPattern: palette.base)
            UIColor(red: CGFloat((argb >> 16) & 255) / 255, green: CGFloat((argb >> 8) & 255) / 255,
                    blue: CGFloat(argb & 255) / 255, alpha: CGFloat(argb >> 24) / 255).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            func tiled(_ image: CGImage?, scale: CGFloat) {
                guard let image else { return }
                let t = 256 * scale
                var y: CGFloat = 0
                while y < size.height {
                    var x: CGFloat = 0
                    while x < size.width {
                        UIImage(cgImage: image).draw(in: CGRect(x: x, y: y, width: t, height: t))
                        x += t
                    }
                    y += t
                }
            }
            tiled(shadow, scale: 3)
            tiled(light, scale: 1.7)
            ctx.setBlendMode(.destinationOut)
            for p in points {
                let rad = max(CGFloat(Mercator.shared.metersToUnits(meters: p.radius, lat: p.lat) * view.scale), 3)
                let x = CGFloat(view.x(lon: p.lon)), y = CGFloat(view.y(lat: p.lat))
                ctx.draw(brush, in: CGRect(x: x - rad, y: y - rad, width: 2 * rad, height: 2 * rad))
            }
        }
    }

    private func drawTrail(_ ctx: CGContext, points: [TrailPoint], view: PostcardView) {
        guard points.count >= 2 else { return }
        ctx.saveGState()
        ctx.setStrokeColor(ember.cgColor)
        ctx.setLineWidth(11)
        ctx.setLineCap(.round)
        ctx.setLineDash(phase: 0, lengths: [0.01, 22])
        ctx.move(to: CGPoint(x: CGFloat(view.x(lon: points[0].lon)), y: CGFloat(view.y(lat: points[0].lat))))
        for p in points.dropFirst() { ctx.addLine(to: CGPoint(x: CGFloat(view.x(lon: p.lon)), y: CGFloat(view.y(lat: p.lat)))) }
        ctx.strokePath()
        ctx.restoreGState()
    }

    private func drawPins(_ ctx: CGContext, pins: [Pin], view: PostcardView) {
        let size: CGFloat = 18 * 4
        for pin in pins {
            let x = CGFloat(view.x(lon: pin.lon)), y = CGFloat(view.y(lat: pin.lat))
            ctx.setFillColor(UIColor(white: 0, alpha: 0.25).cgColor)
            ctx.fillEllipse(in: CGRect(x: x - size * 0.25, y: y - size * 0.06, width: size * 0.5, height: size * 0.12))
            PixelImage.make(pixels: PinSprites.shared.pixels(label: pin.label))?
                .draw(in: CGRect(x: x - size / 2, y: y - size * 0.95, width: size, height: size))
        }
    }

    private func drawCharacter(_ ctx: CGContext, character: Character, last: TrailPoint, view: PostcardView) {
        let size: CGFloat = 18 * 6
        let x = CGFloat(view.x(lon: last.lon)), y = CGFloat(view.y(lat: last.lat))
        ctx.setFillColor(UIColor(white: 0, alpha: 0.25).cgColor)
        ctx.fillEllipse(in: CGRect(x: x - size * 0.28, y: y - size * 0.07, width: size * 0.56, height: size * 0.14))
        let pixels = Characters.shared.frameAt(c: character, walking: false, timeMs: 0)
        ctx.interpolationQuality = .none
        PixelImage.make(pixels: pixels)?.draw(in: CGRect(x: x - size / 2, y: y - size * 0.92, width: size, height: size))
    }

    /// A chunky border with notched corners, like an old game's dialogue box.
    private func pixelFrame(_ ctx: CGContext, _ r: CGRect) {
        let b = border
        ctx.setStrokeColor(ink.cgColor)
        ctx.setLineWidth(b)
        for (a, c) in [(CGPoint(x: r.minX + b, y: r.minY), CGPoint(x: r.maxX - b, y: r.minY)),
                       (CGPoint(x: r.minX + b, y: r.maxY), CGPoint(x: r.maxX - b, y: r.maxY)),
                       (CGPoint(x: r.minX, y: r.minY + b), CGPoint(x: r.minX, y: r.maxY - b)),
                       (CGPoint(x: r.maxX, y: r.minY + b), CGPoint(x: r.maxX, y: r.maxY - b))] {
            ctx.move(to: a); ctx.addLine(to: c); ctx.strokePath()
        }
        // Paper-coloured notches, then an ink square inside each, so the corners look stepped.
        for (x, y) in [(r.minX, r.minY), (r.maxX, r.minY), (r.minX, r.maxY), (r.maxX, r.maxY)] {
            ctx.setFillColor(paper.cgColor)
            ctx.fill(CGRect(x: x - b / 2, y: y - b / 2, width: b, height: b))
            ctx.setFillColor(ink.cgColor)
            let left = x == r.minX, top = y == r.minY
            ctx.fill(CGRect(x: left ? x + b / 2 : x - b * 1.5, y: top ? y + b / 2 : y - b * 1.5, width: b, height: b))
        }
    }

    private func drawTexts(walk: Session, parts: [String], customName: String?, mapRect: CGRect) {
        let y0 = mapRect.maxY + 92 // baseline, as on Android; UIKit draws from the top of the line
        func draw(_ text: String, font: UIFont, color: UIColor, x: CGFloat, baseline: CGFloat, kern: CGFloat = 0, right: Bool = false) {
            let s = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color, .kern: kern])
            let w = s.size().width
            s.draw(at: CGPoint(x: right ? x - w : x, y: baseline - font.ascender))
        }
        let serif = UIFont(name: "Georgia-Bold", size: 64) ?? .boldSystemFont(ofSize: 64)
        let mono34 = UIFont.monospacedSystemFont(ofSize: 34, weight: .regular)
        draw("Misty Steps", font: serif, color: ink, x: margin, baseline: y0, kern: 64 * 0.06)
        if let name = customName { // only a name you gave: the species name would just say what the picture already shows
            draw(PostcardTexts.shared.with(name: name), font: mono34, color: dusk, x: width - margin, baseline: y0, right: true)
        }
        let date = DateFormatter()
        date.dateStyle = .full
        date.timeStyle = .short
        draw(date.string(from: Date(timeIntervalSince1970: Double(walk.startTime) / 1000)), font: mono34, color: dusk, x: margin, baseline: y0 + 56)
        draw(parts.joined(separator: "  ·  "), font: .monospacedSystemFont(ofSize: 44, weight: .bold), color: ember, x: margin, baseline: y0 + 132)
        draw(PostcardTexts.shared.credit(), font: .systemFont(ofSize: 22), color: dusk, x: width - margin, baseline: height - 30, right: true)
    }
}
