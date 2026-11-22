# Streets and Neighbourhoods Offline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the neighbourhood percentage and the "x of y streets walked / Still in the mist" list back for everyone, from a per-province `stats.zip` built on the developer's side and shipped next to each map pack.

**Architecture:** A Python builder (`tools/maps/stats_build.py`) turns the OpenStreetMap dump into one `stats.zip` per province: neighbourhood borders per town, and street lines per neighbourhood, compactly encoded. `publish.py` builds and uploads it with the pack and lists it in the catalog (the catalog slot `stats` already exists). The shared Kotlin module reads the format; the Android app reads single entries from the installed pack's zip, falls back to what older versions saved, fetches missing stats quietly on an unmetered connection, and counts the stats file in the picker size.

**Tech Stack:** Python 3.13 (pyosmium, shapely, pytest), Kotlin Multiplatform `:shared`, Android (ZipFile, WorkManager), Cloudflare R2 via rclone.

**Spec:** `docs/superpowers/specs/2026-11-22-streets-and-neighbourhoods-design.md`

## Global Constraints

- Nothing leaves the phone for this feature; no new network request except the quiet download of the stats file from `maps.potloodgum.com`.
- A street is a named road, or a named footpath or cycleway, of at least 30 m; parking aisles, driveways, service roads and unnamed paths are excluded; all ways with the same name inside one neighbourhood are one street.
- Walked rule unchanged (70% of the street within 25 m of the trail, `StreetCoverage`); eraser spots count like walked spots.
- Neighbourhood kinds and fallback order unchanged (`quarter, suburb, neighbourhood, admin11, admin10, admin9`); a town with none has no neighbourhood card.
- One `stats.zip` per province; format number 1; an app meeting a higher format ignores the file and shows no neighbourhood card, never a crash.
- Size target: at most about 15% of the province's map file; the builder prints the real numbers.
- The catalog is written last by `publish.py`; uploads are checked with HEAD requests first (existing behaviour).
- Pack removal deletes the stats file with the pack (it lives in the pack folder).
- Dutch and English strings; no emoji anywhere.
- Python tests run with `uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt python -m pytest tools/maps/tests -q`; Kotlin tests with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew testDebugUnitTest :shared:macosArm64Test`.

## The file format (all tasks rely on this)

`stats.zip` (deflate), format 1:

- `index.json`: `{"format": 1, "province": "fr", "dataDate": "2026-11-10", "cities": [<relation id>, ...]}`, the towns (ids from `app/src/main/assets/nl-index.json`, `osmId`) that have a `hoods/` file.
- `hoods/<cityId>.json`: `{"hoods": [{"type": "relation" | "way", "id": <osm id>, "name": "...", "kind": "quarter" | "suburb" | "neighbourhood" | "admin11" | "admin10" | "admin9", "polygons": [[ring, ...], ...]}]}`; a polygon is a list of rings (first outer, rest holes).
- `streets/<t><id>.json` where `<t>` is `r` for relation or `w` for way, `<id>` the neighbourhood's OSM id: `{"streets": [{"name": "...", "lines": [line, ...]}]}`.
- A ring or line is a flat list of integers in units of 1e-5 degree, delta-encoded: `[lon0, lat0, dlon1, dlat1, dlon2, dlat2, ...]` (longitude first, as in the app's `Boundary` and `Street`); decoded value = running sum divided by 100000.

## Review Focus

- A zip that is missing, truncated, not a zip, or has a newer `format`: the stats page shows no neighbourhood card and nothing crashes.
- A neighbourhood whose streets entry is missing from the zip: the neighbourhood card shows, the street list does not.
- A neighbourhood with no streets at all (or only streets under 30 m): no "0 of 0" line.
- Two packs installed where a neighbourhood sits on the province border: the streets come from the pack that has the entry; no duplicate streets.
- A very large neighbourhood: reading and computing finishes quickly on a slow phone (timed in Task 8) and only that neighbourhood's entry is read.
- A street that is split into many ways with the same name inside one neighbourhood counts once.
- The same street name in two different neighbourhoods counts once in each.
- An app that has legacy saved data (older versions) and no stats file keeps showing it.

---

### Task 1: Street rules and compact encoding (Python)

**Files:**
- Create: `tools/maps/stats_build.py`
- Test: `tools/maps/tests/test_stats_build.py`

**Interfaces:**
- Produces in `stats_build`: `E5 = 100_000`; `encode_line(coords: list[tuple[float, float]]) -> list[int]` and `decode_line(ints: list[int]) -> list[tuple[float, float]]` (coords are `(lon, lat)`, exact round trip at 1e-5 degree precision); `STREET_HIGHWAYS: frozenset[str]`; `is_street(tags: dict[str, str]) -> bool`; `line_length_m(coords: list[tuple[float, float]]) -> float`; `group_streets(ways: list[tuple[str, list[tuple[float, float]]]], min_m: float = 30.0) -> list[dict]` returning `[{"name": str, "lines": [coords, ...]}]`, one entry per name, dropping streets whose total length is below `min_m`.

- [ ] **Step 1: Write the failing tests** in `test_stats_build.py`: `decode_line(encode_line(c))` equals `c` rounded to 5 decimals for a 4-point line; `encode_line([(5.79, 53.2), (5.80, 53.2)])` is `[579000, 5320000, 1000, 0]`; `is_street({"highway": "residential", "name": "Hoofdstraat"})` is true; false for `{"highway": "service", "name": "x"}`, for `{"highway": "residential"}` (no name), for `{"highway": "footway"}` (no name), and true for `{"highway": "cycleway", "name": "Fietspad"}`; `group_streets` merges three ways named "A" into one entry with three lines, keeps two different names apart, and drops a name whose lines total 20 m while keeping one of 40 m.
- [ ] **Step 2: Run** the Python test command. Expected: FAIL, import or attribute errors.
- [ ] **Step 3: Implement** the functions in `stats_build.py`. `STREET_HIGHWAYS` is `{"residential", "living_street", "pedestrian", "unclassified", "tertiary", "secondary", "primary", "trunk", "footway", "cycleway", "path", "steps", "road"}` and `is_street` requires a non-blank `name` and a highway in the set (service, track and unnamed ways are out). `line_length_m` uses a flat-earth approximation per segment.
- [ ] **Step 4: Run** the test command. Expected: PASS.
- [ ] **Step 5: Commit** `git add tools/maps && git commit -m "Stats builder: street rules and compact line encoding"`.

### Task 2: Neighbourhood assignment and the zip writer (Python)

**Files:**
- Modify: `tools/maps/stats_build.py`
- Test: `tools/maps/tests/test_stats_build.py`

**Interfaces:**
- Consumes: Task 1's `encode_line`, `group_streets`.
- Produces: `assign_hoods_to_cities(hoods: list[dict], cities: list[dict]) -> dict[int, list[dict]]` where a hood is `{"type", "id", "name", "kind", "polygons": [[ring_coords...]...]}` (rings as lists of `(lon, lat)`) and a city is `{"osmId": int, "polygons": [...]}` in the same ring shape; a hood goes to the city containing its representative point (shapely `representative_point`), hoods in no city are dropped; `streets_for_hood(hood: dict, ways: list[tuple[str, list[tuple[float, float]]]]) -> list[dict]` returns `group_streets` of the ways that intersect the hood polygons; `write_stats_zip(path: Path, province: str, data_date: str, city_hoods: dict[int, list[dict]], hood_streets: dict[str, list[dict]]) -> None` writing exactly the layout above (hood key for `hood_streets` is `"<t><id>"`, e.g. `"r12345"`), including `index.json` with `format: 1`; `hood_key(hood: dict) -> str`.

- [ ] **Step 1: Write the failing tests**: a hood whose representative point lies in city 1's square goes to city 1 and a hood outside every city is dropped; `streets_for_hood` keeps a way crossing the hood and drops one far away; `write_stats_zip` on a tiny synthetic province (one city, one hood, two streets) produces a zip with entries `index.json`, `hoods/<cityId>.json` and `streets/r<id>.json`, `index.json` has `format` 1 and the city id, and decoding the hood ring and a street line with `decode_line` gives back the input to 5 decimals; a hood with no streets writes no `streets/` entry.
- [ ] **Step 2: Run** the Python tests. Expected: FAIL.
- [ ] **Step 3: Implement** the functions; polygons are encoded with `encode_line` per ring; use `zipfile.ZipFile(..., compression=ZIP_DEFLATED)`.
- [ ] **Step 4: Run** the Python tests. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Stats builder: neighbourhood assignment and zip writer"`.

