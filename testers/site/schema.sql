-- Tester sign-ups. The email is what the person gave; consent is stored as proof (what was agreed, when).
CREATE TABLE IF NOT EXISTS signups (
  email TEXT PRIMARY KEY,
  platform TEXT NOT NULL,
  newsletter INTEGER NOT NULL,
  consent_version TEXT NOT NULL,
  created_at TEXT NOT NULL,
  invited_at TEXT,
  synced_at TEXT -- set once Todododo has confirmed the row; empty rows are re-pushed hourly
  ,sync_note TEXT -- why the last push failed, if it did
);
