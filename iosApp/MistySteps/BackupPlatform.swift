import Shared
import UIKit
import UniformTypeIdentifiers

/// The backup card's platform side on iOS: the save and open dialogs, and the stored trails, walks and pins.
final class BackupPlatform: NSObject, BackupFiles, BackupData, UIDocumentPickerDelegate {
    private let store: SQLiteTrailStore
    private let fog: FogModel
    private let pinsModel: PinsModel
    private var finishSave: ((KotlinBoolean?) -> Void)?
    private var finishOpen: ((String?) -> Void)?
    private var tempFile: URL?
    /// Test hook (-backuppath): export writes to, and import reads from, this file instead of asking.
    private let testPath = UserDefaults.standard.string(forKey: "backuppath")

    init(store: SQLiteTrailStore, fog: FogModel, pins: PinsModel) {
        self.store = store; self.fog = fog; self.pinsModel = pins
    }

    // MARK: BackupFiles

    func askWhereToSave(fileName: String, text: String, done: @escaping (KotlinBoolean?) -> Void) {
        if let testPath {
            done(KotlinBoolean(value: (try? text.write(toFile: testPath, atomically: true, encoding: .utf8)) != nil))
            return
        }
        DispatchQueue.main.async {
            let file = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            guard (try? text.write(to: file, atomically: true, encoding: .utf8)) != nil else { done(KotlinBoolean(value: false)); return }
            self.tempFile = file
            self.finishSave = done
            let picker = UIDocumentPickerViewController(forExporting: [file], asCopy: true)
            picker.delegate = self
            UIApplication.topViewController()?.present(picker, animated: true)
        }
    }

    func askForFile(done: @escaping (String?) -> Void) {
        if let testPath {
            done((try? String(contentsOfFile: testPath, encoding: .utf8)) ?? "")
            return
        }
        DispatchQueue.main.async {
            self.finishOpen = done
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText, .data], asCopy: true)
            picker.delegate = self
            UIApplication.topViewController()?.present(picker, animated: true)
        }
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if let done = finishSave {
            finishSave = nil
            done(KotlinBoolean(value: true))
        }
        if let done = finishOpen {
            finishOpen = nil
            // An empty string tells the card that the file could not be read.
            done(urls.first.flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? "")
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finishSave?(nil); finishSave = nil
        finishOpen?(nil); finishOpen = nil
    }

    // MARK: BackupData

    func points() -> [TrailPoint] { store.loadAll() }

    func sessions() -> [Session] { store.loadSessions(limit: nil) }

    func pins() -> [Pin] { pinsModel.all }

    func isEmpty() -> Bool { store.isEmpty() }

    func importData(points: [TrailPoint], sessions: [Session]) -> BackupCounts { store.importData(points: points, sessions: sessions) }

    func importPins(pins incoming: [Pin]) -> Int32 { store.importPins(incoming) }

    func reload() {
        DispatchQueue.main.async {
            self.fog.reload()
            self.pinsModel.book.reload()
        }
    }
}
