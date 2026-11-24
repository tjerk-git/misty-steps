# Remove the usage-count question from the first-run flow: design

Status: approved by the user on 2026-11-24 (small change). The user's decision: new installs start with counting off.

## Intent and why

The first-run flow asks "Count opens, anonymously?". The count is anonymous (app version and platform only), the owner does not want to ask it, and may drop the whole feature later because the app stores report installs and usage. So the question goes.

## Goals

- A new install sees onboarding, then the province picker. No consent page, on either platform.
- Counting stays off until someone turns on "Usage counts" in Settings (the You page). Nobody is switched on without saying yes.
- Existing answers stay as they are: someone who said yes keeps counting, someone who said no or was never asked does not count.

## Non-goals

- The Settings toggle, the "What is sent" sheet, the ping code (`Analytics`, `OpenPing`, `OpenPingSender`) and the server endpoint stay. Removing the feature as a whole is a separate decision for later.
- No change to the About or onboarding wording: "If you allow it, the app counts how often it is opened" is still true.

## Anti-goals

- No one who has not turned the toggle on may be counted after this change.

## Constraints

- No emoji anywhere (project rule).
- Android and iPhone behave the same; the saved flag names stay (`analytics_opt_in`); the old `analytics_asked` flag is simply no longer read.

## Approaches

1. Skip the step but keep the page and strings for later: dead code.
2. **Remove the step, the page, its strings and the flag logic (chosen).** The feature's other parts stay and keep working.
3. Remove the whole feature now: bigger, and the user wants to think about it first.

## Design

- `FirstRunStage` loses `CONSENT`; `FirstRun.stage(onboardingDone, pickerSeen, hasTrailPoints)` loses the `consentAsked` argument.
- Delete `ConsentScreen`, `consentViewController`, the `consent_*` strings (EN and NL), the iPhone `consent` route, `ConsentHostView` and `answerConsent`, and the Android consent block.
- `Analytics.wasAsked/markAsked` and the iPhone `analytics_asked` writes go: nothing reads them any more.
- Docs: the store listing note, the workflow, and the old open-count spec get a short "changed" note.
