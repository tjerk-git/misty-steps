# Off-screen Character Pointer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a small tappable pointer on the screen edge, in the direction of the character, whenever the character is off-screen, on Android and iPhone.

**Architecture:** One pure placement function in `shared/commonMain` decides whether the pointer shows and where. Android feeds it from the map camera and fix and draws a Compose button; iPhone feeds it from `MLNMapView` and draws a SwiftUI button. Tapping calls the existing "show my location" action, which also turns follow back on.

**Tech Stack:** Kotlin Multiplatform shared module (kotlin.test), Jetpack Compose + MapLibre (Android), SwiftUI + MapLibre (iPhone).

**Spec:** `docs/superpowers/specs/2026-11-24-offscreen-pointer-design.md`

## Global Constraints

- No emoji anywhere: code, UI, docs, commits (project rule).
- Pointer has no text, no animation, translucent, same round-button look as the existing buttons.
- Hidden while following, with no fix, or when the character is on-screen.
- Tap action is the location button's action (Android `map.flyToMe()` via the same lambda; iPhone `locate`).
- Insets (distance kept clear from the map edge, same unit as the screen size: px on Android, pt on iPhone): left 16dp, right 16dp, top 190dp, bottom 300dp. Hysteresis 24dp.
- Reuse the existing "show my location" string as the accessibility label; add no new strings.
- Work on branch `offscreen-pointer`. Dev app (`com.potloodgum.mistysteps.debug`) only for any on-device check; never the user's release app, never unlock the phone.

## Review Focus

- Non-finite target (NaN or Infinity from the projection at extreme zoom): no pointer, no crash.
- Map size 0 before first layout: no pointer, no crash.
- Insets that leave no room (small window, split screen, landscape): pointer still lands inside the map, no crash.
- Character hovering at the screen edge: no flicker (hysteresis).
- Rotation or resize while shown: placement recomputed from the new size (manual check, Task 2).

---

### Task 1: Placement function in shared code

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/map/OffscreenPointer.kt`
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/map/OffscreenPointerTest.kt`

**Interfaces:**
- Produces:
  - `data class PointerPlacement(val x: Float, val y: Float, val angleRad: Float)` where `angleRad` is the direction from the pointer toward the character (0 = right, positive = clockwise on screen, `atan2(dy, dx)` with y down).
  - `data class PointerInsets(val left: Float, val top: Float, val right: Float, val bottom: Float)`
  - `object OffscreenPointer` with `const val HYSTERESIS_DP = 24f` (callers multiply by density) and
    `fun place(hasFix: Boolean, targetX: Float, targetY: Float, width: Float, height: Float, following: Boolean, insets: PointerInsets, hysteresis: Float, wasShown: Boolean): PointerPlacement?`. Null means hidden. All lengths share one unit (px on Android, pt on iPhone).

Rules: hidden unless `hasFix`, target finite, `width > 0`, `height > 0`, `!following`. When `!wasShown` it shows only if the target is outside `[0,width] x [0,height]`. When `wasShown` it keeps showing until the target is inside that rectangle shrunk by `hysteresis` on every side. The position is where the ray from the map centre to the target meets the safe rectangle `[insets.left, width - insets.right] x [insets.top, height - insets.bottom]`; an axis with no room collapses to the map centre on that axis.

