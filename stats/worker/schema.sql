-- The only table: opens per UTC day, app version and platform. Nothing that identifies anyone.
CREATE TABLE IF NOT EXISTS opens (
  day TEXT NOT NULL,
  version TEXT NOT NULL,
  os TEXT NOT NULL,
  count INTEGER NOT NULL,
  PRIMARY KEY (day, version, os)
);
