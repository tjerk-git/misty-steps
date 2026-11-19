# Tester Sign-ups into Todododo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every sign-up on testers.potloodgum.com also lands in Todododo, where a Testers page lists, marks, exports and deletes them.

**Architecture:** The Cloudflare Worker keeps writing to D1, then pushes each sign-up to a token-locked public route on Todododo (same pattern as `GET /trmnl`). A failed push leaves `synced_at` null and an hourly cron re-pushes. Todododo stores rows in `tester_signups` and shows them on a `/testers` page behind the owner lock.

**Tech Stack:** Laravel 13 / PHP 8.5 / PHPUnit (`~/Projects/todododo`); SvelteKit 2 + Svelte 5 runes + Tailwind; Cloudflare Worker + D1 + `node --test` (`/Users/chairk/Projects/mistysteps/testers/site`), deployed with `cf`.

**Spec:** `docs/superpowers/specs/2026-11-19-tester-signups-to-todododo-design.md` (in the mistysteps repo)

## Global Constraints

- Todododo is a one-owner app: every route is behind `EnsureOwner` unless listed in `SecurityTest::PUBLIC_DOORS` with a reason. Do not widen `EXTENSION_TOKEN`, `ANDROID_TOKEN` or `TRMNL_TOKEN`.
- The new token is `TESTERS_TOKEN` (`config('services.testers_token')`), compared with `hash_equals`; unset or wrong token answers a plain 404.
- Public route: `POST /testers/signup`, `throttle:30,1`, CSRF-exempt (add `'testers/signup'` to the `preventRequestForgery(except: [...])` list in `bootstrap/app.php`).
- Table `tester_signups`: id, email (unique, lower-cased), platform (`android`|`ios`), newsletter bool, consent_version string, signed_up_at, added_at nullable; timestamps are unix seconds (app convention).
- Worker: visitor gets `{ok:true}` once D1 saved, whatever Todododo does. Push has a short timeout (5 s). D1 `signups` gains `synced_at TEXT`.
- No emoji anywhere (code, UI, docs). UI follows the app's notebook look and gentle tone: no percentages, no "missed" counts.
- Commit per task. Todododo commits go in `~/Projects/todododo`, Worker commits in `/Users/chairk/Projects/mistysteps`. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Email that starts with `=`, `+`, `-` or `@` must not become a formula in the CSV (prefix with `'`). Pinned in Task 2.
- A repeat sign-up with a changed newsletter choice (ticked then un-ticked) must store the new choice and must not reset `added_at`. Pinned in Task 1.
- Same address in different case or with spaces must be one row. Pinned in Task 1.
- Todododo returns 500 or times out: the visitor still gets ok, the row stays unsynced, the cron retries, and a retry never makes a duplicate. Pinned in Task 4.
- A body with wrong types (`newsletter: "yes"`, `platform` array, email 10 KB long) gets a 422 and writes nothing. Pinned in Task 1.

---

### Task 1: Todododo intake route and table

**Files:**
- Create: `database/migrations/2026_11_19_100000_create_tester_signups_table.php`
- Create: `app/Http/Controllers/TestersSignupController.php`
- Modify: `routes/web.php` (public route next to `/trmnl`), `bootstrap/app.php:37` (CSRF except list), `config/services.php` (`'testers_token' => env('TESTERS_TOKEN')`), `.env.example` (comment plus `TESTERS_TOKEN=`), `tests/Feature/SecurityTest.php` (`PUBLIC_DOORS`: `'POST testers/signup', // TESTERS_TOKEN is the lock; throttled, writes one sign-up row`)
- Test: `tests/Feature/TestersSignupTest.php`

**Interfaces:**
- Produces: `POST /testers/signup`, header `Authorization: Bearer <TESTERS_TOKEN>`, JSON `{email, platform, newsletter, consent_version, signed_up_at?}` (`signed_up_at` ISO-8601 string, optional, defaults to now). Answers 200 `{"ok":true}`, 404 on bad/missing token, 422 `{"error":"<field>"}` on invalid input. Table `tester_signups` as in Global Constraints.

- [ ] **Step 1: Write failing tests** in `TestersSignupTest` (`RefreshDatabase`, `config(['services.testers_token' => 'tok'])`, post with `withToken('tok')`):
  - `test_no_token_or_wrong_token_is_a_404_and_writes_nothing`; also unset config token is 404 even with an empty bearer.
  - `test_a_signup_is_stored_with_a_lower_cased_trimmed_email`: post `" Ann@Example.NL "`, `ios`, `newsletter true`, `consent_version "2026-11-18"`; assert one row, `email = ann@example.nl`.
  - `test_signing_up_again_updates_the_choices_and_keeps_added_at`: set `added_at` on the row, post again with `newsletter false` and `platform android`; assert one row, new values, `added_at` unchanged.
  - `test_invalid_bodies_are_422_and_write_nothing`: data provider with bad email, `platform: "windows"`, `platform: ["ios"]`, `newsletter: "yes"`, email of 300 chars, missing `consent_version`.
  - `test_the_route_is_throttled`: 31st request in a minute is 429.
  - `test_it_works_with_the_forgery_check_on`: set `app.env` to `local` like `AndroidForgeryTest` so the CSRF exemption is proven.
