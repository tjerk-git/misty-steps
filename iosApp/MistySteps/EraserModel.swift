import Foundation
import MapLibre
import Shared
import SwiftUI
import UIKit

/// The magic eraser on the iPhone: owns the shared engine ([EraserController]), what the person may do with it (bought, free
/// taste, locked) and what the screen shows (in hand, undo, the purchase sheet). The rules live in :shared, so this behaves
/// exactly like Android's.
@MainActor
final class EraserModel: ObservableObject {
    let purchases = EraserPurchases()

    /// The eraser is in hand: one finger wipes the mist.
    @Published var active = false { didSet { syncController() } }
    @Published private(set) var canUndo = false
    @Published var showSheet = false
    /// The sheet opened because the free taste ran out (it says so).
    @Published private(set) var tasteUsedUp = false
    @Published private(set) var tasteUsedM: Double
    /// A short message (zoom in a bit); the screen shows it for a moment.
    @Published var hint: String?

    private static let tasteKey = "eraser_taste_used_m"
    private let ledger: TasteLedger
    private(set) var controller: EraserController?
    private weak var map: MLNMapView?
    private var gesture: EraserGestureRecognizer?

    init() {
        let used = UserDefaults.standard.double(forKey: Self.tasteKey)
        ledger = TasteLedger(usedM: used) { UserDefaults.standard.set($0.doubleValue, forKey: EraserModel.tasteKey) }
        tasteUsedM = ledger.usedM
        // Debug and test hooks (like -autostart): -eraser 0|1|2 forces unlocked, locked or a fresh taste.
        if Self.launchInt("eraser") == 2 { ledger.reset(); tasteUsedM = 0 }
    }

    /// A launch argument such as `-eraser 1` (they arrive in UserDefaults as text), or nil when it was not given.
    private static func launchInt(_ key: String) -> Int? { UserDefaults.standard.string(forKey: key).flatMap { Int($0) } }

    /// 0 unlocked, 1 locked, 2 free taste, nil follows the real state. Only from launch arguments (never set by the app).
    private var debugOverride: Int? {
        guard let v = Self.launchInt("eraser"), (0...2).contains(v) else { return nil }
        return v
    }

    private var state: EraserState {
        switch debugOverride {
        case 0: return EraserStateUnlocked.shared
        case 1: return EraserStateLocked.shared
        default: return EraserAccessKt.resolveEraserState(storeAvailable: true, owned: purchases.owned, tasteUsedM: ledger.usedM)
        }
    }

    /// Unlocked, or the free taste: the tool may be picked up.
    var usable: Bool { state is EraserStateUnlocked || state is EraserStateTaste }

    /// Metres of drag that may still be erased: unlimited when bought, what is left of the taste otherwise.
    private var allowanceM: Double {
        if state is EraserStateUnlocked { return .infinity }
        if state is EraserStateTaste { return ledger.metersLeft }
        return 0
    }

    /// Called when the map is made: builds the engine over it and installs the one-finger recognizer.
    func attach(map: MLNMapView, fog: FogModel) {
        guard controller == nil else { return }
        self.map = map
        let c = EraserController(
            projection: MapLibreProjection(map: map), store: FogErasedStore(model: fog), density: 1,
            setMapGestures: { [weak map] on in map?.allowsScrolling = on.boolValue },
            clock: { KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000)) })
        c.allowance = { [weak self] in KotlinDouble(double: self?.allowanceM ?? 0) }
        c.onSpent = { [weak self] metres in
            guard let self, self.state is EraserStateTaste else { return }
            self.ledger.spend(meters: metres.doubleValue)
            self.tasteUsedM = self.ledger.usedM
        }
        c.onOutOfAllowance = { [weak self] in
            guard let self else { return }
            self.active = false
            self.tasteUsedUp = true
            self.showSheet = true
        }
        c.onZoomedOut = { [weak self] in self?.hint = IosTextsKt.eraserHintZoomText() }
        c.onUndoChanged = { [weak self] in self?.canUndo = self?.controller?.canUndo ?? false }
        controller = c
        let g = EraserGestureRecognizer(controller: c)
        map.addGestureRecognizer(g)
        gesture = g
        syncController()
        runDemoIfAsked()
    }

    private func syncController() { controller?.active = active && usable }

    /// The round button: pick the eraser up or put it down, or show the purchase sheet when it is not yours yet.
    func buttonTapped() {
        if usable { active.toggle() } else { tasteUsedUp = false; showSheet = true }
    }

    func undo() { controller?.undo() }

    func putDown() { active = false }

    /// "Buy": through the App Store; closes the sheet when it worked.
    func buy() async {
        switch await purchases.buy() {
        case .bought:
            showSheet = false
            hint = IosTextsKt.eraserThanksText()
        case .unavailable:
            hint = IosTextsKt.eraserUnavailableText()
        case .cancelled:
            break
        }
    }

    /// Another full-screen page covers the map: put the eraser down.
    func mapCovered() { if active { active = false } }

    // MARK: launch-argument test hooks

    /// -eraserdemo 1 draws a short stroke a few seconds after launch; 2 draws it and then undoes it. For simulator checks,
    /// where touches cannot be scripted. It goes through the same engine and store as a finger would.
    private func runDemoIfAsked() {
        // -erasersheet 1 opens the purchase sheet a few seconds after launch (to look at it).
        if Self.launchInt("erasersheet") == 1 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) { [weak self] in self?.showSheet = true }
        }
        guard let demo = Self.launchInt("eraserdemo"), demo > 0 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 6) { [weak self] in
            guard let self, let c = self.controller, let map = self.map else { return }
            self.active = true
            c.active = true
            let w = Float(map.bounds.width), h = Float(map.bounds.height)
            c.down(x: w * 0.2, y: h * 0.4)
            for i in 1...12 { c.move(x: w * 0.2 + w * 0.05 * Float(i), y: h * 0.4 + Float(i) * 4, pointerCount: 1) }
            c.up()
            if demo == 2 { DispatchQueue.main.asyncAfter(deadline: .now() + 2) { c.undo() } }
        }
    }
}

/// One finger feeds the engine. It never takes the touches from the map (it stays "possible" and the map's own recognizers
/// run alongside); while a stroke is drawn the engine turns the map's scrolling off, and a second finger hands it back.
final class EraserGestureRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    private let controller: EraserController

    init(controller: EraserController) {
        self.controller = controller
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = self
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    private func point(_ t: UITouch) -> (Float, Float) {
        let p = t.location(in: view)
        return (Float(p.x), Float(p.y))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        let all = event.touches(for: self) ?? touches
        if all.count > 1 {
            controller.secondFinger()
        } else if let t = touches.first {
            let (x, y) = point(t)
            controller.down(x: x, y: y)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        let all = event.touches(for: self) ?? touches
        if let t = touches.first { let (x, y) = point(t); controller.move(x: x, y: y, pointerCount: Int32(all.count)) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) { finish(touches, event) }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { finish(touches, event) }

    private func finish(_ touches: Set<UITouch>, _ event: UIEvent) {
        let remaining = (event.touches(for: self) ?? []).subtracting(touches)
        if remaining.isEmpty {
            controller.up()
            state = .failed // resets the recognizer for the next touch
        }
    }
}
