# Offline maps, plan 1 of 4: province pipeline and probes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one province's map file locally, show it in a throwaway MapLibre screen on the Pixel, and answer the five open questions in the spec before any production map code changes.

**Architecture:** A Python pipeline (`tools/maps/`) turns the Geofabrik dump into province borders and a PMTiles file per province. A debug-only spike screen (`app/src/debug/`) loads those files with MapLibre next to a transparent overlay view, so fog sync, multiple packs, glyphs, snapshots and download resume can be judged on the phone. Nothing in the release build changes.

**Tech Stack:** Python 3 with pyosmium, shapely, pyproj, pytest; Planetiler (OpenMapTiles profile); `pmtiles` CLI; Kotlin, MapLibre Native Android 11.8.0 or newer 11.x; Cloudflare R2 through the `cf` CLI.

**Spec:** `docs/superpowers/specs/2026-11-16-offline-maps-provinces-design.md`

**Why only this much of the work:** the spec's biggest risk (the overlay view drifting from the map) decides whether fog stays a view or becomes a MapLibre layer. That changes the migration, downloads and postcard plans, so they are written after this one's results:

- Plan 2: MapLibre migration of the map screen, overlays and postcard.
- Plan 3: catalog, pack downloads, onboarding, picker, cards, "Your maps".
- Plan 4: yearly pipeline in GitHub Actions, stats from packs, cleanup of osmdroid.

## Global Constraints

- Android `minSdk` 26; the release build is unchanged by this plan (MapLibre is `debugImplementation` only).
- Hard rule: never contact an OpenStreetMap-run or community-run OSM service (no tile servers, Nominatim, Overpass, Postpass). Geofabrik is fetched for the dump only, with User-Agent `misty-steps-maps/1 (+https://github.com/tjerk-git/misty-steps)`.
- Province ids: lower-case ISO 3166-2:NL codes `dr fl fr ge gr li nb nh ov ut ze zh`.
- PMTiles zoom 6 to 14, province plus 1 km margin, OpenMapTiles schema.
- Style palette: land paper (246, 243, 236); water soft dusk blue, no outline; parks and woods muted sage; roads white with a thin ink edge; footpaths dotted dusk; no POI icons. Ink `#2E3550`, Ember `#B4536A`, Dusk `#5B6B8C`.
- Glyphs ship in the app as `asset://` PBF ranges, Latin and Latin extended only. Nothing is fetched at runtime.
- Attribution reads "© OpenStreetMap contributors · © OpenMapTiles".
- Cloudflare work uses the `cf` CLI (this project has no Wrangler config).
- No emoji anywhere. New user-facing copy in English and Dutch (this plan adds none).

## Review Focus