- [ ] **Step 2: Run** `php artisan test --filter=TestersSignupTest` -> FAIL (route missing).
- [ ] **Step 3: Implement** migration (columns per Global Constraints, `signed_up_at` and `added_at` unsigned big integers, `created_at`/`updated_at` unsigned big integers like `gratitude_entries`), `TestersSignupController::__invoke(Request): JsonResponse` modelled on `TrmnlController` (token check first, 404 otherwise), strict validation (boolean must be a real JSON boolean, platform must be a string), upsert by email with `DB::table('tester_signups')->updateOrInsert`, never touching `added_at` on update. Wire route, CSRF exception, config, `.env.example`, `PUBLIC_DOORS`.
- [ ] **Step 4: Run** `php artisan test --filter='TestersSignupTest|SecurityTest'` -> PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "Tester sign-ups: token-locked intake route and table"` (todododo repo).

### Task 2: Todododo owner API (list, mark, delete, CSV)

**Files:**
- Create: `app/Http/Controllers/Api/TesterController.php`
- Modify: `routes/api.php` (inside the `whereNumber('id')` group)
- Test: `tests/Feature/TesterApiTest.php`

**Interfaces:**
- Consumes: table `tester_signups` (Task 1).
- Produces (all behind `EnsureOwner`): `GET /api/testers` -> `{"items":[{id,email,platform,newsletter,consent_version,signed_up_at,added_at}],"counts":{"android":n,"ios":n,"newsletter":n,"waiting":n}}` newest first (`waiting` = `added_at` null); `PATCH /api/testers/{id}` body `{"added": true|false}` sets `added_at` to now or null, answers the same view as index; `DELETE /api/testers/{id}` answers the index view; `GET /api/testers/export.csv` -> `text/csv`, header `email,platform,newsletter`, one row per sign-up, `newsletter` is `yes`/`no`; optional `?newsletter=1` limits to newsletter consenters. Unknown id: 404.

- [ ] **Step 1: Write failing tests** (owner set up like `GratitudeTest`): signed-out is 401 on all four routes; index order and counts; patch sets then clears `added_at`; delete removes the row and 404s on an unknown id; CSV header and rows; `?newsletter=1` filter; `test_csv_cells_that_start_like_a_formula_are_defused`: an email `=cmd@x.nl` appears as `'=cmd@x.nl`.
- [ ] **Step 2: Run** `php artisan test --filter=TesterApiTest` -> FAIL.
- [ ] **Step 3: Implement** `TesterController` with `index`, `update`, `destroy`, `export`; a private `view(): array` shared by the first three; CSV streamed with `response()->streamDownload`, cells via `fputcsv` after the formula guard (`=`, `+`, `-`, `@`, tab, CR prefixes get `'`).
- [ ] **Step 4: Run** `php artisan test` (full suite, `SecurityTest` must stay green) -> PASS.
- [ ] **Step 5: Commit** "Tester sign-ups: owner API with CSV export".

### Task 3: Todododo Testers page

**Files:**
- Create: `src/lib/testers.ts` (pure helpers), `src/lib/testers.test.ts`, `src/lib/testers.svelte.ts` (store, modelled on `gratitude.svelte.ts`), `src/routes/testers/+page.svelte`, `src/routes/Testers.svelte`
- Modify: `src/lib/nav.ts` (entry `{ href: '/testers', label: 'Testers', icon: FlaskConical }`, behind the menu, no tab)

**Interfaces:**
- Consumes: the Task 2 API.
- Produces: `export interface Tester {id:number;email:string;platform:'android'|'ios';newsletter:boolean;consent_version:string;signed_up_at:number;added_at:number|null}`; `export function waitingFirst(rows: Tester[]): Tester[]` (not yet added first, each group newest first); store `testers` with `load()`, `toggleAdded(id)`, `remove(id)`, `items`, `counts`, `loaded`, `fault`.

