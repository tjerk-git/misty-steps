# Offline city maps: no more requests to OpenStreetMap's servers

Date: 2026-11-10. Status: design written, waiting for review. Not built.

## Why

Today the app talks to three kinds of OpenStreetMap servers while people use it:

| What | Where in the code | Server |
|---|---|---|
| Map tiles on the map screen | `ui/MapController.kt` (`TileSourceFactory.MAPNIK`) | tile.openstreetmap.org |
| Map tiles on the walk postcard | `postcard/PostcardRenderer.kt` (`MapTileProviderBasic`, `MAPNIK`) | tile.openstreetmap.org |
| City and country border | `stats/BoundaryProvider.kt` | nominatim.openstreetmap.org |
| Neighbourhoods and streets | `stats/Neighbourhoods.kt`, `stats/Overpass.kt` | overpass-api.de, overpass.kumi.systems, overpass.private.coffee |

Update 2026-11-10: version 1.3.1 already removed the Nominatim and Overpass requests. City and
country come from an index shipped in the app; neighbourhoods and streets are only read from what
earlier versions saved. This design removes the tile requests and brings neighbourhoods and streets
back for everyone.

Every one of these costs volunteer-run infrastructure something for every user. That cost grows with the
number of people using Misty Steps. OSM's servers are under real pressure from apps that query them per
user, and Misty Steps should not be one of them. Overpass is also often busy, which makes the stats page
slow or broken. Nominatim gets a rounded (about 1 km) position, which is more than the app needs to share.
And a walking app should work where the signal is poor.

The fix is the Organic Maps model: maps and map data are built once from an OpenStreetMap dump, on our
own server, and people download the city they walk in. After that the app works offline.

## Hard rule: constant load on OpenStreetMap infrastructure

**However many people use Misty Steps, the load on OpenStreetMap and Geofabrik servers stays the same.**

- The app never contacts any OpenStreetMap-run or community-run OSM service: no tile servers, Nominatim,
  Overpass or Postpass (postpass.geofabrik.de), and no other live query API. The only server the app talks
  to is our own `maps.potloodgum.com`.
- The build pipeline gets all its OSM data from a dump, never from a query API. It does not use Overpass,
  Nominatim or Postpass either.
- Postpass was suggested as an alternative to Overpass. It is a good tool for people running one-off
  queries, but it does no caching, so an app querying it per user would put the same per-user load on a
  shared server. It is deliberately not used.
- Only one job downloads from Geofabrik: the monthly refresh. The data it fetches is the same every month
  no matter how many people use the app or how many cities they ask for. Building a city on request reads
  our own copy in R2, never Geofabrik.
- After the first full download, the refresh only fetches Geofabrik's daily change files
  (`pyosmium-up-to-date`, using the replication URL in the extract's header). That is roughly 30 small
  files a month instead of a 1.3 GB re-download. If the change files can't be applied (for example a gap
  Geofabrik no longer keeps), it falls back to one full download.
- Requests to Geofabrik identify themselves: User-Agent
  `misty-steps-maps/1 (+https://github.com/tjerk-git/misty-steps)`.
- The monthly job runs at an odd time (03:17 UTC on the 2nd of the month), not on the hour, so it doesn't
  pile onto the peak of everyone else's cron jobs.
- A check in CI fails if a forbidden hostname shows up in app code (`app/src/main/**`) or in the pipeline
  (`tools/maps/**`): `tile.openstreetmap.org`, `nominatim.openstreetmap.org`, `overpass-api.de`,
  `overpass.kumi.systems`, `overpass.private.coffee`, `postpass.geofabrik.de`, or any URL containing
  `/api/interpreter`. The attribution text ("© OpenStreetMap contributors") doesn't match, so it needs no
  exception.

Load budget, all per month:

| From | Who | How often | Size | Grows with users? |
|---|---|---|---|---|
| download.geofabrik.de | refresh job | once (first run) | 1.3 GB | no |
| download.geofabrik.de | refresh job | once a month | about 30 change files, tens of MB | no |
| Any OSM server | the app | never | 0 | no |
| maps.potloodgum.com (ours) | the app | catalog weekly, index on change, pack once per city | KB to a few MB | yes, our cost only |

