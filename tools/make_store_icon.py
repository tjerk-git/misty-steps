"""Renders the 512x512 store icon (fastlane/.../images/icon.png): the cat in a clearing in the mist,
matching the launcher icon.

Usage: python3 tools/make_store_icon.py
"""
import os, sys
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprites import parse, frame

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
N = 512
S = N / 108  # launcher icon units -> pixels

img = Image.new('RGBA', (N, N), (225, 230, 241, 255))
d = ImageDraw.Draw(img)
# drifting fog puffs
for (cx, cy, rx, ry, col) in [(24, 20, 30, 14, (244, 246, 251)), (92, 92, 34, 15, (244, 246, 251)), (86, 12, 26, 10, (236, 239, 247))]:
    d.ellipse([(cx - rx) * S, (cy - ry) * S, (cx + rx) * S, (cy + ry) * S], fill=col + (255,))

# the cleared patch of map, drawn on its own layer and masked to a soft circle
mp = Image.new('RGBA', (N, N), (242, 239, 233, 255))
md = ImageDraw.Draw(mp)
md.rectangle([62 * S, 20 * S, 92 * S, 46 * S], fill=(205, 235, 176, 255))
md.polygon([(14 * S, 70 * S), (28 * S, 66 * S), (42 * S, 70 * S), (56 * S, 80 * S), (62 * S, 92 * S), (14 * S, 92 * S)], fill=(170, 211, 223, 255))
for w, col in [(8, (201, 194, 182)), (6, (255, 255, 255))]:
    md.line([(10 * S, 40 * S), (54 * S, 62 * S), (98 * S, 30 * S)], fill=col, width=int(w * S), joint='curve')
    md.line([(54 * S, 62 * S), (60 * S, 108 * S)], fill=col, width=int(w * S))
for w, col in [(4, (201, 194, 182)), (2.5, (255, 255, 255))]:
    md.line([(30 * S, 10 * S), (40 * S, 48 * S)], fill=col, width=int(w * S))
    md.line([(76 * S, 46 * S), (96 * S, 80 * S)], fill=col, width=int(w * S))
mask = Image.new('L', (N, N), 0)
ImageDraw.Draw(mask).ellipse([18 * S, 18 * S, 90 * S, 90 * S], fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(4 * S))
img.paste(mp, (0, 0), mask)

# the cat, standing, pixel-perfect
chars = {c['id']: c for c in parse(os.path.join(ROOT, 'app/src/main/assets/characters.txt'))}
px = frame(chars['cat'], 1, idle=True)  # 18x18, None = transparent
P = int(3 * S)                          # same 3 units per sprite pixel as the launcher icon
size = 18 * P
ox = (N - size) // 2
oy = (N - size) // 2 - int(2 * S)
sd = ImageDraw.Draw(img)
feet = oy + 17 * P
shadow = Image.new('RGBA', (N, N), (0, 0, 0, 0))
ImageDraw.Draw(shadow).ellipse([N / 2 - 17 * S, feet - 3.2 * S, N / 2 + 17 * S, feet + 3.2 * S], fill=(0, 0, 0, 56))
img.alpha_composite(shadow)  # ImageDraw would paint the shadow opaque, not blend it
for y in range(18):
    for x in range(18):
        c = px[y][x]
        if c is not None:
            sd.rectangle([ox + x * P, oy + y * P, ox + (x + 1) * P - 1, oy + (y + 1) * P - 1], fill=tuple(c) + (255,))

out = os.path.join(ROOT, 'fastlane/metadata/android/en-US/images/icon.png')
img.convert('RGB').save(out)
print('wrote', out)
