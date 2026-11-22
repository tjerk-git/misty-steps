# Magic Eraser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A paid hand tool on the Android map that erases mist anywhere with a finger, with a 10-stroke undo, a free taste, and a one-time Google Play purchase.

**Architecture:** Rules, stroke-to-spots, undo and access states live in `:shared` (common Kotlin, tested on the JVM and macOS), so iPhone can reuse them later. Erased mist is stored as ordinary rows in the `points` table with a new nullable `erase_stroke` column, so fog drawing and stats work unchanged and walk-only features ignore them. Android adds the touch handling, the buttons and sheets, and a Play-only source set for Play Billing, selected by a Gradle property so F-Droid builds carry no billing code.

**Tech Stack:** Kotlin, Jetpack Compose, MapLibre Android, SQLite, Kotlin Multiplatform `:shared`, Google Play Billing (`billing-ktx`).

**Spec:** `docs/superpowers/specs/2026-11-21-magic-eraser-design.md`

## Global Constraints

- One-time purchase of EUR 3,99; price and product id `magic_eraser` live in the Play Console, the sheet shows the price Play returns.
- Undo: the last 10 strokes while the eraser is in hand; putting it down makes strokes final.
- Brush: about 28 dp radius on screen, radius in metres capped at 100 m; below MapLibre zoom 14.0 the eraser does nothing and says "zoom in a bit".
- Spots along a stroke are spaced 0.6 times the brush radius.
- Free taste: at most 100 m of drag, once per install, stored in preferences.
- Erased mist counts in cleared-area and percentage stats; it adds nothing to walked distance, the walk log, trail dots or postcards.
- F-Droid build (default Gradle build, no `-Pstore=play`): the eraser button is not shown and no billing library is included.
- No licence server, no accounts, no stored receipts.
- Backup files: the new per-point value is optional; old files import unchanged.
- No emoji anywhere (code, strings, docs). Dutch and English strings.
- The debug-only switches follow the existing pattern (`BuildConfig.DEBUG`, `Settings.Global`).

## Review Focus

- A tap with no drag erases one round patch (a stroke with a single point), it must not be ignored.
- A second finger landing mid-stroke ends that stroke cleanly (it stays undoable) and the map pans or zooms.
- The taste allowance runs out mid-stroke: the stroke is cut at the allowance and the sheet appears, nothing past it is erased.
- Undo after a walk added new spots in between removes exactly that stroke's spots and nothing else.
- Play is unavailable or offline at start: the tool shows locked with a short message and never crashes; a previously confirmed purchase still unlocks offline.
- Rotating the phone or leaving eraser mode keeps all erased mist (strokes are saved as they are drawn).
- Importing an old backup, and exporting then importing a backup with erased spots, loses nothing and duplicates nothing.
- Upgrading from database version 5 with a large trail is instant (adding a nullable column only).

---

### Task 1: Eraser rules and stroke-to-spots (shared)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/eraser/EraserRules.kt`
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/eraser/EraserRulesTest.kt`

**Interfaces:**
- Consumes: `LatLon` (`com.mistysteps.app.map`), `Trails.approxDistanceM(lat1, lon1, lat2, lon2): Double` (metres), `FogConstants`.
- Produces: `object EraserRules` with constants `BRUSH_DP = 28f`, `MAX_RADIUS_M = 100.0`, `MIN_ZOOM = 14.0`, `SPACING_FACTOR = 0.6`, `UNDO_LIMIT = 10`, `TASTE_M = 100.0`, and:
  - `fun canErase(zoom: Double): Boolean`
  - `fun brushRadiusM(brushPx: Float, pixelsPerMeter: Float): Double` (brushPx / pixelsPerMeter, capped at `MAX_RADIUS_M`)
  - `fun spotsAlong(path: List<LatLon>, radiusM: Double): List<LatLon>`: first point always, then a point each time the path has travelled at least `SPACING_FACTOR * radiusM` since the last one, plus the final path point if it is more than half a spacing from the last emitted; a one-point path gives one spot.
  - `fun trimToAllowance(path: List<LatLon>, allowanceM: Double): List<LatLon>`: the path cut where its cumulative length reaches the allowance (the cut point interpolated); an allowance of 0 or less gives an empty list.

- [ ] **Step 1: Write the failing tests** `canErase` false at 13.99 and true at 14.0; `brushRadiusM(90f, 1f)` is 90.0 and `brushRadiusM(90f, 0.5f)` is 100.0 (capped); a one-point path gives one spot; a 200 m straight path with radius 50 gives spots about 30 m apart (first at the start, last within 15 m of the end); an empty path gives none; `trimToAllowance` on a 300 m path with allowance 100 returns a path of length about 100 m (within 1 m) and with allowance 0 returns empty.
- [ ] **Step 2: Run** `./gradlew :shared:macosArm64Test --tests '*EraserRulesTest*'` (set `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`). Expected: FAIL, unresolved references.
- [ ] **Step 3: Implement `EraserRules`** as above; interpolate in lat/lon linearly (distances are tens of metres, flat-earth is fine).
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `git add shared/src && git commit -m "Eraser: rules and stroke-to-spots"`.

### Task 2: Stroke undo list and access states (shared)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/eraser/EraserSession.kt`
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/eraser/EraserAccess.kt`
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/eraser/EraserSessionTest.kt`, `EraserAccessTest.kt`