## What the user gets

- When you start wandering in a Dutch town whose map you don't have, a card offers it:
  "No map of Leeuwarden yet. Download (4 MB)" with "Not now". "Not now" is remembered for that town.
- If nobody has asked for that town before, its map has to be made first:
  "Leeuwarden's map is being made. It shows up by itself in a few minutes." The app keeps trying
  in the background.
- Once downloaded, the map, fog, trail, pins, postcard and all stats (neighbourhood, streets, city,
  country) work without a connection.
- Where you have no map, you see solid mist. The fog still clears and your trail is still recorded. You
  just don't see the streets underneath until you download that town.
- The map has its own look: paper, ink and dusk colours to match the app, not the standard OSM style.
- A "Your maps" card on the stats page lists your towns with size and data month, with Update and Remove,
  and "Add a town" to search all Dutch municipalities and download one before a trip.
- Outside the Netherlands: solid mist, no city or country stats ("No maps for this country yet"). The world
  stat keeps working.
- Nothing about where you are is sent anywhere. The app works out which town you're in on the phone and
  asks for that town by its id.

## Scope

In: the Netherlands, one pack per municipality (OSM `admin_level=8`), built when someone first asks for it
and refreshed monthly after that. The pipeline is set up so adding a country is one entry in a config
file, but no other country is switched on.

Out: other countries, MapLibre or vector tiles, search for places or addresses, routing, automatic pack
updates, sharing packs between phones.

## Architecture

```
  Geofabrik  --(monthly: change files)-->  refresh-maps.yml  --> R2: src/nl.osm.pbf
                                                 |                   v1/catalog.json
                                                 |                   v1/nl/index.json.gz
                                                 '--> rebuilds every city already built

  App --GET v1/nl/cities/<id>.zip--> Worker (maps.potloodgum.com)
                                       |  in R2?  yes -> serve the zip
                                       |          no  -> start build-city.yml, answer 202
                                       v
                               build-city.yml (reads src/nl.osm.pbf from R2, never Geofabrik)
                                       '--> R2: v1/nl/cities/<id>.zip
```

### 1. Files on `maps.potloodgum.com`

All files are versioned under `v1/` so a later format change can live next to this one.

**`v1/catalog.json`**: the countries we have maps for.

```json
{
  "format": 1,
  "countries": [
    { "code": "nl", "name": "Netherlands", "bbox": [3.31, 50.75, 7.23, 53.56],
      "index": "v1/nl/index.json.gz", "indexBuilt": "2026-12-02" }
  ]
}
```

**`v1/nl/index.json.gz`**: the country border and every town. Since 1.3.1 the same file already
ships in the app as `assets/nl-index.json` (built by `tools/maps/index.py`); the app downloads it
from the server only when `indexBuilt` in the catalog is newer than the copy it has.

```json
{
  "format": 1,
  "country": { "name": "Netherlands", "osmId": 2323309, "polygons": [[[...]]] },
  "cities": [
    { "osmId": 162081, "name": "Leeuwarden", "polygons": [[[...]]] }
  ]
}
```

`polygons` is a list of polygons, each a list of flat `[lon0, lat0, lon1, lat1, ...]` rings (outer
first, then holes), so they load straight into the existing `Boundary`. Towns are the woonplaatsen
(`admin_level=10` with `ref:woonplaatscode`, about 2,500), the same areas the old Nominatim lookups
returned as "city", simplified with a 0.0003 degree tolerance. The country border is the union of
the municipalities (`admin_level=8` with `ref:gemeentecode`): land and inland water, no territorial
sea, simplified with 0.003. (A country relation can't be assembled from a Geofabrik extract: its sea
border is cut off.)

City packs stay per municipality: the offer card names the municipality whose pack covers the town
you're in.

**`v1/nl/cities/<osmId>.zip`**: one city pack.

