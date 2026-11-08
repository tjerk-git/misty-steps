#!/usr/bin/env python3
"""Summarise a Misty Steps battery log: how fast the battery drains in each app state.

Usage: python3 debug/summarize.py runs/<run>/battery.csv [runs/<run>/sessions.csv]

Each row in battery.csv is the state from that moment until the next row. Drain between two
rows is counted against the earlier row's state. Uses the charge counter (µAh) when the phone
reports it, otherwise the battery percentage. Time spent charging, and gaps where the app was
restarted, are left out.
"""
import csv
import sys
from collections import defaultdict


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def state(r):
    walk = r["recording"] == "1"
    map_on = r["map_visible"] == "1"
    screen = r["screen_on"] == "1"
    if walk and r.get("in_vehicle") == "1":
        return "recording, in a vehicle (clearing paused)"
    if walk and map_on:
        return "walking, map on screen"
    if walk:
        return "walking, screen off / app hidden" if not screen else "walking, other app on screen"
    if map_on:
        return "map on screen, not walking"
    return "app in background, not walking"


def main(battery_path, sessions_path=None):
    with open(battery_path, newline="") as f:
        rows = list(csv.DictReader(f))
    if len(rows) < 2:
        print("Not enough samples yet.")
        return

    # Estimate full capacity so % can be converted to mAh and back.
    caps = [num(r["charge_uah"]) / num(r["level_pct"]) * 100
            for r in rows if num(r["charge_uah"]) and num(r["level_pct"])]
    cap_uah = sorted(caps)[len(caps) // 2] if caps else None

    hours = defaultdict(float)
    used_uah = defaultdict(float)
    used_pct = defaultdict(float)
    for a, b in zip(rows, rows[1:]):
        if b["event"] == "app_start" or a["plugged"] == "1" or b["plugged"] == "1":
            continue
        dt_h = (int(b["epoch_ms"]) - int(a["epoch_ms"])) / 3_600_000
        if dt_h <= 0:
            continue
        s = state(a)
        hours[s] += dt_h
        qa, qb = num(a["charge_uah"]), num(b["charge_uah"])
        if qa is not None and qb is not None:
            used_uah[s] += qa - qb
            if cap_uah:
                used_pct[s] += (qa - qb) / cap_uah * 100
        else:
            la, lb = num(a["level_pct"]), num(b["level_pct"])
            if la is not None and lb is not None:
                used_pct[s] += la - lb
                if cap_uah:
                    used_uah[s] += (la - lb) / 100 * cap_uah

    first, last = rows[0]["time"], rows[-1]["time"]
    print(f"Log: {first} → {last}, {len(rows)} rows")
    if cap_uah:
        print(f"Estimated full battery: {cap_uah / 1000:.0f} mAh")
    print()
    print(f"{'state':38} {'time':>8} {'used':>9} {'drain':>8} {'avg':>8}")
    for s in sorted(hours, key=hours.get, reverse=True):
        h = hours[s]
        mah = used_uah[s] / 1000
        print(f"{s:38} {h * 60:6.0f} m {mah:6.0f} mAh {used_pct[s] / h:5.1f} %/h {mah / h:5.0f} mA")

    currents = [num(r["current_ua"]) for r in rows if num(r["current_ua"]) and r["plugged"] != "1"]
    if currents:
        print()
        print(f"current_now samples: median {sorted(currents)[len(currents) // 2] / 1000:.0f} mA "
              "(sign depends on the phone; a single reading is noisy)")

    if sessions_path:
        try:
            with open(sessions_path, newline="") as f:
                walks = list(csv.DictReader(f))
        except FileNotFoundError:
            walks = []
        if walks:
            print()
            print("Walks:")
            for w in walks:
                mins = int(w["duration_s"]) / 60
                bs, be = num(w["battery_start_pct"]), num(w["battery_end_pct"])
                drain = f"{bs:.0f}% → {be:.0f}%" if bs is not None and be is not None else "battery unknown"
                rate = f", {(bs - be) / (mins / 60):.1f} %/h" if bs is not None and be is not None and mins >= 10 else ""
                new = num(w.get("new_area_m2"))
                new_txt = f"  {new / 10_000:.2f} ha new" if new is not None else ""
                print(f"  {w['start']}  {mins:5.0f} min  {float(w['distance_m']) / 1000:5.2f} km  {drain}{rate}{new_txt}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    main(*sys.argv[1:3])
