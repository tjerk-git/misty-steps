import CoreLocation
import Foundation
import Shared

/// Your pins, in the shared PinBook (which keeps them in the SQLite file). `onChange` tells the map to draw them again.
final class PinsModel: ObservableObject {
    let book: PinBook
    private var observers: [() -> Void] = []

    init(storage: PinStorage) {
        book = PinBook(storage: storage)
        book.onChanged = { [weak self] in self?.observers.forEach { $0() } }
    }

    func observe(_ f: @escaping () -> Void) { observers.append(f) }

    var all: [Pin] { book.pins.value as? [Pin] ?? [] }
}

/// Which pin sheet is open.
enum PinSheetTarget {
    case new(lat: Double, lon: Double, sessionId: Int64?)
    case open(id: Int64)

    var state: PinSheetState {
        switch self {
        case .new(let lat, let lon, let session):
            return PinSheetStateNew(lat: lat, lon: lon, sessionId: session.map { KotlinLong(value: $0) })
        case .open(let id):
            return PinSheetStateOpen(pinId: id)
        }
    }
}
