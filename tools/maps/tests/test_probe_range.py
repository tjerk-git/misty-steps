import hashlib
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import probe_range

BODY = bytes(range(256)) * 400  # 102,400 bytes


class RangeHandler(BaseHTTPRequestHandler):
    ignore_range = False

    def do_GET(self):
        header = self.headers.get("Range")
        if header and not self.ignore_range:
            start, _, end = header.removeprefix("bytes=").partition("-")
            start, end = int(start), int(end) if end else len(BODY) - 1
            chunk = BODY[start:end + 1]
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{len(BODY)}")
        else:
            chunk = BODY
            self.send_response(200)
        self.send_header("Content-Length", str(len(chunk)))
        self.end_headers()
        self.wfile.write(chunk)

    def log_message(self, *args):
        pass


def serve(ignore_range=False):
    handler = type("H", (RangeHandler,), {"ignore_range": ignore_range})
    server = HTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, f"http://127.0.0.1:{server.server_address[1]}/f"


def test_first_kilobyte_comes_back_as_206():
    server, url = serve()
    status, data = probe_range.fetch_range(url, 0, 1023)
    server.shutdown()
    assert status == 206 and data == BODY[:1024]


def test_resumed_download_matches_the_whole_file():
    server, url = serve()
    sha = hashlib.sha256(BODY).hexdigest()
    ok, detail = probe_range.check(url, sha, cut_at=40_000)
    server.shutdown()
    assert ok, detail


def test_server_that_ignores_range_is_reported():
    server, url = serve(ignore_range=True)
    ok, detail = probe_range.check(url, hashlib.sha256(BODY).hexdigest(), cut_at=40_000)
    server.shutdown()
    assert not ok and "206" in detail


def test_wrong_sha_is_reported():
    server, url = serve()
    ok, detail = probe_range.check(url, "0" * 64, cut_at=40_000)
    server.shutdown()
    assert not ok and "sha256" in detail
