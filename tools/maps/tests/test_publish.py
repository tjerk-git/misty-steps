import hashlib

import publish


def make(tmp_path, pid, data):
    f = tmp_path / f"{pid}.pmtiles"
    f.write_bytes(data)
    return {"id": pid, "name": pid.upper(), "bbox": [4.0, 52.0, 6.0, 53.0],
            "map": {"path": str(f), "url": f"v2/nl/2026/{pid}/map.pmtiles"}}


def test_sha256_matches_hashlib(tmp_path):
    f = tmp_path / "x"
    f.write_bytes(b"misty" * 1000)
    assert publish.sha256_of(str(f)) == hashlib.sha256(b"misty" * 1000).hexdigest()


def test_catalog_has_format_2_and_nl(tmp_path):
    cat = publish.catalog_json(2026, "2026-11-10", [make(tmp_path, "fr", b"a")])
    assert cat["format"] == 2 and cat["country"] == "nl"
    assert cat["year"] == 2026 and cat["dataDate"] == "2026-11-10"


def test_catalog_entry_has_url_bytes_and_sha256(tmp_path):
    entry = publish.catalog_json(2026, "2026-11-10", [make(tmp_path, "fr", b"hello")])["provinces"][0]
    assert entry["id"] == "fr" and entry["bbox"] == [4.0, 52.0, 6.0, 53.0]
    assert entry["map"] == {"url": "v2/nl/2026/fr/map.pmtiles", "bytes": 5,
                            "sha256": hashlib.sha256(b"hello").hexdigest()}


def test_catalog_has_no_stats_key_yet(tmp_path):
    entry = publish.catalog_json(2026, "2026-11-10", [make(tmp_path, "fr", b"a")])["provinces"][0]
    assert "stats" not in entry
    assert "path" not in entry["map"]


def test_provinces_sorted_by_id(tmp_path):
    provinces = [make(tmp_path, "zh", b"z"), make(tmp_path, "dr", b"d"), make(tmp_path, "fr", b"f")]
    ids = [p["id"] for p in publish.catalog_json(2026, "2026-11-10", provinces)["provinces"]]
    assert ids == ["dr", "fr", "zh"]
