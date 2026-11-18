"""Minimal, low-risk tweak: keep the shipped Trail Cat pixel-for-pixel, add a
white eye glint + a blush cheek pixel. Verifies no silhouette regression.
"""
import sys
sys.path.insert(0, "/Users/chairk/.claude/skills/pixel-art-studio/scripts")
from pixelstudio import Sprite

LETTERS = {
    "G": "#8a8aa6", "g": "#62627e", "w": "#dcdcea", "E": "#23232f",
    "P": "#f4a3bd", "p": "#e695b3", "W": "#ffffff",
}

BODY = [
    "................",
    "................",
    ".........g...g..",   # kept from shipped
    ".........GgGggG.",   # kept
    ".........GgggggG",   # kept (approx shipped row4 recolored)
    ".........GGGGGG.",   # kept
    "..g......GgGEWw.",   # <- eye glint added (W)
    ".g.......GpGwwwP",   # <- blush added (p)
    ".g...GGG..GGww..",
    ".g..GGgGGGGGG...",
    "..gGGggGGGGG....",
    "..GGGGGGGGGGG...",
    "...GgGgGGGGGG...",
    "................",
    "................",
    "................",
]

s = Sprite(16, 16, snap=False)
for y, row in enumerate(BODY):
    for x, ch in enumerate(row):
        col = LETTERS.get(ch)
        if col:
            s.px(x, y, col)

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
fr.zoom("cat_v2_zoom.png", 0, 0, 17, 17, scale=28)
s.save_silhouette("cat_v2_silhouette.png", scale=20)
for y in range(16):
    row = ''.join('.' if s.get(x,y) is None else '#' for x in range(16))
    print(y, row)