**Interfaces:**
- Consumes: `TrailPoint`, `EraserRules`.
- Produces:
  - `class EraserStroke(val id: Long, val spots: List<TrailPoint>)`
  - `class EraserSession { val canUndo: Boolean; val size: Int; fun push(stroke: EraserStroke); fun undo(): EraserStroke?; fun clear() }` keeping at most `EraserRules.UNDO_LIMIT` strokes (the oldest is dropped when an 11th arrives).
  - `sealed interface EraserState { object NotAvailable; object Locked; data class Taste(val metersLeft: Double); object Unlocked }`
  - `fun resolveEraserState(storeAvailable: Boolean, owned: Boolean, tasteUsedM: Double): EraserState` (not available wins; then owned; then taste while `TASTE_M - tasteUsedM > 0`; else locked).
  - `interface EraserPurchases { val storeAvailable: Boolean; val owned: StateFlow<Boolean>; val priceText: StateFlow<String?>; suspend fun buy(): BuyResult }` and `enum class BuyResult { BOUGHT, CANCELLED, UNAVAILABLE }`.

- [ ] **Step 1: Write failing tests** push 11 strokes then undo 10 times returns strokes 11..2 newest first and the 11th undo is null; `clear()` empties; `resolveEraserState(false, true, 0.0)` is `NotAvailable`; `(true, true, 999.0)` is `Unlocked`; `(true, false, 30.0)` is `Taste(70.0)`; `(true, false, 100.0)` is `Locked`; `(true, false, 150.0)` is `Locked`.
- [ ] **Step 2: Run** the macOS test task for both classes. Expected: FAIL.
- [ ] **Step 3: Implement** the two files; `EraserSession` uses an `ArrayDeque`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Eraser: undo list and access states"`.

### Task 3: Erased spots in the shared data layer

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/data/TrailPoint.kt` (add `val eraseStroke: Long? = null` after `sessionId`)
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/data/PointIndex.kt` (add `fun remove(lat: Double, lon: Double, radiusMeters: Double): Boolean`)
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/data/Backup.kt` (points array gets an optional sixth element; format doc comment updated; `VERSION` stays 1)
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/data/PointIndexTest.kt` (add or extend), `app/src/test/java/com/mistysteps/app/data/BackupTest.kt`

