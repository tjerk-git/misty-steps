"""Build a country's town index for Misty Steps from an OpenStreetMap dump.

The index holds the country's border and the border of every town, simplified, so the app can
work out which town you're in without asking any server.

Towns are the places people call their town: in the Netherlands the woonplaatsen (admin_level 10,
ref:woonplaatscode), which is also what the app's old Nominatim lookups returned as "city". The
country border is the union of its municipalities: land and inland water, without the
territorial sea. (A country relation can't be built from a Geofabrik extract anyway: the extract
cuts off its sea border.) Both are recognised by a tag only the country's own areas carry, so
areas across the border that happen to be complete in the extract are left out.

Reads only a local .osm.pbf file (download it once from Geofabrik). Never queries an online
OSM service.

    uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt \
        python tools/maps/index.py tools/maps/data/netherlands-latest.osm.pbf \
        --country-relation 2323309 \
        --town-admin-level 10 --town-tag ref:woonplaatscode \
        --municipality-admin-level 8 --municipality-tag ref:gemeentecode \
        --out app/src/main/assets/nl-index.json
"""
import argparse
import gzip
import json
import sys

import osmium
import shapely
from shapely import wkb
from shapely.geometry import MultiPolygon, Polygon

FORMAT = 1
COUNTRY_TOLERANCE = 0.003  # degrees; the same as the app's old Nominatim country lookups
CITY_TOLERANCE = 0.0003  # degrees; the same as the app's old Nominatim city lookups
DECIMALS = 5  # about 1 m
MIN_COUNTRY_HOLE = 0.00015  # square degrees, about 1 km2: drops slivers between municipalities


def load_boundaries(pbf, wanted):
    """Administrative boundary relations, as {(level, tag): {osm id: (name, geometry)}}.

    [wanted] is a list of (admin level, tag) pairs; a relation is kept for a pair when it has
    that admin level and carries that tag.
    """
    factory = osmium.geom.WKBFactory()
    found = {key: {} for key in wanted}
    processor = (
        osmium.FileProcessor(pbf)
        .with_areas(osmium.filter.TagFilter(("boundary", "administrative")))
        .with_filter(osmium.filter.EntityFilter(osmium.osm.AREA))
    )
    for area in processor:
        if area.from_way():
            continue
        level = area.tags.get("admin_level")
        keys = [(lvl, tag) for (lvl, tag) in wanted if lvl == level and tag in area.tags]
        name = display_name(area.tags)
        if not keys or not name:
            continue
        try:
            geom = wkb.loads(factory.create_multipolygon(area), hex=True)
        except Exception as e:  # broken or incomplete relation (e.g. cut off at the extract's edge)
            print(f"skip {area.orig_id()} {name}: {e}", file=sys.stderr)
            continue
        for key in keys:
            found[key][area.orig_id()] = (name, geom)
    return found


def display_name(tags):
    """Dutch name if tagged, else the name; bilingual names ("Leeuwarden;Ljouwert") keep the first."""
    name = tags.get("name:nl") or tags.get("name")
    return name.split(";")[0].strip() if name else None


def relation_name(pbf, osm_id):
    """English name of a relation if it has one, else its name."""
    for rel in osmium.FileProcessor(pbf, osmium.osm.RELATION):
        if rel.id == osm_id:
            return rel.tags.get("name:en") or rel.tags.get("name")
    sys.exit(f"Relation {osm_id} isn't in {pbf}")


def without_small_holes(geom):
    parts = geom.geoms if isinstance(geom, MultiPolygon) else [geom]
    return MultiPolygon([
        Polygon(p.exterior, [h for h in p.interiors if Polygon(h).area >= MIN_COUNTRY_HOLE]) for p in parts
    ])


def polygons(geom, tolerance):
    """Simplified geometry as [[ring, hole, ...], ...] with flat [lon, lat, ...] rings."""
    simple = shapely.simplify(geom, tolerance, preserve_topology=True)
    parts = simple.geoms if isinstance(simple, MultiPolygon) else [simple]
    out = []
    for p in parts:
        if not isinstance(p, Polygon) or p.is_empty or p.area == 0:
            continue
        rings = [p.exterior] + list(p.interiors)
        out.append([flat(r.coords[:-1]) for r in rings if len(r.coords) >= 4])
    return out


def flat(coords):
    return [round(v, DECIMALS) for xy in coords for v in xy]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pbf")
    ap.add_argument("--country-relation", type=int, required=True)
    ap.add_argument("--town-admin-level", default="10")
    ap.add_argument("--town-tag", required=True, help="tag only this country's towns carry")
    ap.add_argument("--municipality-admin-level", default="8")
    ap.add_argument("--municipality-tag", required=True, help="tag only this country's municipalities carry")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    town_key = (args.town_admin_level, args.town_tag)
    muni_key = (args.municipality_admin_level, args.municipality_tag)
    found = load_boundaries(args.pbf, [town_key, muni_key])
    towns, municipalities = found[town_key], found[muni_key]
    if not towns or not municipalities:
        sys.exit(f"Found {len(towns)} towns and {len(municipalities)} municipalities")
    country_name = relation_name(args.pbf, args.country_relation)
    country_geom = without_small_holes(shapely.unary_union([g for _, g in municipalities.values()]))
    town_cover = shapely.unary_union([g for _, g in towns.values()])
    print(f"{len(municipalities)} municipalities, {len(towns)} towns covering "
          f"{town_cover.area / country_geom.area:.1%} of the country", file=sys.stderr)
    cities = [
        {"osmId": osm_id, "name": name, "polygons": polygons(geom, CITY_TOLERANCE)}
        for osm_id, (name, geom) in sorted(towns.items(), key=lambda kv: kv[1][0])
    ]

    index = {
        "format": FORMAT,
        "country": {
            "osmId": args.country_relation,
            "name": country_name,
            "polygons": polygons(country_geom, COUNTRY_TOLERANCE),
        },
        "cities": cities,
    }
    data = json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode()
    if args.out.endswith(".gz"):  # for serving; the app's asset is plain (the APK is compressed anyway)
        with open(args.out, "wb") as f, gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as gz:
            gz.write(data)  # mtime=0: the same data gives the same file
    else:
        with open(args.out, "wb") as f:
            f.write(data)
    print(f"{country_name}: {len(cities)} towns, {len(data) // 1024} KB json -> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
