"""Prototype: cuter cat head (rounder ears, bigger sparkly eye, blush) reusing the
existing Trail Cat torso/tail/legs (rows 8-15 untouched), only rows 0-7 redesigned.
"""
import sys
sys.path.insert(0, "/Users/chairk/.claude/skills/pixel-art-studio/scripts")
from pixelstudio import Sprite

LETTERS = {
    "G": "#8a8aa6", "g": "#62627e", "w": "#dcdcea", "E": "#23232f",
    "P": "#f4a3bd", "p": "#f7c1d3",  # p = softer blush pink
    "W": "#ffffff",  # eye glint
}

s = Sprite(16, 16, snap=False)

def put(rows, y0=0):
    for dy, row in enumerate(rows):
        for x, ch in enumerate(row):
            col = LETTERS.get(ch)
            if col:
                s.px(x, y0 + dy, col)

# unchanged torso / tail (rows 6-12 from the shipped cat, minus the old head rows)
torso = [
    "..g......GgGEGw.",   # placeholder, overwritten below row-by-row
]

HEAD_V1 = [
    "................",  # 0
    "................",  # 1
    ".........g...g..",  # 2  small rounded ear tips
    "........GgGgGgG.",  # 3  ear bases blend into head
    "........GGGGGGGG",  # 4  full rounded head top
    "........GGGGGGG.",  # 5
    "..g.....GGEEGGw.",  # 6  bigger 2px eye
    ".g......GGwWwwwP",  # 7  glint + cheek + nose
]
TORSO = [
    ".g...PPP..GGww..",
    ".g..PPpPGGGGG...",
    "..gGPppPGGGGG...",
    "..GGGGGGGGGGG...",
    "...GgGgGGGGGG...",
]

put(HEAD_V1, 0)
put(TORSO, 8)

def outlined():
    out = Sprite(18, 18, snap=False)
    for y in range(16):
        for x in range(16):
            c = s.get(x, y)
            if c is not None:
                out.px(x + 1, y + 1, c)
    filled = {(x, y) for y in range(18) for x in range(18) if out.get(x, y) is not None}
    for y in range(18):
        for x in range(18):
            if (x, y) in filled:
                continue
            near = ((x-1,y) in filled or (x+1,y) in filled or (x,y-1) in filled or (x,y+1) in filled)
            if near:
                out.px(x, y, "#1b1b2a")
    return out

fr = outlined()
fr.zoom("cat_v1_zoom.png", 0, 0, 17, 17, scale=28)
s.save_silhouette("cat_v1_silhouette.png", scale=20)
print("done")
for y in range(16):
    row = ''.join('.' if s.get(x,y) is None else '#' for x in range(16))
    print(y, row)
