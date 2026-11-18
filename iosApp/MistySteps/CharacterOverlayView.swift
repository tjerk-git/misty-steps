import CoreLocation
import MapLibre
import Shared
import UIKit

/// Your chosen character where you are: walking while you move, facing the way you head, with the ring that shows how far one
/// step clears the mist. Same rules as Android's CharacterPainter.
final class CharacterOverlayView: UIView {
    private weak var map: MLNMapView?
    private let ring = CAShapeLayer()
    private let shadow = CAShapeLayer()
    private let sprite = CALayer()
    private var timer: Timer?

    private var last: CLLocation?
    private var lastMoveAt: CFTimeInterval = 0
    private var facingLeft = false
    private var walking = false
    private var cache: [String: CGImage] = [:]
    private let spriteSize: CGFloat = 18 * 2.6 // each sprite pixel is 2.6 points
    private let startedAt = CACurrentMediaTime()

    init(map: MLNMapView) {
        self.map = map
        super.init(frame: map.bounds)
        isUserInteractionEnabled = false
        autoresizingMask = [.flexibleWidth, .flexibleHeight]
        ring.fillColor = UIColor(red: 0xB4 / 255, green: 0x53 / 255, blue: 0x6A / 255, alpha: 0.2).cgColor
        ring.strokeColor = UIColor(red: 0xB4 / 255, green: 0x53 / 255, blue: 0x6A / 255, alpha: 0.7).cgColor
        ring.lineWidth = 2
        shadow.fillColor = UIColor(white: 0, alpha: 0.25).cgColor
        sprite.magnificationFilter = .nearest
        sprite.minificationFilter = .nearest
        [ring, shadow, sprite].forEach { layer.addSublayer($0) }
        isHidden = true
        timer = Timer.scheduledTimer(withTimeInterval: 0.14, repeats: true) { [weak self] _ in self?.tick() }
    }

    required init?(coder: NSCoder) { fatalError() }

    deinit { timer?.invalidate() }

    /// A new fix: works out whether you are walking and which way you face.
    func offer(_ l: CLLocation) {
        let now = CACurrentMediaTime()
        if let prev = last {
            if l.distance(from: prev) >= max(5, l.horizontalAccuracy * 0.5) {
                let dLon = l.coordinate.longitude - prev.coordinate.longitude
                if abs(dLon) > 1e-7 { facingLeft = dLon < 0 }
                last = l
                lastMoveAt = now
            }
        } else {
            last = l
        }
        if l.course >= 0 && l.speed > 0.8 { facingLeft = l.course > 180 }
        walking = l.speed > 0.6 || now - lastMoveAt < 3
        isHidden = false
        refresh()
    }

    /// Call whenever the map moves.
    func refresh() {
        guard let map, let l = last else { return }
        let p = map.convert(l.coordinate, toPointTo: map)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        let metersPerPoint = map.metersPerPoint(atLatitude: l.coordinate.latitude)
        let r = CGFloat(FogConstants.shared.CLEAR_RADIUS_M / max(metersPerPoint, 0.01))
        ring.path = UIBezierPath(ovalIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)).cgPath
        shadow.path = UIBezierPath(ovalIn: CGRect(x: p.x - spriteSize * 0.28, y: p.y - spriteSize * 0.07,
                                                 width: spriteSize * 0.56, height: spriteSize * 0.14)).cgPath
        // Feet on the spot, so the sprite stands slightly above the point.
        sprite.bounds = CGRect(x: 0, y: 0, width: spriteSize, height: spriteSize)
        sprite.position = CGPoint(x: p.x, y: p.y - spriteSize * 0.92 + spriteSize / 2)
        sprite.transform = facingLeft ? CATransform3DMakeScale(-1, 1, 1) : CATransform3DIdentity
        CATransaction.commit()
    }

    private func tick() {
        guard !isHidden, let c = Characters.shared.selected.value as? Character else { return }
        if walking && CACurrentMediaTime() - lastMoveAt > 3 && (last?.speed ?? 0) <= 0.6 { walking = false }
        let ms = Int64((CACurrentMediaTime() - startedAt) * 1000)
        let frameMs = walking ? Characters.shared.WALK_FRAME_MS : Characters.shared.IDLE_FRAME_MS
        let index = Int(ms / frameMs) % 4
        let key = "\(c.id)-\(walking)-\(index)"
        if cache[key] == nil {
            let pixels = Characters.shared.frameAt(c: c, walking: walking, timeMs: Int64(index) * frameMs)
            cache[key] = PixelImage.make(pixels: pixels)?.cgImage
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        sprite.contents = cache[key]
        CATransaction.commit()
    }
}
