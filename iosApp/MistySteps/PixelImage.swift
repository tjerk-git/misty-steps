import CoreGraphics
import Shared
import UIKit

/// Pixel-art sprites (ARGB ints from the shared module) as crisp images.
enum PixelImage {
    /// An [n] x [n] sprite scaled up [scale] times with nearest-neighbour, so edges stay sharp.
    static func make(pixels: KotlinIntArray, n: Int = 18, scale: Int = 3) -> UIImage? {
        var rgba = [UInt8](repeating: 0, count: n * n * 4)
        for i in 0..<(n * n) {
            let p = UInt32(bitPattern: pixels.get(index: Int32(i)))
            let a = (p >> 24) & 255
            rgba[4 * i] = UInt8(((p >> 16) & 255) * a / 255)
            rgba[4 * i + 1] = UInt8(((p >> 8) & 255) * a / 255)
            rgba[4 * i + 2] = UInt8((p & 255) * a / 255)
            rgba[4 * i + 3] = UInt8(a)
        }
        guard let ctx = CGContext(data: &rgba, width: n, height: n, bitsPerComponent: 8, bytesPerRow: n * 4,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue),
              let small = ctx.makeImage() else { return nil }
        let size = CGSize(width: n * scale, height: n * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { r in
            // UIKit's renderer has y pointing down and CGContext.draw draws images from the bottom: flip once so the sprite is upright.
            r.cgContext.translateBy(x: 0, y: size.height)
            r.cgContext.scaleBy(x: 1, y: -1)
            r.cgContext.interpolationQuality = .none
            r.cgContext.draw(small, in: CGRect(origin: .zero, size: size))
        }
    }
}
