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
import json
import math
import zipfile
from pathlib import Path

from shapely.geometry import LineString, MultiPolygon, Polygon
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


def assign_hoods_to_cities(hoods, cities):
    """{town id: [hoods]}: each neighbourhood belongs to the town around its representative point; strays are dropped."""
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
