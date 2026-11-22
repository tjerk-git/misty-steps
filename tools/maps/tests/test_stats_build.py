import math

import stats_build as sb


def near(a, b, tol=1e-5):
    return all(abs(x - y) <= tol for p, q in zip(a, b) for x, y in zip(p, q)) and len(a) == len(b)


def test_encode_line_is_delta_encoded_in_units_of_1e5_degree():
    assert sb.encode_line([(5.79, 53.2), (5.80, 53.2)]) == [579000, 5320000, 1000, 0]


def test_decode_line_round_trips_to_five_decimals():
    line = [(5.79012, 53.20034), (5.79111, 53.20123), (5.79001, 53.2), (5.8, 53.19987)]
    assert near(sb.decode_line(sb.encode_line(line)), line)


def test_decode_of_nothing_is_nothing():
    assert sb.decode_line([]) == []
    assert sb.encode_line([]) == []


def test_named_roads_and_paths_are_streets():
    assert sb.is_street({"highway": "residential", "name": "Hoofdstraat"})
    assert sb.is_street({"highway": "cycleway", "name": "Fietspad"})
    assert sb.is_street({"highway": "footway", "name": "Kerkepad"})


def test_service_roads_and_unnamed_ways_are_not_streets():
    assert not sb.is_street({"highway": "service", "name": "Parkeerterrein"})
    assert not sb.is_street({"highway": "residential"})
    assert not sb.is_street({"highway": "footway"})
    assert not sb.is_street({"highway": "residential", "name": "  "})
    assert not sb.is_street({"name": "Gracht", "waterway": "canal"})


def _east(lon, lat, metres):
    return [(lon, lat), (lon + metres / (111_320.0 * math.cos(math.radians(lat))), lat)]


def test_line_length_is_in_metres():
    assert abs(sb.line_length_m(_east(5.8, 53.2, 100.0)) - 100.0) < 0.5


def test_ways_with_one_name_become_one_street():
    ways = [("A", _east(5.80, 53.2, 40)), ("A", _east(5.81, 53.2, 40)), ("A", _east(5.82, 53.2, 40)), ("B", _east(5.83, 53.2, 60))]
    streets = {s["name"]: s for s in sb.group_streets(ways)}
    assert set(streets) == {"A", "B"}
    assert len(streets["A"]["lines"]) == 3
    assert len(streets["B"]["lines"]) == 1


def test_streets_shorter_than_30_metres_in_total_are_dropped():
    ways = [("Short", _east(5.8, 53.2, 20)), ("Long", _east(5.81, 53.2, 40))]
    assert [s["name"] for s in sb.group_streets(ways)] == ["Long"]
    # Two short pieces that add up to enough are kept.
    pieces = [("Two", _east(5.8, 53.2, 20)), ("Two", _east(5.81, 53.2, 20))]
    assert [s["name"] for s in sb.group_streets(pieces)] == ["Two"]


# ---- Task 2: neighbourhoods, streets per neighbourhood, the zip -------------------------------------------------

import json
import zipfile


def square(lon0, lat0, lon1, lat1):
    return [(lon0, lat0), (lon1, lat0), (lon1, lat1), (lon0, lat1), (lon0, lat0)]


def make_hood(hid, rect, kind="quarter", name="Wijk", typ="relation"):
    return {"type": typ, "id": hid, "name": name, "kind": kind, "polygons": [[square(*rect)]]}


def make_city(cid, rect):
    return {"osmId": cid, "polygons": [[square(*rect)]]}


def test_hood_key_uses_r_for_relations_and_w_for_ways():
    assert sb.hood_key({"type": "relation", "id": 12345}) == "r12345"
    assert sb.hood_key({"type": "way", "id": 7}) == "w7"