| File | What |
|---|---|
| `manifest.json` | `format` (1), `osmId`, `name`, `dataDate` (date of the OSM data), `builtAt`, sizes |
| `map.map` | Mapsforge map of the municipality plus 1 km around it, so the map doesn't stop dead at the border |
| `hoods.geojson` | Neighbourhood polygons (see filters below) as a FeatureCollection; properties `osmType`, `osmId`, `name`, `kind` |
| `streets.geojson` | Named walkable streets as MultiLineStrings, grouped by name; property `hoods`: the osm ids of the neighbourhoods each street lies in |
| `LICENSE.txt` | ODbL notice: data © OpenStreetMap contributors, available under the Open Database License |

### 2. Build pipeline (`tools/maps/`, GitHub Actions)

The repo is public, so Actions minutes are free.

**`tools/maps/countries.toml`**

```toml
[nl]
name = "Netherlands"
geofabrik = "https://download.geofabrik.de/europe/netherlands-latest.osm.pbf"
country_relation = 2323309
town_admin_level = 10
town_tag = "ref:woonplaatscode"
municipality_admin_level = 8
municipality_tag = "ref:gemeentecode"
```

**`refresh-maps.yml`** (cron `17 3 2 * *`, plus manual run):

1. Fetch `src/nl.osm.pbf` from R2. If it isn't there (first run), download it once from Geofabrik.
2. Bring it up to date with `pyosmium-up-to-date` (Geofabrik change files). Fall back to one full download
   only if that fails.
3. Upload the updated `src/nl.osm.pbf` to R2.
4. Build `v1/nl/index.json.gz` (`tools/maps/index.py`) and update `v1/catalog.json`.
5. Rebuild every city pack that exists in R2, with the same steps as `build-city.yml`. The cities are split
   into a matrix of batches of 20, so no job hits Actions' 6-hour limit.

**`build-city.yml`** (`workflow_dispatch`, inputs `country`, `osm_id`):

1. Fetch `src/<country>.osm.pbf` from R2. Never from Geofabrik.
2. Take the city's border from the index, buffer it by 1 km and write a `.poly` file.
3. `osmium extract --polygon city.poly --strategy smart` to get a small `city.osm.pbf`.
4. `osmosis` with the mapsforge-map-writer plugin to write `map.map`, using a tag mapping that keeps only
   what `misty.xml` draws, so files stay small.
5. `tools/maps/export.py` (pyosmium + shapely) to write `hoods.geojson` and `streets.geojson`:
   - Neighbourhoods: ways and relations with `place` = `suburb`, `quarter` or `neighbourhood`, and
     relations with `boundary=administrative` and `admin_level` 9, 10 or 11, inside the city. These are
     the same filters as today's `Neighbourhoods.hoodQuery`. `kind` is the `place` value, or `admin` plus
     the admin level (`admin10`), the same as `Neighbourhoods.parseHoods` makes now, so `pick` ranks them
     the same way. pyosmium's area handler joins relation members into rings, so the phone no longer has
     to (`OsmRings` goes away).
   - Streets: ways with `highway` in residential, living_street, pedestrian, unclassified, tertiary,
     secondary, primary, footway, path, cycleway, service, track or steps, with a `name` and without
     `foot=no` (same as `Neighbourhoods.streetQuery`). Grouped by name. Each street gets the ids of the
     neighbourhoods it crosses (a shapely intersection), matching what Overpass's area query returned.
6. Zip with `manifest.json` and `LICENSE.txt`, upload to `v1/<country>/cities/<osm_id>.zip`, and delete
   the `pending/` marker.

Tools are pinned: osmium-tool and pyosmium from apt or pip with fixed versions, and osmosis plus the
mapsforge writer jar from Maven Central, cached by `actions/cache`. Uploads to R2 use an R2 API token kept
in the repo's Actions secrets.

### 3. Worker (`maps/worker/`)

Static files, plus one bit of logic. Set up like `fdroid/site`, deployed with the `cf` CLI on
`maps.potloodgum.com`, with an R2 bucket binding `misty-maps`.

