"""Sprite sheet parsing shared by the tools (mirrors Characters.kt)."""
OUT=(0x1b,0x1b,0x2a)
def parse(path):
    chars=[]; cur=None; key=None
    for raw in open(path):
        line=raw.rstrip('\n')
        s=line.strip()
        if s.startswith('#'): continue
        if s.startswith('[') and s.endswith(']'):
            cur={'id':s[1:-1],'legs':[]}; chars.append(cur); key=None; continue
        if cur is None: continue
        if '=' in s:
            k,v=[x.strip() for x in s.split('=',1)]
            if k in ('body','legs'):
                key=k
                if k=='legs': cur['legs'].append([])
                else: cur['body']=[]
            else:
                cur[k]=v; key=None
            continue
        if not s: continue
        if key=='body': cur['body'].append(s)
        elif key=='legs': cur['legs'][-1].append(s)
    for c in chars:
        c['pal']={p.split(':')[0]:tuple(int(p.split(':')[1][i:i+2],16) for i in (0,2,4)) for p in c['palette'].split()}
    return chars
def frame(c,i,idle=False):
    g=[[None]*16 for _ in range(16)]
    bob=0 if idle else (1 if i in (1,3) else 0)
    for y,row in enumerate(c['body']):
        for x,ch in enumerate(row):
            if ch!='.' and 0<=y+bob<16: g[y+bob][x]=c['pal'][ch]
    for y,row in enumerate(c['legs'][i]):
        for x,ch in enumerate(row):
            if ch!='.': g[13+y][x]=c['pal'][ch]
    o=[[None]*18 for _ in range(18)]
    for y in range(16):
        for x in range(16): o[y+1][x+1]=g[y][x]
    res=[r[:] for r in o]
    for y in range(18):
        for x in range(18):
            if o[y][x] is None and any(0<=y+dy<18 and 0<=x+dx<18 and o[y+dy][x+dx] is not None for dy,dx in ((0,1),(1,0),(0,-1),(-1,0))):
                res[y][x]=OUT
    return res
