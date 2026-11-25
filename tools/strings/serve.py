#!/usr/bin/env python3
"""The strings dashboard: a local page to read, edit and review every string in English and Dutch.

    python3 tools/strings/serve.py [port]

Opens http://127.0.0.1:8791 . Edits are written straight into shared/strings/values*/strings.xml (git shows what changed);
review marks go to tools/strings/review.json. Only listens on this Mac; writes need the token the page was served with.
"""
import json
import secrets
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import stringsfile as sf

PAGE = (Path(__file__).resolve().parent / "index.html").read_text()
TOKEN = secrets.token_urlsafe(16)
FILES = {"en": sf.EN, "nl": sf.NL}


def rows():
    return sf.build(sf.EN.read_text(), sf.NL.read_text(), sf.load_review())


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, body, kind="application/json"):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", kind + "; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/":
            self._send(200, PAGE.replace("__TOKEN__", TOKEN), "text/html")
        elif self.path == "/api/rows":
            self._send(200, json.dumps(rows()))
        else:
            self._send(404, "{}")

    def do_POST(self):
        if self.headers.get("X-Token") != TOKEN:
            return self._send(403, json.dumps({"error": "bad token"}))
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        try:
            if self.path == "/api/save":
                path = FILES[body["lang"]]
                sf.write_atomic(path, sf.set_value(path.read_text(), body["id"], body["raw"]))
            elif self.path == "/api/review":
                self._review(body)
            else:
                return self._send(404, "{}")
        except (ValueError, KeyError) as e:
            return self._send(400, json.dumps({"error": str(e)}))
        row = next(r for r in rows() if r["id"] == body["id"])
        self._send(200, json.dumps(row))

    def _review(self, body):
        review = sf.load_review()
        row = next(r for r in rows() if r["id"] == body["id"])
        if body["status"] == "none" and not body.get("note"):
            review.pop(body["id"], None)
        else:
            review[body["id"]] = dict(en=sf.digest(row["en"]), nl=sf.digest(row["nl"]), status=body["status"], note=body.get("note", ""))
        sf.write_atomic(sf.REVIEW, json.dumps(review, indent=1, ensure_ascii=False, sort_keys=True) + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8791
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"Strings dashboard on http://127.0.0.1:{port}  (Ctrl-C to stop)")
    webbrowser.open(f"http://127.0.0.1:{port}")
    server.serve_forever()
