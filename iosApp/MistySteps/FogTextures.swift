import CoreGraphics
import Shared

/// The 256-pixel swirl tiles of the mist, from the shared noise: the same picture on the map and on the postcard.
enum FogTextures {
    /// The noise tinted by [tint] times the palette's multiplier ([palette]), as premultiplied pixels.
    static func tile(seed: Int32, tint: UInt32, palette: UInt32, maxAlpha: Float, contrast: Float) -> CGImage? {
        func mix(_ shift: UInt32) -> UInt32 { ((tint >> shift) & 255) * ((palette >> shift) & 255) / 255 }
        let rgb = (mix(16) << 16) | (mix(8) << 8) | mix(0)
        let pixels = FogNoise.shared.pixels(size: 256, seed: seed, tint: Int32(bitPattern: rgb), maxAlpha: maxAlpha, contrast: contrast)
        var rgba = [UInt8](repeating: 0, count: 256 * 256 * 4)
        for i in 0..<(256 * 256) {
            let p = UInt32(bitPattern: pixels.get(index: Int32(i)))
            let a = p >> 24
            rgba[4 * i] = UInt8(((p >> 16) & 255) * a / 255)
            rgba[4 * i + 1] = UInt8(((p >> 8) & 255) * a / 255)
            rgba[4 * i + 2] = UInt8((p & 255) * a / 255)
            rgba[4 * i + 3] = UInt8(a)
        }
        guard let ctx = CGContext(data: &rgba, width: 256, height: 256, bitsPerComponent: 8, bytesPerRow: 1024,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        else { return nil }
        return ctx.makeImage()
    }
}
