# Magic eraser: design (2026-11-21)

Status: written for approval. Nothing is built.

## Intent

Misty Steps clears mist where you walk. The magic eraser is a hand tool for the completionists: you grab it, drag a finger over the map, and the mist comes off wherever you drag. It fixes the corner GPS missed, the indoor bits, the street you walked before installing the app, and it feeds the itch for a clean 100%.

It is a one-time purchase of EUR 4,99. The app, every province and every other feature stay free for good. The copy is quirky, and says plainly that buying it is also a donation to keep the app alive. This is for "the real OCD people", not a requirement for anyone.

## Goals

- A map tool that erases mist anywhere, with a finger, feeling like rubbing a foggy window.
- Erased ground counts like walked ground in the city, country and world percentages, so a completionist can reach a true 100%.
- Undo for the last 10 strokes of an eraser session.
- A one-time Google Play purchase, restored automatically on a new phone through the Play account.
- A small free taste before buying, so people feel the tool first.
- Built so the iPhone version can follow later with little rework: all rules and logic in the shared module, only gestures and purchasing per platform.

## Non-goals

- No licence server, no accounts, no receipts we store, no offline codes. This follows the 2026-11-18 decision.
- No change to what is free. No other unlocks, no supporter badges, no better characters.
- No leaderboards or sharing of erased progress.
- No payment outside Google Play Billing in the Play build.
- No eraser in the F-Droid build (it can't charge). The button is not shown there.
- No iPhone work in this project (see "Later: iPhone").

## Anti-goals (a failure even if it technically works)

- Erased ground leaking into "walked distance", the walk log, the trail dots, postcards or "new ground" of a walk. Those describe walks, and erasing is not walking.
- A slow or janky map. Many stroke spots must not make panning on a mid-range phone worse. Android fog drawing cost is a known sore point.
- Nagging. The paywall shows only when someone taps the eraser. Nothing else in the app mentions it unprompted.
- Accidental wipes: one swipe at low zoom must not erase a province.

## Constraints

- **The 2026-11-18 decision** (free forever, no unlocks, no supporter perks, tips only) is changed by one deliberate exception: a functional tool, not a badge or a perk. The tip jar stays separate and unchanged.
- **Payments:** the eraser needs Google Play Billing, which needs a Play listing. A new personal Play developer account must run a closed test with about 12 testers for 14 days before production release. Test purchases work earlier on the internal testing track. Until a listing exists, the tool can only be built and tested unlocked.
- **F-Droid:** the build there never shows or sells the eraser, and must not contain the billing library.
- **Data model:** fog is a set of circular spots (lat, lon, radius) in the `points` table, mostly tied to a walk through `session_id`. Spots with `session_id` NULL are old, pre-walk spots and are drawn as trail. Erased spots must be told apart from both.
- **Backups:** export and import must carry erased spots without breaking old files. An older app importing a newer file must still work.
- **Privacy:** nothing about erasing leaves the phone. The only network use is Google Play's own purchase flow.
- **Style:** no emoji anywhere (a standing rule). Dutch and English texts, the same quirky voice.

## Approaches considered

**Payments.**
1. *Play-only unlock* (chosen). Google Play Billing, one non-consumable product, a small `EraserAccess` switch in the shared module. The F-Droid build reports "not available".
2. *Own payment link plus a code, so F-Droid can pay too.* Rejected: Google Play forbids selling in-app features outside its billing, and the project rules out licence servers.

**Storing erased mist.**
1. *Extra marker on today's spots* (chosen). Erased mist becomes ordinary `points` rows with an `erase_stroke` column (NULL for walked spots, a stroke id otherwise). The fog mask, percentages and backup mostly work unchanged. Undo deletes a stroke's rows.
2. *Store strokes as paths and derive spots.* Tidier for undo and smaller on disk, but the stats and mask code would need changes for no real gain.

## What was decided

Their decisions: the tool works anywhere on the map; it counts in the percentages; undo "a bit"; payments approach 1; storage approach 1; the iPhone version comes later; price EUR 4,99; quirky copy that doubles as a donation message.

My calls, to check:
- Undo = the last 10 strokes, while the eraser is in hand. Putting the eraser down makes the strokes final.
- The tool is a round button on the map screen's button column. Tapped, it enters eraser mode: one finger erases, two fingers still pan and zoom, tapping the button again puts it down.
- The brush is finger sized on screen (about 28 dp radius). Its radius in metres follows the zoom and is capped at about 100 m. Below zoom 15 the eraser shows "zoom in a bit" and does nothing.
- Spots along a stroke are spaced about 0.6 times the brush radius apart, so the fog stays cheap to draw and a stroke looks continuous.
- Erased spots do not touch walked distance, the walk log, trail dots or postcards. They do feed the cleared-area and percentage stats. A walk's "new ground" counts ground earlier erased as already cleared.
- The free taste is one stroke session of at most 100 m of drag, once per install, stored in preferences. Undo works on it. After that the purchase sheet shows.
- Price and product id (`magic_eraser`) live in the Play Console, not in code. The sheet shows the price Play returns.
- Restoring: the Play purchase is checked on app start and the result cached, so the tool works offline once bought. Purchases are acknowledged within Play's time limit.
- Backup: the `erase_stroke` value is an optional field per point, so old files import as before. An older app importing a new file shows erased spots as old-style trail spots; this is accepted.

## Where the parts live

Shared module (written once, tested with the Kotlin tests, reused on iPhone):
- Turning a finger path into spots: spacing, radius cap, zoom limit.
- The stroke list and the 10-stroke undo.
- The access states: not available, locked, taste (with metres left), unlocked.
- The texts (Dutch and English).

Android only:
- The one-finger drawing layer over the map and its coexistence with MapLibre's gestures.
- The Play Billing code, in a Play-only source set.
- The database change: a new `points.erase_stroke` column (database version 5 to 6), with the trail loader and walk queries ignoring those rows.
- A debug-only switch to force the unlocked state, in the style of the existing `misty_fog_level` one.

## Later: iPhone

The shared parts above carry over. The iPhone version needs a drawing layer over the MapLibre view, a StoreKit non-consumable product, and the same database column in its store. It is out of scope here, but nothing in the Android design may make it harder.

## Left to the builder

- The exact look of the button and the purchase sheet; a first draft of the Dutch and English quirky copy (to be reviewed in the doc).
- How to hand a single-finger drag to the eraser while a second finger still reaches MapLibre for pan and zoom.
- How the in-memory spot index removes a stroke on undo (remove in place, or rebuild from the database).
- How to split the Gradle build so the Play build has billing and the F-Droid build has none, without disturbing `tools/release.sh`.
- Test plan: unit tests for stroke-to-spots, undo and the access states; a migration test; an emulator drag test using real-finger-style taps with small moves (see `bugs.md`), including the two-finger pan.

## Out of scope

Play listing set-up, the closed-test requirement, the Play data-safety form update, store copy for the listing, consumer-law wording for digital purchases, and the tip jar.
