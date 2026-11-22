import Shared
import SwiftUI

/// Shown while the eraser is in hand: take back a stroke, or put it down.
struct EraserBar: View {
    @ObservedObject var eraser: EraserModel

    var body: some View {
        HStack(spacing: 4) {
            Button(IosTextsKt.eraserUndoText()) { eraser.undo() }
                .foregroundStyle(eraser.canUndo ? Misty.ink : Misty.dusk)
                .disabled(!eraser.canUndo)
                .padding(.horizontal, 12).padding(.vertical, 10)
            Button(IosTextsKt.eraserDoneText()) { eraser.putDown() }
                .foregroundStyle(Misty.ember)
                .padding(.horizontal, 12).padding(.vertical, 10)
        }
        .font(.system(size: 16, weight: .medium))
        .padding(.horizontal, 8)
        .background(Misty.frost, in: Capsule())
        .shadow(color: .black.opacity(0.18), radius: 4, y: 2)
    }
}

/// What you see when you tap the eraser without owning it: the pitch, the price, Buy and Not now.
struct EraserSheet: View {
    @ObservedObject var eraser: EraserModel
    @ObservedObject var purchases: EraserPurchases

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "eraser.fill").font(.system(size: 26)).foregroundStyle(Misty.ember)
            Text(IosTextsKt.eraserSheetTitleText())
                .font(.system(size: 22, weight: .semibold, design: .serif)).foregroundStyle(Misty.ink)
            if eraser.tasteUsedUp {
                Text(IosTextsKt.eraserTasteUsedText()).font(.system(size: 14)).foregroundStyle(Misty.ember)
            }
            Text(IosTextsKt.eraserSheetBodyText())
                .font(.system(size: 15)).lineSpacing(4).foregroundStyle(Misty.ink).multilineTextAlignment(.leading)
            if purchases.priceText == nil {
                // No price yet: the App Store cannot be reached or the product is not for sale. Say so instead of a dead button.
                Text(IosTextsKt.eraserUnavailableText()).font(.system(size: 13)).foregroundStyle(Misty.dusk)
            }
            Spacer(minLength: 8)
            HStack {
                Spacer()
                Button(IosTextsKt.eraserSheetNotNowText()) { eraser.showSheet = false }.foregroundStyle(Misty.dusk)
                if let price = purchases.priceText {
                    Button(IosTextsKt.eraserSheetBuyText(price: price)) { Task { await eraser.buy() } }
                        .foregroundStyle(Misty.ember).padding(.leading, 16)
                }
            }
            .font(.system(size: 16, weight: .medium))
        }
        .padding(24)
        .presentationDetents([.medium])
        .presentationBackground(Misty.frost)
    }
}