- `GET v1/catalog.json`, `GET v1/<country>/index.json.gz`: served from R2 with `Cache-Control` of an hour.
- `GET v1/<country>/cities/<osmId>.zip`:
  - In R2: serve it with `ETag`, `Content-Length` and a long `Cache-Control`.
  - Not in R2: check that `osmId` is in that country's index. The index is the allowlist, so at most
    about 340 Dutch packs can ever exist. Anything else gets 404.
  - If `pending/<country>/<osmId>` exists and is under an hour old, answer
    `202 {"status": "building", "retryAfter": 300}`.
  - Otherwise write the marker, start `build-city.yml` through the GitHub API (a fine-grained token with
    only `actions: write` on this repo, kept as a Worker secret) and answer the same 202.
  - A marker older than an hour means the build failed. The next request starts it again.
- `HEAD` on a city zip never starts a build. It answers 200 with `Content-Length` when the pack exists
  (the offer card shows the size), 202 when the id is valid but the pack hasn't been made yet (the card
  says just "Download"), and 404 otherwise.
- The Worker logs no request details beyond Cloudflare's defaults and gets no position from the app, only
  the city id.

### 4. App

New package `com.mistysteps.app.offline`:

- **`MapCatalog`**: fetches `catalog.json` (at most once a week, when online) and the country index
  (when `indexBuilt` changes), and keeps both in `filesDir/maps/`. Gives back the cached copy when offline.
- **`CityIndex`**: parsed index. `cityAt(lat, lon): City?` and `country: Boundary`, both using
  `Boundary.contains` (bbox check first, then the existing banded point-in-polygon test). Pure Kotlin,
  testable on the JVM.
- **`CityPacks`**: the installed packs in `filesDir/maps/<country>/<osmId>/`.
  - `installed()`, `state(city)`: NotDownloaded, Building, Downloading(progress), Ready(manifest), Failed.
  - `download(city)`: runs as a WorkManager job with a network constraint. 200: stream to a temp file,
    unzip into a temp folder, check the manifest, then rename the folder into place, so a half-finished
    pack never shows up. 202: wait `retryAfter` and try again, giving up after about 2 hours (Failed).
    404 or a broken zip: Failed.
  - `remove(city)`, `update(city)` (download again when the catalog says newer data exists).
  - A `version` StateFlow that ticks on every change, so the map and stats can reload.
- **`OfflineMaps`**: builds osmdroid's `MapsForgeTileSource` from all installed `map.map` files and
  `assets/maps/misty.xml`, plus a `MapsForgeTileProvider` with a disk cache of drawn tiles. It is rebuilt
  when `CityPacks.version` changes. One provider is shared by the map and the postcard renderer.

Changes to existing code:

- **`MistyApplication`**: call `AndroidGraphicFactory.createInstance(this)` (Mapsforge needs it). Delete
  the old `osmdroid-tiles` cache once. The user agent setting stays but is no longer needed for OSM.
- **`ui/MapController`**: set the map's tile provider from `OfflineMaps` instead of `MAPNIK`. Set the tiles
  overlay's loading background and line colour to the mist base colour, so places without a pack show
  plain mist instead of osmdroid's grey grid.
- **`postcard/PostcardRenderer`**: draw tiles from `OfflineMaps` instead of a new `MapTileProviderBasic`.
  A walk outside any pack gets the plain paper background it already falls back to.
- **`stats/StatsRepository`**: city from `CityIndex.cityAt`, country from `CityIndex.country`. No network
  in `compute`. Outside a supported country: city and country are null with the problem text
  "No maps for this country yet."
- **`stats/Neighbourhoods`**: reads `hoods.geojson` and `streets.geojson` from the installed pack. Without
  a pack: `NeighbourhoodResult(null, "Download Leeuwarden's map to see neighbourhoods and streets.")`.
  `pick` and its ranking stay as they are. The streets for a neighbourhood are the ones whose `hoods`
  include its id.
