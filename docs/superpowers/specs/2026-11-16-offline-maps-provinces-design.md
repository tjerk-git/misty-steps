# Offline maps by province (MapLibre and PMTiles)

Date: 2026-11-16. Status: design written, waiting for approval. Not built.

This replaces the map format, pack size, build trigger and server parts of
`2026-11-10-offline-maps-design.md` (Mapsforge city packs, built on request, served by a Worker). The
hard rule about OpenStreetMap load, the stats data (neighbourhoods and streets from a pack) and the town
index from that design still stand, and are repeated here where they matter.

## Intent

Misty Steps is released on Android and iOS, in the Netherlands only. People pick the provinces they want
to explore, download them once, and after that the map, fog, trail, pins, postcards and stats all work
without a connection. The maps are built from an OpenStreetMap dump, hosted by us on Cloudflare R2 and
shown with MapLibre, so the same files and the same style serve both platforms.

Why now: the app still loads tiles from `tile.openstreetmap.org` for every user (`ui/MapController.kt`,
`postcard/PostcardRenderer.kt`). That costs volunteer-run infrastructure something per user, and a walking
app should work where the signal is poor. An iOS release is planned, and the old design's Mapsforge
`.map` files only run on Android, so the format has to be one both platforms can read.

## Goals

- First launch offers a province picker. Several provinces can be picked at once, and more added later.
- The app is fully usable without a map. Without a downloaded province you get solid mist: the fog still
  clears and the trail is still recorded.
- After the download, nothing needs a connection.
- Android and iOS share the same map files, style, catalog and stats files.
- The map has its own look in the app's palette (paper, ink, dusk), calm enough to sit under the fog.
- The app never contacts an OpenStreetMap-run or community-run OSM service. The only server it talks to
  is `maps.potloodgum.com`.
- Hosting stays cheap: static files on R2, no server code, no per-request build.

## Non-goals

- Countries other than the Netherlands. The pipeline takes the country from one config file, but no other
  country is switched on.
- Towns or municipalities as a download unit. Provinces only (12).
- Search for places or addresses, routing, offline geocoding.
- Automatic map updates. A new yearly build is offered, never forced.
- Building the iOS app. This design only makes sure nothing here blocks it.
- Rotation, 3D, tilt.

## Anti-goals

Things that would make this a failure even if it technically works:

- A person who skips the download gets a worse app than today in any way other than the missing streets.
  No blocking screens, no nagging more than once per province.
- The fog and the map drift apart while panning or zooming.
- A download that fails halfway leaves a broken pack that the app thinks is fine.
- Anything about where someone is leaves the phone. The server sees province ids and nothing else.
- Battery use or frame rate gets worse than the current build.

## Constraints

- **Hard rule: constant load on OpenStreetMap infrastructure.** However many people use the app, the load
  on OpenStreetMap and Geofabrik servers stays the same. The app contacts none of them. The build
  pipeline downloads once a year from Geofabrik (a dump, never a query API such as Overpass, Nominatim
  or Postpass), with the User-Agent `misty-steps-maps/1 (+https://github.com/tjerk-git/misty-steps)`, and
  at an odd time (03:17 UTC), not on the hour.
- One exception, approved by the owner: Planetiler downloads the ocean polygons as a static file from `osmdata.openstreetmap.de` once per yearly build (plus Natural Earth and lake centre lines from other hosts), so the sea can be drawn. It is a file download, not a query, it is the same size whatever the number of users, and it identifies itself with the same User-Agent.
- CI fails if a forbidden hostname shows up in `app/src/main/**` or `tools/maps/**`. The list in
  `.github/workflows/test.yml` gains `tile.openstreetmap.org`.
- The repo is public, so GitHub Actions minutes are free.
- Licences: map data is ODbL (OpenStreetMap contributors). The OpenMapTiles schema needs "OpenMapTiles"
  named next to it. Both go in the map attribution, the About credits and each pack's `LICENSE.txt`.
