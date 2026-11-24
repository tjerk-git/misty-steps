# Walk Steps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show, on the walk postcard, how many steps the walk's distance is for the selected animal.

**Architecture:** Per-character step length lives in `characters.txt`; shared code turns distance into a readable step count and a translated line; both postcard renderers draw the line.

**Tech Stack:** Kotlin Multiplatform shared module (kotlin.test), Android Canvas, iPhone UIKit drawing.

**Spec:** `docs/superpowers/specs/2026-11-24-walk-steps-design.md`

## Global Constraints

- No emoji anywhere.
- Stride values (metres per step): cat 0.2, blackcat 0.2, dog 0.4, otter 0.25, capybara 0.3, cow 0.8, horse 0.9, fox 0.35, duck 0.15, rat 0.05. Default when missing: 0.3.
- Rounding: exact below 100 steps, two significant digits from 100 up.
- String name `postcard_steps`: English "That is about %1$s %2$s steps." Dutch "Dat zijn zo'n %1$s stappen als %2$s."
- Work on branch `walk-steps`. Never push. Do not touch the user's phone while it is locked.

## Review Focus

- Distance 0 or negative, a stride of 0 or negative, NaN or infinite distance: no line, no crash.
- A very long walk (1,000 km) with the 0.05 m rat: the count stays a valid number (no Int overflow), and the line still fits.
- A custom character name that is long: the line shrinks to fit and does not overlap the credit.
- Dutch with "Indische loopeend": fits the width.
- A character without a `stride` property: falls back to 0.3, not 0.

---

### Task 1: Stride data and step maths

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/postcard/WalkSteps.kt`
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/postcard/WalkStepsTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/character/Characters.kt` (`Character.strideM`, parse the `stride` property)
- Modify: `app/src/main/assets/characters.txt` (a `stride =` line per character, header comment)
- Test: `shared/src/androidHostTest/kotlin/com/mistysteps/app/character/CharactersFileTest.kt` (extend)

**Interfaces:**
- Produces: `object WalkSteps { const val DEFAULT_STRIDE_M = 0.3; fun count(distanceM: Double, strideM: Double): Long; fun readable(steps: Long): Long }`; `Character.strideM: Double`.

- [ ] **Step 1: Write the failing tests** in `WalkStepsTest`: `count(1000.0, 0.5) == 2000L`; `count(0.0, 0.5) == 0L`; `count(-5.0, 0.5) == 0L`; `count(1000.0, 0.0) == 0L`; `count(1000.0, -1.0) == 0L`; `count(Double.NaN, 0.5) == 0L`; `count(Double.POSITIVE_INFINITY, 0.5) == 0L`; `count(1_000_000.0, 0.05) == 20_000_000L`; `readable(0) == 0`; `readable(99) == 99`; `readable(100) == 100`; `readable(31_234) == 31_000`; `readable(7_460) == 7_500`; `readable(123_456) == 120_000`; `readable(20_000_000) == 20_000_000`. In `CharactersFileTest`: every character in the real sheet has a `stride =` line (count lines starting with `stride` equals the number of `[` sections) and `strideM` in `0.02..2.0`; and a parsed sheet entry without `stride` gets `WalkSteps.DEFAULT_STRIDE_M` (parse a small inline sheet).
- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :shared:testAndroidHostTest --tests '*WalkStepsTest*' --tests '*CharactersFileTest*'`
Expected: FAIL (compile errors: `WalkSteps`, `strideM`).

- [ ] **Step 3: Implement** `WalkSteps.count` (round to nearest, 0 for non-finite or non-positive inputs, clamp to `Long` range), `WalkSteps.readable`, `Character.strideM` (`stride` property or the default), and add the `stride =` lines and a header comment to `characters.txt`.
- [ ] **Step 4: Run to verify they pass** (same command). Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add shared app/src/main/assets/characters.txt
git commit -m "Walk steps: stride per character and the step maths"
```

---

### Task 2: The line in shared code

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/ui/PostcardSteps.kt`
- Modify: `shared/src/iosMain/kotlin/com/mistysteps/app/ui/PostcardIos.kt` (`PostcardTexts.steps`)
- Modify: `shared/strings/values/strings.xml`, `shared/strings/values-nl/strings.xml` (`postcard_steps`)
- Test: `shared/src/androidHostTest/kotlin/com/mistysteps/app/character/CharactersFileTest.kt` (strings present)

**Interfaces:**
- Consumes: `WalkSteps`, `Character.strideM`, `formatNumber`, `Res.allStringResources`.
- Produces: `suspend fun postcardStepsLine(character: Character, customName: String?, distanceM: Double): String?` (null when the step count is 0); `PostcardTexts.steps(characterId: String, customName: String?, distanceM: Double): String?` (Swift entry, looks the character up in `Characters.all`).

- [ ] **Step 1: Failing test:** `postcard_steps` exists in both strings files (extend the existing strings test with the key and check `%1$s` and `%2$s` appear in both).
- [ ] **Step 2: Run to verify it fails** (`./gradlew :shared:testAndroidHostTest --tests '*CharactersFileTest*'`). Expected: FAIL (key missing).
- [ ] **Step 3: Add the strings, implement `postcardStepsLine`** (species name from `character_<id>_name` via `Res.allStringResources`, falling back to `Character.name`; a custom name wins; number is `formatNumber(WalkSteps.readable(steps).toDouble(), 0, grouping = true)`) **and `PostcardTexts.steps`** (`runBlocking`, null for an unknown id).
- [ ] **Step 4: Verify** `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit**

```bash
git add shared
git commit -m "Walk steps: the postcard line in shared code"
```

---

### Task 3: Android postcard

**Files:**
- Modify: `app/src/main/java/com/mistysteps/app/postcard/PostcardRenderer.kt`

**Interfaces:**
- Consumes: `postcardStepsLine`.

- [ ] **Step 1: Compute the line in `render`** (`character?.let { postcardStepsLine(it, Characters.customName(it.id), walk.distanceM) }`) and pass it to `compose`.
- [ ] **Step 2: Draw it** under the stats line (baseline `y0 + 196`), monospace, quiet colour, starting at 34 px and shrinking by 1 px until `measureText` fits `W - 2 * MARGIN` (not below 22 px); skipped when null.
- [ ] **Step 3: Build and run all tests** (`./gradlew :shared:testAndroidHostTest testDebugUnitTest assembleDebug`). Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Check on the Dev app** only when the user has unlocked the phone (screenshot before every tap): open a walk's postcard; the steps line shows under the stats. If the phone is locked, record that the check is outstanding.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java
git commit -m "Android: the steps line on the postcard"
```

---

### Task 4: iPhone postcard and docs

**Files:**
- Modify: `iosApp/MistySteps/PostcardRenderer.swift`
- Modify: `workflow.md`

**Interfaces:**
- Consumes: `PostcardTexts.shared.steps(characterId:customName:distanceM:)`.

- [ ] **Step 1: Compute and draw the line** in `render`/`drawTexts` like Android: baseline `y0 + 196`, 34 pt monospaced, quiet colour, shrinking to fit `width - 2 * margin` (not below 22 pt); skipped when nil.
- [ ] **Step 2: Docs:** one sentence in the "Walk postcard" paragraph of `workflow.md` and the `stride =` note for characters.
- [ ] **Step 3: Build and check in the simulator** (`xcodegen generate`, `xcodebuild ... -destination 'platform=iOS Simulator,id=<booted>'`, launch with `-postcard last`): the postcard shows the steps line inside the margins.
- [ ] **Step 4: Commit**

```bash
git add iosApp workflow.md
git commit -m "iPhone: the steps line on the postcard; docs"
```
