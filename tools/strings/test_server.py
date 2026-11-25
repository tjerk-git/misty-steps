import json
import shutil
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

import serve
import stringsfile as sf


class ServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.saved = (sf.EN, sf.NL, sf.REVIEW, serve.FILES)
        cls.tmp = Path(tempfile.mkdtemp())
        cls.en, cls.nl, cls.rev = cls.tmp / "en.xml", cls.tmp / "nl.xml", cls.tmp / "review.json"
        shutil.copy(sf.EN, cls.en)
        shutil.copy(sf.NL, cls.nl)
        sf.EN, sf.NL, sf.REVIEW = cls.en, cls.nl, cls.rev
        serve.FILES = {"en": cls.en, "nl": cls.nl}
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), serve.Handler)
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        sf.EN, sf.NL, sf.REVIEW, serve.FILES = cls.saved
        shutil.rmtree(cls.tmp)

    def call(self, path, body=None, token=serve.TOKEN):
        req = urllib.request.Request(self.base + path, data=json.dumps(body).encode() if body is not None else None,
                                     headers={"X-Token": token} if body is not None else {})
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_page_carries_the_token(self):
        with urllib.request.urlopen(self.base + "/") as r:
            html = r.read().decode()
        self.assertIn(serve.TOKEN, html)
        self.assertNotIn("__TOKEN__", html)

    def test_rows_cover_every_string(self):
        code, rows = self.call("/api/rows")
        self.assertEqual(code, 200)
        self.assertEqual(len(rows), len(sf.parse(self.en.read_text())))

    def test_a_save_changes_only_that_text_in_the_file(self):
        before = self.nl.read_text()
        code, row = self.call("/api/save", {"id": "onboarding_skip", "lang": "nl", "raw": "Sla over!"})
        self.assertEqual((code, row["nl"]), (200, "Sla over!"))
        after = self.nl.read_text()
        self.assertEqual(after, before.replace(next(e["raw"] for e in sf.parse(before) if e["id"] == "onboarding_skip"), "Sla over!", 1))

    def test_invalid_text_and_a_missing_token_are_refused_and_change_nothing(self):
        before = self.en.read_text()
        self.assertEqual(self.call("/api/save", {"id": "onboarding_skip", "lang": "en", "raw": "a & b"})[0], 400)
        self.assertEqual(self.call("/api/save", {"id": "onboarding_skip", "lang": "en", "raw": "x"}, token="nope")[0], 403)
        self.assertEqual(self.en.read_text(), before)

    def test_review_follows_the_text(self):
        code, row = self.call("/api/review", {"id": "onboarding_next", "status": "ok", "note": ""})
        self.assertEqual(row["status"], "ok")
        _, row = self.call("/api/save", {"id": "onboarding_next", "lang": "nl", "raw": "Volgende!"})
        self.assertEqual(row["status"], "changed")
        _, row = self.call("/api/review", {"id": "onboarding_next", "status": "flag", "note": "klinkt stijf"})
        self.assertEqual((row["status"], row["note"]), ("flag", "klinkt stijf"))
        _, row = self.call("/api/review", {"id": "onboarding_next", "status": "none", "note": ""})
        self.assertEqual(row["status"], "none")


if __name__ == "__main__":
    unittest.main()