### Task 3: Reading the dump and publishing (Python)

**Files:**
- Modify: `tools/maps/stats_build.py` (a `read_osm` function and a `main`), `tools/maps/publish.py`
- Test: `tools/maps/tests/test_stats_build.py`, `tools/maps/tests/test_publish.py` (add or extend)

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: `read_osm(pbf: str, province_region: shapely geometry) -> tuple[list[dict], list[tuple[str, list[tuple[float, float]]]]]` returning the neighbourhoods (relations and ways tagged `place` in `{quarter, suburb, neighbourhood}` or `boundary=administrative` with `admin_level` 9, 10 or 11, with a `name`) and the street ways (`is_street` true) inside the region, using pyosmium `FileProcessor(pbf).with_locations().with_areas()`; `build_province(pbf, province_id, out_dir, index_path, data_date) -> Path` writing `<out_dir>/<id>.stats.zip` using `<out_dir>/<id>.geojson` as the region and `index_path` (the app's `nl-index.json`) for the towns; a command line `python tools/maps/stats_build.py --pbf ... --provinces fr --out-dir tools/maps/out --index app/src/main/assets/nl-index.json --data-date 2026-11-10` that prints each zip's size and the percentage of the map file. In `publish.py`: `catalog_json` gains an optional `stats` entry per province (`{"url", "bytes", "sha256"}`) when the province dict has a `stats` path; the command line gains `--pbf` (build missing stats zips first, with the same data date) and uploads `stats.zip` to `v2/nl/<year>/<id>/stats.zip` with the immutable cache header, checks it with a HEAD request, and still writes the catalog last.

- [ ] **Step 1: Write the failing tests**: `catalog_json` with a province carrying a stats file path includes `provinces[0]["stats"]` with the right `bytes` and a 64-character `sha256`, and without a stats path has no `stats` key (existing behaviour unchanged); `build_province` on a tiny hand-made `.osm.pbf` or on injected `read_osm` output (monkeypatch `read_osm`) writes a zip that `zipfile` can open and whose `index.json` lists the city.
- [ ] **Step 2: Run** the Python tests. Expected: FAIL.
- [ ] **Step 3: Implement** `read_osm`, `build_province`, the command line and the `publish.py` changes. Filter objects to the province region by representative point (hoods) or intersection (streets).
- [ ] **Step 4: Run** the Python tests. Expected: PASS; then run `python tools/maps/publish.py --year 2026 --data-date 2026-11-10 --dry-run` and confirm it still works without `--pbf`.
- [ ] **Step 5: Commit** `git commit -m "Stats builder: read the dump, publish the stats file with the pack"`.

### Task 4: The format reader (shared Kotlin)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mistysteps/app/stats/StatsPackFormat.kt`
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/stats/StatsPackFormatTest.kt`

**Interfaces:**
- Consumes: `Boundary`, `Street` (stats package), kotlinx.serialization JSON.
- Produces: `object StatsPackFormat { const val FORMAT = 1; fun decodeLine(ints: List<Long>): DoubleArray; fun parseIndex(text: String): StatsIndex?; fun parseHoods(text: String): List<Boundary>; fun parseStreets(text: String): List<Street>; fun streetsEntry(osmType: String, osmId: Long): String; fun hoodsEntry(cityId: Long): String }` and `data class StatsIndex(val province: String, val dataDate: String, val cities: Set<Long>)`. `decodeLine` returns the flat `[lon0, lat0, lon1, lat1, ...]` doubles; `parseIndex` returns null for anything that is not format 1 (broken JSON, missing keys, `format` other than 1); `parseHoods` builds `Boundary(name, polygons, type, id, kind)` and skips entries without a name or area; `parseStreets` returns `Street(name, lines)` and drops entries without lines; `streetsEntry("relation", 12345)` is `"streets/r12345.json"` and `("way", 7)` is `"streets/w7.json"`; `hoodsEntry(99)` is `"hoods/99.json"`.

- [ ] **Step 1: Write the failing tests** using a fixture string built from the format above (a hood with one 4-point ring and one street with two lines): `decodeLine(listOf(579000, 5320000, 1000, 0))` equals `[5.79, 53.2, 5.80, 53.2]` within 1e-9; `parseIndex` of a valid index returns the province and the city set, and returns null for `{"format": 2, ...}`, for `not json`, and for `{}`; `parseHoods` returns one `Boundary` with the right name, kind, `osmType`, `osmId`, and `contains(lat, lon)` true at the ring's centre and false outside; `parseStreets` returns the street with two lines; entry-name helpers as above; `parseHoods` of an entry without a name returns empty.
- [ ] **Step 2: Run** `./gradlew :shared:macosArm64Test --tests '*StatsPackFormatTest*'` (with `DEVELOPER_DIR` set). Expected: FAIL.
- [ ] **Step 3: Implement `StatsPackFormat`** with `Json.parseToJsonElement`; never throw for bad input (return null or empty).
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Stats pack: shared format reader"`.

### Task 5: Reading from installed packs (Android)

**Files:**
- Create: `app/src/main/java/com/mistysteps/app/stats/ZipStatsPack.kt`
- Modify: `app/src/main/java/com/mistysteps/app/stats/Neighbourhoods.kt`, `app/src/main/java/com/mistysteps/app/stats/StatsRepository.kt`, `app/src/main/java/com/mistysteps/app/maps/ProvincePacks.kt`
- Test: `app/src/test/java/com/mistysteps/app/stats/ZipStatsPackTest.kt`, `app/src/test/java/com/mistysteps/app/maps/ProvincePacksTest.kt` (extend)

**Interfaces:**
- Consumes: Task 4's `StatsPackFormat`; `ProvincePacks.packDir(year, id)`.
- Produces: `class ZipStatsPack(file: File)` with `val index: StatsIndex?` (null when the file is missing, broken or a newer format), `fun hoods(cityId: Long): List<Boundary>?` and `fun streets(osmType: String, osmId: Long): List<Street>?` reading one entry at a time (null when absent); `ProvincePacks.statsFiles(): List<File>` returning `<packDir>/stats.zip` for every installed pack that has one (newest year per province, sorted by id) and `fun hasStats(id: String): Boolean`; `Neighbourhoods(context, statsFiles: () -> List<File>)` whose `saved(city)` looks first in the stats files whose index lists the city and then falls back to the legacy saved JSON, and whose `streets(hood)` looks for the neighbourhood's entry in each stats file (first found) and then the legacy file. Every read swallows I/O and parse failures and returns null.

- [ ] **Step 1: Write the failing tests**: `ZipStatsPackTest` builds a real zip in a temp folder (using the Task 2 layout with one town, one hood and one street) and checks `index`, `hoods(cityId)` (one hood), `streets("relation", id)` (one street), `streets("way", 999)` null, a non-zip file gives `index == null` and null reads, a zip with `"format": 2` gives `index == null`, a missing file gives null; `ProvincePacksTest`: `statsFiles()` lists only packs that have a `stats.zip`, newest year only, and `hasStats` agrees.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*ZipStatsPackTest*' --tests '*ProvincePacksTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `ZipStatsPack` with `java.util.zip.ZipFile` (open per read, close after), the `ProvincePacks` additions, and wire `Neighbourhoods` and `StatsRepository` (the repository passes `MapsGraph.packs::statsFiles`); keep the existing legacy reading intact as the fallback and keep `Neighbourhoods.pick` unchanged.
- [ ] **Step 4: Run** the tests and `./gradlew assembleDebug`. Expected: PASS and a clean build.
- [ ] **Step 5: Commit** `git commit -m "Stats: read neighbourhoods and streets from the installed packs"`.

### Task 6: Quiet fetch for installed packs and the picker size (Android and shared)

**Files:**
- Modify: `app/src/main/java/com/mistysteps/app/maps/PackDownloadWorker.kt`, `shared/src/commonMain/kotlin/com/mistysteps/app/maps/PackEntry.kt`, the picker row and total code in `shared/src/commonMain/kotlin/com/mistysteps/app/maps/PickerState.kt` and `shared/src/commonMain/kotlin/com/mistysteps/app/ui/ProvincePickerScreen.kt`, `app/src/main/java/com/mistysteps/app/maps/MapsGraph.kt` or the catalog refresh call site
- Test: `shared/src/commonTest/kotlin/com/mistysteps/app/maps/PickerStateTest.kt` or `app/src/test/java/com/mistysteps/app/maps/PickerStateTest.kt` (the existing file), `app/src/test/java/com/mistysteps/app/maps/StatsFetchRulesTest.kt`

**Interfaces:**
- Consumes: `PackEntry.stats: FileRef?`, `ProvincePacks.hasStats`, `ProvincePacks.installedYear`.
- Produces: `val PackEntry.totalBytes: Long` (map bytes plus stats bytes, stats absent counts 0), used by the picker rows and the total; `fun statsToFetch(entries: List<PackEntry>, installed: (String) -> Boolean, hasStats: (String) -> Boolean): List<PackEntry>` (pure: entries that have a `stats` reference, whose pack is installed and that lack stats); `PackDownloads.enqueueStats(context: Context, entry: PackEntry)` enqueueing unique work `stats-<id>` with `NetworkType.UNMETERED` that downloads only the stats file to `<packDir>/stats.zip` using the same installer (checks size and sha256, writes via `.part` then renames), with the existing retry rules; the call site that refreshes the catalog (`CatalogStore.refresh`) calls `statsToFetch` and enqueues each result.

- [ ] **Step 1: Write the failing tests**: `totalBytes` is map plus stats and equals map bytes when stats is null; the picker total for two selected provinces uses `totalBytes`; `statsToFetch` returns an installed pack without stats whose catalog entry has stats, and returns nothing for a pack that has stats, for a pack that is not installed, and for an entry without a `stats` reference.
- [ ] **Step 2: Run** the named tests. Expected: FAIL.
- [ ] **Step 3: Implement** `totalBytes`, the picker changes (size shown per row and total use `totalBytes`), `statsToFetch`, `enqueueStats` (reuse `PackInstaller.install` and the worker's foreground notification only if the existing worker needs it; a small background download needs no notification) and the call after a catalog refresh.
- [ ] **Step 4: Run** the full unit tests and `./gradlew assembleDebug`. Expected: PASS and a clean build.
- [ ] **Step 5: Commit** `git commit -m "Stats: quiet fetch for installed packs, combined size in the picker"`.

### Task 7: "Streets ready" note and strings

**Files:**
- Modify: `shared/strings/values/strings.xml`, `shared/strings/values-nl/strings.xml`, `shared/src/commonMain/kotlin/com/mistysteps/app/ui/MapsCards.kt` (the "Your maps" rows)

**Interfaces:**
- Produces: string `maps_streets_ready` ("Streets and neighbourhoods are ready" / "Straten en buurten staan klaar") shown as a small line under a province row in "Your maps" when its stats file is present; the line needs `hasStats` passed through `PackManager` (add `fun hasStats(id: String): Boolean` to the `PackManager` interface, implemented by `AndroidPackManager` through `ProvincePacks.hasStats`, and by any test fake).

- [ ] **Step 1: Write the English and Dutch copy**, short and calm, no emoji.
- [ ] **Step 2: Implement** the interface method, the implementations and the row line.
- [ ] **Step 3: Run** `./gradlew testDebugUnitTest :shared:macosArm64Test assembleDebug`. Expected: PASS and a clean build (fix any fake of `PackManager` the compiler reports).
- [ ] **Step 4: Commit** `git commit -m "Stats: streets-ready note in Your maps"`.

### Task 8: Real data and phone check

**Files:**
- Modify: `workflow.md` (a short "Streets and neighbourhoods" section: how to build and publish, how to test), `docs/superpowers/specs/2026-11-10-offline-maps-design.md` only if it states the old plan

- [ ] **Step 1: Build a real stats file** for Friesland: `python tools/maps/stats_build.py --pbf tools/maps/data/netherlands-latest.osm.pbf --provinces fr --out-dir tools/maps/out --index app/src/main/assets/nl-index.json --data-date 2026-11-10` (run it with the project's `uv` command). Expected: `tools/maps/out/fr.stats.zip` exists; record its size and its percentage of `fr.pmtiles` in `workflow.md`. If it is above about 15% of the map, reduce the line precision or the street set and rebuild, and note the decision.
- [ ] **Step 2: Emulator:** install the debug build with the 25k-point Leeuwarden trail, push `fr.stats.zip` into `files/maps/nl/2026/fr/stats.zip` (the map file can be a stand-in), open the stats page. Expected: the neighbourhood card with a percentage and "x of y streets walked" with a "Still in the mist" list.
- [ ] **Step 3: Failure cases on the emulator:** replace the zip with a garbage file, then with a format-2 file, then delete it. Expected: no neighbourhood card each time, no crash, and the legacy data (if any) still shown.
- [ ] **Step 4: Timing:** time the stats page's neighbourhood and street computation for the largest neighbourhood in the file, unthrottled and with the emulator throttled to about 25% (see `bugs.md` for the method). Expected: a few seconds at most throttled; record the numbers. If slower, find what dominates before changing anything.
- [ ] **Step 5: Document and commit** `git commit -m "Stats: docs and verification notes"`. Publishing to R2 is not part of this task (it needs the developer's rclone credentials).

---

## Self-review

- **Spec coverage:** per-province stats file and format (Tasks 1 to 3); the catalog slot and quiet publishing (Task 3); lazy single-entry reading, legacy fallback, no crash on bad files (Task 5); quiet fetch for installed packs and the combined size in the picker (Task 6); "streets ready" note (Task 7); eraser counts (unchanged, points already include erased spots); slow-phone behaviour and real data (Task 8); end users never see the build step (the builder is wired into `publish.py`, Task 3).
- **Type consistency:** `StatsIndex`, `StatsPackFormat`, `ZipStatsPack`, `hood_key`, `totalBytes`, `statsToFetch`, `enqueueStats` and `hasStats` are used with the same names and signatures across tasks.
- **Proportion:** signatures, tests and exact values; bodies are left to the implementer.
