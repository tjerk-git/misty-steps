import Shared
import SwiftUI

/// The colours of the Android theme (Theme.kt).
enum Misty {
    static let ink = Color(red: 0x2E / 255, green: 0x35 / 255, blue: 0x50 / 255)
    static let dusk = Color(red: 0x5B / 255, green: 0x6B / 255, blue: 0x8C / 255)
    static let ember = Color(red: 0xB4 / 255, green: 0x53 / 255, blue: 0x6A / 255)
    /// Surface: near-white with a hint of blue, 95% opaque.
    static let frost = Color(red: 0xFA / 255, green: 0xFB / 255, blue: 0xFF / 255).opacity(0xF2 / 255.0)
}

/// The controls on top of the map: the same layout, sizes and texts as Android's MistyScreen.
struct HubView: View {
    @ObservedObject var fog: FogModel
    let showPinTip: Bool
    let onDismissPinTip: () -> Void
    let onStats: () -> Void
    let onCharacters: () -> Void
    let onLocate: () -> Void
    let onPin: () -> Void
    let onRecord: () -> Void
    @ObservedObject var eraser: EraserModel

    private var recording: Bool { fog.session != nil }

    /// One message at a time under the title: in a vehicle, the pin tip, or a welcome for a new map.
    private var banner: (text: String, onTap: (() -> Void)?)? {
        if recording && fog.inVehicle { return (IosTextsKt.notifTextVehicleText(), nil) }
        if eraser.active { return nil }
        if showPinTip && !recording { return (IosTextsKt.bannerPinTipText(), onDismissPinTip) }
        if !recording && fog.totalDistanceM == 0 { return (IosTextsKt.bannerStartText(), nil) }
        return nil
    }

    var body: some View {
        ZStack {
            // Title and total distance (tap for stats)
            Button(action: onStats) {
                VStack(spacing: 0) {
                    Text("Misty Steps")
                        .font(.system(size: 18, weight: .semibold, design: .serif)).kerning(1.5)
                        .foregroundStyle(Misty.ink)
                    Text(IosTextsKt.mistClearedText(distanceM: fog.totalDistanceM))
                        .font(.system(size: 12)).foregroundStyle(Misty.dusk)
                }
                .padding(.horizontal, 22).padding(.vertical, 10)
                .background(Misty.frost, in: Capsule())
                .shadow(color: .black.opacity(0.18), radius: 5, y: 3)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(.top, 12)

            if let banner {
                Button { banner.onTap?() } label: {
                    Text(banner.text)
                        .font(.system(size: 13)).lineSpacing(3).multilineTextAlignment(.center)
                        .foregroundStyle(Misty.ink)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(Misty.frost, in: RoundedRectangle(cornerRadius: 20))
                        .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                }
                .buttonStyle(.plain).disabled(banner.onTap == nil)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .padding(.top, 88).padding(.horizontal, 32)
            }

            // The magic eraser: picks it up, or shows the purchase sheet when it is not yours yet.
            round(action: { eraser.buttonTapped() }, label: IosTextsKt.eraserButtonText(), fill: eraser.active ? Misty.ember : Misty.frost) {
                Image(systemName: "eraser").font(.system(size: 22, weight: .regular))
                    .foregroundStyle(eraser.active ? Color.white : Misty.ink)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            .padding(.leading, 20).padding(.bottom, 252)
            if eraser.active {
                EraserBar(eraser: eraser)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .padding(.top, 88)
            }

            // Bottom left: your character (opens the picker) above the stats button.
            round(action: onCharacters, label: "Characters") { CharacterFace() }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.leading, 20).padding(.bottom, 180)
            round(action: onStats, label: "Stats") { StatsBars() }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.leading, 20).padding(.bottom, 108)

            // Bottom right: the pin (only while wandering) above the locate button.
            if recording {
                round(action: onPin, label: IosTextsKt.dropAPinText()) { PlaceIcon().frame(width: 24, height: 24) }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(.trailing, 20).padding(.bottom, 180)
            }
            round(action: onLocate, label: IosTextsKt.showMyLocationText()) { Image(systemName: "scope").font(.system(size: 24, weight: .regular)) }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(.trailing, 20).padding(.bottom, 108)

            // Start / stop wandering
            Button(action: onRecord) {
                HStack(spacing: recording ? 12 : 8) {
                    if recording {
                        PulsingDot()
                        Text(IosTextsKt.stopWanderingText()).font(.system(size: 16))
                    } else {
                        Image(systemName: "play.fill").font(.system(size: 16))
                        Text(IosTextsKt.startWanderingText()).font(.system(size: 16))
                    }
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 24).frame(height: 56)
                .background(recording ? Misty.ember : Misty.ink, in: Capsule())
                .shadow(color: .black.opacity(0.25), radius: 5, y: 3)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .padding(.bottom, 28)

            // Required by the OpenStreetMap tile usage policy.
            Text("\u{00A9} OpenStreetMap contributors")
                .font(.system(size: 10)).foregroundStyle(Misty.dusk)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 4).allowsHitTesting(false)
        }
    }

