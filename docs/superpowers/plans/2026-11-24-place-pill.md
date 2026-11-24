# Place Pill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the top pill's "Misty Steps / km of mist cleared" with the buckets of mist cleared in your city or town (and its share), as a big number with a small caption.

**Architecture:** A pure picker and a shared text builder; each platform recounts periodically through its existing stats source and draws the result.

**Tech Stack:** Kotlin Multiplatform shared module (kotlin.test), Jetpack Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-11-24-place-pill-design.md`

## Global Constraints

- No emoji anywhere. Strings: `pill_place_caption` ("buckets of mist cleared in %1$s (%2$s)" / "emmers mist opgeklaard in %1$s (%2$s)"; %1$s the place, %2$s its share) and `pill_total_caption` ("buckets of mist cleared" / "emmers mist opgeklaard").
- A bucket is 10 litres and the mist 2 m deep: `MistBuckets.PER_SQUARE_METRE = 200.0`. Rounding: exact below 100, two significant digits from 100 up.
- Refresh every 10 seconds (constant `PillRefresh.INTERVAL_MS = 10_000`), only while the map is visible; never on the main thread.
- Work on branch `place-pill`. Never push. Do not touch the user's phone while it is locked.

## Review Focus

- No fix yet / outside the Netherlands / border data missing: the pill shows the total area cleared, never a blank or "0%" caused by missing data.
- A tiny share (e.g. 1e-8) reads "1 in N" in the caption, and a share of exactly 0 reads "0%"; zero area gives "0" buckets.
- A huge area (the whole Netherlands, 4e10 m2) stays a valid number (no Int overflow) and the number still fits the pill.
- A city whose total area is unknown (null or 0): falls back to the country, then to the total.
- While a recount runs, the old number stays; a slow or failing recount does not blank the pill.
- Dutch: the caption with a long town name ("Nieuwegein", "'s-Hertogenbosch") fits one line or wraps cleanly.

---

### Task 1: Shared picker and text

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/stats/PillPick.kt`, `shared/src/commonMain/kotlin/com/mistysteps/app/stats/MistBuckets.kt`, `shared/src/commonMain/kotlin/com/mistysteps/app/ui/PillContent.kt`
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/stats/PillPickTest.kt`, `shared/src/commonTest/kotlin/com/mistysteps/app/stats/MistBucketsTest.kt`
- Modify: `shared/strings/values/strings.xml`, `shared/strings/values-nl/strings.xml`, `shared/src/androidHostTest/kotlin/com/mistysteps/app/character/CharactersFileTest.kt` (strings test), `shared/src/iosMain/kotlin/com/mistysteps/app/ui/StatsIos.kt` (`PillSource`)

**Interfaces:**
- Consumes: `Stats`, `AreaStat` (`fraction`, `name`, `clearedM2`), `AreaKind`.
- Produces: `object MistBuckets { const val PER_SQUARE_METRE = 200.0; fun count(areaM2: Double): Long; fun readable(buckets: Long): Long }`; `object PillPick { fun place(stats: Stats): AreaStat? }`; `object PillRefresh { const val INTERVAL_MS = 10_000L }`; `data class PillContent(val value: String, val caption: String)`; `suspend fun pillContent(stats: Stats): PillContent`; iOS `class PillSource(points: PointStore, sessions: SessionStore) { fun refresh(lat: Double?, lon: Double?, onResult: (PillContent) -> Unit) }`.

- [ ] **Step 1: Write the failing tests** in `PillPickTest` (build `Stats` and `AreaStat` values directly): `cityWinsWhenItsShareIsKnown`; `cityWithoutATotalFallsBackToTheCountry` (city `totalM2 = null`); `cityWithZeroTotalFallsBackToTheCountry` (`totalM2 = 0.0`); `neitherCityNorCountryMeansNothing` (both null, or both without a total) returns null; `aCityWithNothingClearedStillCounts` (`clearedM2 = 0.0`, known total) returns the city. In `MistBucketsTest`: `count(1.0) == 200L`; `count(39_000.0) == 7_800_000L`; `count(0.0) == 0L`; `count(-3.0) == 0L`; `count(Double.NaN) == 0L`; `count(Double.POSITIVE_INFINITY) == 0L`; `count(1.0e300) == Long.MAX_VALUE`; `readable(99) == 99L`; `readable(100) == 100L`; `readable(7_812_345) == 7_800_000L`; `readable(848_000_000) == 850_000_000L`. Extend the strings test: the two new keys exist in both strings files, `pill_place_caption` has `%1$s` and `%2$s` in both.
- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :shared:testAndroidHostTest --tests '*PillPickTest*' --tests '*CharactersFileTest*'`
Expected: FAIL (unresolved `PillPick`, missing strings).

