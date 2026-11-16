"""Check that a server answers HTTP range requests the way the app's resumable download needs.

Fetches the first part of a file, then the rest with a `Range` request, joins them and compares the
sha256 with the expected one. Exits 0 when everything matches.

    python tools/maps/probe_range.py <url> <expected-sha256> [--cut-at <bytes>]
"""
import argparse
import hashlib
import sys
import urllib.request

USER_AGENT = "misty-steps-maps/1 (+https://github.com/tjerk-git/misty-steps)"


def fetch_range(url, start, end=None):
    """(status, body) for bytes [start, end] of [url]; end is inclusive, None means to the end."""
    spec = f"bytes={start}-{'' if end is None else end}"
    request = urllib.request.Request(url, headers={"Range": spec, "User-Agent": USER_AGENT})
    with urllib.request.urlopen(request) as response:
        return response.status, response.read()


def check(url, expected_sha256, cut_at):
    """(ok, detail): a download cut at [cut_at] bytes and resumed with a range request matches the sha256."""
    status, first = fetch_range(url, 0, cut_at - 1)
    if status != 206 or len(first) != cut_at:
        return False, f"first part: expected 206 with {cut_at} bytes, got {status} with {len(first)}"
    status, rest = fetch_range(url, cut_at)
    if status != 206:
        return False, f"resume: expected 206, got {status}"
    digest = hashlib.sha256(first + rest).hexdigest()
    if digest != expected_sha256:
        return False, f"sha256 of the joined file is {digest}, expected {expected_sha256}"
    return True, f"206 twice, {len(first) + len(rest)} bytes, sha256 matches"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("url")
    ap.add_argument("sha256")
    ap.add_argument("--cut-at", type=int, default=1_000_000)
    args = ap.parse_args()
    ok, detail = check(args.url, args.sha256, args.cut_at)
    print(("OK: " if ok else "FAIL: ") + detail)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
