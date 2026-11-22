"""Build the neighbourhoods and streets file (`stats.zip`) that ships next to each province's map.

The app reads it offline: for each town the neighbourhood borders, and for each neighbourhood its named streets,
so the stats page can show "49% of your wijk" and "41 of 120 streets walked" without any network request.

File format 1 (see docs/superpowers/plans/2026-11-22-streets-and-neighbourhoods.md):

    index.json              {"format": 1, "province": "fr", "dataDate": "...", "cities": [<town relation id>, ...]}
    hoods/<town id>.json    {"hoods": [{"type", "id", "name", "kind", "polygons": [[ring, ...], ...]}]}
    streets/<r|w><id>.json  {"streets": [{"name", "lines": [line, ...]}]}   (one per neighbourhood)

A ring or line is a flat list of integers in units of 1e-5 degree, delta-encoded: [lon0, lat0, dlon1, dlat1, ...].

Reads the OpenStreetMap dump from disk and never contacts any service.
"""
import argparse
import json
import math
import sys
import zipfile
from pathlib import Path

from shapely.geometry import LineString, MultiPolygon, Polygon, shape as geojson_shape
from shapely.strtree import STRtree

E5 = 100_000
FORMAT = 1
MIN_STREET_M = 30.0

# Roads and named paths people walk along. Service roads (parking aisles, driveways), tracks and anything
# without a name are left out so that "x of y streets" means something.
STREET_HIGHWAYS = frozenset({
    "residential", "living_street", "pedestrian", "unclassified", "tertiary", "secondary", "primary", "trunk",
    "footway", "cycleway", "path", "steps", "road",
})


def encode_line(coords):
    """[(lon, lat), ...] as delta-encoded integers in units of 1e-5 degree."""
    out = []
    prev_x = prev_y = 0
    for lon, lat in coords:
        x, y = round(lon * E5), round(lat * E5)
        out += [x - prev_x, y - prev_y]
        prev_x, prev_y = x, y
    return out


def decode_line(ints):
    """The inverse of [encode_line]."""
    coords = []
    x = y = 0
    for i in range(0, len(ints) - 1, 2):
        x += ints[i]
        y += ints[i + 1]
        coords.append((x / E5, y / E5))
    return coords


def is_street(tags):
    """A named road or named footpath or cycleway (see [STREET_HIGHWAYS])."""
    return tags.get("highway") in STREET_HIGHWAYS and bool((tags.get("name") or "").strip())


def line_length_m(coords):
    """Length of a (lon, lat) line in metres; flat-earth per segment is plenty at street scale."""
    total = 0.0
    for (lon1, lat1), (lon2, lat2) in zip(coords, coords[1:]):
        dx = (lon2 - lon1) * 111_320.0 * math.cos(math.radians((lat1 + lat2) / 2))
        dy = (lat2 - lat1) * 111_320.0
        total += math.hypot(dx, dy)
    return total


def group_streets(ways, min_m=MIN_STREET_M):
    """[(name, coords)] ways as one street per name: {"name", "lines"}; streets under [min_m] in total are dropped."""
    by_name = {}
    for name, coords in ways:
        by_name.setdefault(name.strip(), []).append(coords)
    return [
        {"name": name, "lines": lines}
        for name, lines in by_name.items()
        if sum(line_length_m(line) for line in lines) >= min_m
    ]


def hood_key(hood):
    """The name a neighbourhood's streets are stored under: 'r<id>' for a relation, 'w<id>' for a way."""
    return f"{'r' if hood['type'] == 'relation' else 'w'}{hood['id']}"


def to_shape(polygons):
    """Polygons as [[outer ring, hole, ...], ...] of (lon, lat) lines into one shapely geometry."""
    parts = [Polygon(rings[0], rings[1:]) for rings in polygons if rings and len(rings[0]) >= 4]
    parts = [p if p.is_valid else p.buffer(0) for p in parts]
    return MultiPolygon([q for p in parts for q in (p.geoms if p.geom_type == "MultiPolygon" else [p])]) if parts else None


