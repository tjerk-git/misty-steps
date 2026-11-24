# Remove the Consent Step Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take the "Count opens, anonymously?" step out of the first-run flow on both platforms; counting stays off until switched on in Settings.

**Architecture:** Delete the shared first-run stage and its page; the two hosts stop showing it. The ping, the toggle and the "What is sent" sheet are untouched.

**Tech Stack:** Kotlin Multiplatform shared module (kotlin.test), Jetpack Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-11-24-remove-consent-design.md`

## Global Constraints

- No emoji anywhere.
- Counting stays off by default: do not change `Analytics.isOptedIn` (default false) or `OpenPing.shouldPing`.
- Work on branch `remove-consent`. Never push. Do not touch the user's phone while it is locked.

## Review Focus

- A person who answered yes before this change keeps counting (the saved `analytics_opt_in` is untouched); one who answered no or was never asked does not count.
- A new install goes onboarding, then the picker, then the map, with no blank or stuck step in between (both platforms).
- Someone who finished onboarding and the picker before is not shown anything new.
- The Settings toggle still switches counting on and off and still works without the old "asked" flag.
- No leftover reference to the consent page, its strings, route or flag in code, tests or docs.

---

### Task 1: Shared rules and page

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mistysteps/app/maps/FirstRun.kt`, `shared/src/commonTest/kotlin/com/mistysteps/app/maps/FirstRunTest.kt`, `shared/src/commonTest/kotlin/com/mistysteps/app/announce/RatAnnouncementTest.kt`
- Delete: `shared/src/commonMain/kotlin/com/mistysteps/app/ui/ConsentScreen.kt`
- Modify: `shared/src/iosMain/kotlin/com/mistysteps/app/ui/MainViewController.kt` (remove `consentViewController`), `shared/src/iosMain/kotlin/com/mistysteps/app/ui/StatsIos.kt` (stop writing `analytics_asked`), `shared/strings/values/strings.xml`, `shared/strings/values-nl/strings.xml` (remove `consent_title/body/yes/no`), `shared/src/androidHostTest/kotlin/com/mistysteps/app/character/CharactersFileTest.kt` (remove the consent text test)

**Interfaces:**
- Produces: `enum class FirstRunStage { ONBOARDING, PICKER, DONE }`; `FirstRun.stage(onboardingDone: Boolean, pickerSeen: Boolean, hasTrailPoints: Boolean): FirstRunStage`.

- [ ] **Step 1: Write the failing tests.** Rewrite `FirstRunTest` for the new signature: `aNewInstallGoesThroughOnboardingThenThePicker` (`stage(false,false,false)` ONBOARDING, `stage(true,false,false)` PICKER, `stage(true,true,false)` DONE); `anExistingUserWhoSawThePickerSeesNothing` (`stage(false,true,true)` DONE); `anExistingUserWhoNeverSawThePickerSkipsOnboardingButGetsThePicker` (`stage(false,false,true)` PICKER); `onboardingIsNotShownAgainOnceDone` (`stage(true,false,false)` PICKER); `everythingSeenShowsNothing` (`stage(true,true,true)` DONE). Remove the `CONSENT` line from `RatAnnouncementTest`. Remove the consent text test from `CharactersFileTest` and add `noConsentStringsAreLeft` there: neither strings file contains `name="consent_`.
- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :shared:testAndroidHostTest`
Expected: FAIL (compile error on the new `stage` signature).

- [ ] **Step 3: Implement** the new `FirstRunStage` and `FirstRun.stage`; delete `ConsentScreen.kt`, `consentViewController`, the strings, and the `analytics_asked` write in `StatsIos.kt`.
- [ ] **Step 4: Verify**

Run: `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (the Android app and the Swift code are fixed in Task 2).

- [ ] **Step 5: Commit**

```bash
git add shared
git commit -m "First run: remove the usage-count question (shared)"
```

---

### Task 2: Android, iPhone and docs

**Files:**
- Modify: `app/src/main/java/com/mistysteps/app/ui/MistyScreen.kt`, `app/src/main/java/com/mistysteps/app/ui/UsageCountsSwitch.kt`, `app/src/main/java/com/mistysteps/app/stats/Analytics.kt`
- Modify: `iosApp/MistySteps/AppModel.swift`, `iosApp/MistySteps/ContentView.swift`, `iosApp/MistySteps/SharedComposeView.swift`
- Modify: `docs/app-store-listing.md`, `workflow.md`, `docs/superpowers/specs/2026-11-16-open-count-design.md` (a "changed 2026-11-24" note)

- [ ] **Step 1: Android.** `MistyScreen`: `stageNow()` calls the new `FirstRun.stage(...)`; remove the consent block and the comment mention. `Analytics`: remove `wasAsked`, `markAsked` and `KEY_ASKED`; `UsageCountsSwitch`: drop the `markAsked` call.
- [ ] **Step 2: iPhone.** `AppModel`: remove `Route.consent`, the `consentAsked` key, its argument, the `.consent` case and `answerConsent`; `ContentView`: remove the `.consent` case; `SharedComposeView`: remove `ConsentHostView`.
- [ ] **Step 3: Docs.** Store listing notes and workflow: the first run is onboarding then the picker, counting is off until switched on in Settings; the open-count spec gets a note that the question was removed on 2026-11-24 and the toggle remains.
- [ ] **Step 4: Build and test**

Run: `./gradlew :shared:testAndroidHostTest testDebugUnitTest assembleDebug` and `xcodegen generate` plus `xcodebuild` for the booted simulator.
Expected: BUILD SUCCESSFUL / BUILD SUCCEEDED.

- [ ] **Step 5: Check in the simulator.** With `onboarding_done` set and no `analytics_asked`, a launch shows the province picker, not a consent page (before this change it showed consent).
- [ ] **Step 6: Commit**

```bash
git add app iosApp docs workflow.md
git commit -m "First run: remove the usage-count question (Android, iPhone, docs)"
```
