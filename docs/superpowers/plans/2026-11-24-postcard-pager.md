# Postcard Pager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swipe left and right through your postcards, and leave back to the screen you came from.

**Architecture:** A pure list builder in shared code; the postcard screen becomes a pager that paints pages lazily and caches only nearby ones; hosts supply the walk list; navigation keeps the stats page open under the postcard.

**Tech Stack:** Kotlin Multiplatform shared module (Compose foundation pager, kotlin.test), Android Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-11-24-postcard-pager-design.md`

## Global Constraints

- No emoji anywhere.
- Order: newest first (descending walk id); the walk list is `walksToShow(...)` plus the start walk; cache the current page and one neighbour each side.
- Work on branch `postcard-pager`. Never push. Do not touch the user's phone while it is locked; screenshot before every tap.

## Review Focus

- Opening a walk that is not in the log list (a just-ended 40 m walk, or the only walk): the pager still starts on it, with one page.
- Fast swiping through 17 walks: no crash, no stale picture on the wrong page, memory stays bounded.
- Save and Share on page N act on page N (Android and especially iPhone, where the host kept one PNG).
- Back and "Back to the mist" return to the stats page (from the log) or the map (after a walk); the stats scroll position is kept.
- A walk that fails to paint (no points): that page shows the failure text and swiping on still works.
- A walk that ends while the stats page is open: the postcard opens over the map, not stuck under stats.
- Swiping against the start or the end of the list: nothing happens, no crash.

---

### Task 1: Shared list, host interface and the pager

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/ui/PostcardPaging.kt`
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/ui/PostcardPagingTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/ui/PostcardScreen.kt`, `shared/src/iosMain/kotlin/com/mistysteps/app/ui/PostcardIos.kt`

**Interfaces:**
- Consumes: `Session` (`id`, `endTime`, `distanceM`), `walksToShow`, `MIN_WALK_M`.
- Produces: `object PostcardPaging { fun walkIds(walks: List<Session>, walksWithPins: Set<Long>, startId: Long): List<Long> }`; `PostcardHost.walkIds(startId: Long): List<Long>` (suspend); `PostcardPainter.postcardSessions(): List<Session>` and `PostcardPainter.walkIdsWithPins(): List<Long>` (iOS); `PostcardScreen(walkId, host, onClose)` unchanged.

- [ ] **Step 1: Write the failing tests** in `PostcardPagingTest` (build `Session` values directly): `newestFirst` (ids 3, 1, 2 give 3, 2, 1); `shortWalksWithoutPinsAreLeftOut` (a 30 m finished walk without a pin is dropped; with a pin id it stays); `aRunningWalkStays` (`endTime == null`); `theStartWalkIsAlwaysIncluded` (a 30 m walk without a pin, as `startId`, is present and in its sorted place); `theStartWalkIsNotDuplicated`; `emptyListGivesJustTheStart`.
- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :shared:testAndroidHostTest --tests '*PostcardPagingTest*'`
Expected: FAIL (unresolved `PostcardPaging`).

- [ ] **Step 3: Implement** `PostcardPaging.walkIds` (filter with `walksToShow`, add `startId` if missing, sort descending, distinct).
- [ ] **Step 4: The pager.** `PostcardHost.walkIds(startId)`; `PostcardScreen` loads the ids first (a progress indicator meanwhile), then a `HorizontalPager` with `initialPage = ids.indexOf(walkId)`; each page paints with `host.render` when composed and keeps the result in a state map keyed by walk id; failure per page; pages more than one away from the settled page are removed from the map; under the title a "3 / 17" when there is more than one page; Save and Share use the current page's picture and file name; back and the button call `onClose`. `IosPostcardHost` keeps PNGs by file name and implements `walkIds` through `PostcardPainter.postcardSessions()` and `walkIdsWithPins()`.
- [ ] **Step 5: Verify**

Run: `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (the Android app and Swift are fixed in Tasks 2 and 3).

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "Postcard pager: walk list, host interface and the pager (shared)"
```

---

### Task 2: Android

**Files:**
- Modify: `app/src/main/java/com/mistysteps/app/ui/AndroidPostcardHost.kt`, `app/src/main/java/com/mistysteps/app/ui/MistyScreen.kt`

**Interfaces:**
- Consumes: `PostcardPaging.walkIds`, `AndroidStatsSource.sessions()`, `PinStore.pins`.

- [ ] **Step 1: Host.** `AndroidPostcardHost.walkIds(startId)` reads the sessions and the pins' walk ids and calls `PostcardPaging.walkIds`.
- [ ] **Step 2: Navigation.** In `MistyScreen`, the stats page's `onOpenWalk` only sets `postcardWalk` (it no longer sets `showStats = false`), so closing the postcard shows the stats page again; the walk-ended path keeps closing stats and the You page first.
- [ ] **Step 3: Build and test** (`./gradlew :shared:testAndroidHostTest testDebugUnitTest assembleDebug`). Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Check on the Dev app** when the phone is unlocked (screenshot before every tap): open a walk from the log, swipe left and right, watch the counter, press Back and land on the stats page.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java
git commit -m "Android: swipe through postcards; back returns to the stats page"
```

---

### Task 3: iPhone and docs

**Files:**
- Modify: `iosApp/MistySteps/PostcardPainterImpl.swift`, `iosApp/MistySteps/ContentView.swift`, `workflow.md`

**Interfaces:**
- Consumes: `PostcardPainter.postcardSessions()`, `walkIdsWithPins()`.

- [ ] **Step 1: Painter.** `postcardSessions()` returns all sessions from the store; `walkIdsWithPins()` the pins' session ids.
- [ ] **Step 2: Navigation.** `ContentView`: `onOpenWalk` only sets `postcardWalk` (no `app.close()`); the postcard overlay moves after the route pages, so it draws above the stats page.
- [ ] **Step 3: Docs.** `workflow.md`: the walk postcard paragraph.
- [ ] **Step 4: Build and check in the simulator** (`xcodegen generate`, `xcodebuild`, launch with `-postcard last`): the postcard shows with a counter; the simulator cannot swipe, so swiping is checked on the Pixel.
- [ ] **Step 5: Commit**

```bash
git add iosApp workflow.md
git commit -m "iPhone: swipe through postcards; back returns to the stats page; docs"
```
