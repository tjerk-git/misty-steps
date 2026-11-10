"""Made-up walks through Amsterdam for store screenshots, so no real trail or home shows.

Routes follow real streets: they are found in a local OpenStreetMap dump (no online service).
Writes a Misty Steps database (trails.db, schema version 5) and the app's preferences
(misty.xml) to load into the debug app on an emulator; see tools/screenshots/README.md.

    uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt \
        python tools/screenshots/demo_walks.py tools/maps/data/netherlands-latest.osm.pbf OUTDIR
"""
import heapq
import math
import os
import random
import sqlite3
import sys
from datetime import datetime
from zoneinfo import ZoneInfo

import osmium

BBOX = (52.358, 4.865, 52.392, 4.905)  # south, west, north, east: Jordaan, canal belt, Westerpark
WALKABLE = {"residential", "living_street", "pedestrian", "unclassified", "tertiary", "secondary",
            "primary", "footway", "path", "cycleway", "service", "steps", "track"}
STEP_M = 8.0  # one GPS spot every ~8 m, like a walk at ~1.3 m/s with a fix every ~6 s
SPEED = 1.35  # m/s
AMS = ZoneInfo("Europe/Amsterdam")

# Each walk: start time and waypoints (lat, lon); the route between them follows streets.
WALKS = [
    (datetime(2026, 10, 25, 11, 30, tzinfo=AMS), [
        (52.3862, 4.8775),  # Westerpark
        (52.3872, 4.8738),
        (52.3881, 4.8702),
        (52.3866, 4.8688),
        (52.3851, 4.8728),
        (52.3862, 4.8775),
    ]),
    (datetime(2026, 10, 29, 19, 5, tzinfo=AMS), [
        (52.3800, 4.8868),  # Brouwersgracht
        (52.3812, 4.8812),
        (52.3796, 4.8790),  # Marnixstraat
        (52.3760, 4.8792),
        (52.3755, 4.8830),  # Egelantiersgracht
        (52.3780, 4.8855),
    ]),
    (datetime(2026, 11, 1, 10, 12, tzinfo=AMS), [
        (52.3747, 4.8838),  # Westerkerk
        (52.3736, 4.8800),  # Bloemgracht
        (52.3712, 4.8790),  # Rozengracht
        (52.3690, 4.8812),  # Elandsgracht
        (52.3668, 4.8828),  # Lijnbaansgracht
        (52.3700, 4.8850),  # Prinsengracht
        (52.3747, 4.8838),
    ]),
    (datetime(2026, 11, 5, 16, 40, tzinfo=AMS), [
        (52.3760, 4.8866),  # Keizersgracht
        (52.3722, 4.8869),
        (52.3695, 4.8880),
        (52.3678, 4.8905),  # Herengracht
        (52.3712, 4.8902),
        (52.3745, 4.8895),
        (52.3731, 4.8926),  # Dam
    ]),
    (datetime(2026, 11, 8, 14, 1, tzinfo=AMS), [
        (52.3862, 4.8775),  # Westerpark
        (52.3843, 4.8815),
        (52.3822, 4.8846),  # Haarlemmerplein
        (52.3809, 4.8890),  # Haarlemmerdijk
        (52.3800, 4.8868),  # Brouwersgracht
        (52.3785, 4.8838),  # Lindengracht
        (52.3772, 4.8808),  # Westerstraat
        (52.3786, 4.8862),  # Noordermarkt
    ]),
]

# Pins: (walk index, fraction along the walk, label key, note)
PINS = [
    (0, 0.40, "birdsong", None),
    (1, 0.60, "path", None),
    (2, 0.30, "cat", None),
    (2, 0.75, "building", "Westerkerk tower"),
    (3, 0.45, "view", None),
    (4, 0.12, "tree", None),
    (4, 0.55, "ducks", None),
    (4, 0.97, "sit", "Bench by the market"),
]


