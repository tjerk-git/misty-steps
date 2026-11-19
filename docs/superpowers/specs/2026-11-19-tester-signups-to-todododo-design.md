# Tester sign-ups flow into Todododo (design)

## Intent
testers.potloodgum.com collects an email, a platform and two consents. Today the rows sit in a Cloudflare D1
table that only `cf d1 query` can read. Todododo (Laravel, one-owner app) is where the owner already works, so
the sign-ups should land there, be readable on a page, exportable for the mailing provider, and deletable on request.

## Goals
- Every sign-up reaches Todododo within seconds, with email, platform, newsletter choice, consent version, time.
- A "Testers" page in Todododo lists them; mark "added to TestFlight / Play"; CSV export; delete one.
- The form never fails for the visitor because Todododo is down.

## Non-goals / anti-goals
- No open read access to the list; no widening of existing tokens.
- No todo per sign-up (the board is for decisions, not a log).
- Not replacing D1 yet; no mailing-provider integration (separate step).

## Approaches
1. **Worker forwards to a token-locked Laravel route (chosen).** Same pattern as TRMNL: public route, secret
   bearer token, `hash_equals`, 404 when wrong, throttled. D1 stays as safety copy and retry queue.
2. Laravel serves the form itself. Rejected: moves the page off Cloudflare and exposes the app's origin to the public form.
3. Owner pulls from D1 into Laravel by hand/cron. Rejected: delay and one more moving part for no gain.

## Design
- **Worker** (`testers/site/src/index.ts`): after the D1 upsert, `fetch(TODODODO_URL + /api/testers/signup)` with
  `Authorization: Bearer TODODODO_TOKEN` (Worker secret), short timeout, inside `ctx.waitUntil`. D1 gets a
  `synced_at` column; a failed push leaves it null. A scheduled trigger (hourly) re-pushes unsynced rows.
  Visitor always gets `{ok:true}` once D1 saved.
- **Laravel route** `POST /testers/signup` in `routes/web.php` outside `EnsureOwner`, `throttle:30,1`,
  `TestersSignupController`; token in `config('services.testers_token')` / `TESTERS_TOKEN`; unset or wrong = 404.
  Validates like the Worker (email, platform android|ios, booleans). Upsert by lower-cased email, so a retry or a
  changed choice never duplicates. Add to `SecurityTest::PUBLIC_DOORS` with the reason.
- **Table** `tester_signups`: id, email (unique), platform, newsletter bool, consent_version, signed_up_at,
  added_at nullable, timestamps.
- **Owner API** (behind `EnsureOwner`): `GET /api/testers` (list), `PATCH /api/testers/{id}` (added_at),
  `DELETE /api/testers/{id}`, `GET /api/testers/export.csv` (columns: email, platform, newsletter).
- **Svelte page** `/testers`, in the app's notebook look: counts per platform, rows with platform tag,
  newsletter tag, "added" tick, delete; "Export CSV" button. Linked from the nav like the other pages.
- **Tests**: token lock (missing/wrong/unset = 404), upsert idempotence, validation, throttle, owner-only list,
  CSV, delete; Worker unit test for the push and the failure path. Update README of `testers/`, Todododo `workflow.md`.
- **Privacy**: consent text version travels with each row; deleting in Todododo should also delete the D1 copy
  (owner runs the existing `cf d1` delete; noted in `testers/README.md`) - the page shows a reminder.

## Left to the builder
Nav placement, exact page layout, retry cadence details, migration name.
