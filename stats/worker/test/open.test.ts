import assert from "node:assert/strict";
import { test } from "node:test";
import { dayOf, parseOpen, recordOpen } from "../src/open.ts";

const ok = (body: string) => parseOpen("POST", null, body);

test("accepts a version and a platform", () => {
  assert.deepEqual(ok('{"v":"1.4.0","os":"android"}'), { version: "1.4.0", os: "android" });
  assert.deepEqual(ok('{"v":"1.4.0-beta","os":"ios"}'), { version: "1.4.0-beta", os: "ios" });
});

test("only POST is accepted", () => {
  assert.ok("error" in parseOpen("GET", null, '{"v":"1.4.0","os":"android"}'));
  assert.ok("error" in parseOpen("PUT", null, '{"v":"1.4.0","os":"android"}'));
});

test("a body over 200 bytes is refused, by length header or by size", () => {
  const big = JSON.stringify({ v: "1.4.0", os: "android", pad: "x".repeat(200) });
  assert.ok("error" in ok(big));
  assert.ok("error" in parseOpen("POST", 201, '{"v":"1.4.0","os":"android"}'));
});

test("bad JSON and bad shapes are refused", () => {
  for (const body of ["", "not json", "[]", "null", '"1.4.0"', '{"v":"1.4.0"}', '{"os":"android"}']) {
    assert.ok("error" in ok(body), body);
  }
});

test("extra fields are refused, so nothing else can be smuggled in", () => {
  assert.ok("error" in ok('{"v":"1.4.0","os":"android","id":"abc"}'));
});

test("versions the app would never send are refused", () => {
  for (const v of ["1.4", "1.4.0.1", "v1.4.0", "1.4.0; DROP TABLE opens", "1.4.0-Beta", "1.4.0-dev1", " 1.4.0", ""]) {
    assert.ok("error" in ok(JSON.stringify({ v, os: "android" })), v);
  }
});

test("only android and ios are known platforms", () => {
  assert.ok("error" in ok('{"v":"1.4.0","os":"windows"}'));
  assert.ok("error" in ok('{"v":"1.4.0","os":"Android"}'));
  assert.ok("error" in ok('{"v":"1.4.0","os":1}'));
});

test("dayOf gives the UTC day, also around midnight", () => {
  assert.equal(dayOf(new Date("2026-11-17T23:59:59Z")), "2026-11-17");
  assert.equal(dayOf(new Date("2026-11-18T00:00:00Z")), "2026-11-18");
  assert.equal(dayOf(new Date("2026-11-17T01:30:00+02:00")), "2026-11-16");
});

test("recordOpen adds one to the counter for the day, version and platform", async () => {
  const calls: { sql: string; args: unknown[] }[] = [];
  const db = {
    prepare(sql: string) {
      return {
        bind(...args: unknown[]) {
          return { run: async () => { calls.push({ sql, args }); } };
        },
      };
    },
  };
  await recordOpen(db, "1.4.0", "android", "2026-11-17");
  assert.equal(calls.length, 1);
  assert.match(calls[0].sql, /INSERT INTO opens/);
  assert.match(calls[0].sql, /ON CONFLICT\(day, version, os\) DO UPDATE SET count = count \+ 1/);
  assert.deepEqual(calls[0].args, ["2026-11-17", "1.4.0", "android"]);
});