- [ ] **Step 1: Write the failing tests** in `OffscreenPointerTest`, helper `place(tx, ty, following = false, wasShown = false)` using a 1000 x 2000 map, insets `PointerInsets(16f, 190f, 16f, 300f)`, hysteresis 24f, `hasFix = true`:
  - `hiddenWhenTargetOnScreen`: `place(500f, 1000f) == null`
  - `hiddenWithoutFix`: `hasFix = false` gives null
  - `hiddenWhileFollowing`: `following = true` with target `(5000f, 1000f)` gives null
  - `rightEdge`: target `(5000f, 1000f)` gives `x == 984f`, `y == 1000f`, `angleRad == 0f`
  - `leftEdge`: target `(-4000f, 1000f)` gives `x == 16f`, `angleRad` within 1e-5 of PI
  - `aboveClampsToTopInset`: target `(500f, -3000f)` gives `y == 190f`
  - `belowClampsToBottomInset`: target `(500f, 6000f)` gives `y == 1700f`
  - `cornerStaysInsideSafeRect`: target `(5000f, 9000f)` gives `x` in `16f..984f` and `y` in `190f..1700f`
  - `angleLooksAtTarget`: for target `(2000f, 1000f)`, `atan2(ty - y, tx - x)` equals `angleRad` within 1e-4
  - `noFlickerAtEdge`: target `(1010f, 1000f)`: `wasShown = false` is shown; target `(990f, 1000f)`: `wasShown = false` is null but `wasShown = true` is shown; target `(900f, 1000f)`: `wasShown = true` is null
  - `nonFiniteTargetHidden`: NaN and `Float.POSITIVE_INFINITY` for x or y give null
  - `zeroSizeHidden`: width 0 and height 0 each give null
  - `noRoomStillInsideMap`: map 300 x 400 with the same insets and target `(5000f, 5000f)` gives `x` in `0f..300f`, `y` in `0f..400f`

- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :shared:allTests --tests '*OffscreenPointerTest*'` (fall back to `./gradlew :shared:jvmTest` or the task name `./gradlew tasks --all | grep -i test` shows for the shared module)
Expected: FAIL, unresolved reference `OffscreenPointer`.

- [ ] **Step 3: Implement** `OffscreenPointer.place` and the two data classes in `OffscreenPointer.kt` with the signature and rules above. The scale factor along the ray is the smaller of the per-axis factors that reach the safe rectangle; an axis whose safe range is empty uses the centre.

- [ ] **Step 4: Run to verify they pass** (same command). Expected: PASS, all 13 tests.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/mistysteps/app/map/OffscreenPointer.kt shared/src/commonTest/kotlin/com/mistysteps/app/map/OffscreenPointerTest.kt
git commit -m "Shared: off-screen pointer placement"
```

---

### Task 2: Android pointer

**Files:**
- Modify: `app/src/main/java/com/mistysteps/app/map/MistyMap.kt` (add `val isFollowing: Boolean get() = following`)
- Modify: `app/src/main/java/com/mistysteps/app/ui/MapController.kt` (pointer state)
- Create: `app/src/main/java/com/mistysteps/app/ui/OffscreenPointerButton.kt`
- Modify: `app/src/main/java/com/mistysteps/app/ui/MistyScreen.kt` (draw it; share the locate lambda)

**Interfaces:**
- Consumes: `OffscreenPointer.place`, `PointerPlacement`, `PointerInsets` from Task 1; `feed.fix` (`Fix?` with `lat`, `lon`); `map.projection.toScreen(lat, lon)`, `width`, `height`.
- Produces: `MapController.pointer: StateFlow<PointerPlacement?>`; `@Composable fun OffscreenPointerButton(placement: PointerPlacement, onClick: () -> Unit)`.

- [ ] **Step 1: Expose follow state.** Add `val isFollowing: Boolean get() = following` to `MistyMap`.

- [ ] **Step 2: Add `MapController.pointer`.** A `MutableStateFlow<PointerPlacement?>(null)` exposed read-only, and a private `updatePointer()` that calls `OffscreenPointer.place` with: `hasFix = feed.fix.value != null`, the projected fix as target, `map.projection.width/height`, `map.isFollowing`, insets and hysteresis scaled by display density, `wasShown = pointer.value != null`. Call it from `map.onCameraMove` (next to `fog.moved(); markers.moved()`), from a collector on `feed.fix` in the existing scope, and when the map view size changes (`mapView.addOnLayoutChangeListener`). Unit-testable logic stays in Task 1; this step is wiring.

- [ ] **Step 3: Write `OffscreenPointerButton`.** A 44dp circle, `surface` colour at 85% alpha, the selected character's idle sprite (use the same bitmap source `CharacterBitmaps` gives the character button) inside, and a small triangle on the rim rotated by `placement.angleRad`. Positioned by the caller with `Modifier.offset`. `contentDescription = stringResource(R.string.show_my_location)`.

