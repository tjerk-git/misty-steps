"""Prototype: a Fox companion for Misty Steps, matching the 16x16 side-view
quadruped format used by Characters.kt (body 16 rows + 4 leg frames of 3 rows,
outline added automatically). Draw with the Sprite API, look, iterate — then
bake the final pixel grid into characters.txt ascii.
"""
import sys
sys.path.insert(0, "/Users/chairk/.claude/skills/pixel-art-studio/scripts")
from pixelstudio import Sprite

# ---- palette -----------------------------------------------------------
F  = "#c9703c"   # main fur (orange)
o  = "#9a4f25"   # shade fur (dark orange)
W  = "#f6ece0"   # white muzzle / chest / tail-tip
E  = "#1c1c22"   # eye
N  = "#14100e"   # nose
K  = "#201d22"   # black ear-tip / near "sock"
k  = "#111116"   # darker black / far "sock"

s = Sprite(16, 16, snap=False)

def put(rows, y0=0):
    for dy, row in enumerate(rows):
        for x, ch in enumerate(row):
            col = {"F": F, "o": o, "W": W, "E": E, "N": N, "K": K, "k": k}.get(ch)
            if col:
                s.px(x, y0 + dy, col)

body = [
    "................",
    "................",
    ".........KK.KK..",
    ".........KFFFKF.",
    ".........FFFFFFF",
    "........FFFFFFF.",
    "..W......FoFFEW.",
    ".oW......FFFWWWN",
    ".oo...WWWFFFWWF.",
    ".o...WWWWFFFFF..",
    "..oFWWWWFFFFFF..",
    "..FFFFFFFFFFFF..",
    "...FoFoFFFFFFF..",
]
put(body)

legs_frames = [
    ["...k.K....k.K...",
     "...k..K...k..K..",
     "..kk..KK.kk..KK."],
    ["....kK.....kK...",
     "....kK.....kK...",
     "....kKK....kKK.."],
    ["...K.k....K.k...",
     "...K..k...K..k..",
     "..KK..kk.KK..kk."],
    ["....Kk.....Kk...",
     "....Kk.....Kk...",
     "....KKk....KKk.."],
]

def with_legs(legs, bob):
    s.clear(None)
    put(body, y0=bob)
    for dy, row in enumerate(legs):
        for x, ch in enumerate(row):
            col = {"K": K, "k": k}.get(ch)
            if col:
                s.px(x, 13 + dy, col)

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
            near = ((x - 1, y) in filled or (x + 1, y) in filled or (x, y - 1) in filled or (x, y + 1) in filled)
            if near:
                out.px(x, y, "#1b1b2a")
    return out

sheet = Sprite(6 * 20 + 2, 20 + 2, snap=False)
for i in range(4):
    with_legs(legs_frames[i], 1 if i % 2 == 1 else 0)
    fr = outlined()
    ox = 2 + i * 20
    for y in range(18):
        for x in range(18):
            c = fr.get(x, y)
            if c is not None:
                sheet.px(ox + x, 2 + y, c)
# idle x2 (legs[1], no bob / bob1)
for j, bob in enumerate([0, 1]):
    with_legs(legs_frames[1], bob)
    fr = outlined()
    ox = 2 + (4 + j) * 20
    for y in range(18):
        for x in range(18):
            c = fr.get(x, y)
            if c is not None:
                sheet.px(ox + x, 2 + y, c)

sheet.preview("fox_preview.png", scale=20)
print("-> fox_preview.png")
