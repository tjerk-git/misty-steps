"""Turns Cloudflare request rows for the F-Droid repo into a short report. Used by tools/stats.sh."""
import json, re, sys
from collections import defaultdict

days = sys.argv[1]
names = {}  # versionCode -> versionName, from the local repo index if it's there
try:
    pkg = json.load(open(sys.argv[2]))["packages"]["com.potloodgum.mistysteps"]["versions"]
    names = {str(v["manifest"]["versionCode"]): v["manifest"]["versionName"] for v in pkg.values()}
except Exception:
    pass

try:
    rows = json.load(sys.stdin)["data"]
except Exception:
    print("Cloudflare didn't return any data. Try again in a minute, or with fewer days.")
    sys.exit(1)


def client(ua):
    """Which app made the request. Tools and bots are left out of the counts."""
    u = ua or ""
    if re.search(r"curl|python|wget|go-http|httpie|okhttp/[0-9.]+$", u, re.I):
        return None  # scripts (including our own release checks)
    if re.search(r"bot|crawl|spider|preview|monitor", u, re.I):
        return None
    for name, pat in [("F-Droid", r"^F-Droid"), ("Droid-ify", r"droid-?ify"), ("Neo Store", r"neo ?store"),
                      ("Obtainium", r"obtainium"), ("Aurora", r"aurora")]:
        if re.search(pat, u, re.I):
            return name
    if u.startswith("Mozilla/"):
        return "browser"
    return "other"


apk = defaultdict(lambda: defaultdict(int))      # version -> client -> downloads
checks = defaultdict(lambda: defaultdict(int))   # day -> client -> repo checks
clients = set()                                  # distinct app user agents (app + its version)
visits = defaultdict(int)                        # day -> landing page visits from browsers
for r in rows:
    c = client(r["ua"])
    if c is None or int(r.get("status") or 200) >= 400:
        continue
    path, n, day = r["path"], int(r["n"]), r["day"][:10]
    m = re.search(r"_(\d+)\.apk$", path)
    if m:
        code = m.group(1)
        apk[f"{names.get(code, '?')} ({code})"][c] += n
    elif path in ("/repo/entry.jar", "/repo/index-v1.jar", "/repo/index-v2.json"):
        checks[day][c] += n
        if c != "browser":
            clients.add(r["ua"])
    elif path in ("/", "/index.html") and c == "browser":
        visits[day] += n

print(f"Misty Steps, last {days} days (fdroid.potloodgum.com)\n")

print("Downloads of the app")
if not apk:
    print("  none yet")
for version in sorted(apk, key=lambda v: int(re.search(r"\((\d+)\)", v).group(1)), reverse=True):
    by = apk[version]
    detail = ", ".join(f"{k} {v}" for k, v in sorted(by.items(), key=lambda x: -x[1]))
    print(f"  {version:<12} {sum(by.values()):>4}   ({detail})")

print("\nPhones checking the repo for updates")
if not checks:
    print("  none yet")
for day in sorted(checks):
    by = checks[day]
    detail = ", ".join(f"{k} {v}" for k, v in sorted(by.items(), key=lambda x: -x[1]))
    print(f"  {day}  {sum(by.values()):>4} checks   ({detail})")
if clients:
    print(f"  At least {len(clients)} different phone{'s' if len(clients) != 1 else ''} "
          f"(different F-Droid/Droid-ify versions; phones on the same version look alike)")

print("\nVisits to the landing page (browsers)")
print("  " + (", ".join(f"{d}: {n}" for d, n in sorted(visits.items())) or "none yet"))

print("\nCounts are Cloudflare's (sampled when busy), without IP addresses. Scripts and bots are left out;"
      "\nyour own phone is counted like anyone else's.")