def assign_hoods_to_cities(hoods, cities, max_fraction=0.5):
    """
    {town id: [hoods]}: each neighbourhood belongs to the town around its representative point; strays are dropped,
    and so is any area of [max_fraction] of the town or more (the app never picks one that big, so it only costs space).
    """
    shapes = [(c["osmId"], to_shape(c["polygons"])) for c in cities]
    shapes = [(cid, shape) for cid, shape in shapes if shape is not None]
    tree = STRtree([shape for _, shape in shapes])
    out = {}
    for hood in hoods:
        shape = to_shape(hood["polygons"])
        if shape is None:
            continue
        point = shape.representative_point()
        for i in tree.query(point, predicate="intersects"):
            if shape.area < max_fraction * shapes[i][1].area:
                out.setdefault(shapes[i][0], []).append(hood)
            break
    return out


def streets_for_hood(hood, ways):
    """The named streets of one neighbourhood: every way that touches it, grouped by name (under 30 m in total: dropped)."""
    shape = to_shape(hood["polygons"])
    if shape is None:
        return []
    near = [(name, coords) for name, coords in ways if len(coords) >= 2 and shape.intersects(LineString(coords))]
    return group_streets(near)


def write_stats_zip(path, province, data_date, city_hoods, hood_streets):
    """Writes the file described at the top. [city_hoods]: {town id: [hood]}; [hood_streets]: {hood_key: [street]}."""
    def rings(polygons):
        return [[encode_line(ring) for ring in polygon] for polygon in polygons]

    with zipfile.ZipFile(Path(path), "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("index.json", json.dumps(
            {"format": FORMAT, "province": province, "dataDate": data_date, "cities": sorted(city_hoods)}, separators=(",", ":")))
        for city, hoods in sorted(city_hoods.items()):
            body = {"hoods": [
                {"type": h["type"], "id": h["id"], "name": h["name"], "kind": h["kind"], "polygons": rings(h["polygons"])}
                for h in hoods
            ]}
            z.writestr(f"hoods/{city}.json", json.dumps(body, separators=(",", ":"), ensure_ascii=False))
        for key, streets in sorted(hood_streets.items()):
            if not streets:
                continue
            body = {"streets": [{"name": s["name"], "lines": [encode_line(line) for line in s["lines"]]} for s in streets]}
            z.writestr(f"streets/{key}.json", json.dumps(body, separators=(",", ":"), ensure_ascii=False))


def streets_by_hood(hoods, ways):
    """{hood_key: [street]} for many neighbourhoods at once, through one spatial index over the ways."""
    lines = [LineString(coords) for _, coords in ways]
    tree = STRtree(lines)
    out = {}
    for hood in hoods:
        shape = to_shape(hood["polygons"])
        if shape is None:
            continue
        near = [ways[i] for i in tree.query(shape, predicate="intersects")]
        streets = group_streets(near)
        if streets:
            out[hood_key(hood)] = streets
    return out


HOOD_PLACES = frozenset({"quarter", "suburb", "neighbourhood"})
HOOD_ADMIN_LEVELS = frozenset({"9", "10", "11"})


def hood_kind(tags):
    """'quarter', 'suburb' or 'neighbourhood' from the place tag, else 'admin<level>' for an administrative boundary."""
    place = tags.get("place")
    if place in HOOD_PLACES:
        return place
    if tags.get("boundary") == "administrative" and tags.get("admin_level") in HOOD_ADMIN_LEVELS:
        return "admin" + tags["admin_level"]
    return None


