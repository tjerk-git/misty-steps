# The top pill shows how much of your place you have cleared: design

Status: approved by the user on 2026-11-24 (small change; "city or town depending on where you are").

## Intent and why

The pill at the top of the map says "Misty Steps" and "64,1 km of mist cleared", which is total distance walked, not progress anywhere. The owner wants only the numbers, in clear type: how much of the place you are in you have cleared.

## Goals

- The pill shows one big number and a small caption, no app name: for example "0,066%" over "of Leeuwarden cleared".
- The place is your city or town (the municipality you are standing in), worked out on the phone from the bundled borders.
- Same on Android and iPhone, English and Dutch; tapping the pill still opens the stats page.

## Non-goals

- No neighbourhood level (its data is no longer fetched).
- No change to the stats page or how areas are counted.
- No new permissions or network use.

## Anti-goals

- A real walk must never read "0%" because of rounding: tiny shares use the stats page's "1 in N" style.
- The number must not flicker or jump to a placeholder while it is recounted.

## Constraints

- No emoji anywhere (project rule).
- The counting is the existing `StatsSource.compute(lat, lon)` (shared interface; Android `AndroidStatsSource`, iPhone `LocalStatsSource`), run off the main thread.
- Texts in `shared/strings` (English and `values-nl`).

## Approaches

1. Recount on every GPS point: too heavy while walking.
2. **Recount when the app opens and every 10 seconds while the map is visible, keeping the old number until the new one is ready (chosen).**
3. Cache the number in the walk store: more moving parts for no visible gain.

## Design

- Pure rule in shared code: `PillPick.place(stats)`: the city (or town) if its share is known, else the country if known, else nothing (then the pill shows the total area cleared).
- `pillContent(stats)` (shared, suspend): `PillContent(value, caption)`: a percentage from `formatShare` ("0,066%", or "1 in 32,000,000" for tiny shares) with the caption "of Leeuwarden cleared" / "van Leeuwarden opgeklaard"; or the total area ("4,24 km2") with "of mist cleared" / "mist opgeklaard".
- Android: a loop in `MistyScreen` that asks `AndroidStatsSource.compute(here)` every 10 s while the map is visible and uncovered; iPhone: a small Swift model with a timer that calls a shared `PillSource.refresh(lat, lon)` and publishes the result.
- Typography: the number in the serif face at about 26 sp/pt, the caption small in the quiet colour.

## Left to the builder

Exact sizes and padding, and the refresh interval constant.
