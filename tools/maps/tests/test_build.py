import build


def test_planetiler_command_identifies_itself_and_limits_zoom():
    cmd = build.planetiler_cmd("data/nl.osm.pbf", "out/nl.pmtiles")
    assert "--http-user-agent=" + build.USER_AGENT in cmd
    assert "--osm-path=data/nl.osm.pbf" in cmd
    assert "--output=out/nl.pmtiles" in cmd
    assert "--minzoom=6" in cmd and "--maxzoom=14" in cmd


def test_planetiler_command_keeps_memory_small():
    cmd = build.planetiler_cmd("a", "b")
    assert "--storage=mmap" in cmd
    assert any(part.startswith("-Xmx") for part in cmd)


def test_extract_command_uses_the_buffered_region():
    cmd = build.extract_cmd("out/nl.pmtiles", "fr", "out")
    assert cmd[-3:] == ["out/nl.pmtiles", "out/fr.pmtiles", "--region=out/fr.geojson"]
