import Shared
import SwiftUI

/// A small round pointer on the screen edge: your character's face and an arrow toward where it is, shown while the map is
/// looking elsewhere. A tap brings you back. Same look as Android's OffscreenPointerButton; the placement comes from the
/// shared OffscreenPointer. Position it with its centre on the placement.
struct OffscreenPointerView: View {
    let placement: PointerPlacement
    let onTap: () -> Void

    var body: some View {
        ZStack {
            Arrow()
                .fill(Misty.ink.opacity(0.8))
                .frame(width: 64, height: 64)
                .rotationEffect(.radians(Double(placement.angleRad)))
            Button(action: onTap) {
                CharacterFace(size: 30)
                    .frame(width: 44, height: 44)
                    .background(Misty.frost.opacity(0.85), in: Circle())
                    .shadow(color: .black.opacity(0.15), radius: 2, y: 1)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(IosTextsKt.showMyLocationText())
        }
        .frame(width: 64, height: 64)
    }
}

/// A small triangle on the right rim of a 64 pt square, pointing right; the caller rotates it.
private struct Arrow: Shape {
    func path(in rect: CGRect) -> Path {
        let c = CGPoint(x: rect.midX, y: rect.midY)
        var p = Path()
        p.move(to: CGPoint(x: c.x + 32, y: c.y))
        p.addLine(to: CGPoint(x: c.x + 21, y: c.y - 8))
        p.addLine(to: CGPoint(x: c.x + 21, y: c.y + 8))
        p.closeSubpath()
        return p
    }
}
