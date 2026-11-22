# iPhone Magic Eraser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps use checkbox syntax.

**Goal:** The magic eraser on the iPhone app, behaving exactly like Android's, sold as one Non-Consumable StoreKit 2 product.

**Architecture:** Move the stroke engine (`EraserController`, `ErasedStore`) from the Android module to `:shared` commonMain, keeping Android's `MotionEvent` adapter as an extension. The iPhone app implements `MapProjection` over `MLNMapView`, a one-finger gesture recognizer, a SQLite store with the `erase_stroke` column, a StoreKit 2 purchases class and SwiftUI views.

**Tech Stack:** Kotlin Multiplatform `:shared`, Swift/SwiftUI, MapLibre iOS, SQLite3, StoreKit 2 (iOS 17).

**Spec:** `docs/superpowers/specs/2026-11-21-magic-eraser-design.md` (iPhone addendum at the end).

## Global Constraints

- Same numbers as Android: brush 28 points, max radius 100 m, MIN_ZOOM 14.0, spacing 0.6, undo 10 strokes, taste 100 m once per install.
- Product id `magic_eraser`, Non-Consumable, EUR 3,99 (price shown from the store, never hard-coded).
- Erased spots never join trails, walked distance, walk log or postcards; they count in stats.
- No emoji. Dutch and English strings come from `shared/strings` through `IosTexts.kt`.
- Android behaviour and its existing tests must not change (run `testDebugUnitTest :shared:macosArm64Test` after Task 1).
- Swift cannot use Kotlin default arguments: pass every parameter.
- iOS build check: `tools/ios-testflight.sh` archives (export may fail on Apple sign-in; an archive without Swift errors is the compile check). Simulator checks need a concrete simulator id.

## Review Focus

- A tap with no drag erases one patch; a second finger ends the stroke and map pinch still works.
- Taste runs out mid-stroke: cut at the allowance, then the sheet.
- Undo removes exactly that stroke from the in-memory index and from SQLite.
- Existing iPhone databases (no `erase_stroke` column) upgrade without losing data.
- Store unavailable or product missing: locked state, sheet without Buy, no crash; owned state survives offline.
- Backup export then import on iPhone keeps erased spots; old backups import unchanged.

---

### Task 1: Move the engine to `:shared`

**Files:** Move `app/src/main/java/com/mistysteps/app/eraser/EraserController.kt` core into `shared/src/commonMain/kotlin/com/mistysteps/app/eraser/EraserController.kt`; keep an Android file `app/src/main/java/com/mistysteps/app/eraser/EraserTouch.kt` with `interface`-free pieces: `object FogErasedStore : ErasedStore` and `fun EraserController.onTouch(e: MotionEvent)` (mapping `ACTION_DOWN` to `down`, `ACTION_MOVE` to `move(x, y, pointerCount)`, `ACTION_POINTER_DOWN` to `secondFinger`, `ACTION_UP/CANCEL` to `up`). Update `MapController.kt` and `MistyMap` call sites (`map.onTouch = { eraser.onTouch(it) }` keeps working through the extension import).
**Interfaces:** `class EraserController(projection: MapProjection, store: ErasedStore, density: Float, setMapGestures: (Boolean) -> Unit, clock: () -> Long)` and `interface ErasedStore { fun add(points: List<TrailPoint>); fun remove(strokeId: Long, points: List<TrailPoint>) }` in package `com.mistysteps.app.eraser`; same public members as today (`active`, `canUndo`, `allowance`, `onSpent`, `onOutOfAllowance`, `onZoomedOut`, `onUndoChanged`, `down`, `move`, `secondFinger`, `up`, `undo`). Pass `clock` explicitly from callers: a default of `{ currentTimeMillis }` is not available in common code, so give it a default through an `expect`-free helper `fun nowMs(): Long` defined per platform or require the argument.
- [ ] Step 1: run `./gradlew testDebugUnitTest :shared:macosArm64Test` (baseline green).
- [ ] Step 2: move the code, keep package and names; run the same command. Expected: PASS with the unchanged `EraserControllerTest`.
- [ ] Step 3: `./gradlew assembleDebug`; commit `git commit -m "Eraser: engine moves to :shared"`.

### Task 2: iPhone store: `erase_stroke` column and load, insert, delete, import

**Files:** `iosApp/MistySteps/SQLiteTrailStore.swift`.
**Interfaces:** `func deleteStroke(id: Int64)`; `insert`, `loadAll`, `loadSessionPoints`/other readers and `importData` read and write `erase_stroke`; on open, if `PRAGMA table_info(points)` lacks the column run `ALTER TABLE points ADD COLUMN erase_stroke INTEGER`.
- [ ] Step 1: build for the simulator after the change (no Swift test target exists; verification is Task 8).
- [ ] Step 2: commit.

### Task 3: iPhone model: erased spots

