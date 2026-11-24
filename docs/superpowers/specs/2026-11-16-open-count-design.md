# Anonymous open count (opt-in)

Date: 2026-11-16. Status: design written, waiting for approval. Not built.

## Intent

The owner wants to know how often Misty Steps is opened, without learning anything about who opens it. People are asked once, in plain words, and
can change their mind at any time. The count goes to a small Cloudflare Worker with a dashboard only the owner can open.

Why this and nothing more: the app's promise is that trails never leave the phone, and the owner wants to keep it. A count of opens is enough to see
whether the app is used and which versions are in use. It cannot be turned into a profile, because there is nothing to link it to.

## Goals

- A person can allow or refuse the count in the onboarding and later on the stats page. It is **off until they say yes**.
- When it is on, each time the app starts the phone sends one tiny message: "opened, version 1.4.0, Android". Nothing else.
- The owner sees opens per day and per app version on a dashboard behind a login.
- The wording about privacy in the app stays true, in English and Dutch.

## Non-goals

- No identifier of any kind: no install ID, no daily ID, no advertising ID, no cookie. So no count of unique people, only of opens.
- No province downloads, no walk data, no location, no device model, no language, no country. (Province counts were mentioned first and set
  aside by the owner: "opens only". They can be added later as one more counter, with its own consent wording.)
- No third-party analytics service, no SDK, no crash reporting.
- iOS is not built here; the endpoint and the consent wording are the same when it comes.

## Anti-goals

- Anything that makes the count work for someone who said no, or before they were asked.
- Anything that lets the server tell two opens apart, or tell who sent one.
- A promise in the app that the code does not keep ("no tracking" stays only where it is still true).

## What the user sees

- **Onboarding, a fourth step** before the province picker (new installs): the title "Count opens, anonymously?" and the text "One tiny message each time
  you open the app: just that it was opened and which version. No name, no ID, no location, nothing about your walks. You can change this any time under
  More on the stats page." Buttons: "Yes, count my opens" and "No thanks". Both are equal in size and look; "No thanks" is not hidden.
- **Existing users** see the same step once, after the update, instead of the onboarding (as the picker works today).
- **Stats page, More:** a card "Usage counts" with a switch and a one-line "What is sent" that opens a small sheet showing the exact message.
- **Wording that changes** (English and Dutch): About says "No ads, no accounts, no tracking. Your trails never leave your phone." and the onboarding
  page "Yours alone" says "No account, no tracking." Both become "No ads, no accounts. Your trails never leave your phone. If you allow it, the app counts
  how often it is opened, with nothing that says who you are." (and the shorter form on the onboarding page). The stats-page privacy line gets a clause.

## The message and the server

`POST https://stats.potloodgum.com/v1/open` with `{"v": "1.4.0", "os": "android"}` and the app's user agent. No cookies, no other headers of ours.

The Worker `misty-stats`:

- Accepts only that shape: `v` matching `^\d+\.\d+\.\d+(-[a-z]+)?$`, `os` equal to `android` or `ios`, body under 200 bytes, `POST` only. Anything else
  gets a 400 and is not counted.
- Adds one to a counter row for (today's date in UTC, version, os) in a D1 table `opens(day, version, os, count)`. It stores no IP address, no
  request headers and no timestamp finer than the day. Workers logging is switched off.
- Answers `204`. The app does not retry and keeps no queue: a lost message is a lost count, which is fine for a count.
- `GET /dashboard` (behind Cloudflare Access, email login for the owner only) shows opens per day for the last 90 days as a small chart and a table
  per version. Pages and the chart are plain HTML and SVG served by the Worker; no external scripts or fonts.

**Abuse:** the endpoint is public and could be spammed to inflate the numbers. That costs nothing but accuracy, so it gets only light protection: the same
user-agent rule and a per-IP rate limit as the maps host, and the body checks above. The dashboard says "counts can be inflated by anyone who knows the address".

## App changes

- `AnalyticsSettings` (preference `analytics_opt_in`, default false; `analytics_asked`, default false).
- `OpenPing`: pure rules (`shouldPing(optedIn, lastPingAt, now)` sends at most once every 30 minutes, so a process killed and restarted does not count
  twice) and the payload builder; a small client posts it on a background thread with 5 s timeouts, no retry. Called once from `MistyApplication.onCreate`.
- `FirstRun` gains the consent step; `OnboardingScreen` and a new `ConsentScreen`; a "Usage counts" card on the stats page.
- Copy in `values/` and `values-nl/`.
- The manifest already has the internet permission. Release builds only: debug builds never send (so test runs do not pollute the count).

## Approaches considered

| | Chosen: own Worker and D1 | Cloudflare Web Analytics or a hosted tool | Workers Analytics Engine |
|---|---|---|---|
| Fits "opens only, no ID" | Yes: it stores one number per day and version | Built for web pages, tracks visitors by default | Yes, but queried through SQL tokens and harder to show on a page |
| Control over what is kept | Complete | Partial | Complete |
| Work | Small Worker plus a page | Least, but the wrong shape | Small, but a more awkward dashboard |

D1 is chosen because a counter table is all that is needed and the dashboard is one query.

## Constraints

- The Worker is deployed with the `cf` CLI (the project has no Wrangler configuration for it yet; follow whatever the CLI needs).
- Hostname `stats.potloodgum.com` on the `potloodgum.com` zone; Access protects `/dashboard*` only.
- Store listings need a data-safety line (Play) and F-Droid may add the Tracking anti-feature even for an opt-in count: check both before release.
- No emoji. English and Dutch for everything the user reads.

## Left to the builder

- The exact Dutch wording (the owner reads it), the chart's look, the D1 migration file, how Access is set up with the `cf` CLI or the dashboard.
- Whether the sheet "What is sent" is a dialog or a bottom sheet (follow the pin sheet).

## Risks to check first

1. Whether the Free plan's Access (up to 50 users) and D1 fit: they should.
2. That the Worker really stores nothing beyond the counter (read the deployed code and the D1 table after a test ping).
3. That a debug build and an opted-out release build send nothing (watch the network with the phone in airplane mode plus a packet check, or the Worker's count).

## Changed 2026-11-24

The consent question in the first-run flow was removed (the count is anonymous and the owner does not want to ask). The count itself is
unchanged: it stays off until someone turns on "Usage counts" in Settings (the You page), people who said yes before keep counting, and
the "What is sent" sheet and the ping stay. Whether to remove the whole feature (the app stores report installs) is left for later.
