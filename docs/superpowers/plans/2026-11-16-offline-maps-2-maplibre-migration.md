# Offline maps, plan 2: MapLibre migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The map screen draws the downloaded province packs with MapLibre instead of online OpenStreetMap tiles through osmdroid, and the app no longer contacts any OpenStreetMap tile server.

**Architecture:** MapLibre Native draws the map from local PMTiles packs (plan 1 and 3). The trail, pins and character become map layers drawn in the same frame as the map. The fog is decided by Task 1: it is the one part that is not a simple layer, because it draws animated drifting mist every frame and cuts the cleared areas out of it. Everything else is ported behind a small `MapProjection` interface so the migration can be done in steps while the app keeps working.

**Tech Stack:** Kotlin, Jetpack Compose (`AndroidView`), MapLibre Native Android 11.8.0 or newer, the existing `MapStyle`, `ProvincePacks` and `Catalog` classes.

**Specs:** `docs/superpowers/specs/2026-11-16-offline-maps-provinces-design.md`, `2026-11-16-offline-maps-probe-results.md` (probe 2: overlay views drift from the map by up to about 35 px while panning; probe 4: the snapshotter works).

**Status of this plan:** Task 1 is a gate and is written in full. Tasks 2 to 8 are written at the level of files, interfaces and checks; their step-by-step detail is added after Task 1, because its result changes Task 4.

## Global Constraints

- `minSdk` 26. MapLibre becomes `implementation` (it is `debugImplementation` now).
- After this plan the app contacts only `maps.potloodgum.com`. `tile.openstreetmap.org` joins the CI forbidden-host list in `.github/workflows/test.yml`.
- Attribution reads "© OpenStreetMap contributors · © OpenMapTiles" on the map, in About and on the postcard.
- Where no pack covers the view there is no basemap: the Misty background colour with the mist on top. The fog still clears and the trail is still recorded.
- Existing behaviour must not change: camera follow and the 5-minute reset, the 50 m clearing ring, pins (tap, long-press on cleared ground, "Walk here first"), the postcard, battery use no worse than today.
- Copy in English and Dutch. No emoji.

## Review Focus

