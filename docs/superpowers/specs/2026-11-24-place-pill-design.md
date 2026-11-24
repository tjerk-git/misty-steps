# The top pill shows how much of your place you have cleared: design

Status: approved by the user on 2026-11-24 (small change; "city or town depending on where you are"; and the amount is told in buckets of mist: "buckets, that's funnier").

## Intent and why

The pill at the top of the map says "Misty Steps" and "64,1 km of mist cleared", which is total distance walked, not progress anywhere. The owner wants only the numbers, in clear type: how much of the place you are in you have cleared.

## Goals

- The pill shows one big number and a small caption, no app name: the mist you cleared in your city or town, told in buckets, for example "7.800.000" over "buckets of mist cleared in Leeuwarden (0,066%)". One bucket is 10 litres; the mist is taken as 2 m deep (head height), so one square metre of ground is 200 buckets.
- The place is your city or town (the municipality you are standing in), worked out on the phone from the bundled borders.
- Same on Android and iPhone, English and Dutch; tapping the pill still opens the stats page.

## Non-goals

- No neighbourhood level (its data is no longer fetched).
- No change to the stats page or how areas are counted.
- No new permissions or network use.

## Anti-goals

- The percentage in the caption must never read "0%" for a real walk because of rounding: tiny shares use the stats page's "1 in N" style.
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
- `MistBuckets` (shared): `count(areaM2)` = area x 200, rounded; `readable(count)` keeps two significant digits from 100 up ("7.800.000").
- `pillContent(stats)` (shared, suspend): `PillContent(value, caption)`: the buckets cleared in your city or town (digits grouped for the language) with the caption "buckets of mist cleared in Leeuwarden (0,066%)" / "emmers mist opgeklaard in Leeuwarden (0,066%)"; outside a known place or without a fix, the buckets for the total area cleared with "buckets of mist cleared" / "emmers mist opgeklaard".
- Android: a loop in `MistyScreen` that asks `AndroidStatsSource.compute(here)` every 10 s while the map is visible and uncovered; iPhone: a small Swift model with a timer that calls a shared `PillSource.refresh(lat, lon)` and publishes the result.
- Typography: the number in the serif face at about 26 sp/pt, the caption small in the quiet colour.

## Left to the builder

Exact sizes and padding, and the refresh interval constant.
