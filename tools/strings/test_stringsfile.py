import json
import tempfile
import unittest
from pathlib import Path

import stringsfile as sf

SAMPLE = '''<resources>
    <!-- Words: keep it friendly. -->
    <string name="a_title">Hello &amp; welcome</string>

    <!-- Notification -->
    <string name="b_body">%1$s walks</string>
    <plurals name="c_walks">
        <item quantity="one">%1$s walk</item>
        <item quantity="other">%1$s walks</item>
    </plurals>
</resources>
'''


class ParseAndEdit(unittest.TestCase):
    def test_entries_in_order_with_ids_groups_and_comments(self):
        got = [(e["id"], e["group"], e["raw"], e["comment"]) for e in sf.parse(SAMPLE)]
        self.assertEqual(got, [
            ("a_title", "a", "Hello &amp; welcome", "Words: keep it friendly."),
            ("b_body", "b", "%1$s walks", "Notification"),
            ("c_walks#one", "c", "%1$s walk", ""),
            ("c_walks#other", "c", "%1$s walks", ""),
        ])

    def test_editing_changes_only_that_text(self):
        out = sf.set_value(SAMPLE, "b_body", "%1$s wandelingen")
        self.assertEqual(out, SAMPLE.replace("%1$s walks</string>", "%1$s wandelingen</string>", 1))

    def test_editing_a_plural_item(self):
        out = sf.set_value(SAMPLE, "c_walks#one", "%1$s wandeling")
        self.assertIn('<item quantity="one">%1$s wandeling</item>', out)
        self.assertIn('<item quantity="other">%1$s walks</item>', out)

    def test_a_missing_string_is_added_before_the_end(self):
        out = sf.set_value(SAMPLE, "d_new", "Nieuw")
        self.assertTrue(out.endswith('    <string name="d_new">Nieuw</string>\n</resources>\n'))
        self.assertEqual([e["id"] for e in sf.parse(out)][-1], "d_new")

    def test_a_missing_plural_item_joins_its_block_or_makes_one(self):
        two = sf.set_value(SAMPLE, "c_walks#few", "x")
        self.assertIn('<item quantity="few">x</item>', two)
        self.assertEqual(sum(1 for e in sf.parse(two) if e["key"] == "c_walks"), 3)
        new = sf.set_value(SAMPLE, "e_new#one", "y")
        self.assertIn('<plurals name="e_new">', new)

    def test_text_that_is_not_valid_xml_is_refused(self):
        for bad in ["a & b", "<b>open"]:
            with self.assertRaises(ValueError):
                sf.set_value(SAMPLE, "a_title", bad)

    def test_the_real_files_round_trip_unchanged(self):
        for path in (sf.EN, sf.NL):
            text = path.read_text()
            entries = sf.parse(text)
            self.assertGreater(len(entries), 200)
            again = text
            for e in entries:
                again = sf.set_value(again, e["id"], e["raw"])
            self.assertEqual(again, text, path.name)
            for e in entries:
                self.assertIsNone(sf.well_formed(e["raw"]), f'{path.name}: {e["id"]}')


class Checks(unittest.TestCase):
    def test_missing(self):
        self.assertEqual(sf.checks("Hello", ""), ["missing_nl"])
        self.assertEqual(sf.checks("", "Hallo"), ["missing_en"])

    def test_placeholders_must_match(self):
        self.assertIn("placeholders", sf.checks("%1$s walks", "wandelingen"))
        self.assertIn("placeholders", sf.checks("%1$s of %2$s", "%1$s van %1$s"))
        self.assertNotIn("placeholders", sf.checks("%1$s of %2$s", "%2$s van %1$s"))

    def test_same_text_in_both_languages_is_flagged_only_when_long(self):
        self.assertIn("untranslated", sf.checks("Something fairly long here", "Something fairly long here"))
        self.assertEqual(sf.checks("Misty Steps", "Misty Steps"), [])

    def test_length_and_spacing(self):
        self.assertIn("long_nl", sf.checks("A short enough line ok.", "x" * 40))
        self.assertIn("short_nl", sf.checks("A reasonably long line here.", "kort"))
        self.assertIn("spaces", sf.checks("two  spaces here ok", "twee spaties hier ok"))
        self.assertIn("apostrophe", sf.checks("don't", "niet"))
        self.assertNotIn("apostrophe", sf.checks("don\\'t", "niet"))


class Review(unittest.TestCase):
    def test_status_follows_the_text(self):
        rec = {"en": sf.digest("Hello"), "nl": sf.digest("Hallo"), "status": "ok"}
        self.assertEqual(sf.review_status(None, "Hello", "Hallo"), "none")
        self.assertEqual(sf.review_status(rec, "Hello", "Hallo"), "ok")
        self.assertEqual(sf.review_status(rec, "Hello", "Hoi"), "changed")
        self.assertEqual(sf.review_status({**rec, "status": "flag"}, "Hello", "Hallo"), "flag")

    def test_rows_cover_ids_from_both_files(self):
        nl = SAMPLE.replace('name="b_body">%1$s walks', 'name="b_body">%1$s wandelingen').replace("a_title", "a_other")
        rows = {r["id"]: r for r in sf.build(SAMPLE, nl, {})}
        self.assertEqual(rows["a_title"]["checks"], ["missing_nl"])
        self.assertEqual(rows["a_other"]["checks"], ["missing_en"])
        self.assertEqual(rows["b_body"]["nl"], "%1$s wandelingen")
        self.assertEqual(rows["b_body"]["status"], "none")


if __name__ == "__main__":
    unittest.main()
