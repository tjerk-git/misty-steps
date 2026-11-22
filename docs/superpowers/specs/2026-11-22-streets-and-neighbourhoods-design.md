# Streets and neighbourhoods, offline: design (2026-11-22)

Status: written for approval. Nothing is built. This is project 1 of three (streets, things you passed, the monthly recap); the other two get their own designs.

## Intent

The stats page can tell you how much of your neighbourhood you cleared and which streets are still in the mist ("41 of 120 streets walked", "Still in the mist"). Since 1.3.1 that card only shows for people whose phone saved the data in an older version, because the live OpenStreetMap lookups were removed. This project brings it back for everyone, with no network request at all: the data ships next to each province's map, built once on your side.

People compare their stats by sending each other how much of their city they cleared. Neighbourhood percentage and streets walked are what make that comparison richer than one number, and a street list gives completionists a concrete next target. The city percentage itself needs no new data (the borders are already in the app).

## Goals

- Neighbourhood percentage and "x of y streets walked" with a "Still in the mist" list, for any province whose map is installed.
- Fully offline on the phone; no request leaves the phone for this.
- No visible build step for end users. For you: one command publishes the pack and its stats file together.
- Cheap on slow phones: nothing is parsed until it is needed, and only for the one neighbourhood on screen.
- Eraser spots count toward streets and neighbourhoods exactly like walked spots ("we trust people").

## Non-goals

- No GPX import, no server, no accounts, no leaderboards. Comparison stays outside the app (shared stat cards, a later project).
- No change to the city and country percentages or their data.
- No street data for provinces that are not installed.
- No in-app editing of streets or neighbourhoods.

## Anti-goals

- A larger download that surprises people: the combined size must be shown in the picker before they choose.
- A stats page that freezes while it reads a province-sized file.
- Streets lists full of junk (parking aisles, driveways, unnamed paths), which would make "x of y" meaningless.
- Wrong or flickering numbers when data is missing: a missing file must give "no neighbourhood card", never a crash or a zero.

## Constraints

- **Existing hooks:** the catalog entry already has an optional `stats` file reference (`PackEntry.stats`), and the download worker already fetches and installs `stats.zip` after the map when the catalog names one. The stats screen reads neighbourhoods and streets through `Neighbourhoods` and `StreetCoverage`, which consume a JSON shape (neighbourhood boundaries with a kind; streets as named lists of lines). The new file should keep that shape so the app side changes little.
- **Delivery:** R2 bucket `misty-maps` at `maps.potloodgum.com`, published by `tools/maps/publish.py`; the catalog is written last so it never points at missing files. A format number in the catalog guards against a newer file than the app understands, as for the map packs.
- **Size:** the stats file per province should stay small compared to the map (target at most about 15% of the pack size; the builder reports the real numbers).
- **Provenance and licence:** the data comes from the same OpenStreetMap dump and the same data date as the pack; the ODbL attribution already shown covers it.
- **Platforms:** the file format and the reading logic are shared so the iPhone version can use the same files later.
- No emoji; Dutch and English strings.

## Approaches considered

1. **A stats file per province, built from your OpenStreetMap dump** (chosen). It matches how maps ship, refreshes with each yearly pack, and works offline.
2. **Reading streets out of the downloaded map tiles on the phone.** No new file, but tiles are cut and simplified per zoom level, names split across tiles, and the work is heavy on slow phones. Rejected.
3. **One national file in the app.** Well over 100 MB. Rejected.

## What was decided

Their decisions: option 1; end users never see the build step; eraser spots count; the shared stat is the total.

My calls, to check:
- **A street** is a named road, or a named footpath or cycleway, of at least 30 m. Parking aisles, driveways, service roads and unnamed paths are excluded. All ways with the same name inside one neighbourhood are one street.
- **Walked** keeps today's rule: a street counts once 70% of it lies within 25 m of your spots.
- **Neighbourhoods** come from the OpenStreetMap quarter, suburb and neighbourhood levels, with the existing fallback order. A place with none has no neighbourhood card, as today.
- **One stats file per province**, laid out so each neighbourhood's streets can be read on their own without parsing the whole file (a zip with a small index of neighbourhood boundaries and one streets file per neighbourhood).
- **People who already have a map** get their stats file by a quiet background download on an unmetered connection at the next catalog refresh, with no prompt. "Your maps" shows a small "streets ready" note once it is in. New downloads include the stats file, and the picker size shown is the combined total.
- **Neighbourhoods across a province border:** each pack knows only its own province. Streets of a neighbourhood that straddles the border are taken from whichever installed pack has them; a partly covered neighbourhood shows the numbers it can. This is accepted.
- **The format number** starts at 1; an app that meets a higher number ignores the file and shows no neighbourhood card.
- **Publishing:** `publish.py` builds the stats file for each province in the same run as the pack, with the same data date, and checks both uploads before writing the catalog.

## Where the parts live

- `tools/maps/`: a stats builder that reads the OpenStreetMap dump (the same input as `build.py`), writes `stats.zip` per province, and reports sizes; wired into `publish.py`.
- Catalog and worker: the existing `stats` slot and `stats.zip` install; add the quiet fetch for packs installed without it and the combined size in the picker.
- App: `Neighbourhoods` reads from the installed pack's stats file instead of `files/boundaries/`; the stats screen and `StreetCoverage` stay as they are.
- Shared: the file format description and the reading logic go in `:shared` so iPhone can reuse them.

## Left to the builder

- The exact layout inside `stats.zip`, line precision and compression, within the size target.
- Reading single entries from a zip efficiently on Android and iOS.
- The wording of the "streets ready" note and the picker size line (Dutch and English).
- Test data: a small real extract (for example Leeuwarden) and a synthetic province; tests for the street rules (name merging, 30 m minimum, junk exclusion), border neighbourhoods, a missing or newer-format file, and a slow-phone timing check on a large neighbourhood.

## Out of scope

Hosting costs and the bucket layout beyond adding the file, store listing text, and the other two projects.