- [ ] **Step 4: Wire into `MistyScreen`.** Extract the locate button's `onClick` body (the permission check, `map.flyToMe()`, the `toast_looking` toast) into one local `val locate: () -> Unit` used by both the existing `FloatingActionButton` and the new pointer. Draw the pointer inside the same `Box`, after the `AndroidView` and before the other controls, only when `controller.pointer` is non-null, offset to `(x - 22dp, y - 22dp)` converted from px.

- [ ] **Step 5: Verify build and tests**

Run: `./gradlew testDebugUnitTest assembleDebug` (with the JDK 17 `JAVA_HOME`)
Expected: BUILD SUCCESSFUL, no test failures.

- [ ] **Step 6: Check on the Dev app** (ask the user to connect the phone and confirm it is unlocked). Install `app-debug.apk`, start it by explicit component, and before every tap confirm `mCurrentFocus` and take a fresh screenshot. Drag the map far from the character: the pointer appears on the edge facing the character and clears no control. Tap it: the camera returns and the pointer disappears. Rotate the phone with the pointer showing: it moves to the right edge position. Report any temporary setting changed.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mistysteps/app
git commit -m "Android: pointer to the character when it is off-screen"
```

---

### Task 3: iPhone pointer and docs

**Files:**
- Modify: `iosApp/MistySteps/MapView.swift` (publish the placement)
- Create: `iosApp/MistySteps/OffscreenPointerView.swift`
- Modify: `iosApp/MistySteps/ContentView.swift` (state, overlay, tap)
- Modify: `workflow.md` (one line under "What the app does", Camera paragraph)
- If the Xcode project lists sources explicitly (`project.yml` or `.pbxproj`), add `OffscreenPointerView.swift` there.

**Interfaces:**
- Consumes: Task 1 via the `Shared` framework: `OffscreenPointer.shared.place(hasFix:targetX:targetY:width:height:following:insets:hysteresis:wasShown:) -> PointerPlacement?`; `following` binding and `locate` in `ContentView`.
- Produces: `MapView` gains `@Binding var pointer: PointerPlacement?`; `struct OffscreenPointerView: View { let placement: PointerPlacement; let onTap: () -> Void }`.

- [ ] **Step 1: Publish the placement from `MapView`.** In the coordinator, recompute with `map.convert(coordinate, toPointTo: map)` for the user's coordinate on `mapViewRegionIsChanging`, `regionDidChangeAnimated`, on location updates, and on bounds change; write the result to the `pointer` binding on the main queue only when it changed. Use points for size and insets (same numbers as Android's dp).

- [ ] **Step 2: Write `OffscreenPointerView`.** Same look as Android: 44pt circle, `Misty.frost` at 85% opacity, the character's idle sprite (same image source as the character button), a small triangle on the rim rotated by `angleRad`, `accessibilityLabel` from the existing locate button's label.

- [ ] **Step 3: Wire into `ContentView`.** Add `@State private var pointer: PointerPlacement?`, pass `pointer: $pointer` to `MapView`, and in the `ZStack` after `MapView` draw `OffscreenPointerView` at `.position(x: CGFloat(p.x), y: CGFloat(p.y))` when non-nil, with `onTap: locate`.

- [ ] **Step 4: Build for the simulator**

Run: `xcodebuild -project iosApp/*.xcodeproj -scheme MistySteps -destination 'platform=iOS Simulator,name=iPhone 16' build` (use the simulator name `xcrun simctl list devices available` shows; the project's workflow notes list the existing simulator hooks)
Expected: BUILD SUCCEEDED.

- [ ] **Step 5: Check in the simulator.** Launch, set a location, drag the map away: the pointer shows on the edge toward you; tap it: the map recentres and it hides.

- [ ] **Step 6: Docs.** Add to the Camera paragraph in `workflow.md`: when your character is off-screen a small pointer on the screen edge points to it and a tap brings you back (Android and iPhone; `map/OffscreenPointer.kt`).

- [ ] **Step 7: Commit**

```bash
git add iosApp workflow.md
git commit -m "iPhone: pointer to the character when it is off-screen; docs"
```
