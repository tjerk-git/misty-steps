# Pins: leave a note in the places you find

Date: 2026-11-09. Status: design approved, not built.

## Why

Misty Steps wants people to go for short strolls through parts of their own town they have never seen.
Pins add savouring to that: while walking you mark what caught your eye ("great place to sit", "a crow
lives here"), and later you find those moments again on the map, on the walk's postcard and in a list.
The app helps by offering ready-made labels, so leaving a pin takes one tap and no typing.

Stroll reminders and onboarding ("Would you like a reminder for a stroll? After dinner / on Sunday /
whenever the weather is nice") are a separate design, done after this one.

## What the user gets

- While walking, a "Drop a pin" button. Tap it, pick a label, optionally add a short note, place it.
- Long-press anywhere on ground you have already cleared to add a pin you forgot. Not in the fog: you
  have to have been there.
- Pins show as small pixel icons on the map, on the walk postcard, in the walk log and in a "Your places"
  list on the stats page.
- Tap a pin to read it, edit its label or note, or delete it.
- Pins stay on the phone and are part of export/import. Nothing is sent anywhere.

## Labels

Twelve fixed labels in four groups, plus "Something else" (13 in all). Each has a stable key (stored in the
database), a text and its own 16x16 pixel icon. No emoji anywhere, in the app or in the icons.

| Group   | Key        | Text                 |
|---------|------------|----------------------|
| Rest    | `sit`      | Great place to sit   |
| Rest    | `view`     | Nice view            |
| Rest    | `shelter`  | Shelter from rain    |
| Senses  | `quiet`    | Real quiet           |
| Senses  | `smell`    | Smells lovely        |
| Senses  | `birdsong` | Birds sing here      |
| Animals | `crow`     | A crow lives here    |
| Animals | `cat`      | Friendly cat         |
| Animals | `ducks`    | Ducks and geese      |
| Finds   | `tree`     | Special tree         |
| Finds   | `building` | Beautiful building   |
| Finds   | `path`     | Hidden path          |
| (none)  | `other`    | Something else       |

Users can't make their own labels; the note covers what the list misses. Texts go in
`res/values/strings.xml`.

## Data and storage

New table in `TrailDatabase`, database version 4 to 5. The upgrade only creates the table; `points` and
`sessions` are untouched.

```sql
CREATE TABLE pins (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    lat        REAL NOT NULL,
    lon        REAL NOT NULL,
    time       INTEGER NOT NULL,  -- epoch millis when placed
    label      TEXT NOT NULL,     -- PinLabel key
    note       TEXT,              -- trimmed, max 80 characters, NULL when empty
    session_id INTEGER            -- walk it was placed on; NULL when placed by long-press
)
```

- `PinLabel` is an enum in code: key, group, text resource, sprite. Only the key is stored, so renaming
  or translating a text never breaks old pins.
- A key the app doesn't know (from a backup made by a newer version) is kept as is in the database and
  shown as "Something else". A later version that knows the key shows it properly.
- Long-press pins have no walk, so they don't appear in a walk's log row or postcard. They do appear on
  the map and in "Your places".
- Walks can't be deleted today. If that is added later, their pins stay and lose the walk link.
- `PinStore` (new, next to `FogStore`): add, edit, delete, list all, list per walk, list in an area. It
  keeps all pins in memory so drawing never waits on the database.

### Export and import

`Backup` gets a `pins` array:

```json
"pins": [ { "lat": 53.2, "lon": 5.8, "time": 1762700000000, "label": "crow", "note": "on the church", "sessionId": 1762699000000 } ]
```

`note` and `sessionId` may be null. Files without `pins` import as before. On import, a pin with the same
`time`, `lat` and `lon` as an existing pin is skipped, so importing the same file twice doesn't double pins.

## Placing a pin

### During a walk

- A round "Drop a pin" button, shown only while recording, on the right side above the location button
  (mirroring the character and stats buttons on the left; `ui/MistyScreen.kt`).
- The pin's position is the last good GPS fix (accuracy 35 m or better, the same filter as recording) at
  the moment the button is tapped, so typing a note while walking on doesn't move the pin.
- No good fix in the last 30 s: show "Looking for you in the mist…" and don't open the sheet.
- The pin is linked to the current walk.

### Long-press on the map

- Works whether recording or not.
- If the pressed spot is cleared (inside a cleared circle in the `PointIndex`), the same sheet opens and
  the pin goes exactly where you pressed, with no walk link.
- If the spot is still in fog: show "Walk here first to leave a pin" and open nothing.

### The sheet

A Compose bottom sheet:

```
Leave a pin
  Rest      [sit]    [view]   [shelter]
  Senses    [quiet]  [smell]  [birdsong]
  Animals   [crow]   [cat]    [ducks]
  Finds     [tree]   [building] [path]
            [something else]

  Note (optional)  "the bench by the red door"     0/80
                                         [ Place pin ]
```

- Each tile is the pixel icon with the label text under it. Tapping selects it.
- "Place pin" is disabled until a label is picked. With "Something else" the note is required.
- On placing, the sheet closes and the icon drops onto the map with a small bounce.

### Reading, editing and deleting

- Tapping within about 24 dp of a pin icon opens a small sheet: icon, label, note, date, and "on your
  walk of 3 Nov" when it has a walk.
- Edit changes the label or note (same tiles and note field). Delete asks "Remove this pin?" first.
- Pins can't be moved; delete and place again.

## Showing pins

### Map

- `PinOverlay` (new, an osmdroid `Overlay` like `CharacterOverlay`): drawn above the fog and the trail,
  below the character.
- Icons are 16x16 pixel sprites drawn at 2 dp per pixel (32 dp) with a small shadow, crisp (no filtering),
  like the character.
- At zoom 14 and lower, a pin shrinks to a small dot coloured by its group ("Something else" uses
  `Dusk`). Below zoom 10 pins are hidden.
- A pin dropped during a walk goes where you stand even if that spot isn't cleared yet (for example
  while clearing is paused at vehicle speed): you were there.
- Only pins on screen are drawn. When icons overlap, a tap picks the closest.

### Icons

The 13 sprites live in `pin/PinSprites.kt`, as sprite data in the same format and palette as
`character/Characters.kt`, so they are edited the same way ("Editing characters" in workflow.md).

### Postcard

`PostcardRenderer` draws the pins placed on that walk at their spots, at postcard pixel scale. Long-press
pins (no walk) are not on any postcard.

### Walk log

Each walk row on the stats page shows up to 4 small icons of its pins, then "+N" when there are more.
Walks without pins look as they do today.

### "Your places"

A card on the stats page, below the walk log:

- Grouped by label with a count: "Great place to sit (3)". Tap a group to open it.
- Each row: icon, the note (or the label text when there is no note), date.
- Tapping a row closes the stats page, flies the map to the pin at zoom 17 and opens its sheet.
- With no pins yet: "Leave a pin when something catches your eye." No targets or percentages.

## Errors and edge cases

- Database upgrade 4 to 5 only adds a table. Install over the current build (don't uninstall): the fog
  must survive.
- Note: trimmed, cut at 80 characters, empty stored as NULL.
- Unknown label key: kept, shown as "Something else".
- App killed while the sheet is open: the pin is not saved. Acceptable.
- Many pins: all in memory, only visible ones drawn. Thousands are still small.

## Code layout

| Piece | File |
|---|---|
| Labels, groups, keys | `pin/PinLabel.kt` |
| Pure rules: note checks, key parsing, "is this spot cleared?", import dedupe | `pin/PinRules.kt` |
| Pixel icons | `pin/PinSprites.kt` |
| Storage | `data/TrailDatabase.kt` (table, upgrade), `data/PinStore.kt` |
| Export/import | `data/Backup.kt` |
| Map drawing and taps | `pin/PinOverlay.kt`, `ui/MapController.kt` (long-press) |
| Sheets | `ui/PinSheet.kt` |
| Drop button | `ui/MistyScreen.kt` |
| Walk log icons, "Your places" | `ui/StatsScreen.kt` |
| Postcard | `postcard/PostcardRenderer.kt` |

## Testing

Unit tests (plain JUnit, like the existing ones in `app/src/test`; no device needed):

- `PinLabel` key parsing; an unknown key maps to "Something else" but keeps its key.
- Note checks: trim, 80-character cut, required for `other`, empty becomes null.
- "Is this spot cleared?" against a `PointIndex`: inside a circle, just outside, empty index.
- Import dedupe: same time and position skipped, different kept.
- `Backup` round trip with pins; an old backup without `pins` still parses.

On the phone (added to workflow.md as a checklist):

- [ ] Install over the current build: old fog still there.
- [ ] Walk, drop a pin with a label and a note: it appears where you stood.
- [ ] Long-press on cleared ground: sheet opens, pin lands there. Long-press in fog: "Walk here first to
      leave a pin".
- [ ] Tap a pin: read, edit label and note, delete.
- [ ] Zoom out past 14: pins turn into dots; past 10: gone.
- [ ] Postcard of the walk shows its pins; the walk log row shows the icons.
- [ ] "Your places": groups and counts right; tapping a row flies to the pin.
- [ ] Export, wipe the app's data, import: pins come back. Import again: no doubles.

## Out of scope

- Photos on pins.
- Own labels.
- Moving pins.
- Sharing single pins.
- Stroll reminders and onboarding (next design).
