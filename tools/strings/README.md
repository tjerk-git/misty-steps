# Strings dashboard

A local page to read, edit and review every app text in English and Dutch.

    python3 tools/strings/serve.py        # opens http://127.0.0.1:8791

- **Edits are saved on leaving a field**, straight into `shared/strings/values/strings.xml` and `values-nl/strings.xml`. Only that text changes, so comments and layout stay as they are; `git diff` shows exactly what you changed. Write `&` as `&amp;` and a straight apostrophe as `\'` (or use the curly one the app already uses). Text that is not valid XML is refused.
- **Filters:** All, Problems, Missing, Not reviewed, Changed since review, Flagged, Reviewed; plus search and a screen (key prefix) picker. Read mode shows the text large and plain, for reading a screen as a person would.
- **Review marks** (Reviewed, Flag, note) are kept in `tools/strings/review.json`. They record the text that was reviewed, so editing a string turns its mark into "Changed since review".
- **Automatic checks:** missing in either language, `%1$s`-style placeholders that differ, identical text in both languages, Dutch much longer or shorter than English, straight apostrophes, odd spacing. These are hints; the page does not judge the wording.
- **Safety:** listens on this Mac only; saving needs the token the page was served with. Tests: `cd tools/strings && python3 -m unittest`.
- **Not included yet:** the store listing texts (`fastlane/metadata`).