**Files:** `iosApp/MistySteps/FogModel.swift`.
**Interfaces:** `func addErased(_ points: [TrailPoint])` (adds to `index`, `version += 1`, writes through `writer`, notifies; never adds to `trails`); `func removeErased(strokeId: Int64, points: [TrailPoint])` (index remove, delete rows on `writer`, notify); `init` and `reload` skip `trails.add` for points with `eraseStroke != nil` and keep `lastKnown` to walked ones.
- [ ] Step 1: implement; archive-build check; commit.

### Task 4: `MapProjection` over `MLNMapView`, store adapter, strings

**Files:** create `iosApp/MistySteps/EraserBridge.swift`; modify `shared/src/appleMain/kotlin/com/mistysteps/app/ui/IosTexts.kt`.
**Interfaces:** `final class MapLibreProjection: NSObject, MapProjection` (`width`, `height` points; `zoom` = `map.zoomLevel`; `worldSizePx` = `512 * 2^zoom`; `toScreen`, `fromScreen` through `map.convert(_:toPointTo:)` / `map.convert(_:toCoordinateFrom:)`; `metersToPixels(meters, lat)` = `meters / map.metersPerPoint(atLatitude: lat)`); `final class FogErasedStore: NSObject, ErasedStore` forwarding to `FogModel`; IosTexts accessors `eraserButtonText()`, `eraserHintZoomText()`, `eraserDoneText()`, `eraserUndoText()`, `eraserSheetTitleText()`, `eraserSheetBodyText()`, `eraserSheetBuyText(price:)`, `eraserSheetNotNowText()`, `eraserTasteUsedText()`, `eraserUnavailableText()`, `eraserThanksText()`.
- [ ] Step 1: add the Kotlin accessors; `./gradlew :shared:macosArm64Test`; implement the Swift types; archive-build; commit.

### Task 5: StoreKit 2 purchases

**Files:** create `iosApp/MistySteps/EraserPurchases.swift`, `iosApp/MistySteps/Misty.storekit`; modify `iosApp/project.yml` (scheme `storeKitConfiguration` for Debug runs only).
**Interfaces:** `@MainActor final class EraserPurchases: ObservableObject` with `@Published var owned: Bool` (initial from `UserDefaults "eraser_owned"`), `@Published var priceText: String?`, `func start()` (load product `magic_eraser`, read `Transaction.currentEntitlements`, listen to `Transaction.updates`), `func buy() async -> BuyResult` (`.bought`, `.cancelled`, `.unavailable`; `.pending` counts as `.cancelled`), `func restore() async` (`AppStore.sync()`); verified transactions only, `finish()` each; `owned` only turns false when entitlements are read successfully and the product is absent. The `.storekit` file declares one non-consumable `magic_eraser` at 3.99 EUR with Dutch and English display names.
- [ ] Step 1: implement; archive-build; commit.

### Task 6: Gesture and the screen

**Files:** create `iosApp/MistySteps/EraserGesture.swift`, `iosApp/MistySteps/EraserViews.swift`; modify `MapView.swift`, `HubView.swift`, `ContentView.swift`.
**Interfaces:** `final class EraserGestureRecognizer: UIGestureRecognizer` feeding `EraserController.down/move/secondFinger/up` from `touchesBegan/Moved/Ended/Cancelled` (`cancelsTouchesInView = false`, `delaysTouchesBegan = false`, never moves to `.began`/`.changed` so the map's own recognizers keep working; `setMapGestures` toggles `map.allowsScrolling`); `MapView` gets `var eraserActive: Bool` and `let eraser: EraserController` and keeps the long-press and tap pin gestures disabled while active; `HubView` gets an `onEraser` round button (eraser icon via a SwiftUI shape or SF Symbol `eraser`) at `bottom: 252` left column and, while active, an `EraserBar` (Undo, Put it down) at the top under the title; `ContentView` owns `@StateObject purchases`, `TasteLedger` (UserDefaults key `eraser_taste_used_m`), the state via `EraserAccessKt.resolveEraserState(storeAvailable:owned:tasteUsedM:)`, the sheet (`EraserSheet` with title, body, taste line, price Buy button only when `priceText != nil`, Not now) and launch args `-eraser 0|1|2`, `-eraserdemo 1`.
- [ ] Step 1: implement; archive-build; commit.

### Task 7: Verify on the simulator

- [ ] Step 1: build and run on a simulator (id from `xcrun simctl list devices available`), launch with `-eraser 0 -eraserdemo 1`; screenshot: a cleared band exists. Then `-eraser 1`: tapping is not scriptable, so check the locked state by launch screenshot of the button and trust the unit-tested access rules.
- [ ] Step 2: relaunch and confirm the demo stroke persisted (store round trip) and that undo is exercised by a second launch hook `-eraserdemo 2` (draw then undo, expect no band).
- [ ] Step 3: `workflow.md` note, `docs/moscow.md` update, merge to main, push; TestFlight upload with `tools/ios-testflight.sh --upload`.
- Real touch behaviour (drag, second finger, undo button) can only be checked on a real iPhone through TestFlight: say so in the final message.