- [ ] **Step 1: Write failing test** `testers.test.ts` for `waitingFirst` (mixed added/unadded, ordering within groups) using the runner of `src/lib/recipes.test.ts`.
- [ ] **Step 2: Run** that test -> FAIL.
- [ ] **Step 3: Implement** `waitingFirst`, the store (fetch `/api/testers`, PATCH, DELETE; faults shown with `Fault`), and the page: `PageHeader title="Testers"`, a line of counts per platform, newsletter and waiting (plain numbers, gentle tone), rows with platform tag, newsletter tag, a tick button "Added" (toggles), a delete button with confirm via the existing `Dialog`, an "Export CSV" link to `/api/testers/export.csv` plus a second "Newsletter only" link (`?newsletter=1`), and a standing note: "Deleting here does not delete the Cloudflare copy: run `cf d1 query ... DELETE` (see testers/README.md in Misty Steps)". Empty state: a quiet sentence, no zero-count shame.
- [ ] **Step 4: Run** `npm run check` and the unit test -> PASS; then `npm run build`, start `php artisan serve --port=8077`, open `/testers` locally (local mode is open) after inserting two rows through the intake route with curl and the dev token; verify list, tick, delete, CSV download by eye.
- [ ] **Step 5: Commit** "Tester sign-ups: Testers page".

### Task 4: Worker pushes to Todododo, with retry

**Files:**
- Create: `testers/site/src/push.ts`, `testers/site/test/push.test.ts`
- Modify: `testers/site/src/index.ts`, `testers/site/schema.sql`, `testers/site/cloudflare.config.ts` (and `wrangler.config.ts` if it is the one `cf deploy` reads: add an hourly cron trigger `0 * * * *`, check with `cf cli search` how triggers are declared), `testers/README.md`

**Interfaces:**
- Produces: `export async function pushSignup(f: typeof fetch, env: {TODODODO_URL: string; TODODODO_TOKEN: string}, row: {email:string;platform:string;newsletter:boolean;consent_version:string;created_at:string}): Promise<boolean>` -> POSTs JSON to `${TODODODO_URL}/testers/signup` with `Authorization: Bearer`, `signed_up_at = created_at`, 5 s `AbortSignal.timeout`; true only on HTTP 200, never throws. `export async function syncPending(f, env, db): Promise<number>` -> selects up to 50 rows `WHERE synced_at IS NULL`, pushes each, sets `synced_at` on success, returns how many synced. Worker exports `scheduled(event, env, ctx)` calling `syncPending`. Env gains `TODODODO_URL`, `TODODODO_TOKEN` (secret).

- [ ] **Step 1: Write failing tests** `push.test.ts` with a fake fetch and fake db: `pushSignup` sends the exact URL, header and body; returns false on 500, on a thrown fetch error and on timeout, never throws; `syncPending` marks only successful rows and returns the count; `test_a_retry_sends_the_same_email_so_todododo_upserts` (same body twice, identical payload).
- [ ] **Step 2: Run** `npm test` in `testers/site` -> FAIL.
- [ ] **Step 3: Implement** `push.ts`; in `index.ts` after the D1 upsert call `ctx.waitUntil(pushThenMark(...))` (push, then `UPDATE signups SET synced_at = ? WHERE email = ?` on success; a re-sign-up must clear `synced_at` in the upsert so the changed choice is pushed again); add `scheduled`. Update `schema.sql` (`synced_at TEXT`), README (secrets, cron, delete-on-request now two places, how to see unsynced rows).
- [ ] **Step 4: Run** `npm test` -> PASS.
- [ ] **Step 5: Commit** "Testers Worker: push sign-ups to Todododo, hourly retry" (mistysteps repo).

### Task 5: Ship and verify end to end

**Files:** none new. Needs the owner for steps 1 and 2.

- [ ] **Step 1: Make the token** (`php -r 'echo bin2hex(random_bytes(32));'`, show it to the owner once, never commit it). Owner sets `TESTERS_TOKEN` in the Ploi site environment for todo.potloodgum.com and deploys todododo (`git push`; Ploi deploy script per `workflow.md`).
- [ ] **Step 2: Worker secrets and schema.** `cf d1 query <misty-testers id> --sql="ALTER TABLE signups ADD COLUMN synced_at TEXT"`; set secrets `TODODODO_URL=https://todo.potloodgum.com` and `TODODODO_TOKEN` through the `cf` CLI (find the command with `cf cli search secret`); `cf deploy` in `testers/site`.
- [ ] **Step 3: Verify.** Submit the live form with a throwaway address; confirm it shows on `https://todo.potloodgum.com/testers` within seconds. Then break the token on purpose (wrong secret), submit again, confirm the visitor still sees success, the D1 row has `synced_at` null, fix the secret, trigger the cron (`cf` scheduled test, or wait for the hour) and confirm it appears once. Delete the throwaway rows in both places. Backfill: push the existing D1 rows by clearing `synced_at` (they are null already) and letting the cron run.
- [ ] **Step 4: Docs.** Add a line about the Testers page and `TESTERS_TOKEN` to Todododo `workflow.md` (Security section, next to the other side doors) and commit; update `testers/README.md` if reality differed from the plan; commit.
