# The You Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the combined stats page into a stats-only page and a new "You" page (character plus settings), with the character picker as a bottom sheet.

**Architecture:** Shared Compose: `YouScreen` and `CharacterSheet` are new/converted, `StatsScreen` is trimmed. The platform cards stay as slots passed by each host.

**Tech Stack:** Kotlin Multiplatform shared module (Compose Multiplatform, kotlin.test), Jetpack Compose (Android), SwiftUI (iPhone).

**Spec:** `docs/superpowers/specs/2026-11-24-you-screen-design.md`

## Global Constraints

- No emoji anywhere.
- New string names: `you_title` (EN "You", NL "Jij"), `you_settings_header` ("Settings" / "Instellingen"), `you_about_header` ("About" / "Over"), `you_change_character` ("Change character" / "Verander van dier").
- Everything reachable before stays reachable (maps card, backup card, usage card, privacy note, Made by, feedback, rename).
- Work on branch `you-screen`. Never push. Do not touch the user's phone while it is locked; screenshot before every tap.

## Review Focus

- Import of a backup from the You page: the stats page must show the new numbers next time it is opened (it recounts on open).
- Rename a character from the You page and from the sheet: both update the name shown in the other place at once.
- Picking a character in the sheet while the sheet is dragged or the back button pressed: no stuck sheet, no double pick.
- Back button / back arrow on Android and iPhone: from the sheet closes the sheet only; from You or Stats returns to the map.
- Opening You or Stats while the eraser is active: the eraser turns off (as the character page did).
- Dutch: "Jij" title and all new rows fit one line or wrap cleanly.

---

### Task 1: Shared UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/ui/YouScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/ui/CharacterScreen.kt` (`CharacterScreen` becomes `CharacterSheet`; `RenameDialog` and `characterText` become `internal`)
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/ui/StatsScreen.kt` (trim; `MadeBy` and `FeedbackRow` become `internal`; `StatsSlots` stays and is used by `YouScreen`)
- Modify: `shared/src/iosMain/kotlin/com/mistysteps/app/ui/MainViewController.kt` (`characterViewController` becomes `youViewController`) and `StatsIos.kt` (`statsViewController` loses its settings arguments)
- Modify: `shared/strings/values/strings.xml`, `shared/strings/values-nl/strings.xml`
- Test: `shared/src/androidHostTest/kotlin/com/mistysteps/app/character/CharactersFileTest.kt` (strings test)

**Interfaces:**
- Produces: `@Composable fun YouScreen(slots: StatsSlots, versionName: String, onClose: () -> Unit, onOpenAbout: () -> Unit)`; `@Composable fun CharacterSheet(onClose: () -> Unit)`; `StatsScreen(source, pins, here, showBattery, onClose, onOpenWalk, onOpenPin)` (no `versionName`, `slots`, `onOpenAbout`); iOS `youViewController(versionName: String, backup: BackupHost, packs: PackManager, provinces: List<PackEntry>?, onOpenPicker: () -> Unit, onClose: () -> Unit, onOpenAbout: () -> Unit): UIViewController` (with the usage card and feedback slots built as `statsViewController` built them); `statsViewController(points, sessions, hereLat, hereLon, pins, onOpenWalk, onClose)`.

- [ ] **Step 1: Failing test:** extend the strings test so the four `you_*` keys must exist in both strings files. Run `./gradlew :shared:testAndroidHostTest --tests '*CharactersFileTest*'`. Expected: FAIL (keys missing).
- [ ] **Step 2: Add the strings** (EN and NL as in Global Constraints). Re-run the test. Expected: PASS.
- [ ] **Step 3: Convert `CharacterScreen` to `CharacterSheet`** (`ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))` holding today's title, subtitle and card grid; picking a character calls `Characters.select(c)` then `onClose()`; no full-screen background or back arrow).
- [ ] **Step 4: Trim `StatsScreen`** (remove the Maps header and card, the privacy note, the More block, `MadeBy`, `FeedbackRow` usage; drop the now unused parameters and the `refresh` bump from the backup card).
- [ ] **Step 5: Write `YouScreen`** per the spec's Design section: same page background and scrolling as `StatsScreen`, back arrow and title; the character card (walking sprite from `SpriteFrames` and `rememberTicker`, name with a pencil that opens `RenameDialog`, "Change character" that shows `CharacterSheet`); "Settings" header, `slots.mapsCard()`, `slots.backupCard {}`, `slots.usageCard()`, the privacy note; "About" header, `MadeBy(versionName, onOpenAbout)`, `FeedbackRow(slots.onFeedback)`. `PlatformBackHandler(onBack = onClose)`.
- [ ] **Step 6: iOS entry points** as listed under Produces.
- [ ] **Step 7: Verify**

Run: `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (the Android app will not compile until Task 2).

