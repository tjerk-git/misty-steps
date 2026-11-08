"""Renders every character's walk frames to a PNG for checking the pixel art.

Usage: python3 tools/preview_sprites.py out.png
"""
import os, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sprites import parse, frame
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
chars=parse(os.path.join(ROOT, 'app/src/main/assets/characters.txt')); S=8
img=Image.new('RGB',(18*S*4+50,18*S*len(chars)+10*len(chars)),(205,214,196))
for ci,c in enumerate(chars):
    assert len(c['body'])==16 and all(len(r)==16 for r in c['body']), (c['id'],'body',len(c['body']),[len(r) for r in c['body']])
    assert len(c['legs'])==4 and all(len(f)==3 and all(len(r)==16 for r in f) for f in c['legs']), (c['id'],'legs')
    for fi in range(4):
        f=frame(c,fi)
        for y in range(18):
            for x in range(18):
                if f[y][x]:
                    for a in range(S):
                        for b in range(S): img.putpixel((fi*(18*S+16)+x*S+a, ci*(18*S+10)+y*S+b), f[y][x])
img.save(sys.argv[1]); print('ok', [c['id'] for c in chars])