def test_a_hood_goes_to_the_town_around_its_middle_and_stray_ones_are_dropped():
    cities = [make_city(1, (5.0, 53.0, 6.0, 54.0)), make_city(2, (7.0, 53.0, 8.0, 54.0))]
    inside = make_hood(10, (5.2, 53.2, 5.4, 53.4))
    elsewhere = make_hood(11, (7.2, 53.2, 7.4, 53.4))
    nowhere = make_hood(12, (9.2, 53.2, 9.4, 53.4))
    got = sb.assign_hoods_to_cities([inside, elsewhere, nowhere], cities)
    assert {k: [h["id"] for h in v] for k, v in got.items()} == {1: [10], 2: [11]}


def test_streets_for_a_hood_are_the_ways_that_touch_it_grouped_by_name():
    hood = make_hood(10, (5.0, 53.0, 5.01, 53.01))
    ways = [
        ("In", [(5.002, 53.005), (5.004, 53.005)]),           # inside, about 130 m
        ("In", [(5.005, 53.005), (5.007, 53.005)]),           # same street, second piece
        ("Crossing", [(4.99, 53.005), (5.02, 53.005)]),       # runs through the hood
        ("Far", [(6.0, 54.0), (6.01, 54.0)]),                 # nowhere near
    ]
    streets = {s["name"]: s for s in sb.streets_for_hood(hood, ways)}
    assert set(streets) == {"In", "Crossing"}
    assert len(streets["In"]["lines"]) == 2


def test_write_stats_zip_lays_out_the_index_the_hoods_and_the_streets(tmp_path):
    hood = make_hood(10, (5.0, 53.0, 5.01, 53.01), kind="suburb", name="Binnenstad")
    street = {"name": "Hoofdstraat", "lines": [[(5.002, 53.005), (5.004, 53.005)]]}
    out = tmp_path / "fr.stats.zip"
    sb.write_stats_zip(out, "fr", "2026-11-10", {7: [hood]}, {"r10": [street]})
    with zipfile.ZipFile(out) as z:
        assert sorted(z.namelist()) == ["hoods/7.json", "index.json", "streets/r10.json"]
        index = json.loads(z.read("index.json"))
        assert index == {"format": 1, "province": "fr", "dataDate": "2026-11-10", "cities": [7]}
        hoods = json.loads(z.read("hoods/7.json"))["hoods"]
        assert (hoods[0]["type"], hoods[0]["id"], hoods[0]["name"], hoods[0]["kind"]) == ("relation", 10, "Binnenstad", "suburb")
        ring = sb.decode_line(hoods[0]["polygons"][0][0])
        assert near(ring, square(5.0, 53.0, 5.01, 53.01))
        streets = json.loads(z.read("streets/r10.json"))["streets"]
        assert streets[0]["name"] == "Hoofdstraat"
        assert near(sb.decode_line(streets[0]["lines"][0]), [(5.002, 53.005), (5.004, 53.005)])


def test_a_hood_without_streets_gets_no_streets_entry(tmp_path):
    hood = make_hood(10, (5.0, 53.0, 5.01, 53.01))
    out = tmp_path / "fr.stats.zip"
    sb.write_stats_zip(out, "fr", "2026-11-10", {7: [hood]}, {})
    with zipfile.ZipFile(out) as z:
        assert not [n for n in z.namelist() if n.startswith("streets/")]


# ---- Task 3: spatial helpers, reading the dump, building a province -------------------------------------------------

import pytest
from shapely.geometry import Polygon as ShapelyPolygon, mapping