- **Removed**: `stats/Overpass.kt`, `stats/OsmRings.kt` (and their tests move to pytest for
  `export.py`), and the network parts of `stats/BoundaryProvider.kt` (the class goes; `Boundary` stays).
  `Boundary.overpassAreaId` goes too. Old caches in `files/boundaries/` are deleted on the first start
  after the update.
- **`ui/StatsScreen`**: the "Your maps" card, and new wording for the privacy line: "Maps and borders come
  from OpenStreetMap data, made into town maps on our own server. You download the towns you walk in.
  Nothing about where you are is sent." Error texts that name OpenStreetMap's servers go.
- **`ui/MistyScreen`**: the offer card and the "being made" card. The OSM attribution stays.
- **Strings and metadata**: `about_credits` becomes "Map data © OpenStreetMap contributors (ODbL). Maps
  drawn with Mapsforge." The network paragraph in `fastlane/.../full_description.txt` and the README
  privacy notes get the same change.
- **Dependencies**: `org.osmdroid:osmdroid-mapsforge` (matching osmdroid 6.1.20), the Mapsforge Android
  libraries it needs, and `androidx.work:work-runtime-ktx`. All are open source and fine for F-Droid.

### 5. Map style: `assets/maps/misty.xml`

A Mapsforge render theme in the app's palette. It sits under the fog, so it should read clearly where
you've cleared it but stay calm.

| Layer | Look |
|---|---|
| Land | Paper (246, 243, 236) |
| Water, rivers, canals | soft Dusk blue, no outline |
| Parks, grass, woods | muted sage, woods slightly darker |
| Buildings | a shade darker than paper, from zoom 15 |
| Roads | white with a thin Ink edge, width by road class |
| Footpaths, cycleways, steps | dotted Dusk lines, from zoom 15 |
| Railways | thin Ink line with ticks |
| Street names | Ink, small, along the road, from zoom 16 |
| Place names | Ink, from zoom 12 |
| POI icons | none: pins are the app's own places |

The tag mapping for the writer keeps only these features.

## Error handling

| Situation | What happens |
|---|---|
| Never been online since the update | No offer card. Stats: "Go online once to fetch the map list." |
| Offline later | Everything runs from the cached catalog, index and packs |
| Download cut off | Temp files deleted. WorkManager tries again |
| Build takes longer than 2 hours or keeps failing | Failed: "Couldn't make the map. Try again later." with a Try again button |
| Broken pack on disk | Deleted, card shows "Download again" |
| Pack or index with a `format` newer than the app knows | "Update the app to use this map." |
| Low storage | The download fails with "Not enough space on this phone." |

## Testing

- **JVM unit tests (app)**: index parsing and `cityAt` (inside, outside, holes, between two cities);
  manifest and GeoJSON parsing into `Boundary` and `Street`; the street-to-neighbourhood selection;
  `CityPacks` states with a fake HTTP client (200, 202 then 200, 404, cut-off body, unknown format).
- **pytest (`tools/maps/tests/`)**: `export.py` and `index.py` on a small hand-made `.osm` file containing
  a multipolygon relation with a hole, a `place=quarter` way, an admin 10 relation, streets with and
  without `name` and `foot=no`, and a street crossing two neighbourhoods.
- **CI**: the forbidden-hosts check (see the hard rule), next to the existing tests.
- **On the phone**: download Leeuwarden, switch to airplane mode, walk: map, fog, stats, postcard all work.
  Walk across the municipal border: the 1 km margin shows, then mist. A battery run with `debug/run.sh`
  compared with the current baseline, because Mapsforge draws tiles on the phone.

## Risks to check first

These come first in the implementation plan, as short probes, before anything else is built:

1. Whether `osmdroid-mapsforge` 6.1.20 works with a current Mapsforge release and Compose's `AndroidView`,
   and whether several `.map` files load into one tile source.
2. How long the mapsforge writer takes on Actions for Amsterdam (the largest municipality), and how big
   the pack gets. Target: under 10 minutes, under 25 MB.
3. Battery and frame rate of on-phone tile drawing compared with today.
