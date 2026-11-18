"""Build the Netherlands map file and cut one file per province from it.

1. Planetiler (OpenMapTiles profile) turns the Geofabrik dump into `out/nl.pmtiles`, zoom 6 to 14.
2. `pmtiles extract` cuts `out/<id>.pmtiles` for each province, using the 1 km region that
   `provinces.py` wrote to `out/<id>.geojson`.

Reads the dump from disk and never contacts Geofabrik. Planetiler itself downloads a few static source
files once (ocean polygons, Natural Earth, lake centre lines) into `tools/maps/data/sources/`, and says
who it is while doing so. Nothing here uses a live OSM query service.

    uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt \
        python tools/maps/build.py --pbf tools/maps/data/netherlands-latest.osm.pbf --provinces fr zh

Needs Java 21 (JAVA_HOME), `tools/maps/bin/planetiler.jar` and `tools/maps/bin/pmtiles`.
"""
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
USER_AGENT = "misty-steps-maps/1 (+https://github.com/tjerk-git/misty-steps)"
MIN_ZOOM = 6
MAX_ZOOM = 14
ALL = ["dr", "fl", "fr", "ge", "gr", "li", "nb", "nh", "ov", "ut", "ze", "zh"]


def planetiler_cmd(pbf, output, heap="4g"):
    java = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "java"
    return [
        str(java) if java.exists() else "java", f"-Xmx{heap}", "-jar", str(HERE / "bin" / "planetiler.jar"),
        f"--osm-path={pbf}", f"--output={output}", "--force", "--download",
        f"--download-dir={HERE / 'data' / 'sources'}",
        f"--http-user-agent={USER_AGENT}",
        "--storage=mmap", "--nodemap-type=sparsearray",
        f"--minzoom={MIN_ZOOM}", f"--maxzoom={MAX_ZOOM}",
    ]


def extract_cmd(source, province, out_dir):
    return [str(HERE / "bin" / "pmtiles"), "extract", source, f"{out_dir}/{province}.pmtiles",
            f"--region={out_dir}/{province}.geojson"]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pbf", required=True)
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--provinces", nargs="+", default=ALL)
    ap.add_argument("--force", action="store_true", help="rebuild nl.pmtiles even if it exists")
    args = ap.parse_args()

    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    country = out / "nl.pmtiles"
    results = []
    if args.force or not country.exists():
        start = time.time()
        subprocess.run(planetiler_cmd(args.pbf, str(country)), check=True)
        results.append(("nl", country.stat().st_size, time.time() - start))
    for pid in args.provinces:
        start = time.time()
        subprocess.run(extract_cmd(str(country), pid, str(out)), check=True)
        results.append((pid, (out / f"{pid}.pmtiles").stat().st_size, time.time() - start))
    print(f"{'file':6} {'MB':>8} {'seconds':>8}", file=sys.stderr)
    for name, size, secs in results:
        print(f"{name:6} {size / 1e6:8.1f} {secs:8.1f}", file=sys.stderr)


if __name__ == "__main__":
    main()
