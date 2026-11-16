from pathlib import Path

import provinces

FIXTURE = str(Path(__file__).parent / "data" / "provinces.osm")


def polys():
    return provinces.province_polygons(FIXTURE)


def test_finds_provinces_by_iso_code():
    found = polys()
    assert set(found) == {"fr", "gr"}


def test_names_come_from_the_dutch_name():
    assert provinces.province_names(FIXTURE) == {"fr": "Friesland", "gr": "Groningen"}


def test_keeps_every_part_of_a_multipolygon():
    assert len(polys()["fr"].geoms) == 2


def test_buffer_is_valid_and_larger():
    fr = polys()["fr"]
    buf = provinces.buffered_region(fr)
    assert buf.is_valid
    assert buf.contains(fr)
    assert buf.area > fr.area


def test_buffer_of_neighbours_overlaps_but_stays_valid():
    found = polys()
    fr_buf = provinces.buffered_region(found["fr"])
    gr_buf = provinces.buffered_region(found["gr"])
    assert fr_buf.is_valid and gr_buf.is_valid
    assert fr_buf.intersects(gr_buf)


def test_buffer_is_about_a_kilometre():
    fr = polys()["fr"]
    grown = provinces.buffered_region(fr, 1000.0).bounds
    # 1 km is about 0.009 degrees of latitude: south edge moves down by that much.
    assert abs((fr.bounds[1] - grown[1]) - 0.009) < 0.001


def test_app_json_shape():
    found = polys()
    data = provinces.app_json(found, provinces.province_names(FIXTURE))
    assert data["format"] == 1
    assert [p["id"] for p in data["provinces"]] == ["fr", "gr"]
    fr = data["provinces"][0]
    assert fr["name"] == "Friesland"
    assert len(fr["polygons"]) == 2
    for polygon in fr["polygons"]:
        for ring in polygon:
            assert len(ring) % 2 == 0


def test_app_json_simplifies_dense_rings():
    found = polys()
    original_points = sum(len(p.exterior.coords) for p in found["fr"].geoms)
    data = provinces.app_json(found, provinces.province_names(FIXTURE))
    json_points = sum(len(ring) // 2 for poly in data["provinces"][0]["polygons"] for ring in poly[:1])
    assert json_points < original_points
