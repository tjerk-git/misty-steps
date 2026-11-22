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