**Interfaces:**
- Consumes: existing `PointIndex.add`, `Backup.toJson/fromJson`.
- Produces: `TrailPoint.eraseStroke: Long?`; `PointIndex.remove(...)` removes one spot that was added with the same lat, lon and radius (swap with the bucket's last spot), decrements `count`, returns whether one was found; Backup JSON points are `[lat, lon, time, radius, sessionId|null, eraseStroke|null]` and the sixth element is omitted when null.

- [ ] **Step 1: Write failing tests** `PointIndexTest`: after adding two spots, `remove` of one returns true, `count` is 1, `snapshotIn` over the area contains only the other; `remove` of a spot not present returns false. `BackupTest`: a backup with an erased spot (`eraseStroke = 42L`) round-trips through `toJson()`/`fromJson` with the value kept; a JSON point array of five elements (an old file) parses with `eraseStroke == null`; a walked spot serialises without a sixth element.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*BackupTest*' :shared:macosArm64Test --tests '*PointIndexTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the three changes; `PointIndex.remove` finds the bucket via the same `key(cell(x), cell(y))` as `add` and compares the stored mercator x, y, radius with a small tolerance.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Eraser: erased spots in TrailPoint, PointIndex and backups"`.

### Task 4: Database and FogStore (Android)

**Files:**
- Modify: `app/src/main/java/com/mistysteps/app/data/TrailDatabase.kt` (version 5 to 6; `erase_stroke INTEGER` column; insert, `loadAll`, `query`, `importData` read and write it; `fun deleteStroke(id: Long)`; index on `erase_stroke`)
- Modify: `app/src/main/java/com/mistysteps/app/data/FogStore.kt`
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/data/Trails.kt` only if needed (see Step 3)
- Test: `app/src/test/java/com/mistysteps/app/data/EraseStoreTest.kt` (pure logic only; the SQLite part is verified in Task 8)

**Interfaces:**
- Consumes: Task 3's `TrailPoint.eraseStroke`, `PointIndex.remove`.
- Produces on `FogStore` (main thread): `fun addErased(points: List<TrailPoint>)` (adds each to `index`, bumps `version`, writes to the database on the IO executor; never adds to `trails`) and `fun removeErased(strokeId: Long, points: List<TrailPoint>)` (removes each from `index`, bumps `version`, deletes rows with that `erase_stroke` on the IO executor). `loadFromDatabase` adds erased spots to the index but not to `trails`. `TrailDatabase.deleteStroke(id: Long)`.

- [ ] **Step 1: Write failing tests** none can run SQLite on the JVM here, so test the one piece of pure logic added: a small `internal fun walkedOnly(points: List<TrailPoint>): List<TrailPoint>` in `FogStore.kt`'s file (or `data/EraseFilter.kt`) that drops spots with `eraseStroke != null`; assert it keeps walked and old (null-session) spots and drops erased ones.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*EraseStoreTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the database changes (`ALTER TABLE points ADD COLUMN erase_stroke INTEGER` in `onUpgrade` for `oldVersion < 6`, and in `createPoints`), the two `FogStore` functions, and use `walkedOnly` where `loadFromDatabase` feeds `Trails`. Check that `newGround`/`loadOtherPointsIn` still select erased spots (they are other ground: they select `session_id IS NULL OR != id`, which is right) and that `loadSessionPoints` (by `session_id`) never returns them.
- [ ] **Step 4: Run** the test, then `./gradlew assembleDebug`. Expected: PASS and a clean build.
- [ ] **Step 5: Commit** `git commit -m "Eraser: database column and FogStore erased spots"`.

### Task 5: Strings (Dutch and English)

**Files:**
- Modify: `shared/strings/values/strings.xml`, `shared/strings/values-nl/strings.xml`

**Interfaces:**
- Produces string names: `eraser_button` (content description), `eraser_hint_zoom`, `eraser_done`, `eraser_undo`, `eraser_sheet_title`, `eraser_sheet_body`, `eraser_sheet_buy` (takes the price as `%1$s`), `eraser_sheet_not_now`, `eraser_taste_used`, `eraser_unavailable`, `eraser_thanks`.

- [ ] **Step 1: Write the English copy**: quirky, short, honest that it is also a donation, no emoji, no tax or refund claims. Example tone for the body: the app stays free, this is for people who cannot leave one grey corner alone, and buying it also keeps the app going.
- [ ] **Step 2: Write the Dutch copy** in the same voice (not a literal translation; natural Dutch).
- [ ] **Step 3: Run** `./gradlew assembleDebug` to check the resources compile. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `git commit -m "Eraser: strings"`. Show the copy to the user for edits before release.

### Task 6: Eraser touch handling and map integration (Android)

**Files:**
- Create: `app/src/main/java/com/mistysteps/app/eraser/EraserController.kt`
- Modify: `app/src/main/java/com/mistysteps/app/map/MistyMap.kt` (expose the touch hook and scroll-gesture switch), `app/src/main/java/com/mistysteps/app/ui/MapController.kt`
- Test: `app/src/test/java/com/mistysteps/app/eraser/EraserControllerTest.kt` (the pure part: turning a list of finger points into a stroke through the rules)

**Interfaces:**
- Consumes: `EraserRules`, `EraserSession`, `EraserStroke`, `FogStore.addErased/removeErased`, `MapProjection.fromScreen/metersToPixels/zoom`.
- Produces: `class EraserController(projection: MapProjection, store: ErasedStore, density: Float)` with `var active: Boolean`, `val canUndo: Boolean`, `fun onTouch(e: MotionEvent): Boolean` (true while it has consumed a one-finger stroke), `fun undo()`, `fun finish()` (ends the session and clears undo), a callback `onBlockedByZoom: () -> Unit`, and a callback `onTasteSpent: (metersUsed: Double) -> Boolean` returning whether drawing may continue. `interface ErasedStore { fun add(points: List<TrailPoint>); fun remove(strokeId: Long, points: List<TrailPoint>) }` implemented by `FogStore`.
- Pure helper: `fun strokeFor(id: Long, now: Long, path: List<LatLon>, radiusM: Double): EraserStroke` builds the stroke's `TrailPoint`s (time `now + index`, radius `radiusM`, `sessionId = null`, `eraseStroke = id`).

- [ ] **Step 1: Write the failing test** `strokeFor` on a 100 m path with radius 50 gives points whose `eraseStroke` all equal the id, whose `sessionId` is null, whose times are strictly increasing, and whose radius is 50.0; a one-point path gives one point.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*EraserControllerTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement `strokeFor`, then `EraserController`.** One finger: on `ACTION_DOWN` disable MapLibre's scroll gesture and start a path; on each `ACTION_MOVE` append the point, convert new points to spots and add them live through the store (so the mist wipes as the finger moves); on `ACTION_UP` finish the stroke and push it to the `EraserSession`. A second finger (`ACTION_POINTER_DOWN`) ends the stroke and re-enables the scroll gesture so the map handles the pan or pinch; `ACTION_CANCEL` ends the stroke. Zoom below `EraserRules.MIN_ZOOM` calls `onBlockedByZoom` and erases nothing. Hook into `MistyMap.watchForDrags`' touch listener so the map still receives every event.
- [ ] **Step 4: Run** the test and `./gradlew assembleDebug`. Expected: PASS and a clean build.
- [ ] **Step 5: Commit** `git commit -m "Eraser: touch handling on the map"`.

### Task 7: Buttons, bar and purchase sheet (Android UI)

**Files:**
- Create: `app/src/main/java/com/mistysteps/app/eraser/EraserUi.kt` (the eraser button, the eraser bar with Undo and Done, the purchase sheet), `app/src/main/res/drawable/ic_eraser.xml`
- Modify: `app/src/main/java/com/mistysteps/app/ui/MistyScreen.kt` (add the button in the left column above the character button, state for eraser mode, show the bar and the sheet), `app/src/main/java/com/mistysteps/app/data/` (a small `EraserPrefs` holding the taste metres used)
- Test: `app/src/test/java/com/mistysteps/app/eraser/EraserPrefsTest.kt` (pure counting), plus the emulator check in Task 9

**Interfaces:**
- Consumes: `resolveEraserState`, `EraserPurchases`, `EraserController`, strings from Task 5.
- Produces: `object EraserAccessHolder { var purchases: EraserPurchases }` defaulting to a `NoPurchases` whose `storeAvailable` is false (F-Droid); the button is shown only when `storeAvailable` is true or the debug switch forces a state. Tapping the button: `Unlocked` or `Taste` enters eraser mode; `Locked` shows the sheet. Entering eraser mode shows the bar (Undo, Done) and hides the start/stop button's competing taps are not needed. The sheet shows price text, Buy, Not now, and the donation line.
- Debug switch: `adb shell settings put global misty_eraser 0|1|2` forces unlocked (0), locked (1), fresh taste (2); any other value follows the real state; honoured only when `BuildConfig.DEBUG`.

- [ ] **Step 1: Write the failing test** `EraserPrefs.spend(metres)` accumulates and never goes negative; `usedM` after spending 30 and 40 is 70.0.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*EraserPrefsTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the prefs class, the UI composables and the `MistyScreen` wiring. Keep the eraser's blue pill look consistent with the existing round buttons (`containerColor = surface`, `Ink` content, `CircleShape`).
- [ ] **Step 4: Run** the test and `./gradlew assembleDebug`. Expected: PASS and a clean build.
- [ ] **Step 5: Commit** `git commit -m "Eraser: button, bar and purchase sheet"`.

### Task 8: Google Play Billing in a Play-only source set

**Files:**
- Modify: `app/build.gradle.kts` (read `-Pstore=play`; when set add `src/play/java` to `main` sources, add `implementation("com.android.billingclient:billing-ktx:8.0.0")`, and set `buildConfigField("String", "STORE", "\"play\"")`, otherwise `"fdroid"`)
- Create: `app/src/play/java/com/mistysteps/app/eraser/PlayPurchases.kt`
- Modify: `app/src/main/java/com/mistysteps/app/MistyApplication.kt` (set `EraserAccessHolder.purchases` once at start: `PlayPurchases` in the Play build, `NoPurchases` otherwise)
- Mechanism is the implementer's choice (a Gradle product flavor `store` with `fdroid` and `play`, or source sets switched by `-Pstore=play`). Constraints: plain `./gradlew assembleDebug` and `tools/release.sh` keep working without changes to how they are called (adjust `release.sh` only if unavoidable), and the F-Droid APK contains zero billing classes.
- Test: build checks below

**Interfaces:**
- Consumes: `EraserPurchases`, `BuyResult`.
- Produces: `class PlayPurchases(context: Context) : EraserPurchases` using one non-consumable product `magic_eraser`: connects on creation with retry, queries product details for the price text, queries owned purchases on connect and on resume, caches `owned = true` in preferences so the tool works offline once bought, acknowledges unacknowledged purchases, `buy()` launches the Play flow on the current activity and returns `BOUGHT`, `CANCELLED` or `UNAVAILABLE`; any billing error leaves `owned` as it was and never throws to the UI.

- [ ] **Step 1: Verify the default build still has no billing** Run `./gradlew assembleDebug` then check the APK has no billing classes: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -c billingclient`. Expected: `0`.
- [ ] **Step 2: Implement** the Gradle switch, the stub, `PlayPurchases` and the hook.
- [ ] **Step 3: Run** `./gradlew assembleDebug -Pstore=play` and check `unzip -l ... | grep -c billingclient` is greater than 0; run `./gradlew -PfdroidAbi=arm64-v8a assembleRelease` and check `billingclient` count is 0 (the F-Droid release must stay clean).
- [ ] **Step 4: Run** `tools/release.sh patch --dry-run` only to confirm it still passes (it bumps nothing). Expected: "Dry run done".
- [ ] **Step 5: Commit** `git commit -m "Eraser: Play Billing in a Play-only build"`.

### Task 9: Verify on the emulator and on a real phone, then document

**Files:**
- Modify: `workflow.md` (a short "Magic eraser" section: what it is, how to test with the debug switches, the Play steps still to do), `bugs.md` only if a bug is found
- Create: `docs/superpowers/specs/2026-11-21-magic-eraser-design.md` is unchanged

- [ ] **Step 1: Emulator, unlocked:** `adb shell settings put global misty_eraser 0`; install the debug build; tap the eraser button; drag with `adb shell input swipe x1 y1 x2 y2 400` and with a small-move tap `input swipe x y x+3 y+1 150`; screenshot shows mist removed along the drag and nothing in the walk log. Expected: mist removed; stats page cleared area increased; "Wandered" distance unchanged.
- [ ] **Step 2: Undo and persistence:** tap Undo twice, mist returns for those strokes; force-stop and reopen the app: remaining erased mist is still gone.
- [ ] **Step 3: Zoom gate:** zoom out below 14 and drag: nothing is erased and the zoom hint shows.
- [ ] **Step 4: Locked and taste:** `misty_eraser 2`: the first 100 m of drag erases, the next drag opens the sheet; `misty_eraser 1`: tapping the button opens the sheet.
- [ ] **Step 5: Backup:** export, wipe the app's data (`pm clear`), import: the erased mist is back, with no duplicates on a second import.
- [ ] **Step 6: Real phone:** install the debug build on the user's Galaxy S21 (verify the foreground app and take a screenshot before any tap), check that a real finger drags smoothly, two fingers pan and zoom, and frames stay smooth after a long stroke.
- [ ] **Step 7: Document and commit** `git commit -m "Eraser: docs and verification notes"`.

---

## Self-review

- **Spec coverage:** anywhere erasing (Tasks 1, 6); percentages count it and walk features ignore it (Tasks 3, 4); undo of 10 strokes (Tasks 2, 6, 7); taste (Tasks 1, 2, 7); Play purchase and restore (Tasks 2, 7, 8); F-Droid hidden and billing-free (Tasks 7, 8); backup compatibility (Task 3); iPhone reuse by keeping rules in `:shared` (Tasks 1 to 3); quirky Dutch and English copy (Task 5); performance guard by spacing and zoom gate (Tasks 1, 6, 9 step 6).
- **Type consistency:** `EraserStroke`, `EraserSession`, `EraserState`, `EraserPurchases`, `BuyResult`, `TrailPoint.eraseStroke`, `FogStore.addErased/removeErased`, `ErasedStore` are used with the same names across tasks.
- **Proportion:** signatures, tests and values only; bodies are left to the implementer.