- [ ] **Step 3: Implement** `PillPick`, `PillRefresh`, the strings, `PillContent`/`pillContent` (value: `formatNumber(MistBuckets.readable(MistBuckets.count(areaM2)).toDouble(), 0, grouping = true)` with `areaM2` the place's `clearedM2`, else `stats.clearedM2`; caption: `pill_place_caption` with the place name (same naming as the stats page's `areaName`, make it `internal` if needed) and its share from `formatShare(fraction) { getString(Res.string.area_one_in, it) }`, else `pill_total_caption`), and the iOS `PillSource` (a coroutine on `Dispatchers.Default` calling `LocalStatsSource.compute`, results delivered on `Dispatchers.Main`; errors keep the old value by sending nothing).
- [ ] **Step 4: Verify**

Run: `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared
git commit -m "Place pill: picker, text and the iPhone source (shared)"
```

---

### Task 2: Android

**Files:**
- Modify: `app/src/main/java/com/mistysteps/app/ui/MistyScreen.kt`

**Interfaces:**
- Consumes: `PillContent`, `pillContent`, `PillRefresh.INTERVAL_MS`, `AndroidStatsSource.compute`, the map's current position (`currentPlace()` as the stats call uses).

- [ ] **Step 1: State and loop.** `var pill by remember { mutableStateOf<PillContent?>(null) }`; a `LaunchedEffect` that, while the map is visible and not covered, computes `pillContent(AndroidStatsSource(context).compute(lat, lon))` (off the main thread, exceptions swallowed), stores it only on success, then waits `PillRefresh.INTERVAL_MS`; before the first result show the total distance-free fallback (the total area from the first `compute`).
- [ ] **Step 2: Draw.** In the pill, remove "Misty Steps"; show `pill.value` (serif, semi-bold, about 26 sp) over `pill.caption` (12 sp, quiet colour); nothing until the first result.
- [ ] **Step 3: Build and test** (`./gradlew :shared:testAndroidHostTest testDebugUnitTest assembleDebug`). Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Check on the Dev app** only when the phone is unlocked: the pill shows the buckets for your city and the caption; walking is not simulated, so check that it shows after opening and again after leaving and returning to the map.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java
git commit -m "Android: the pill shows how much of your city is cleared"
```

---

### Task 3: iPhone and docs

**Files:**
- Create: `iosApp/MistySteps/PillModel.swift`
- Modify: `iosApp/MistySteps/HubView.swift`, `iosApp/MistySteps/ContentView.swift`, `workflow.md`

**Interfaces:**
- Consumes: `PillSource`, `PillContent`, `PillRefresh.shared.INTERVAL_MS`, the location model's coordinate.

- [ ] **Step 1: `PillModel`** (`ObservableObject`, `@Published var content: PillContent?`, a timer every 10 s while active, calls `PillSource.refresh(lat:lon:)` and publishes the result on the main queue; keeps the old value when nothing arrives).
- [ ] **Step 2: Draw** the pill in `HubView` like Android (no title; number and caption), driven by the model; `ContentView` owns the model and starts it when the map is shown.
- [ ] **Step 3: Docs.** `workflow.md`: the pill paragraph.
- [ ] **Step 4: Build and check in the simulator** (`xcodegen generate`, `xcodebuild`, launch): the pill shows buckets, the town name and its share for the simulated location (Leeuwarden).
- [ ] **Step 5: Commit**

```bash
git add iosApp workflow.md
git commit -m "iPhone: the pill shows how much of your city is cleared; docs"
```
