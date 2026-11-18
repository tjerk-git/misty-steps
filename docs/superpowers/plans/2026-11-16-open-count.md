# Opt-in anonymous open count Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** People are asked once whether the app may count how often it is opened, anonymously; if they agree, each app start sends one tiny message to a Cloudflare Worker that keeps a counter per day and version, shown on a dashboard only the owner can open.

**Architecture:** Pure Kotlin rules decide when a ping is sent and what it contains; a small client sends it on a background thread. A new consent step joins the first-run flow and a "Usage counts" card on the stats page lets people change their mind. A Worker (`stats/worker/`, set up like `fdroid/site` and deployed with the `cf` CLI) validates the message, adds one to a D1 counter and serves a dashboard behind Cloudflare Access.

**Tech Stack:** Kotlin, Jetpack Compose, `HttpURLConnection`; TypeScript Worker, D1, Cloudflare Access; `node:test` for the Worker's tests.

**Spec:** `docs/superpowers/specs/2026-11-16-open-count-design.md`

## Global Constraints

- Off until the person says yes. Debug builds never send (`BuildConfig.DEBUG`).
- The message is exactly `{"v": "<versionName>", "os": "android"}` sent as `POST https://stats.potloodgum.com/v1/open` with the app's user agent `<applicationId>/<versionName>`; no other body fields or headers of ours, no cookies, no retry, no queue.
- At most one ping per 30 minutes (`MIN_GAP_MS = 30 * 60 * 1000`), at most one per app start, 5 s connect and read timeouts, on a background thread.
- Worker: `v` must match `^\d+\.\d+\.\d+(-[a-z]+)?$`, `os` is `android` or `ios`, body under 200 bytes, `POST` only, otherwise 400 and nothing counted; answers 204; stores only (UTC day, version, os, count); logs nothing; no IP address, header or finer timestamp is stored.
- Dashboard at `/dashboard` only, behind Cloudflare Access (owner email); the ingest path `/v1/open` stays public.
- English and Dutch copy for everything a person reads. No emoji. The wording about privacy must stay true.
- Cloudflare work uses the `cf` CLI, following the pattern in `fdroid/site` (`cloudflare.config.ts`, `wrangler.config.ts`). Show each command that creates or changes a Cloudflare resource before running it.

## Review Focus