TINY_OSM = """<?xml version='1.0' encoding='UTF-8'?>
<osm version="0.6" generator="test">
  <node id="1" lat="53.000" lon="5.000"/><node id="2" lat="53.000" lon="5.010"/>
  <node id="3" lat="53.010" lon="5.010"/><node id="4" lat="53.010" lon="5.000"/>
  <node id="6" lat="53.005" lon="5.002"/><node id="7" lat="53.005" lon="5.006"/>
  <node id="8" lat="53.500" lon="5.500"/><node id="9" lat="53.500" lon="5.505"/>
  <way id="100"><nd ref="1"/><nd ref="2"/><nd ref="3"/><nd ref="4"/><nd ref="1"/>
    <tag k="place" v="quarter"/><tag k="name" v="Binnenstad"/></way>
  <way id="101"><nd ref="6"/><nd ref="7"/><tag k="highway" v="residential"/><tag k="name" v="Hoofdstraat"/></way>
  <way id="102"><nd ref="6"/><nd ref="7"/><tag k="highway" v="service"/><tag k="name" v="Parkeerterrein"/></way>
  <way id="103"><nd ref="8"/><nd ref="9"/><tag k="highway" v="residential"/><tag k="name" v="Verweggestraat"/></way>
</osm>
"""


def test_streets_by_hood_uses_one_index_for_many_hoods():
    h1 = make_hood(1, (5.0, 53.0, 5.01, 53.01))
    h2 = make_hood(2, (6.0, 54.0, 6.01, 54.01), typ="way")
    ways = [("A", [(5.002, 53.005), (5.006, 53.005)]), ("B", [(6.002, 54.005), (6.006, 54.005)])]
    got = sb.streets_by_hood([h1, h2], ways)
    assert {k: [s["name"] for s in v] for k, v in got.items()} == {"r1": ["A"], "w2": ["B"]}


def test_a_neighbourhood_as_big_as_its_town_is_dropped():
    city = make_city(1, (5.0, 53.0, 6.0, 54.0))
    small = make_hood(10, (5.2, 53.2, 5.4, 53.4))
    huge = make_hood(11, (5.0, 53.0, 5.9, 53.9))     # most of the town: the app would never pick it
    got = sb.assign_hoods_to_cities([small, huge], [city])
    assert [h["id"] for h in got[1]] == [10]


def test_read_osm_finds_neighbourhoods_and_street_ways_inside_the_region(tmp_path):
    osm = tmp_path / "tiny.osm"
    osm.write_text(TINY_OSM)
    region = ShapelyPolygon(square(4.9, 52.9, 5.1, 53.1))
    hoods, ways = sb.read_osm(str(osm), region)
    assert [(h["type"], h["id"], h["name"], h["kind"]) for h in hoods] == [("way", 100, "Binnenstad", "quarter")]
    ring = hoods[0]["polygons"][0][0]
    assert len(ring) == 5 and near(sorted(set(ring)), sorted(set(square(5.0, 53.0, 5.01, 53.01))))
    assert [name for name, _ in ways] == ["Hoofdstraat"]      # not the service road, not the far-away street


def test_build_province_writes_the_zip_for_the_towns_it_finds(tmp_path, monkeypatch):
    out = tmp_path
    (out / "fr.geojson").write_text(json.dumps(mapping(ShapelyPolygon(square(4.9, 52.9, 5.1, 53.1)))))
    index = tmp_path / "nl-index.json"
    index.write_text(json.dumps({"format": 1, "country": {}, "cities": [
        {"osmId": 7, "name": "Teststad", "polygons": [[[4.95, 52.95, 5.05, 52.95, 5.05, 53.05, 4.95, 53.05, 4.95, 52.95]]]}]}))
    hood = make_hood(100, (5.0, 53.0, 5.01, 53.01), typ="way", name="Binnenstad")
    monkeypatch.setattr(sb, "read_osm", lambda pbf, region: ([hood], [("Hoofdstraat", [(5.002, 53.005), (5.006, 53.005)])]))
    path = sb.build_province("unused.pbf", "fr", str(out), str(index), "2026-11-10")
    assert path == out / "fr.stats.zip"
    with zipfile.ZipFile(path) as z:
        assert sorted(z.namelist()) == ["hoods/7.json", "index.json", "streets/w100.json"]
        assert json.loads(z.read("index.json"))["cities"] == [7]