- A province made of several parts (Friesland has the Wadden islands): the extract region must keep every part.
- A 1 km buffer that overlaps a neighbouring province or the sea must still give a valid polygon.
- Frisian and Dutch names with accents and "IJ" (Súdwest-Fryslân, IJsselmeer, Ameland's Hollum) render in the glyphs.
- The style with zero packs, one pack and two overlapping packs loads without an error, and a pack file that is missing or corrupt does not crash the screen.
- R2 answers a `Range` request with `206` and the right bytes, and a resumed request after a cut connection matches the sha256 of the whole file.

## File Structure

| File | Responsibility |
|---|---|
| `tools/maps/provinces.py` | Province polygons from the dump, 1 km buffered regions, the app's simplified `nl-provinces.json` |
| `tools/maps/build.py` | Runs Planetiler and `pmtiles extract` for one or all provinces, prints sizes and timings |
| `tools/maps/style/template.json`, `build_style.py` | The Misty style, one source `__SOURCE__`, written to `app/src/main/assets/maps/misty-style.json` |
| `tools/maps/style/fetch_glyphs.sh` | Fetches Noto Sans glyph ranges into `app/src/main/assets/maps/glyphs/` |
| `app/src/main/java/com/mistysteps/app/maps/MapStyle.kt` | Pure Kotlin: template plus installed packs becomes one style JSON |
| `app/src/debug/java/com/mistysteps/app/spike/MapSpikeActivity.kt` | Throwaway probe screen (debug builds only) |
| `docs/superpowers/specs/2026-11-16-offline-maps-probe-results.md` | What the probes showed, and the go or no-go |

---

### Task 1: Province borders and regions

**Files:**
- Create: `tools/maps/provinces.py`, `tools/maps/tests/test_provinces.py`, `tools/maps/tests/data/provinces.osm`
- Modify: `tools/maps/requirements.txt` (add `pyproj`, `pytest`)

**Interfaces:**
- Produces: `province_polygons(pbf_path: str) -> dict[str, BaseGeometry]` (key is the province id, geometry is a lon/lat `MultiPolygon`); `buffered_region(geom: BaseGeometry, meters: float = 1000.0) -> BaseGeometry`; `app_json(polys: dict[str, BaseGeometry], names: dict[str, str], tolerance: float = 0.003) -> dict`; CLI `python3 tools/maps/provinces.py --pbf <file> --out-dir <dir>`.
- `app_json` returns `{"format": 1, "provinces": [{"id", "name", "polygons"}]}` where `polygons` is a list of polygons, each a list of flat `[lon0, lat0, lon1, lat1, ...]` rings (outer first, then holes), the same layout as `nl-index.json`.

- [ ] **Step 1: Write the fixture** `tests/data/provinces.osm`: two `boundary=administrative`, `admin_level=4` relations with `ISO3166-2` values `NL-FR` and `NL-GR`; `NL-FR` is a multipolygon of two separate outer ways (mainland and an island) and `NL-GR` touches it along one edge. Also one `admin_level=4` relation without an `ISO3166-2` tag that must be ignored.

- [ ] **Step 2: Write failing tests**
  - `test_finds_provinces_by_iso_code`: keys are exactly `{"fr", "gr"}`; names `Friesland`, `Groningen`.
  - `test_keeps_every_part_of_a_multipolygon`: `fr` geometry has 2 polygons.
  - `test_buffer_is_valid_and_larger`: for `fr`, `buffered_region(g).is_valid`, `.contains(g)`, and its area is greater than `g`'s.
  - `test_buffer_of_neighbours_overlaps_but_stays_valid`: both buffered regions valid and `fr_buf.intersects(gr_buf)`.
  - `test_app_json_shape`: `format == 1`, each ring flat with an even length, first ring of each polygon is the outer one, simplified ring has fewer points than the original for a dense fixture ring.

- [ ] **Step 3: Run** `python3 -m pytest tools/maps/tests/test_provinces.py -q`. Expected: FAIL (module missing).

- [ ] **Step 4: Implement** `provinces.py` with pyosmium's `osmium.area.AreaManager`-style two-pass read like `tools/maps/index.py` does, keyed by the `ISO3166-2` tag with the `NL-` prefix stripped and lower-cased. `buffered_region` projects to EPSG:28992 with `pyproj.Transformer`, buffers by `meters`, applies `make_valid`, projects back. The CLI writes `<id>.geojson` per province into `--out-dir` and `nl-provinces.json` next to it.

- [ ] **Step 5: Run the tests again.** Expected: PASS.

- [ ] **Step 6: Commit** `git add tools/maps && git commit -m "Build province borders and buffered regions"`.

---

### Task 2: Build Friesland and Zuid-Holland locally, and measure

**Files:**
- Create: `tools/maps/build.py`
- Modify: `tools/maps/README.md` (create if missing): how to run it, tool versions

**Interfaces:**
- Consumes: Task 1's `<id>.geojson` regions.
- Produces: `tools/maps/out/nl.pmtiles`, `tools/maps/out/<id>.pmtiles`; CLI `python3 tools/maps/build.py --pbf <file> --provinces fr zh` that prints, per province, bytes and seconds.

- [ ] **Step 1: Fetch the dump once** to `tools/maps/data/netherlands-latest.osm.pbf` (git-ignored) with `curl -A "misty-steps-maps/1 (+https://github.com/tjerk-git/misty-steps)"`. This is the only Geofabrik request of the whole plan; do not repeat it.
- [ ] **Step 2: Fetch the tools** (Planetiler release jar and the `pmtiles` CLI) into `tools/maps/bin/` (git-ignored). Confirm Planetiler's flags for output path, max zoom and min zoom with `java -jar planetiler.jar --help` before writing the script.
- [ ] **Step 3: Implement `build.py`**: run Planetiler OpenMapTiles profile over the dump to `out/nl.pmtiles` at zoom 6 to 14 (skip if it exists and `--force` is not given), then for each requested province run `pmtiles extract out/nl.pmtiles out/<id>.pmtiles --region=out/<id>.geojson`. Print a table of province, bytes, seconds.
- [ ] **Step 4: Run** for `fr zh`. Expected: both files exist and `pmtiles show out/fr.pmtiles` reports zoom 6 to 14 and a bounding box around Friesland.
- [ ] **Step 5: Record** sizes and timings in the results doc (Task 8). Targets from the spec: each province under 150 MB, the whole job under an hour. If Zuid-Holland is over, try dropping the `poi` and `housenumber` layers and building footprints below zoom 14 and record what that saves.
- [ ] **Step 6: Commit** `git commit -m "Build province map files locally"`.

---

### Task 3: Misty style and glyphs

**Files:**
- Create: `tools/maps/style/template.json`, `tools/maps/style/build_style.py`, `tools/maps/style/fetch_glyphs.sh`, `tools/maps/tests/test_style.py`
- Create (generated, committed): `app/src/main/assets/maps/misty-style.json`, `app/src/main/assets/maps/glyphs/<fontstack>/<range>.pbf`

**Interfaces:**
- Produces: `build_style(template_path: str) -> dict`, a MapLibre style (version 8) in which every layer reads from the single source named `"__SOURCE__"` (a vector source with no URL, filled in by Kotlin), `glyphs` is `"asset://maps/glyphs/{fontstack}/{range}.pbf"`, and fonts are `["Noto Sans Regular"]` and `["Noto Sans Bold"]`.

- [ ] **Step 1: Write failing tests** in `test_style.py`:
  - `test_layer_ids_unique_and_sources_known`: ids unique; every layer's `source` is `__SOURCE__` (background layers have none).
  - `test_uses_palette`: the background colour is `#F6F3EC`; no layer uses a POI source-layer (`poi`).
  - `test_only_asset_urls`: no string in the style starts with `http`.
  - `test_fonts_exist_as_glyph_dirs`: each fontstack named in `text-font` has a directory under `app/src/main/assets/maps/glyphs/`.
- [ ] **Step 2: Run** `python3 -m pytest tools/maps/tests/test_style.py -q`. Expected: FAIL.
- [ ] **Step 3: Write `template.json`** following the spec's look: layers for land, water, parks and woods, buildings from zoom 14, roads by class with casing, footpaths and cycleways dotted, railways, street names along the line, place names. Source layer and class names come from the OpenMapTiles schema. `build_style.py` loads and validates it and writes `misty-style.json`.
- [ ] **Step 4: `fetch_glyphs.sh`** downloads the Noto Sans Regular and Bold glyph ranges 0-255, 256-511 and 512-767 from the `openmaptiles/fonts` project (SIL OFL; keep its licence text in `glyphs/LICENSE.txt`).
- [ ] **Step 5: Run the tests.** Expected: PASS.
- [ ] **Step 6: Commit** `git commit -m "Add the Misty map style and glyphs"`.

---

### Task 4: MapStyle builder (Kotlin)

**Files:**
- Create: `app/src/main/java/com/mistysteps/app/maps/MapStyle.kt`, `app/src/test/java/com/mistysteps/app/maps/MapStyleTest.kt`

**Interfaces:**
- Produces: `object MapStyle { fun build(template: String, packs: List<File>): String }`. For each pack file it adds a vector source `pack-<n>` with `"url": "pmtiles://file://<absolutePath>"` and a copy of every template layer with id `<id>-<n>` and `source` set to `pack-<n>`; the background layer is kept once, first. With no packs the result is the template's background alone, with no sources.

- [ ] **Step 1: Write failing tests** (JUnit, `org.json`):
  - `zeroPacksGivesBackgroundOnly`: no `sources`, one layer.
  - `onePackAddsOneSourceWithFileUrl`: the url equals `"pmtiles://file://" + file.absolutePath`; layer count equals the template's non-background layers plus the background.
  - `twoPacksGiveUniqueIds`: all layer ids unique, layers for pack 1 come before pack 2's, sources are `pack-0` and `pack-1`.
  - `pathWithSpacesIsKept`: a file named `my pack.pmtiles` appears unchanged in the url.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.mistysteps.app.maps.MapStyleTest'`. Expected: FAIL (class missing).
- [ ] **Step 3: Implement `MapStyle.build`.**
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Build a map style from the installed packs"`.

---

### Task 5: Spike screen on the Pixel (probes 1 and 2)

**Files:**
- Modify: `app/build.gradle.kts` (add `debugImplementation("org.maplibre.gl:android-sdk:11.8.0")`, newest 11.x that resolves)
- Create: `app/src/debug/java/com/mistysteps/app/spike/MapSpikeActivity.kt`, `app/src/debug/AndroidManifest.xml` (registers the activity, exported for `adb shell am start`)

**Interfaces:**
- Consumes: `MapStyle.build(template: String, packs: List<File>): String`, the generated `misty-style.json`.
- The activity loads every `*.pmtiles` in `getExternalFilesDir("spike")`, calls `MapLibre.getInstance(this)`, and shows a `MapView` with a transparent `OverlayView` above it (not clickable, so touches reach the map).
- `OverlayView` draws, from the map's `projection.toScreenLocation(LatLng)`: a 50 m circle at the Oldehove, Leeuwarden (53.2014, 5.7936); a small cross at the corner of each of a 3 x 3 grid of fixed coordinates around it; and a text label per frame with the frame time. It redraws from `addOnCameraMoveListener` and `Choreographer`.

- [ ] **Step 1: Push test data**: `adb -s <phone> push tools/maps/out/fr.pmtiles /sdcard/Android/data/com.potloodgum.mistysteps.debug/files/spike/`.
- [ ] **Step 2: Build and install the debug app**, start the activity: `adb -s <phone> shell am start -n com.potloodgum.mistysteps.debug/com.mistysteps.app.spike.MapSpikeActivity`.
- [ ] **Step 3: Probe 1, vector packs.** Expected: streets, water, parks and names show for Friesland, zoom 6 to 18 (enlarged beyond 14). Names with accents (Súdwest-Fryslân, Ljouwert's streets) and "IJ" place names render without missing-glyph boxes. Push `zh.pmtiles` as well and open an area where the 1 km margins overlap: the map looks the same as with one pack, with no doubled labels. Record anything that fails.
- [ ] **Step 4: Probe 1, bad files.** With an empty file named `broken.pmtiles` and with the folder empty, the screen shows the background and does not crash.
- [ ] **Step 5: Probe 2, overlay sync.** Record the screen (`adb shell screenrecord --size 1080x2400 /sdcard/sync.mp4`) while doing three hard flings, two pinch zooms and a slow pan. Pull it and extract frames (`ffmpeg -i sync.mp4 -vf fps=60 frames/%04d.png`, installing ffmpeg with brew if missing). Pass: in every frame the grid crosses stay on the same street corners, with no offset a person would notice (rule of thumb: under 3 dp). If frames show the crosses a frame behind, repeat once with drawing moved into `OnDidFinishRenderingFrame` to see whether that fixes it; record both results.
- [ ] **Step 6: Battery and frames.** `adb shell dumpsys gfxinfo com.potloodgum.mistysteps.debug reset`, pan for 30 s, `dumpsys gfxinfo` again; compare janky percent and p99 with today's numbers in `bugs.md` (about 1% and 17 ms).
- [ ] **Step 7: Commit** `git commit -m "Add the debug map spike screen"`.

---

### Task 6: Snapshotter (probe 4)

**Files:**
- Modify: `app/src/debug/java/com/mistysteps/app/spike/MapSpikeActivity.kt`

**Interfaces:**
- Adds a button that renders `MapSnapshotter` (1080 x 1350, same style string, bounds from the Friesland pack's walk area, e.g. a 400 m box around the Oldehove) and shows the bitmap in an `ImageView`, logging the time taken.

- [ ] **Step 1: Add the button and the snapshot call.** Pass: the snapshot shows the same streets as the live map, with the glyph-based street names, in under 5 seconds on the Pixel, and works with the pack files local and airplane mode on.
- [ ] **Step 2: Try a box with no pack.** Expected: background colour only, no crash.
- [ ] **Step 3: Commit** `git commit -m "Spike: render a postcard map with the snapshotter"`.

---

### Task 7: R2 range requests (probe 5)

**Files:**
- Create: `tools/maps/probe_range.py` (small script: fetch a URL in two parts, join, compare sha256)

**Interfaces:**
- CLI `python3 tools/maps/probe_range.py <url> <expected-sha256> [--cut-at <bytes>]`; exits 0 when the joined body matches.

- [ ] **Step 1: Create the bucket and domain.** With the `cf` CLI (the user signs in first, with `! cf login` in the prompt): create R2 bucket `misty-maps`, attach the custom domain `maps.potloodgum.com`, make it publicly readable. Show the user each command before running it, since this creates paid-tier resources and a public hostname.
- [ ] **Step 2: Upload** `fr.pmtiles` as `v2/probe/fr.pmtiles` (the S3 API through `rclone` or the AWS CLI with an R2 token the user creates; never put the token in the repo).
- [ ] **Step 3: Test** `curl -s -o /dev/null -w '%{http_code} %{size_download}\n' -r 0-1023 https://maps.potloodgum.com/v2/probe/fr.pmtiles`. Expected: `206 1024`. Then run `probe_range.py` with `--cut-at` at about 40% of the file and the file's sha256. Expected: exit 0.
- [ ] **Step 4: Check headers**: `Accept-Ranges: bytes`, `ETag`, `Cache-Control`. Record them.
- [ ] **Step 5: Mobile data and reboot resume** are not tested here: they need the real download job, so plan 3 covers them. Write "not tested on mobile data" in the results.
- [ ] **Step 6: Delete the probe object**, keep the bucket. Commit the script: `git commit -m "Add a range request probe"`.

---

### Task 8: Results and gate

**Files:**
- Create: `docs/superpowers/specs/2026-11-16-offline-maps-probe-results.md`

- [ ] **Step 1: Write the results**: one section per probe (1 maps and glyphs, 2 overlay sync, 3 sizes and times, 4 snapshotter, 5 range), each with the measured numbers, a pass or fail against the spec's criteria, and screenshots or file names where useful.
- [ ] **Step 2: End with a decision**: keep the overlay view approach (probe 2 passed), or use a MapLibre custom layer for the fog (probe 2 failed), plus any change to zoom range, layers dropped or pack sizes.
- [ ] **Step 3: Update the spec** where results change it (sizes, zoom range, style layers).
- [ ] **Step 4: Commit** `git commit -m "Record the offline map probe results"` and stop. Plans 2 to 4 are written after the user reads the results.