def dist(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    return math.hypot((b[0] - a[0]) * 110_540, (b[1] - a[1]) * 111_320 * math.cos(lat))


def load_graph(pbf):
    s, w, n, e = BBOX
    nodes, adj = {}, {}
    for way in osmium.FileProcessor(pbf, osmium.osm.NODE | osmium.osm.WAY).with_locations():
        if not way.is_way():
            continue
        if way.tags.get("highway") not in WALKABLE or way.tags.get("foot") == "no" or way.tags.get("access") == "private":
            continue
        pts = [(nd.ref, nd.lat, nd.lon) for nd in way.nodes if nd.location.valid()]
        pts = [p for p in pts if s <= p[1] <= n and w <= p[2] <= e]
        for (i1, la1, lo1), (i2, la2, lo2) in zip(pts, pts[1:]):
            nodes[i1] = (la1, lo1); nodes[i2] = (la2, lo2)
            d = dist((la1, lo1), (la2, lo2))
            adj.setdefault(i1, []).append((i2, d)); adj.setdefault(i2, []).append((i1, d))
    return nodes, adj


def route(nodes, adj, a, b):
    start = min(nodes, key=lambda i: dist(nodes[i], a))
    goal = min(nodes, key=lambda i: dist(nodes[i], b))
    best, prev, todo = {start: 0.0}, {}, [(0.0, start)]
    while todo:
        d, u = heapq.heappop(todo)
        if u == goal:
            break
        if d > best[u]:
            continue
        for v, c in adj.get(u, []):
            if d + c < best.get(v, math.inf):
                best[v] = d + c; prev[v] = u; heapq.heappush(todo, (d + c, v))
    path = [goal]
    while path[-1] != start:
        path.append(prev[path[-1]])
    return [nodes[i] for i in reversed(path)]


def densify(line, rnd):
    out = [line[0]]
    carry = 0.0
    for a, b in zip(line, line[1:]):
        seg = dist(a, b)
        t = STEP_M - carry
        while t <= seg:
            f = t / seg
            out.append((a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
            t += STEP_M
        carry = seg - (t - STEP_M)
    # A little GPS wobble (about 1.5 m).
    return [(la + rnd.gauss(0, 1.5) / 110_540, lo + rnd.gauss(0, 1.5) / 68_000) for la, lo in out]


def cells(points, lat0):
    """~10 m cells within the clearing radius of each point (first one 100 m, then 50 m)."""
    kx = 111_320 * math.cos(math.radians(lat0))
    out = set()
    for i, (la, lo, r) in enumerate(points):
        cx, cy = lo * kx / 10, la * 110_540 / 10
        k = int(r / 10) + 1
        for dx in range(-k, k + 1):
            for dy in range(-k, k + 1):
                if dx * dx + dy * dy <= (r / 10) ** 2:
                    out.add((int(cx) + dx, int(cy) + dy))
    return out


def main():
    pbf, outdir = sys.argv[1], sys.argv[2]
    os.makedirs(outdir, exist_ok=True)
    rnd = random.Random(7)
    nodes, adj = load_graph(pbf)
    db_path = os.path.join(outdir, "trails.db")
    if os.path.exists(db_path):
        os.remove(db_path)
    db = sqlite3.connect(db_path)
    db.executescript("""
        CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL NOT NULL, lon REAL NOT NULL,
            time INTEGER NOT NULL, radius REAL NOT NULL DEFAULT 50.0, session_id INTEGER);
        CREATE TABLE sessions (id INTEGER PRIMARY KEY, start_time INTEGER NOT NULL, end_time INTEGER,
            distance_m REAL NOT NULL DEFAULT 0, points INTEGER NOT NULL DEFAULT 0, battery_start INTEGER,
            battery_end INTEGER, new_area_m2 REAL);
        CREATE INDEX points_session ON points(session_id);
        CREATE TABLE pins (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL NOT NULL, lon REAL NOT NULL,
            time INTEGER NOT NULL, label TEXT NOT NULL, note TEXT, session_id INTEGER);
        CREATE INDEX pins_session ON pins(session_id);
        PRAGMA user_version = 5;
    """)
    seen = set()
    total = 0.0
    walks = []
    for sid, (start, waypoints) in enumerate(WALKS, start=1):
        line = []
        for a, b in zip(waypoints, waypoints[1:]):
            leg = route(nodes, adj, a, b)
            line += leg if not line else leg[1:]
        pts = densify(line, rnd)
        t0 = int(start.timestamp() * 1000)
        d = sum(dist(a, b) for a, b in zip(pts, pts[1:]))
        times = [t0]
        for a, b in zip(pts, pts[1:]):
            times.append(times[-1] + int(dist(a, b) / SPEED * 1000))
        rows = [(la, lo, t, 100.0 if i == 0 else 50.0) for i, ((la, lo), t) in enumerate(zip(pts, times))]
        c = cells([(la, lo, r) for la, lo, _, r in rows], pts[0][0])
        new = len(c - seen) * 100.0
        seen |= c
        total += d
        db.execute("INSERT INTO sessions VALUES (?,?,?,?,?,NULL,NULL,?)", (sid, t0, times[-1], d, len(rows), new))
        db.executemany("INSERT INTO points (lat, lon, time, radius, session_id) VALUES (?,?,?,?,?)",
                       [(la, lo, t, r, sid) for la, lo, t, r in rows])
        walks.append(rows)
        print(f"walk {sid}: {d / 1000:.1f} km, {len(rows)} spots, {new / 10_000:.1f} ha new", file=sys.stderr)
    for wi, frac, label, note in PINS:
        la, lo, t, _ = walks[wi][int(frac * (len(walks[wi]) - 1))]
        db.execute("INSERT INTO pins (lat, lon, time, label, note, session_id) VALUES (?,?,?,?,?,?)",
                   (la, lo, t, label, note, wi + 1))
    db.commit()
    db.close()
    last = walks[-1][-1]
    with open(os.path.join(outdir, "misty.xml"), "w") as f:
        f.write(f"""<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <float name="distance_m" value="{total:.1f}" />
    <string name="character">horse</string>
    <boolean name="pin_tip_seen" value="true" />
</map>
""")
    with open(os.path.join(outdir, "here.txt"), "w") as f:
        f.write(f"{last[1]} {last[0]}\n")  # lon lat, for `adb emu geo fix`
    print(f"total {total / 1000:.1f} km; you stand at {last[0]:.5f}, {last[1]:.5f}", file=sys.stderr)


if __name__ == "__main__":
    main()
