# Santa Rat character and Christmas card: design

Status: approved by the user on 2026-11-24 (small change: the look of the sprite, the approach and the calls below).

## Intent and why

A new character for the next test build: a rat in a Christmas hat, with a small one-time "Merry Christmas" card that tells people
about it. It is a seasonal gift to the testers, in the app they already use.

## Goals

- A new character, `rat` ("Santa Rat"), drawn in the existing 16x16 sprite format, available in the picker on Android and iPhone.
- A small card over the map, shown once per install, with the rat's walking sprite, "Merry Christmas!", and two buttons: "Try the rat" (selects the rat) and "Not now".
- English and Dutch text, like the rest of the app.

## Non-goals

- No date logic: the card is shown once whenever it is first due, even after Christmas. The rat is permanent, not seasonal.
- No general "what's new" system. This is one card with one flag.
- No change to the first-run flow: new installs see onboarding first; the card comes after it.

## Anti-goals

- The card must never appear on top of onboarding, the consent question or the province picker.
- "Not now" and tapping outside must leave the user's character as it was.

## Constraints

- No emoji anywhere (project rule), including the card text.
- Characters are data in `app/src/main/assets/characters.txt`, shared by both apps; names and blurbs come from `shared/strings` (`character_<id>_name`, `character_<id>_blurb`, English and `values-nl`).
- Shared Compose content: Android draws it directly, the iPhone hosts it as a transparent overlay, the same way as the pin sheets.
- Saved flag: `rat_xmas_2026_seen` (Android `SharedPreferences` "misty", iPhone `UserDefaults`).

## Approaches

1. Full-screen page like onboarding: one shared design, but too heavy for a small announcement.
2. **Small shared card over the map (chosen):** one design for both platforms, matches the pin-sheet pattern.
3. Native card per platform: the same design written twice, strings duplicated.

## Design

- Sprite: the `[rat]` entry already drawn (grey rat, red Santa hat with white band and pom-pom, round ear, pink hooked tail), approved by the user.
- Rule in shared code: `RatAnnouncement.due(stage, seen)` is true only when the first-run stage is `DONE` and the flag is not set.
- Card: `RatCard(onTry, onLater)` in shared UI. Title "Merry Christmas!", body one or two short sentences, the rat walking (same ticker and sprite drawing as the picker), buttons "Try the rat" and "Not now".
- Both buttons and a tap outside set the flag; "Try the rat" also selects the rat first.
- Android: shown from `MistyScreen` when due. iPhone: `AppModel`/`ContentView` overlay, plus a simulator launch hook `-ratcard 1` to force it.

## Left to the builder

Exact wording of the body text (short, warm, English and Dutch), card size and spacing (match the pin sheets), and where the Dutch name "Kerstrat" and blurb read best.
