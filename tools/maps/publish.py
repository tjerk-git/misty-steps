"""Publish the province map files and the catalog to R2 (bucket misty-maps, served at maps.potloodgum.com).

Uploads each province's `map.pmtiles` to `v2/nl/<year>/<id>/`, checks every upload with a HEAD request, and
writes `v2/catalog.json` last, so the catalog never points at files that aren't there yet. Needs the rclone
remote `misty-r2` (an R2 API token kept in rclone's own config, never in this repo).

    uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt \
        python tools/maps/publish.py --year 2026 --data-date 2026-11-10 [--dry-run]
"""
import argparse
import hashlib
import json
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

from shapely.geometry import shape

import build

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
REMOTE = "misty-r2:misty-maps"
HOST = "https://maps.potloodgum.com/"
FORMAT = 2
IMMUTABLE = "Cache-Control: public, max-age=31536000, immutable"
SHORT = "Cache-Control: public, max-age=3600"


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def catalog_json(year, data_date, provinces):
    """The catalog for [provinces] (dicts with id, name, bbox and map {path, url}), sorted by id."""
    return {
        "format": FORMAT,
        "country": "nl",
        "year": year,
        "dataDate": data_date,
        "provinces": [
            {
                "id": p["id"], "name": p["name"], "bbox": p["bbox"],
                "map": {"url": p["map"]["url"], "bytes": Path(p["map"]["path"]).stat().st_size,
                        "sha256": sha256_of(p["map"]["path"])},
                **({"stats": {"url": p["stats"]["url"], "bytes": Path(p["stats"]["path"]).stat().st_size,
                              "sha256": sha256_of(p["stats"]["path"])}} if p.get("stats") else {}),
            }
            for p in sorted(provinces, key=lambda p: p["id"])
        ],
    }


def upload(local, key, cache_control, content_type):
    subprocess.run(["rclone", "copyto", str(local), f"{REMOTE}/{key}", "--s3-no-check-bucket",
                    "--header-upload", cache_control, "--header-upload", f"Content-Type: {content_type}"], check=True)


def remote_size(key):
    request = urllib.request.Request(HOST + key, method="HEAD", headers={"User-Agent": build.USER_AGENT})
    with urllib.request.urlopen(request) as response:
        return int(response.headers["Content-Length"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--year", type=int, required=True)
    ap.add_argument("--data-date", required=True)
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--pbf", help="the OpenStreetMap dump: build any missing neighbourhoods-and-streets file from it first")
    args = ap.parse_args()

    out = Path(args.out_dir)
    names = {p["id"]: p["name"] for p in json.loads((ROOT / "app/src/main/assets/nl-provinces.json").read_text())["provinces"]}
    provinces = []
    for pid in sorted(names):
        pack = out / f"{pid}.pmtiles"
        if not pack.exists():
            subprocess.run(build.extract_cmd(str(out / "nl.pmtiles"), pid, str(out)), check=True)
        bounds = shape(json.loads((out / f"{pid}.geojson").read_text())).bounds
        entry = {"id": pid, "name": names[pid], "bbox": [round(v, 4) for v in bounds],
                 "map": {"path": str(pack), "url": f"v2/nl/{args.year}/{pid}/map.pmtiles"}}
        stats = out / f"{pid}.stats.zip"
        if not stats.exists() and args.pbf:
            import stats_build
            stats_build.build_province(args.pbf, pid, str(out), str(ROOT / "app/src/main/assets/nl-index.json"), args.data_date)
        if stats.exists():
            entry["stats"] = {"path": str(stats), "url": f"v2/nl/{args.year}/{pid}/stats.zip"}
        provinces.append(entry)
    catalog = catalog_json(args.year, args.data_date, provinces)
    for entry in catalog["provinces"]:
        print(f"{entry['id']} {entry['name']:15} {entry['map']['bytes'] / 1e6:7.1f} MB", file=sys.stderr)
    print(f"total {sum(e['map']['bytes'] for e in catalog['provinces']) / 1e6:.0f} MB", file=sys.stderr)
    if args.dry_run:
        print(json.dumps(catalog, indent=1))
        return
    for p in provinces:
        upload(p["map"]["path"], p["map"]["url"], IMMUTABLE, "application/octet-stream")
        if p.get("stats"):
            upload(p["stats"]["path"], p["stats"]["url"], IMMUTABLE, "application/zip")
    for entry, p in zip(catalog["provinces"], sorted(provinces, key=lambda p: p["id"])):
        for kind in ("map", "stats"):
            if kind not in entry:
                continue
            size = remote_size(entry[kind]["url"])
            if size != entry[kind]["bytes"]:
                sys.exit(f"{entry['id']}: uploaded {kind} {size} bytes, expected {entry[kind]['bytes']}")
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(catalog, f, indent=1)
    upload(f.name, "v2/catalog.json", SHORT, "application/json")
    print("published", file=sys.stderr)


if __name__ == "__main__":
    main()
