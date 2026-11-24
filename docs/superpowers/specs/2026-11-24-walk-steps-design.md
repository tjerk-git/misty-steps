# Walk steps on the postcard: design

Status: approved by the user on 2026-11-24 (small change; the user approved the approach, the playful stride table and "the steps follow the selected animal", and said yes to everything).

## Intent and why

After a walk the postcard says how many steps that distance would be for the animal you picked: 6 km is about 31,000 Trail Cat steps,
about 7,500 Trek Horse steps. It is a small joke that gives each character a size, in the same spirit as the character blurbs.

## Goals

- One extra line on the postcard, on Android and iPhone, in English and Dutch.
- Each character has its own step length (metres per step), kept in `characters.txt` next to its other data, so both apps read one table.
- The number is rounded for reading: two significant digits from 100 steps up, exact below.

## Non-goals

- No change to the walk log, the stats page, or what is stored per walk (no database change).
- No claim to be accurate: the strides are playful approximations.
- No step counting from the phone's sensors.

## Anti-goals

- The line must never run off the postcard (Dutch with a long species name is the longest case) or crowd the credit line.
- A walk with no distance must not show "about 0 steps".

## Constraints

- No emoji anywhere (project rule).
- The selected character at the moment the postcard is drawn decides the animal (walks do not record who walked). If you named your character, the line uses that name, otherwise the translated species name.
- Shared code does the maths and the text; the two postcard renderers only draw the line (Android `PostcardRenderer.kt`, iPhone `PostcardRenderer.swift`).
- Strings: `postcard_steps` in `shared/strings/values` (English) and `values-nl`.

## Approaches

1. Stride table in code (a `when` on the character id): easy, but a new character needs a code change and the two apps could drift.
2. **Stride in `characters.txt` (chosen):** data next to the sprite, one source for both apps, a test can check that every character has one.
3. Store the character on each walk: stable old postcards, but needs a database migration. Not wanted: the user chose "follows the selected animal".

## Design

- `characters.txt`: a new property `stride = <metres>` per character: cat 0.2, Black Cat 0.2, dog 0.4, otter 0.25, capybara 0.3, cow 0.8, horse 0.9, fox 0.35, duck 0.15, rat 0.05. `Character.strideM` (default 0.3 when missing).
- `WalkSteps.count(distanceM, strideM)` and `WalkSteps.readable(steps)` in shared code, with unit tests.
- `postcardStepsLine(character, customName, distanceM): String?` (shared, null when there are no steps), and `PostcardTexts.steps(...)` for Swift.
- English: "That is about %1$s %2$s steps." Dutch: "Dat zijn ongeveer %1$s stappen als %2$s."
- Both renderers draw it under the stats line, in the quiet colour, shrinking the text to fit the width (not below 22 px).

## Left to the builder

Exact vertical position (between the stats line and the credit), and the font size steps used when shrinking.
