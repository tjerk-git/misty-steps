import json
import re
from pathlib import Path

import pytest

import build_style

ROOT = Path(__file__).resolve().parents[3]
TEMPLATE = ROOT / "tools/maps/style/template.json"
GLYPHS = ROOT / "app/src/main/assets/maps/glyphs"


@pytest.fixture(scope="module")
def style():
    return build_style.build_style(str(TEMPLATE))


def test_layer_ids_unique_and_sources_known(style):
    ids = [layer["id"] for layer in style["layers"]]
    assert len(ids) == len(set(ids))
    for layer in style["layers"]:
        if layer["type"] != "background":
            assert layer["source"] == "__SOURCE__", layer["id"]


def test_uses_palette(style):
    background = style["layers"][0]
    assert background["type"] == "background"
    assert background["paint"]["background-color"].upper() == "#F6F3EC"
    assert not [l for l in style["layers"] if l.get("source-layer") == "poi"]


def test_only_asset_urls(style):
    assert not re.search(r"https?:", json.dumps(style))
    assert style["glyphs"] == "asset://maps/glyphs/{fontstack}/{range}.pbf"


def test_fonts_exist_as_glyph_dirs(style):
    stacks = {tuple(l["layout"]["text-font"]) for l in style["layers"] if "text-font" in l.get("layout", {})}
    assert stacks, "the style has no labels"
    for stack in stacks:
        assert len(stack) == 1
        assert (GLYPHS / stack[0]).is_dir(), stack[0]
        assert (GLYPHS / stack[0] / "0-255.pbf").is_file()
