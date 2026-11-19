import { test } from "node:test";
import assert from "node:assert/strict";
import { authorized, pendingSignups, acknowledge } from "../src/export.ts";

test("authorized needs the exact bearer token and a token set", () => {
  assert.equal(authorized("Bearer tok", "tok"), true);
  assert.equal(authorized("Bearer wrong", "tok"), false);
  assert.equal(authorized("tok", "tok"), false);
  assert.equal(authorized(null, "tok"), false);
  assert.equal(authorized("Bearer ", ""), false);
  assert.equal(authorized("Bearer tok", undefined), false);
});

function fakeDb(rows: Record<string, unknown>[] = []) {
  const runs: { sql: string; args: unknown[] }[] = [];
  const db = {
    prepare(sql: string) {
      let args: unknown[] = [];
      const stmt = {
        bind(...v: unknown[]) { args = v; return stmt; },
        async run() { runs.push({ sql, args }); return {}; },
        async all() { runs.push({ sql, args }); return { results: rows }; },
      };
      return stmt;
    },
  };
  return { db, runs };
}

test("pendingSignups reads only unsynced rows, oldest first, at most 100, newsletter as a boolean", async () => {
  const { db, runs } = fakeDb([{ email: "a@example.nl", platform: "ios", newsletter: 1, consent_version: "2026-11-18", created_at: "2026-11-18T09:00:00.000Z" }]);
  const items = await pendingSignups(db);
  assert.match(runs[0].sql, /synced_at IS NULL/);
  assert.match(runs[0].sql, /ORDER BY created_at/);
  assert.match(runs[0].sql, /LIMIT 100/);
  assert.deepEqual(items, [{ email: "a@example.nl", platform: "ios", newsletter: true, consent_version: "2026-11-18", signed_up_at: "2026-11-18T09:00:00.000Z" }]);
});

test("acknowledge marks each given address as synced and returns how many", async () => {
  const { db, runs } = fakeDb();
  assert.equal(await acknowledge(db, ["a@example.nl", "b@example.nl"]), 2);
  assert.equal(runs.length, 2);
  assert.match(runs[0].sql, /UPDATE signups SET synced_at = \? WHERE email = \?/);
  assert.equal(runs[1].args[1], "b@example.nl");
});

test("acknowledge ignores anything that is not a short string and caps the list at 100", async () => {
  const { db, runs } = fakeDb();
  const many = Array.from({ length: 150 }, (_, i) => `p${i}@example.nl`);
  assert.equal(await acknowledge(db, [...many, 5 as unknown as string, "x".repeat(300)]), 100);
  assert.equal(runs.length, 100);
});