- [ ] **Step 8: Commit**

```bash
git add shared
git commit -m "You page: shared screens (You, character sheet, stats trimmed)"
```

---

### Task 2: Android

**Files:**
- Create: `app/src/main/java/com/mistysteps/app/ui/AndroidYouScreen.kt`
- Modify: `app/src/main/java/com/mistysteps/app/ui/AndroidStatsScreen.kt`, `app/src/main/java/com/mistysteps/app/ui/MistyScreen.kt`

**Interfaces:**
- Consumes: `YouScreen`, `StatsSlots`, `StatsScreen` new signature.
- Produces: `@Composable fun AndroidYouScreen(onClose: () -> Unit, onOpenAbout: () -> Unit, onOpenPicker: () -> Unit)`.

- [ ] **Step 1: `AndroidYouScreen`** builds the `StatsSlots` exactly as `AndroidStatsScreen` does today (maps card, backup card, usage switch, feedback) and calls `YouScreen`.
- [ ] **Step 2: Trim `AndroidStatsScreen`** (no slots, no versionName, no onOpenAbout/onOpenPicker).
- [ ] **Step 3: `MistyScreen`:** the character button now sets `showYou` (rename `showCharacters`) and draws `AndroidYouScreen`; the old `CharacterScreen` call goes; the stats call loses the removed arguments; the eraser-off effect includes `showYou`; the picker and About openings keep working from the You page.
- [ ] **Step 4: Build and test**

Run: `./gradlew :shared:testAndroidHostTest testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Check on the Dev app** only when the phone is unlocked (screenshot before every tap): the face button opens You; Change character opens the sheet and picking closes it; rename works; Maps, export and import, the usage toggle, Made by and feedback are all there; the bar-chart button opens stats-only; Back behaves as listed in Review Focus. If the phone is locked, record that the check is outstanding.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java
git commit -m "Android: the You page behind the character button"
```

---

### Task 3: iPhone and docs

**Files:**
- Modify: `iosApp/MistySteps/AppModel.swift` (`Route.characters` becomes `.you`), `iosApp/MistySteps/HubView.swift`, `iosApp/MistySteps/ContentView.swift`, `iosApp/MistySteps/SharedComposeView.swift` (`CharacterHostView` becomes `YouHostView`, `StatsHostView` loses its settings arguments), `workflow.md`

**Interfaces:**
- Consumes: `youViewController`, the new `statsViewController`.

- [ ] **Step 1: Routes and hosts.** `.you` replaces `.characters` everywhere (hub callback `onCharacters` becomes `onYou`; the `-route characters` hook becomes `-route you`); `YouHostView(backup:packs:provinces:versionName:onOpenPicker:onOpenAbout:onClose:)` calls `youViewController`; `StatsHostView` calls the trimmed `statsViewController`; the About, picker and stats routes stay.
- [ ] **Step 2: Docs.** `workflow.md`: the stats page paragraph (stats only) and a line for the You page (character, maps, backup, usage counts, privacy note, Made by, feedback) and the character sheet.
- [ ] **Step 3: Build and check in the simulator** (`xcodegen generate`, `xcodebuild` for the booted simulator, launch with `-route you` and `-route stats`): You shows the character card, Settings and About; Stats shows only stats.
- [ ] **Step 4: Commit**

```bash
git add iosApp workflow.md
git commit -m "iPhone: the You page; docs"
```