    /// A round 56 pt button on the frosted surface, like Android's small floating buttons.
    private func round<Content: View>(action: @escaping () -> Void, label: String, fill: Color = Misty.frost, @ViewBuilder content: () -> Content) -> some View {
        Button(action: action) {
            content()
                .foregroundStyle(Misty.ink)
                .frame(width: 56, height: 56)
                .background(fill, in: Circle())
                .shadow(color: .black.opacity(0.2), radius: 5, y: 3)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// The white dot that pulses while a walk is being recorded.
private struct PulsingDot: View {
    @State private var dim = false
    var body: some View {
        Circle().fill(.white).frame(width: 10, height: 10)
            .opacity(dim ? 0.3 : 1)
            .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: dim)
            .onAppear { dim = true }
    }
}

/// Your character standing still, as on the Android button (40 pt, idle pose).
struct CharacterFace: View {
    var size: CGFloat = 40

    var body: some View {
        TimelineView(.periodic(from: .now, by: Double(Characters.shared.IDLE_FRAME_MS) / 1000)) { context in
            if let c = Characters.shared.selected.value as? Character {
                let ms = Int64(context.date.timeIntervalSince1970 * 1000)
                let pixels = Characters.shared.frameAt(c: c, walking: false, timeMs: ms)
                if let image = PixelImage.make(pixels: pixels, n: 18, scale: 3) {
                    Image(uiImage: image).interpolation(.none).resizable().frame(width: size, height: size)
                }
            }
        }
    }
}

/// Material's "Place" pin: a teardrop with a hole, like the Android button.
private struct PlaceIcon: View {
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            var drop = Path()
            drop.addArc(center: CGPoint(x: w / 2, y: h * 0.4), radius: w * 0.38, startAngle: .degrees(-210), endAngle: .degrees(30), clockwise: false)
            drop.addLine(to: CGPoint(x: w / 2, y: h * 0.98))
            drop.closeSubpath()
            drop.addEllipse(in: CGRect(x: w / 2 - w * 0.14, y: h * 0.4 - w * 0.14, width: w * 0.28, height: w * 0.28))
            ctx.fill(drop, with: .color(Misty.ink), style: FillStyle(eoFill: true))
        }
    }
}

/// The three bars of Android's stats icon.
private struct StatsBars: View {
    var body: some View {
        HStack(alignment: .bottom, spacing: 3) {
            RoundedRectangle(cornerRadius: 1).frame(width: 4, height: 9)
            RoundedRectangle(cornerRadius: 1).frame(width: 4, height: 16)
            RoundedRectangle(cornerRadius: 1).frame(width: 4, height: 12)
        }
        .frame(height: 18)
    }
}
