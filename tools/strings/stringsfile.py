"""Reads and edits the app's strings.xml files (English and Dutch) without reformatting them, and checks the pair.

Each entry has an id: the key for a plain string, "key#quantity" for one item of a plurals block. The text is kept exactly as
written in the file (XML escapes and all), and edits replace only that text, so comments and layout stay untouched.
"""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EN = ROOT / "shared/strings/values/strings.xml"
NL = ROOT / "shared/strings/values-nl/strings.xml"
REVIEW = Path(__file__).resolve().parent / "review.json"

_STRING = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.S)
_PLURALS = re.compile(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', re.S)
_ITEM = re.compile(r'<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>', re.S)
_COMMENT = re.compile(r'<!--(.*?)-->', re.S)
_PLACEHOLDER = re.compile(r'%(?:\d+\$)?[sdf]')


def parse(text):
    """The entries of a strings file in file order: dicts with id, key, quantity, raw, comment, group, start, end (the text's span)."""
    found = []
    for m in _STRING.finditer(text):
        found.append((m.start(), dict(id=m.group(1), key=m.group(1), quantity=None, raw=m.group(2), start=m.start(2), end=m.end(2))))
    for m in _PLURALS.finditer(text):
        for i in _ITEM.finditer(m.group(2)):
            s = m.start(2) + i.start(2)
            found.append((m.start() + i.start(), dict(id=f"{m.group(1)}#{i.group(1)}", key=m.group(1), quantity=i.group(1), raw=i.group(2), start=s, end=s + len(i.group(2)))))
    found.sort(key=lambda p: p[0])
    comments = [(c.start(), " ".join(c.group(1).split())) for c in _COMMENT.finditer(text)]
    entries, prev = [], 0
    for pos, e in found:
        e["comment"] = " ".join(t for p, t in comments if prev <= p < pos)
        e["group"] = e["key"].split("_")[0]
        entries.append(e)
        prev = pos
    return entries


def well_formed(raw):
    """None when [raw] is valid as the text of a string, else the reason it is not."""
    try:
        ET.fromstring(f"<r>{raw}</r>")
    except ET.ParseError as e:
        return str(e)
    return None


def set_value(text, entry_id, raw):
    """The file text with the entry's text replaced by [raw] (added at the end when the entry does not exist yet)."""
    problem = well_formed(raw)
    if problem:
        raise ValueError(problem)
    for e in parse(text):
        if e["id"] == entry_id:
            return text[:e["start"]] + raw + text[e["end"]:]
    key, _, quantity = entry_id.partition("#")
    end = text.rindex("</resources>")
    if not quantity:
        return text[:end] + f'    <string name="{key}">{raw}</string>\n' + text[end:]
    block = re.search(rf'<plurals\s+name="{re.escape(key)}"[^>]*>', text)
    item = f'        <item quantity="{quantity}">{raw}</item>\n'
    if block:
        close = text.index("</plurals>", block.end())
        close = text.rindex("\n", 0, close) + 1
        return text[:close] + item + text[close:]
    return text[:end] + f'    <plurals name="{key}">\n{item}    </plurals>\n' + text[end:]


def digest(raw):
    return hashlib.sha1((raw or "").encode()).hexdigest()[:10]


def checks(en, nl):
    """Problems with a pair, as short codes: missing_en, missing_nl, placeholders, untranslated, long_nl, short_nl, apostrophe, spaces."""
    out = []
    if not (en or "").strip():
        out.append("missing_en")
    if not (nl or "").strip():
        out.append("missing_nl")
    if out:
        return out
    if sorted(_PLACEHOLDER.findall(en)) != sorted(_PLACEHOLDER.findall(nl)):
        out.append("placeholders")
    if en == nl and len(en) > 15:
        out.append("untranslated")
    if len(en) > 20 and len(nl) > 1.6 * len(en):
        out.append("long_nl")
    if len(en) > 20 and len(nl) < 0.5 * len(en):
        out.append("short_nl")
    if any(re.search(r"(?<!\\)'", t) for t in (en, nl)):
        out.append("apostrophe")
    if any(t != t.strip() or "  " in t for t in (en, nl)):
        out.append("spaces")
    return out


def review_status(record, en, nl):
    """none (never reviewed), ok, flag, or changed (the text was edited after the review)."""
    if not record:
        return "none"
    if record.get("en") != digest(en) or record.get("nl") != digest(nl):
        return "changed"
    return record.get("status", "ok")


def load_review():
    return json.loads(REVIEW.read_text()) if REVIEW.exists() else {}


def build(en_text, nl_text, review):
    """The rows for the page: one per id from either file, in English file order, then any only in Dutch."""
    en = {e["id"]: e for e in parse(en_text)}
    nl = {e["id"]: e for e in parse(nl_text)}
    ids = list(en) + [i for i in nl if i not in en]
    rows = []
    for i in ids:
        e, n = en.get(i), nl.get(i)
        e_raw, n_raw = e["raw"] if e else "", n["raw"] if n else ""
        base = e or n
        rec = review.get(i)
        rows.append(dict(
            id=i, group=base["group"], comment=base["comment"], en=e_raw, nl=n_raw, checks=checks(e_raw, n_raw),
            status=review_status(rec, e_raw, n_raw), note=(rec or {}).get("note", ""),
        ))
    return rows


def write_atomic(path, text):
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(text)
    tmp.replace(path)
