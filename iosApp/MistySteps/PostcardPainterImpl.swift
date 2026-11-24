import Photos
import Shared
import SwiftUI
import UIKit

/// The postcard screen's platform side on iOS: paints the picture, saves it to Photos and opens the share sheet.
final class PostcardPainterImpl: NSObject, ObservableObject, PostcardPainter {
    private let store: SQLiteTrailStore
    var pins: PinBook?
    var packs: PackStore?

    init(store: SQLiteTrailStore) { self.store = store }

    func paint(walkId: Int64, done: @escaping (Data?) -> Void) {
        Task { @MainActor in
            guard let walk = store.loadSession(id: walkId) else { done(nil); return }
            let points = store.loadSessionPoints(id: walkId)
            let walkPins = (pins?.forWalk(sessionId: walkId) as? [Pin]) ?? []
            let character = Characters.shared.selected.value as? Character
            let name = character.flatMap { Characters.shared.customName(id: $0.id) }
            let hasPacks = !(packs?.manager.mapFiles().isEmpty ?? true)
            let image = await PostcardRenderer().render(walk: walk, points: points, pins: walkPins, character: character,
                                                        customName: name, styleJSON: hasPacks ? packs?.styleJSON : nil)
            done(image?.pngData())
        }
    }

    /// All the walks, for the list you can swipe through.
    func postcardSessions() -> [Session] {
        (store.loadSessions(limit: KotlinInt(value: 10_000)) as? [Session]) ?? []
    }

    /// The ids of the walks that have a pin.
    func walkIdsWithPins() -> [KotlinLong] {
        ((pins?.pins.value as? [Pin]) ?? []).compactMap { $0.sessionId }
    }

    func fileName(walkId: Int64) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd-HHmm"
        f.locale = Locale(identifier: "en_US_POSIX")
        return "misty-steps-" + f.string(from: Date(timeIntervalSince1970: Double(walkId) / 1000))
    }

    func saveToPhotos(png: Data, done: @escaping (KotlinBoolean) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else { DispatchQueue.main.async { done(KotlinBoolean(value: false)) }; return }
            PHPhotoLibrary.shared().performChanges({
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: png, options: nil)
            }, completionHandler: { ok, _ in DispatchQueue.main.async { done(KotlinBoolean(value: ok)) } })
        }
    }

    func share(png: Data, name: String) {
        DispatchQueue.main.async {
            let file = FileManager.default.temporaryDirectory.appendingPathComponent("\(name).png")
            guard (try? png.write(to: file)) != nil else { return }
            let sheet = UIActivityViewController(activityItems: [file], applicationActivities: nil)
            let top = UIApplication.topViewController()
            sheet.popoverPresentationController?.sourceView = top?.view
            top?.present(sheet, animated: true)
        }
    }
}
