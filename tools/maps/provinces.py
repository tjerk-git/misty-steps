"""Province borders for Misty Steps, from an OpenStreetMap dump.

Provinces are the admin_level 4 boundaries that carry an ISO 3166-2 code ("NL-FR"). From them this
makes:

- one GeoJSON region per province, grown by 1 km, that `pmtiles extract --region` cuts the province's
  map file with (so the map doesn't stop dead at the border);
- `nl-provinces.json` for the app, with simplified borders in the same flat-ring layout as
  `nl-index.json`, so the app can tell which province a coordinate is in.

Reads only a local .osm.pbf file. Never queries an online OSM service.

    uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt \
        python tools/maps/provinces.py --pbf tools/maps/data/netherlands-latest.osm.pbf \
        --out-dir tools/maps/out
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
import osmium
import shapely
from pyproj import Transformer
from shapely import wkb
from shapely.geometry import MultiPolygon, mapping

from index import display_name, polygons

FORMAT = 1
APP_TOLERANCE = 0.003  # degrees, like the country border in nl-index.json
BUFFER_M = 1000.0
ISO_PREFIX = "NL-"

# WGS84 degrees to the Dutch national grid (metres) and back, for buffering by a real distance.
_TO_RD = Transformer.from_crs(4326, 28992, always_xy=True).transform
_FROM_RD = Transformer.from_crs(28992, 4326, always_xy=True).transform


def _project(geom, fn):
    """[geom] with every coordinate pair run through [fn] (a pyproj transform of x and y arrays)."""
    return shapely.transform(geom, lambda c: np.column_stack(fn(c[:, 0], c[:, 1])))


def _read(pbf):
    """{province id: (name, MultiPolygon)} for every admin_level 4 area with an NL- ISO code."""
    factory = osmium.geom.WKBFactory()
    found = {}
    processor = (
        osmium.FileProcessor(pbf)
        .with_areas(osmium.filter.TagFilter(("boundary", "administrative")))
        .with_filter(osmium.filter.EntityFilter(osmium.osm.AREA))
    )
    for area in processor:
        if area.from_way() or area.tags.get("admin_level") != "4":
            continue
        iso = area.tags.get("ISO3166-2", "")
        name = display_name(area.tags)
        if not iso.startswith(ISO_PREFIX) or not name:
            continue
        try:
            geom = wkb.loads(factory.create_multipolygon(area), hex=True)
        except Exception as e:  # broken or cut-off relation
            print(f"skip {area.orig_id()} {name}: {e}", file=sys.stderr)
            continue
        if geom.geom_type == "Polygon":
            geom = MultiPolygon([geom])
        found[iso[len(ISO_PREFIX):].lower()] = (name, geom)
    return dict(sorted(found.items()))


def province_polygons(pbf_path):
    """{province id: MultiPolygon in lon/lat}, ids like "fr"."""
    return {pid: geom for pid, (_, geom) in _read(pbf_path).items()}


def province_names(pbf_path):
    """{province id: Dutch name}."""
    return {pid: name for pid, (name, _) in _read(pbf_path).items()}


def buffered_region(geom, meters=BUFFER_M):
    """[geom] grown by [meters], valid, in lon/lat."""
    grown = _project(shapely.make_valid(_project(geom, _TO_RD)).buffer(meters), _FROM_RD)
    return shapely.make_valid(grown)


def app_json(polys, names, tolerance=APP_TOLERANCE):
    """The app's nl-provinces.json content."""
    return {
        "format": FORMAT,
        "provinces": [
            {"id": pid, "name": names[pid], "polygons": polygons(geom, tolerance)}
            for pid, geom in sorted(polys.items())
        ],
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pbf", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--app-json", default="app/src/main/assets/nl-provinces.json")
    args = ap.parse_args()

    read = _read(args.pbf)
    if len(read) != 12:
        sys.exit(f"Expected 12 provinces, found {len(read)}: {sorted(read)}")
    polys = {pid: geom for pid, (_, geom) in read.items()}
    names = {pid: name for pid, (name, _) in read.items()}

    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    for pid, geom in polys.items():
        region = buffered_region(geom)
        (out / f"{pid}.geojson").write_text(json.dumps(mapping(region)))
        print(f"{pid} {names[pid]}: {region.area:.4f} square degrees with margin", file=sys.stderr)
    Path(args.app_json).write_text(json.dumps(app_json(polys, names), separators=(",", ":")))
    print(f"wrote {args.app_json}", file=sys.stderr)


if __name__ == "__main__":
    main()