- Everything the user reads exists in English and Dutch (the app is localised in both).
- Cloudflare work uses the `cf` CLI, unless the project has a Wrangler config (it doesn't yet).

## Approaches considered

| | A. Mapsforge city packs (the 2026-11-10 design) | B. Vector tiles: PMTiles and MapLibre (chosen) | C. Pre-drawn raster tiles |
|---|---|---|---|
| Runs on iOS | No. Needs a second pipeline and renderer later | Yes, same files and style | Yes |
| Pack size | Smallest (about 4 MB for Leeuwarden) | Larger (tens of MB per province) | Largest, and blurry when zoomed in |
| Work now | Least: fits the osmdroid map | Most: replaces osmdroid under four overlays | Medium, but a renderer is needed in the pipeline |
| Style changes | Rebuild nothing, edit a theme | Edit one style file, no rebuild | Rebuild every tile |

B was picked because iOS is committed, and A would be thrown away or duplicated. C was dropped: heavy,
fixed, and fuzzy at street zoom, which is where the game happens.

Two choices inside B:

- **One country file or one file per province.** The country file is about 645 MB, too much for someone
  who walks in one province. One file per province, with a 1 km margin so the map doesn't stop dead at the
  border.
- **Multiple packs on screen.** A MapLibre source reads one PMTiles file, so each installed pack becomes
  its own source, with the style's layers generated once per source. This is cheap for a handful of
  packs, and it is probe 1 below.

## What the user gets

**First launch (new install): a short onboarding, then the picker.** Three screens, each skippable with
a Skip link at the top, and a Next button at the bottom:

```
  1. Mist covers the map.        2. Your steps clear it.         3. Yours alone.
     [mist and a street]            [trail clearing the mist]       No account, no tracking.
     Misty Steps hides the          Press Start wandering and       Your trails never leave
     Netherlands in mist.           walk, run or cycle. The mist    your phone.
                                    lifts around you.
```

The fourth step is the picker:

```
  Which provinces do you want to explore?

  [ ] Drenthe          52 MB
  [ ] Flevoland        38 MB
  [x] Friesland        62 MB
  [ ] Gelderland      118 MB
  ...
  Total: 62 MB        (sizes here are made up; real ones come from the catalog)

  [ Download 1 province ]
  Skip for now
```

- The three onboarding screens are my wording, to be finalised in English and Dutch. They explain the
  idea and the privacy promise only. The location permission still comes later, with its own explanation,
  when someone first taps Start wandering.
- The picker is an alphabetical list with a check box each and the size of each pack. Nothing is
  pre-selected. It does not ask for location, because asking here would make people decide before they
  know why.
- **Mobile data warning (wanted):** on mobile data, a download over 50 MB first asks: "62 MB. You're on
  mobile data. Download anyway?" with Download and Wait for Wi-Fi. On Wi-Fi it starts straight away.
- Download runs in the background with a progress row per province. It resumes after a cut-off, a reboot
  or a lost connection.
- "Skip for now" on the picker goes straight to the map. The picker is also at "Your maps" on the stats
  page.
- Onboarding is shown once (`onboarding_done` in preferences). It is not shown again after an update or
  after a reinstall that restores nothing (backup is off).

**Existing users after the update.** The onboarding is skipped, since they know the app. The picker appears once, with the province where most of their
trail lies already ticked (worked out on the phone from the stored points, no permission needed). The old
online map is gone in this version, so without it they see solid mist with their trail and clearing
intact. This is the most visible change for them, so the picker says so in one line.

**Walking where there is no map.** When the app is open inside a province that isn't installed, a card
offers it once: "No map of Friesland yet. Download (62 MB)", with "Not now" (remembered per province).
Which province you're in is worked out on the phone from borders that ship in the app.

**"Your maps" card on the stats page.** Lists installed provinces with size and data year, and a Remove
button. When a newer yearly build exists, an Update button appears. "Add a province" opens the picker.

**Outside the Netherlands.** Solid mist, no city or country stats, the world stat keeps working
(unchanged from today).

## Architecture

```
  Geofabrik --(once a year)--> build-maps.yml --> R2  v2/nl/<year>/<id>/map.pmtiles
                                                      v2/nl/<year>/<id>/stats.zip
                                                      v2/catalog.json   (written last)

  App --GET v2/catalog.json-->  maps.potloodgum.com (R2 bucket, custom domain, no Worker)
      --GET (Range) map.pmtiles, stats.zip
      MapLibre reads  pmtiles://file://<filesDir>/maps/nl/<year>/<id>/map.pmtiles
```

### 1. Files on `maps.potloodgum.com`

A public R2 bucket `misty-maps` on a custom domain. R2 has no egress fees, so download volume doesn't
change the bill. Each year's files live under their own path and never change, so they can be cached for
a year, and apps from before a later format change keep working (`v2/` is the format version).

**`v2/catalog.json`** (short cache, one hour):

```json
{
  "format": 2,
  "country": "nl",
  "year": 2027,
  "dataDate": "2027-01-02",
  "provinces": [
    {
      "id": "fr", "name": "Friesland", "bbox": [4.9, 52.8, 6.3, 53.5],
      "map":   { "url": "v2/nl/2027/fr/map.pmtiles", "bytes": 65011712, "sha256": "..." },
      "stats": { "url": "v2/nl/2027/fr/stats.zip",   "bytes": 4194304,  "sha256": "..." }
    }
  ]
}
```

- `id` is the lower-case ISO 3166-2:NL code (`fr`, `gr`, `dr`, `ov`, `fl`, `ge`, `ut`, `nh`, `zh`, `ze`,
  `nb`, `li`).
- `map.pmtiles` is read directly by MapLibre, so it is not zipped. It is one province plus 1 km, zoom
  levels 6 to 14 (MapLibre draws closer in by enlarging zoom 14, which is enough for streets).
- `stats.zip`: `hoods.geojson` (neighbourhood polygons), `streets/<municipality-id>.geojson` (named
  walkable streets, one file per municipality, so the stats page only loads the part it needs), and
  `LICENSE.txt`. The filters are the ones in the 2026-11-10 design (`place` suburb, quarter or
  neighbourhood, admin levels 9 to 11, the street `highway` list, `name` present, `foot` not `no`).

**In the app**, not on the server: `assets/nl-provinces.json` (12 borders, simplified, built by
`tools/maps/index.py`), which says which province a coordinate is in, and the existing
`assets/nl-index.json` (towns and country) for the city and country stats. Neither needs a download.

### 2. Build pipeline (`tools/maps/`, GitHub Actions)

`build-maps.yml`: a yearly cron (`17 3 2 1 *`, the 2nd of January) plus `workflow_dispatch` for a manual
rebuild. A manual run is the way to ship a big OSM change early.

1. Download `netherlands-latest.osm.pbf` from Geofabrik. This is the only Geofabrik request, once a year.
2. Planetiler, OpenMapTiles profile, writes `nl.pmtiles` at zoom 6 to 14.
3. For each province (borders from the same dump, buffered by 1 km into a GeoJSON region):
   `pmtiles extract nl.pmtiles <id>.pmtiles --region=<id>.geojson`. This needs a clustered source
   archive, which Planetiler writes.
4. `tools/maps/export.py` (pyosmium + shapely) writes the stats files per province.
5. Compute sizes and sha256, upload to `v2/nl/<year>/<id>/` through R2's S3-compatible API, then write
   `v2/catalog.json` last, so the catalog never points at files that aren't there yet.

Tools are pinned (Planetiler jar, pmtiles CLI, pyosmium), and the R2 token lives in Actions secrets.
`tools/maps/countries.toml` holds the Geofabrik URL and the province table. The style is built from
`tools/maps/style/` into `app/src/main/assets/maps/misty-style.json` by a script, and committed.

### 3. The map in the app

MapLibre Native (`org.maplibre.gl:android-sdk`, 11.8 or later for PMTiles) replaces osmdroid.

- **Style.** One JSON style in the app's palette. The look: land paper (246, 243, 236); water a soft
  dusk blue without outline; parks and woods muted sage; buildings a shade darker than paper from zoom 14;
  roads white with a thin ink edge, width by class; footpaths and cycleways dotted dusk lines; railways a
  thin ink line; street names in ink along the road; place names in ink; no POI icons (pins are the app's
  own). Glyphs ship in the app as `asset://` PBF ranges (Latin and Latin extended only) because
  PMTiles sources do no caching and nothing may be fetched.
- **Packs on screen.** `MapStyle` takes the style template and, for each installed pack, adds a vector
  source `pmtiles://file://<abs path>` plus a copy of the layer list with suffixed ids. It is rebuilt when
  the set of installed packs changes. With no packs there is no basemap: solid mist.
- **Overlays.** Fog, trail, pins and the character are osmdroid `Overlay` classes today (`fog/FogOverlay.kt`,
  `trail/TrailOverlay.kt`, `pin/PinOverlay.kt`, `character/CharacterOverlay.kt`) that draw through
  `mapView.projection`. They move to a transparent Android `View` stacked above the MapLibre view and
  not clickable, drawing through a small `MapProjection` interface (screen position of a coordinate,
  metres to pixels, zoom, visible bounds) implemented over MapLibre's projection. It redraws from the
  camera-move listener and on the fog's own ticks. Taps, long-presses and drags go to MapLibre, whose
  click listeners drive pins and "stop following". The existing drawing code stays; only its projection
  calls change. Camera follow, the saved camera and the 5-minute reset move to MapLibre's camera
  animation. The location dot (osmdroid's `MyLocationNewOverlay`) is replaced by a small location feed
  that the character overlay reads.
- **Postcard.** `PostcardRenderer` draws the map from a MapLibre snapshotter on the same style and packs,
  instead of osmdroid tiles. A walk outside every installed province gets a mist-only card.
- **Attribution.** "© OpenStreetMap contributors · © OpenMapTiles", where "© OpenStreetMap contributors"
  already shows today, plus the About credits. The postcard keeps its credit line.

### 4. Downloads and state (`com.mistysteps.app.maps`)

- **`MapCatalog`**: fetches `catalog.json` at most once a week when online; keeps the last copy in
  `filesDir/maps/catalog.json`; works from that copy offline.
- **`ProvinceIndex`**: from `assets/nl-provinces.json`. `provinceAt(lat, lon)` is pure Kotlin (bounding box
  first, then the existing point-in-polygon on `Boundary`).
- **`ProvincePacks`**: state per province: NotDownloaded, Downloading(bytes, total), Ready(year),
  UpdateAvailable(year), Failed(reason). Packs live in `filesDir/maps/nl/<year>/<id>/`.
  - A WorkManager job with a network constraint downloads with HTTP `Range` requests into
    `map.pmtiles.part` and `stats.zip.part`, checks the sha256 against the catalog, unzips the stats into a
    temp folder, and renames everything into place last. A half-finished pack never counts as installed.
  - `remove(id)`, `update(id)` (downloads the new year, then removes the old one after the swap).
  - A `version` StateFlow ticks on every change, so the map style, stats and cards reload.
- **Stats.** `Neighbourhoods` reads `hoods.geojson` and `streets/<municipality>.geojson` from installed
  packs, which brings neighbourhoods and streets back for everyone and makes `OsmRings.kt` and the saved
  `files/boundaries/` data unnecessary (old saved data is deleted once).
- **Removing osmdroid.** `MistyApplication`'s osmdroid configuration and the `osmdroid-android`
  dependency go. The old `osmdroid-tiles` cache is deleted once.
- **Copy.** New strings (picker, sizes, mobile data prompt, cards, errors) go in `values/` and `values-nl/`.

## Error handling

| Situation | What happens |
|---|---|
| Never online since install | The picker shows "Go online once to see the maps." Everything else works as mist-only |
| Offline later | Everything runs from the installed packs and the cached catalog |
| Download cut off | `.part` files stay, the job resumes from where it stopped |
| sha256 does not match | Part files deleted, "Download failed. Try again." with a Try again button |
| Pack on disk that MapLibre can't read | Pack removed, card shows "Download again" |
| Catalog `format` newer than the app knows | "Update the app to use new maps." Installed packs keep working |
| Low storage | "Not enough space on this phone." (free space is checked before the download starts) |
| Wandering inside an uninstalled province | One card per province, never repeated after "Not now" |

## Testing

- **JVM unit tests (app):** catalog parsing; `provinceAt` (inside, outside, on a border, in the sea);
  the style builder (N packs make N sources and N layer sets with unique ids; zero packs make none); the
  pack state machine against a fake HTTP server (200, `Range` resume, wrong sha256, cut-off body, unknown
  catalog format); stats file parsing; the upgrade picker's choice of the province with most trail points.
- **pytest (`tools/maps/tests/`):** `export.py` on a small hand-made `.osm` file (multipolygon with a hole,
  quarter, admin 10 relation, streets with and without `name` and `foot=no`, a street crossing two
  neighbourhoods).
- **CI:** the forbidden-hosts check, now with `tile.openstreetmap.org`.
- **On the phone:** download Friesland, switch to airplane mode, walk: map, fog, stats and postcard all
  work. Walk over a province border with only one installed: the 1 km margin shows, then mist. A battery
  run with `debug/run.sh` against the current baseline, and `dumpsys gfxinfo` while panning.

## Risks to check first

These are short probes at the start of the plan, before anything else is built:

1. **Maps on a Pixel.** MapLibre Android reads a local vector PMTiles file (OpenMapTiles schema) built by
   Planetiler and cut by `pmtiles extract`; two packs at once as two sources; glyphs from `asset://`. The
   MapLibre docs show local PMTiles on Android 11.7+ but with a raster example, so vector has to be seen.
   For iOS, check the docs and one tiny spike when the iOS project starts.
2. **Overlay sync (decided: probe it early, and test it on the phone before committing to the approach).** The fog and trail drawn on a view above the map stay glued to the streets during
   fast pans, flings and pinch zoom, with no visible lag. If a frame behind shows, fall back to drawing
   the fog as a MapLibre custom layer, which is a larger job and should be known early.
3. **Pack sizes and build time.** Planetiler for the whole Netherlands, then the 12 extracts. Target:
   each province under 150 MB, the whole job under an hour. If Zuid-Holland or Noord-Holland come out far
   larger, drop building footprints below zoom 14 and POI layers, or lower the top zoom.
4. **Snapshotter.** The postcard renders from local packs with the same style, and finishes in a few
   seconds on the Pixel.
5. **Resumable download.** `Range` resume through R2's custom domain and WorkManager survives a reboot.

## Left to the builder

- Exact colours, line widths and font sizes per style layer, within the palette above.
- Glyph subset and which font, as long as Dutch names (ij, accents) render.
- R2 upload tooling (rclone or the AWS CLI against the S3 API), bucket and custom domain set-up with `cf`.
- How the location feed is shared between the recording service and the map.
- WorkManager details, notification text for background downloads, progress UI.
- The wording of the onboarding screens and of the existing-user line in the picker, in English and Dutch.

## Open for later

- iOS: the packs, catalog, style, borders, index and stats files are platform neutral, and the fog and stats
  maths is plain Kotlin that can be shared (the parked KMP idea). The iOS map view, overlay view and
  downloads are that project's work.
- Municipality or town packs: possible later from the same country file with another `pmtiles extract`.