def read_osm(pbf, region):
    """
    (neighbourhoods, street ways) of the OpenStreetMap file [pbf] that lie inside [region] (a shapely geometry).
    Neighbourhoods: areas tagged place=quarter/suburb/neighbourhood or administrative levels 9 to 11, with a name,
    kept when their representative point is in the region. Street ways: [is_street] ways that touch the region.
    """
    import osmium  # imported here so the pure helpers above work without pyosmium
    from shapely import wkb
    from shapely.prepared import prep

    inside = prep(region)
    factory = osmium.geom.WKBFactory()
    hoods = []
    areas = (
        osmium.FileProcessor(pbf)
        .with_areas(osmium.filter.TagFilter(("boundary", "administrative"), ("place", "quarter"),
                                            ("place", "suburb"), ("place", "neighbourhood")))
        .with_filter(osmium.filter.EntityFilter(osmium.osm.AREA))
    )
    for area in areas:
        kind = hood_kind(area.tags)
        name = (area.tags.get("name:nl") or area.tags.get("name") or "").split(";")[0].strip()
        if kind is None or not name:
            continue
        try:
            geom = wkb.loads(factory.create_multipolygon(area), hex=True)
        except Exception as e:  # broken or cut-off relation
            print(f"skip {area.orig_id()} {name}: {e}", file=sys.stderr)
            continue
        if geom.is_empty or not inside.contains(geom.representative_point()):
            continue
        polygons = [[list(poly.exterior.coords)] + [list(r.coords) for r in poly.interiors]
                    for poly in (geom.geoms if geom.geom_type == "MultiPolygon" else [geom])]
        hoods.append({"type": "way" if area.from_way() else "relation", "id": area.orig_id(), "name": name,
                      "kind": kind, "polygons": polygons})

    ways = []
    streets = (
        osmium.FileProcessor(pbf, osmium.osm.NODE | osmium.osm.WAY).with_locations().with_filter(osmium.filter.KeyFilter("highway"))
    )
    for way in streets:
        if not is_street(way.tags):
            continue
        try:
            coords = [(n.lon, n.lat) for n in way.nodes]
        except Exception:  # a node outside the extract
            continue
        if len(coords) >= 2 and inside.intersects(LineString(coords)):
            ways.append((way.tags["name"].strip(), coords))
    return hoods, ways


def read_cities(index_path):
    """The towns of the app's `nl-index.json` as {"osmId", "polygons"} with (lon, lat) rings."""
    cities = json.loads(Path(index_path).read_text())["cities"]
    return [{"osmId": c["osmId"],
             "polygons": [[list(zip(ring[0::2], ring[1::2])) for ring in polygon] for polygon in c["polygons"]]}
            for c in cities]


def build_province(pbf, province_id, out_dir, index_path, data_date):
    """Writes `<out_dir>/<province_id>.stats.zip` and returns its path."""
    out_dir = Path(out_dir)
    region = geojson_shape(json.loads((out_dir / f"{province_id}.geojson").read_text()))
    hoods, ways = read_osm(pbf, region)
    cities = [c for c in read_cities(index_path) if (to_shape(c["polygons"]) or region).intersects(region)]
    city_hoods = assign_hoods_to_cities(hoods, cities)
    kept = [h for hs in city_hoods.values() for h in hs]
    path = out_dir / f"{province_id}.stats.zip"
    write_stats_zip(path, province_id, data_date, city_hoods, streets_by_hood(kept, ways))
    return path


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pbf", required=True)
    ap.add_argument("--provinces", nargs="+", required=True)
    ap.add_argument("--out-dir", default=str(Path(__file__).resolve().parent / "out"))
    ap.add_argument("--index", default=str(Path(__file__).resolve().parents[2] / "app/src/main/assets/nl-index.json"))
    ap.add_argument("--data-date", required=True)
    args = ap.parse_args()
    for pid in args.provinces:
        path = build_province(args.pbf, pid, args.out_dir, args.index, args.data_date)
        pack = Path(args.out_dir) / f"{pid}.pmtiles"
        share = f" ({100 * path.stat().st_size / pack.stat().st_size:.1f}% of the map)" if pack.exists() else ""
        print(f"{pid}: {path.stat().st_size / 1e6:.1f} MB{share}", file=sys.stderr)


if __name__ == "__main__":
    main()
