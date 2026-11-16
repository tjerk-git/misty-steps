"""Check the Misty style template and write the file the app ships.

The template has one vector source called `__SOURCE__` that has no URL: the app adds one real source
per installed map pack and copies every layer for each (see `MapStyle.kt`).

    python tools/maps/style/build_style.py
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent.parent.parent / "app/src/main/assets/maps/misty-style.json"
SOURCE = "__SOURCE__"


def build_style(template_path):
    """The style from [template_path], checked: unique layer ids, one source, background first."""
    style = json.loads(Path(template_path).read_text())
    layers = style["layers"]
    ids = [layer["id"] for layer in layers]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate layer ids")
    if layers[0]["type"] != "background":
        raise ValueError("the first layer must be the background")
    if list(style["sources"]) != [SOURCE]:
        raise ValueError(f"the only source must be {SOURCE}")
    for layer in layers[1:]:
        if layer.get("source") != SOURCE:
            raise ValueError(f"layer {layer['id']} must read from {SOURCE}")
    return style


def main():
    style = build_style(HERE / "template.json")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(style, separators=(",", ":")))
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)", file=sys.stderr)


if __name__ == "__main__":
    main()
