# Offline maps: probe results

Date: 2026-11-16. Plan: `docs/superpowers/plans/2026-11-16-offline-maps-1-probes-and-pipeline.md`.
Spec: `2026-11-16-offline-maps-provinces-design.md`. Phone: Pixel 10 Pro, MapLibre Native Android 11.8.0.

## Summary

| Probe | Result |
|---|---|
| 1. Vector PMTiles, glyphs, several packs, bad files | Pass (names with ê, â, û not seen rendered yet) |
| 2. Overlay view stays glued to the map | **Fail.** Fog, trail, pins and character cannot live on a view above the map |
| 3. Pack sizes and build time | Pass: 58 MB and 106 MB, 9 minutes for the country |
| 4. Snapshotter for the postcard | Pass: 157 ms warm |
| 5. R2 range requests | Not run yet: needs the bucket and hostname (waiting for the owner's go-ahead) |

**Decision: drop the overlay-view approach.** Draw the fog, trail, pins and character as layers inside MapLibre,
so they are rendered in the same frame as the map. The spec's Overlays paragraph is changed to say so. The
fog-as-image-layer idea below needs one more probe at the start of plan 2, before any migration work.

## 1. Maps and glyphs: pass

- Friesland (58 MB) loads from a local file with `pmtiles://file://`, vector tiles in the OpenMapTiles schema. Streets,
  water, parks, buildings, railways and place names all draw, at zoom 6 to 18 (zoom 14 data enlarged above 14).
- Glyphs from `asset://maps/glyphs/Noto Sans Regular/...` work even with spaces in the folder names. Street names,
  place names and "IJlst" render.
- Two packs at once (Friesland and Flevoland) near Lemmer, where their 1 km margins overlap: one source per pack, no seams,
  no doubled labels.
- A corrupt pack (random bytes), an empty pack and no packs at all: MapLibre logs "Error fetching PMTiles header", the
  screen keeps drawing the background colour, nothing crashes.
- Not seen yet: a name with ê, â or û. The glyph ranges (0 to 767) include them. Check once, with a Frisian village.
- The style needs tuning. In town centres the dotted footpath layer dominates (it draws every footway and sidewalk).
  Limit it to named paths and tracks, and draw it lighter.

## 2. Overlay sync: fail

Method: a debug screen draws a blue dot with MapLibre itself at nine fixed coordinates, and a red cross at the same
coordinates from a transparent view above the map (the plan's design). A screen recording at 1080 x 2400 during
repeated pans shows how far the cross is from its dot. The gap when still is about 2 px, so the measurement itself is sound.

| Variant | Gap while the map moves |
|---|---|
| Overlay redraws from the camera listener and every Choreographer frame (the plan's design) | up to 25 to 38 px, about 10 to 14 dp, in some frames |
| Overlay redraws only when MapLibre reports a finished frame | worse: about 23 px on average while moving |
| Map in TextureView mode, same redraws | better at medium speed (p95 5 px at 10 to 40 px per frame) but still frames with about 35 px |

The target was under 3 dp in every frame. None of the variants reach it: the default map draws on its own surface,
which is composited one frame apart from the views above it, and the TextureView mode only narrows the gap.
Frame rate was fine (0.32% janky, p99 12 ms while panning the plain map).

**What replaces it.** MapLibre can draw everything in the same frame as the map:

- Fog: the existing mask bitmap (built on a worker thread, placed by `MaskPlan`) goes in as an image source with four
  corner coordinates and a raster layer, so MapLibre moves and scales it with the map. Probe 2b, first in plan 2: does
  an image source accept a new bitmap a few times a second without a visible flash, and does the drifting mist still look
  right?
- Trail: a GeoJSON line layer, dashed, with a data-driven colour for the current walk.
- Pins: a symbol layer with the existing pixel icons, and tap and long-press through MapLibre's click listeners.
- Character: a symbol layer whose image is swapped per animation frame, and a small rotation-free offset for the feet.

If the image source does not hold up, the fallback is a MapLibre custom layer for the fog (a larger job).

## 3. Sizes and build time: pass

Dump from 2026-11-10 (1.4 GB), Planetiler OpenMapTiles profile, zoom 6 to 14, on an 8 GB Mac with a 4 GB heap.

| File | Size | Time |
|---|---|---|
| Netherlands (`nl.pmtiles`) | 679 MB | 545 s (9 min) |
| Friesland (`fr.pmtiles`) | 58.4 MB | under 1 s to cut |
| Zuid-Holland (`zh.pmtiles`) | 106.2 MB | under 1 s to cut |
| Flevoland (`fl.pmtiles`) | 25.7 MB | under 1 s to cut |

The targets were under 150 MB per province and under an hour for the whole job. The two biggest provinces were not both
measured: Noord-Holland and Gelderland may be larger than Zuid-Holland, so measure all twelve in plan 4.
Planetiler downloaded its static sources once: ocean polygons from `osmdata.openstreetmap.de` (approved by the
owner, recorded in the spec), Natural Earth and lake centre lines. Its attribution string is
"OpenMapTiles, OpenStreetMap contributors", as the spec requires.

## 4. Snapshotter: pass

`MapSnapshotter` renders 1080 x 1350 from the same style and local packs in 388 ms cold and 157 ms warm, with the
attribution drawn into the image. A box with no pack gives the plain background in 36 ms and does not crash. Not tested
in airplane mode: the style contains no web addresses and the packs are local files.

## 5. R2 range requests: not run yet

Needs a new R2 bucket (`misty-maps`) with the public hostname `maps.potloodgum.com`. Waiting for the owner's go-ahead,
since this creates a public hostname on their account. The plan's test is a 1 KB range request (expect `206`) and a
resumed download compared by sha256. Mobile data and reboot resume are covered by plan 3.

## Other findings

- Zeeland's code is `ze`, not `zl`. Friesland's name in OSM is "Fryslân".
- Planetiler downloads need a User-Agent flag, which the build script passes.
- The build wrote all 12 province borders to `app/src/main/assets/nl-provinces.json` (32 KB) and regions of 45 to 380 KB each.
