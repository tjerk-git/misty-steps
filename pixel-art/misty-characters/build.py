"""Review harness for Misty Steps' characters.txt sprite format.

Reproduces the exact parsing/compositing the Kotlin app does (Characters.kt /
PixelArt.kt) so we can *see* the real game sprites, critique them, and preview
the costume-stripped edit before touching the real asset.

Usage:
    python3 build.py                 # renders assets/characters.txt as shipped
    python3 build.py --after         # renders the edited (costume-stripped) copy
"""
import sys, pathlib, argparse
sys.path.insert(0, "/Users/chairk/.claude/skills/pixel-art-studio/scripts")
from pixelstudio import Sprite

ASSET = pathlib.Path("/Users/chairk/Projects/mistysteps/app/src/main/assets/characters.txt")

# ---- parser mirrors Characters.kt exactly -----------------------------------

class Raw:
    def __init__(self, id):
        self.id = id
        self.props = {}
        self.body = []
        self.legs = []

def parse(lines):
    raws = []
    cur = None
    block = None
    for line in lines:
        s = line.strip()
        if s.startswith("#"):
            continue
        if s.startswith("[") and s.endswith("]"):
            cur = Raw(s[1:-1]); raws.append(cur); block = None
            continue
        if cur is None:
            continue
        if "=" in s:
            key, _, value = s.partition("=")
            key = key.strip(); value = value.strip()
            if key == "body":
                block = cur.body
            elif key == "legs":
                block = []
                cur.legs.append(block)
            else:
                cur.props[key] = value
                block = None
            continue
        if s:
            block.append(s)
    return raws

def outline_rule(sp, outline_color="#1b1b2a"):
    """Pads 16x16 -> 18x18 and outlines every opaque pixel, exactly like PixelArt.outline."""
    out = Sprite(18, 18, snap=False)
    for y in range(16):
        for x in range(16):
            c = sp.get(x, y)
            if c is not None:
                out.px(x + 1, y + 1, c)
    filled = set()
    for y in range(18):
        for x in range(18):
            if out.get(x, y) is not None:
                filled.add((x, y))
    for (x, y) in list(filled):
        pass
    for y in range(18):
        for x in range(18):
            if out.get(x, y) is not None:
                continue
            near = ((x - 1, y) in filled or (x + 1, y) in filled or
                    (x, y - 1) in filled or (x, y + 1) in filled)
            if near:
                out.px(x, y, outline_color)
    return out

def build_frame(raw, legs, bob, palette_hex):
    sp = Sprite(16, 16, snap=False)
    for y, row in enumerate(raw.body):
        ty = y + bob
        if ty >= 16:
            continue
        for x, ch in enumerate(row):
            if ch in palette_hex:
                sp.px(x, ty, palette_hex[ch])
    for y, row in enumerate(legs):
        for x, ch in enumerate(row):
            if ch in palette_hex:
                sp.px(x, 13 + y, palette_hex[ch])
    return outline_rule(sp)

def parse_palette(raw):
    out = {}
    for tok in raw.props["palette"].split():
        letter, _, hexval = tok.partition(":")
        out[letter] = "#" + hexval
    return out

def load(path):
    raws = parse(path.read_text().splitlines())
    chars = []
    for raw in raws:
        assert len(raw.body) == 16 and len(raw.legs) == 4, f"bad sprite {raw.id}"
        pal = parse_palette(raw)
        walk = [build_frame(raw, raw.legs[i], 1 if i % 2 == 1 else 0, pal) for i in range(4)]
        idle = [build_frame(raw, raw.legs[1], 0, pal), build_frame(raw, raw.legs[1], 1, pal)]
        chars.append((raw.id, raw.props.get("name", raw.id), walk, idle))
    return chars

def contact_sheet(chars, out_path, scale=10):
    cols = 6  # 4 walk + 2 idle
    rows = len(chars)
    cell = 18
    sheet = Sprite(cols * (cell + 2) + 2, rows * (cell + 2) + 2, snap=False)
    sheet.clear("#2b2b3d")
    for r, (cid, name, walk, idle) in enumerate(chars):
        frames = walk + idle
        for c, frame in enumerate(frames):
            ox, oy = 2 + c * (cell + 2), 2 + r * (cell + 2)
            for y in range(cell):
                for x in range(cell):
                    col = frame.get(x, y)
                    if col is not None:
                        sheet.px(ox + x, oy + y, col)
    sheet.preview(out_path, scale=scale)
    return sheet

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", default=None)
    ap.add_argument("--out", default="preview_before.png")
    ap.add_argument("--zoom", default=None, help="character id to render solo at high scale")
    args = ap.parse_args()
    path = pathlib.Path(args.file) if args.file else ASSET
    chars = load(path)
    print(f"Loaded {len(chars)} characters from {path}")
    for cid, name, walk, idle in chars:
        print(f"  {cid:10s} {name}")
    if args.zoom:
        cid, name, walk, idle = next(c for c in chars if c[0] == args.zoom)
        solo = Sprite(18 * 2 + 4, 18 + 4, snap=False)
        for i, fr in enumerate([walk[0], idle[0]]):
            ox = 2 + i * 20
            for y in range(18):
                for x in range(18):
                    col = fr.get(x, y)
                    if col is not None:
                        solo.px(ox + x, 2 + y, col)
        out = f"zoom_{cid}.png"
        solo.preview(out, scale=24)
        print(f"-> {out}")
    else:
        contact_sheet(chars, args.out, scale=12)
        print(f"-> {args.out}")