- A fling, a pinch zoom and a double-tap zoom with the fog and trail on: no visible gap between the fog edge, the trail and the streets (Task 1 and Task 8).
- No pack installed, one pack, two packs, and a view that crosses a province border (Task 4 and Task 8).
- A pack removed or added while the map is showing: the style reloads without a crash and without losing the camera (Task 3).
- Rotating the phone, backgrounding and returning, and the screen turning off during a walk (Task 3 and Task 8).
- Pins: a long-press on cleared ground opens the sheet at that spot; a long-press in the fog shows the toast; tapping a pin opens it (Task 5).
- The postcard for a walk outside every installed province, and for one that crosses two (Task 6).

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/mistysteps/app/map/MapProjection.kt` | Interface the overlays draw through; osmdroid and MapLibre adapters |
| `.../map/LocationFeed.kt` | Your position and bearing, replacing osmdroid's `MyLocationNewOverlay` |
| `.../map/MistyMap.kt` | The MapLibre view, style reloads, camera follow, saved camera |
| `.../map/layers/TrailLayer.kt`, `PinLayer.kt`, `CharacterLayer.kt` | Map layers for the trail, pins and your wanderer |
| `.../fog/FogLayer.kt` | The fog, as decided in Task 1 |
| `.../postcard/PostcardRenderer.kt` | Draws the postcard map with `MapSnapshotter` |
| `.../ui/MapController.kt`, `MistyScreen.kt` | Switch from osmdroid to `MistyMap` |

---

### Task 1: Decide how the fog is drawn (gate)

**Files:**
- Modify: `app/src/debug/java/com/mistysteps/app/spike/MapSpikeActivity.kt`
- Modify: `docs/superpowers/specs/2026-11-16-offline-maps-probe-results.md`

**Question:** the fog edge is soft, and the 35 px gap probe 2 measured may not be visible on a soft edge. Compare the real mist on top of the map in two ways, on the Pixel, with the Friesland pack:

- **A. Overlay view in TextureView mode (hybrid).** The existing fog drawing code runs on a transparent view above the map, through a `MapProjection` backed by MapLibre. Trail, pins and character are map layers. Cheapest to build; keeps the drifting mist, the time-of-day tint and the cut-out exactly as they are.
- **B. Fog as an image layer.** The finished fog (mist plus cut-out) is drawn into a bitmap at the map's resolution a few times a second and given to a MapLibre image source with the view's four corner coordinates, so MapLibre moves it with the map. More work and a texture upload per update; exact sync.

- [ ] **Step 1: Port the fog drawing to the spike.** Make the fog drawing code in `fog/FogOverlay.kt` usable without osmdroid by passing it a `MapProjection` (see Task 2 for the interface: `toScreen`, `fromScreen`, `zoom`, `width`, `height`); in the spike implement it over MapLibre's projection and draw it on the transparent view above a TextureView-mode map (variant A).
- [ ] **Step 2: Build variant B in the spike** (`--ei fog 2`): a bitmap drawn with the same fog code at half resolution, set with `GeoJsonSource`-free `ImageSource.setImage` and four corner coordinates from the projection, refreshed at about 4 Hz and on every camera idle, with the previous image kept until the next is ready.
- [ ] **Step 3: Record both** (`adb shell screenrecord`) during three flings, two double-tap zooms and a slow pan over a cleared patch with a visible trail, and look at stills around the fastest frames. Also run `dumpsys gfxinfo` while panning for each.
- [ ] **Step 4: Decide.** Choose A unless the gap between the fog edge and the streets or trail is visible in the stills, or its frame times are clearly worse; choose B otherwise. Write the numbers, the stills' file names and the decision into the results doc, and update Task 4 below to match.
- [ ] **Step 5: Commit** `git commit -m "Compare two ways of drawing the fog on a MapLibre map"`.

---

### Task 2: `MapProjection` and the osmdroid adapter

**Files:**
- Create: `.../map/MapProjection.kt`, test `MapProjectionTest.kt`
- Modify: `fog/FogOverlay.kt`, `trail/TrailOverlay.kt`, `pin/PinOverlay.kt`, `character/CharacterOverlay.kt` (draw through the interface)

**Interfaces:**
- Produces: `interface MapProjection { val width: Int; val height: Int; val zoom: Double; fun toScreen(lat: Double, lon: Double): PointF; fun fromScreen(x: Float, y: Float): LatLon; fun metersToPixels(meters: Double, lat: Double): Float }`; `data class LatLon(val lat: Double, val lon: Double)`; `class OsmdroidProjection(private val mapView: MapView) : MapProjection`; `class MercatorProjection(width, height, centerLat, centerLon, zoom, tileSize = 256) : MapProjection` (pure Kotlin, used by tests and as the model for the MapLibre adapter).
- Tests: `MercatorProjectionTest`: the centre maps to the middle of the view; `fromScreen(toScreen(p))` returns `p` within 1e-6 degrees at zoom 3, 15 and 18; one zoom level up doubles distances from the centre; `metersToPixels(50, 53.2)` at zoom 15 is about 50 / (156543.03 * cos(lat) / 2^15) pixels.

- [ ] Steps: write the tests, watch them fail, implement `MercatorProjection`, then change the four overlays to take a `MapProjection` instead of an osmdroid `MapView` while the osmdroid map keeps working through `OsmdroidProjection`. Run `./gradlew testDebugUnitTest assembleDebug` and check the app on the Pixel (fog, trail, pins, character unchanged). Commit.

---

### Task 3: `MistyMap`: the MapLibre view, style reloads, camera

**Files:** Create `.../map/MistyMap.kt`, `.../map/LocationFeed.kt`, tests `LocationFeedTest.kt`; modify `ui/MapController.kt`.

**Interfaces:**
- `class LocationFeed(context)`: `val fix: StateFlow<Fix?>` with `data class Fix(val lat: Double, val lon: Double, val bearing: Float?, val speed: Float?, val accuracy: Float?, val time: Long)`; fed by the recording service while it runs and by a plain `LocationManager` listener otherwise; `fun start(hasPermission: Boolean)`, `fun stop()`.
- `class MistyMap(context, packs: ProvincePacks)`: owns the `MapView` (TextureView mode if Task 1 chose A), rebuilds the style with `MapStyle.build(template, packs.mapFiles())` whenever `packs.version` changes while keeping the camera, exposes `projection: MapProjection`, `fun flyTo(lat, lon, zoom)`, `fun follow(on: Boolean)`, and the saved-camera and 5-minute reset behaviour that `MapController` has today.
- `ProvincePacks.mapFiles(): List<File>` (new, tested in `ProvincePacksTest`): the installed `map.pmtiles` files, newest year per province, sorted by id.

Checks: unit tests for `LocationFeed` (prefers the newest fix, drops fixes older than 10 s when a newer one exists) and `mapFiles()`; on the phone, the map opens on your position, follows while you walk, a drag stops following, the location button resumes it; adding and removing a pack in "Your maps" updates the map without losing the camera.

---

### Task 4: Fog on the MapLibre map

Per the Task 1 decision. **A:** `FogView` (the transparent view) drawing the existing fog code through `MistyMap.projection`, redrawn from the camera listener and the mist's own ticks, not clickable. **B:** `FogLayer` owning the image source and the refresh schedule. Either way the fog keeps: the mist drift, `MistPalette` tint, the 25% padded mask built on the worker thread (`MaskPlan`), and `FogStore.version` rebuilds. Checks: the cleared ring and the trail line up with the streets on a fling and a zoom; the existing `MaskPlanTest` still passes; `gfxinfo` no worse than the osmdroid build in `bugs.md` (about 1% janky, p99 17 ms).

### Task 5: Trail, pins and character as map layers

`TrailLayer` (GeoJSON line source plus a dashed line layer, current walk in Ember, earlier walks in the faint grey-blue, split where GPS jumps more than about 330 m, as `data/Trails.kt` does now); `PinLayer` (symbol layer with the pixel icons from `PinSprites`, dots below zoom 10 as `PinRules.mode` says, hidden below zoom 10 for icons); `CharacterLayer` (symbol layer whose image is swapped per animation frame, facing the walking direction, with the mist ring drawn as a circle layer at 50 m). Taps and long-presses go through `MapLibreMap.addOnMapClickListener` and `addOnMapLongClickListener` into the existing `PinRules` logic ("Walk here first" in the fog). Tests: the existing `PinRulesTest` and `TrailsTest` still pass, plus a test for the GeoJSON built from a trail with a GPS jump. Checks on the phone: the pin flows from `workflow.md`.

### Task 6: Postcard with `MapSnapshotter`

Replace the osmdroid tile fetch in `PostcardRenderer.drawTiles` with a `MapSnapshotter` on the same style and packs (probe 4: 157 ms warm); keep the mist cut-out, trail, character, pins, text and credit line drawn on the bitmap. A walk outside every installed pack gets the plain background. Checks: the postcard for a walk inside Friesland and one that crosses into a province without a pack.

### Task 7: Remove osmdroid

Remove `osmdroid-android` and the osmdroid setup in `MistyApplication`, delete the old tile cache once, add `tile.openstreetmap.org` to the CI forbidden list, change MapLibre to `implementation`, update the stats privacy line ("Your trails never leave your phone. Maps are downloaded from maps.potloodgum.com.") and `about_credits` in English and Dutch, and show "© OpenStreetMap contributors · © OpenMapTiles" as the map attribution. Check: `./gradlew testDebugUnitTest assembleDebug assembleRelease`, and the CI grep passes locally.

### Task 8: Check it on the Pixel

Fresh launch, existing-user flow, a short real walk (or the replay of a recorded one), cycling speed gate, pins, postcard, a battery run with `debug/run.sh` against the baseline in `workflow.md`, rotation, background and return, a pack added and removed while the map is showing, a view across a province border with only one pack. Record the results in the results doc.