- A person who declines, or never answers, is never pinged, also after a restart and after the update (Tasks 2 and 3).
- Two starts within 30 minutes (a killed and restarted process) send one ping (Task 1).
- Starting the app with no connection does not crash, queue or retry (Task 2).
- An existing user after the update sees only the consent step, once, and a new install sees onboarding, then consent, then the picker; rotating the phone or being killed on the consent screen keeps the stage (Tasks 3 and 4).
- Switching the card off stops pings at once, and switching it on does not send a ping by itself (Task 5).
- The Worker rejects wrong methods, oversized bodies, bad versions and unknown platforms, and the D1 table holds nothing but the counter columns (Task 6).
- `/dashboard` cannot be opened without logging in (Task 6).

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/mistysteps/app/stats/OpenPing.kt` | Pure rules and payload for the ping |
| `.../stats/Analytics.kt` | The opt-in preference, the background client, the call at app start |
| `.../maps/FirstRun.kt` | First-run stages including the consent step (changed) |
| `.../ui/ConsentScreen.kt`, `ui/UsageCountsCard.kt` | The consent step and the stats-page card with "What is sent" |
| `app/src/main/res/values/strings.xml`, `values-nl/strings.xml` | Copy |
| `stats/worker/src/index.ts`, `src/open.ts`, `src/dashboard.ts`, `schema.sql`, `test/*.test.ts` | The Worker |

---

### Task 1: Ping rules and payload

**Files:** Create `app/src/main/java/com/mistysteps/app/stats/OpenPing.kt`, test `app/src/test/java/com/mistysteps/app/stats/OpenPingTest.kt`

**Interfaces:**
- Produces: `object OpenPing { const val MIN_GAP_MS = 30L * 60 * 1000; fun shouldPing(optedIn: Boolean, release: Boolean, lastPingAt: Long, now: Long): Boolean; fun payload(versionName: String): String; const val URL = "https://stats.potloodgum.com/v1/open" }`. `payload` returns exactly `{"v":"1.4.0","os":"android"}`.

- [ ] **Step 1: Write failing tests:** `notOptedInNeverPings`; `debugBuildsNeverPing`; `firstPingAfterOptInIsSent` (lastPingAt 0); `aSecondStartWithinThirtyMinutesIsNotSent` (gap 29 min); `aStartAfterThirtyMinutesIsSent` (gap exactly 30 min is sent); `clockGoingBackwardsDoesNotSendTwice` (now < lastPingAt is not sent); `payloadIsExactlyVersionAndPlatform` (`{"v":"1.4.0","os":"android"}`); `payloadEscapesNothingItShouldNotNeed` (a version with a quote or backslash throws `IllegalArgumentException`).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.mistysteps.app.stats.OpenPingTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `OpenPing` (version must match the Worker's regex, else throw).
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add the open-ping rules"`.

---

### Task 2: Preference, client and the call at app start

**Files:** Create `.../stats/Analytics.kt`; modify `MistyApplication.kt`

**Interfaces:**
- Produces: `object Analytics { fun isOptedIn(context: Context): Boolean; fun setOptedIn(context: Context, value: Boolean); fun wasAsked(context: Context): Boolean; fun markAsked(context: Context); fun pingIfAllowed(context: Context) }` over the `misty` preferences (`analytics_opt_in` default false, `analytics_asked` default false, `analytics_last_ping` default 0). `pingIfAllowed` checks `OpenPing.shouldPing(...)`, writes `analytics_last_ping = now` before sending, and posts on a `Thread`; any exception is swallowed.
- Consumes: `OpenPing`, `BuildConfig.DEBUG`, `BuildConfig.VERSION_NAME`, `BuildConfig.APPLICATION_ID`.

- [ ] **Step 1: Implement `Analytics`** (no unit test: it is thin glue over Android preferences and the network; its decisions are tested in Task 1). The request has `Content-Type: application/json`, the user agent, 5 s timeouts, and reads nothing but the status code.
- [ ] **Step 2: Call `Analytics.pingIfAllowed(this)`** at the end of `MistyApplication.onCreate`.
- [ ] **Step 3: Build** `./gradlew assembleDebug assembleRelease testDebugUnitTest`. Expected: success.
- [ ] **Step 4: Commit** `git commit -m "Send the open ping when allowed"`.

---

### Task 3: First-run stages with a consent step

**Files:** Modify `app/src/main/java/com/mistysteps/app/maps/FirstRun.kt`, `FirstRunTest.kt`, and `ui/MistyScreen.kt`

**Interfaces:**
- Produces: `enum class FirstRunStage { ONBOARDING, CONSENT, PICKER, DONE }` and `FirstRun.stage(onboardingDone: Boolean, consentAsked: Boolean, pickerSeen: Boolean, hasTrailPoints: Boolean): FirstRunStage` returning the first stage still due: ONBOARDING when `!onboardingDone && !pickerSeen && !hasTrailPoints`; then CONSENT when `!consentAsked`; then PICKER when `!pickerSeen`; else DONE. The old `FirstRunScreen` and `decide` are removed.

- [ ] **Step 1: Rewrite `FirstRunTest`** with: new install is ONBOARDING; after onboarding it is CONSENT; after consent PICKER; after the picker DONE; an existing user (trail points, picker seen, consent not asked) is CONSENT only; an existing user who never saw the picker is CONSENT then PICKER; everything done is DONE; a new install that skipped onboarding is not shown it again (`onboardingDone` true).
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement `stage`** and change `MistyScreen` to use it: the saved stage string becomes `FirstRunStage.name`; after finishing a step set that step's flag in the `misty` preferences and call `FirstRun.stage(...)` again; `fresh` (debug start option) ignores all three flags. Show a placeholder `ConsentScreen` for CONSENT until Task 4 (a screen with one Continue button that marks the step asked without opting in).
- [ ] **Step 4: Run the tests and `./gradlew assembleDebug`.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add a consent stage to the first-run flow"`.

---

### Task 4: The consent screen

**Files:** Create `ui/ConsentScreen.kt`; modify `MistyScreen.kt`, both strings files.

**Interfaces:** `@Composable fun ConsentScreen(onAnswer: (allowed: Boolean) -> Unit)`: the page chrome of `OnboardingScreen`, a title, the explanation, and two buttons of equal size and weight. Answering calls `Analytics.setOptedIn` and `Analytics.markAsked`, then `MistyScreen` moves to the next stage. Back counts as "No thanks".

Strings (key: English / Dutch):
- `consent_title`: Count opens, anonymously? / Anoniem tellen hoe vaak de app opent?
- `consent_body`: One tiny message each time you open the app: just that it was opened and which version. No name, no ID, no location, nothing about your walks. You can change this any time under More on the stats page. / Een klein bericht elke keer dat je de app opent: alleen dat hij is geopend en welke versie. Geen naam, geen ID, geen locatie, niets over je wandelingen. Je kunt dit altijd aanpassen onder Meer op de statistiekenpagina.
- `consent_yes`: Yes, count my opens / Ja, tel mijn keren openen
- `consent_no`: No thanks / Nee, bedankt

- [ ] **Step 1: Add the strings** and check key parity between `values/` and `values-nl/` with the script from plan 3.
- [ ] **Step 2: Implement `ConsentScreen`** and replace the Task 3 placeholder. State survives rotation (`rememberSaveable` for the stage already; the screen itself has no state).
- [ ] **Step 3: Build and check on the Pixel** with `--ez fresh true`: onboarding, then the consent step, then the picker; answer each way and check `analytics_opt_in` and `analytics_asked` with `adb shell run-as <debug id> cat shared_prefs/misty.xml`. Expected: Yes sets opt_in true, No leaves it false, both set asked.
- [ ] **Step 4: Commit** `git commit -m "Ask for consent before the province picker"`.

---

### Task 5: "Usage counts" card, the sheet and the copy changes

**Files:** Create `ui/UsageCountsCard.kt`; modify `ui/StatsScreen.kt` (in More, between the backup card and "Made by"), `ui/AboutScreen.kt` (no code change; its text is in strings), both strings files.

**Interfaces:** `@Composable fun UsageCountsCard()`: a card "Usage counts" with a short description, a `Switch` bound to `Analytics.isOptedIn` / `setOptedIn` (it never sends a ping by itself), and a text button "What is sent" that opens an `AlertDialog` showing the exact message (`OpenPing.payload(BuildConfig.VERSION_NAME)`) and "Nothing else is sent: no ID, no location, no walks."

Copy changes (English / Dutch):
- `about_promise`: No ads, no accounts. Your trails never leave your phone. If you allow it, the app counts how often it is opened, with nothing that says who you are. / Geen advertenties, geen accounts. Je routes verlaten je telefoon nooit. Als je dat toestaat, telt de app hoe vaak hij wordt geopend, zonder iets dat zegt wie je bent.
- `onboarding_3_body`: No account. Your trails never leave your phone. / Geen account. Je routes verlaten je telefoon nooit.
- `stats_privacy`: add at the end: "If you allow it, the app also counts how often it is opened, anonymously." / "Als je dat toestaat, telt de app ook anoniem hoe vaak hij wordt geopend."
- New: `usage_title` Usage counts / Gebruikstellingen; `usage_body` Count how often the app is opened, anonymously. / Tel anoniem hoe vaak de app wordt geopend.; `usage_what` What is sent / Wat wordt verstuurd; `usage_sent_note` Nothing else is sent: no ID, no location, no walks. / Er wordt niets anders verstuurd: geen ID, geen locatie, geen wandelingen.

- [ ] **Step 1: Add and change the strings; run the key parity check.**
- [ ] **Step 2: Implement the card and the dialog** and place it on the stats page.
- [ ] **Step 3: Build and check on the Pixel:** the card shows in More; the switch follows the preference; the dialog shows the exact message; screenshots in English and Dutch.
- [ ] **Step 4: Commit** `git commit -m "Add the Usage counts card and reword the privacy text"`.

---

### Task 6: The Worker, D1 and the dashboard

**Files:** Create `stats/worker/` (`package.json`, `cloudflare.config.ts`, `wrangler.config.ts` following `fdroid/site`; `src/index.ts`, `src/open.ts`, `src/dashboard.ts`; `schema.sql`; `test/open.test.ts`, `test/dashboard.test.ts`).

**Interfaces (TypeScript):**
- `export function parseOpen(method: string, contentLength: number | null, body: string): { version: string; os: "android" | "ios" } | { error: string }`: the validation rules in Global Constraints.
- `export function dayOf(date: Date): string` (UTC `YYYY-MM-DD`).
- `export async function recordOpen(db: D1Database, version: string, os: string, day: string): Promise<void>`: `INSERT INTO opens(day, version, os, count) VALUES (?, ?, ?, 1) ON CONFLICT(day, version, os) DO UPDATE SET count = count + 1`.
- `export function renderDashboard(rows: { day: string; version: string; os: string; count: number }[], today: string): string`: HTML with an inline SVG bar chart of opens per day for the last 90 days (missing days are zero) and a table of opens per version; no scripts, no external requests; the note "counts can be inflated by anyone who knows the address".
- `schema.sql`: `CREATE TABLE opens (day TEXT NOT NULL, version TEXT NOT NULL, os TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY (day, version, os));`.
- Routes in `index.ts`: `POST /v1/open` (204 or 400), `GET /dashboard` (HTML), everything else 404; observability and logs off in the config.

- [ ] **Step 1: Write failing tests** (`node --test`): `parseOpen` accepts `{"v":"1.4.0","os":"android"}` and `1.4.0-beta`; rejects `GET`, a 201-byte body, bad JSON, extra fields, `v` of `"1.4"`, `"1.4.0; DROP"`, `os` of `"windows"`; `dayOf` returns the UTC day around midnight; `renderDashboard` shows zero for days without rows, sums two rows of one day, contains no `<script`, no `http` link and the inflation note, and escapes a version containing `<`.
- [ ] **Step 2: Run** `node --test` in `stats/worker`. Expected: FAIL.
- [ ] **Step 3: Implement** the functions and the routes.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Create the database and deploy** (show each command first): `cf d1 create misty-stats`, apply `schema.sql` with `cf d1 migrations` or `cf d1 query`, add the route `stats.potloodgum.com/*` for the Worker, `cf deploy`. Then check: `curl -s -o /dev/null -w '%{http_code}' -X POST -A 'com.potloodgum.mistysteps/1.4.0' -d '{"v":"1.4.0","os":"android"}' https://stats.potloodgum.com/v1/open` returns 204; a wrong body returns 400; a `GET /v1/open` returns 404 or 405.
- [ ] **Step 6: Protect the dashboard.** In the Cloudflare dashboard (Zero Trust, Access) the owner adds a self-hosted application for `stats.potloodgum.com/dashboard*` with a policy "emails equal the owner's address" and one-time PIN login (the `cf` CLI may not cover Access; give the owner the exact clicks and verify afterwards). Check: `curl -sI https://stats.potloodgum.com/dashboard` gives a redirect to the login, not the page.
- [ ] **Step 7: Read back what is stored:** `cf d1 query misty-stats "SELECT * FROM opens"` shows only (day, version, os, count) rows; the Worker code has no logging calls.
- [ ] **Step 8: Commit** `git add stats && git commit -m "Add the open-count Worker and dashboard"`.

---

### Task 7: End to end on the phone

- [ ] **Step 1: Debug build sends nothing:** with the Dev app opted in (via the card), start it a few times and check the D1 counter does not change.
- [ ] **Step 2: Release build:** install the release APK (`com.potloodgum.mistysteps`, a separate install from the Dev app, signed as usual), opt in on the consent screen, close and start the app: the counter for today and this version goes up by one, a second start within 30 minutes does not change it, and the dashboard (after logging in) shows the number.
- [ ] **Step 3: Opt out:** switch the card off, start the app after waiting 30 minutes or clearing `analytics_last_ping`: no change in the counter.
- [ ] **Step 4: No connection:** airplane mode, start the app: no crash and nothing queued (switch the network back on and start again after 30 minutes: one new count only).
- [ ] **Step 5: Write the results** into `docs/superpowers/specs/2026-11-16-open-count-design.md` under a "Checks" heading, with what was and was not tested, and commit.
