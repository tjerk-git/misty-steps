import { test } from "node:test";
import assert from "node:assert/strict";
import { pushSignup, syncPending } from "../src/push.ts";

const env = { TODODODO_URL: "https://todo.example.nl", TODODODO_TOKEN: "tok" };
const row = { email: "ann@example.nl", platform: "ios", newsletter: true, consent_version: "2026-11-18", created_at: "2026-11-18T09:30:00.000Z" };

type Call = { url: string; init: RequestInit };
function fakeFetch(status: number | "throw", calls: Call[] = []) {
  const f = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    if (status === "throw") throw new Error("down");
    return new Response("{}", { status });
  }) as unknown as typeof fetch;
  return { f, calls };
}

test("pushSignup posts the exact URL, header and body", async () => {
  const { f, calls } = fakeFetch(200);
  assert.equal(await pushSignup(f, env, row), true);
  assert.equal(calls[0].url, "https://todo.example.nl/testers/signup");
  assert.equal(calls[0].init.method, "POST");
  const h = calls[0].init.headers as Record<string, string>;
  assert.equal(h.authorization, "Bearer tok");
  assert.equal(h["content-type"], "application/json");
  assert.deepEqual(JSON.parse(calls[0].init.body as string), {
    email: "ann@example.nl", platform: "ios", newsletter: true, consent_version: "2026-11-18", signed_up_at: "2026-11-18T09:30:00.000Z",
  });
});

test("pushSignup is false on a 500, a 404, a thrown error, and never throws", async () => {
  assert.equal(await pushSignup(fakeFetch(500).f, env, row), false);
  assert.equal(await pushSignup(fakeFetch(404).f, env, row), false);
  assert.equal(await pushSignup(fakeFetch("throw").f, env, row), false);
});

test("pushSignup is false when the URL or token is not set", async () => {
  const { f, calls } = fakeFetch(200);
  assert.equal(await pushSignup(f, { TODODODO_URL: "", TODODODO_TOKEN: "tok" }, row), false);
  assert.equal(await pushSignup(f, { TODODODO_URL: "https://x.nl", TODODODO_TOKEN: "" }, row), false);
  assert.equal(calls.length, 0);
});

function fakeDb(pending: Record<string, unknown>[]) {
  const updates: unknown[][] = [];
  const db = {
    prepare(sql: string) {
      let args: unknown[] = [];
      const stmt = {
        bind(...v: unknown[]) { args = v; return stmt; },
        async run() { assert.match(sql, /UPDATE signups SET synced_at/); updates.push(args); return {}; },
        async all() { assert.match(sql, /synced_at IS NULL/); return { results: pending }; },
      };
      return stmt;
    },
  };
  return { db, updates };
}

test("syncPending marks only the rows that went through and returns how many", async () => {
  const pending = [
    { ...row, email: "a@example.nl", newsletter: 1 },
    { ...row, email: "b@example.nl", newsletter: 0 },
  ];
  const { db, updates } = fakeDb(pending);
  let n = 0;
  const f = (async (_u: string, init: RequestInit) => new Response("{}", { status: JSON.parse(init.body as string).email === "a@example.nl" ? 200 : 500 })) as unknown as typeof fetch;
  void n;
  assert.equal(await syncPending(f, env, db), 1);
  assert.equal(updates.length, 1);
  assert.equal(updates[0][1], "a@example.nl");
});

test("a retry sends the same payload, so Todododo upserts instead of duplicating", async () => {
  const calls: Call[] = [];
  const { f } = fakeFetch(200, calls);
  await pushSignup(f, env, row);
  await pushSignup(f, env, row);
  assert.equal(calls[0].init.body, calls[1].init.body);
});

test("syncPending sends newsletter as a real boolean (D1 stores 0 and 1)", async () => {
  const { db } = fakeDb([{ ...row, newsletter: 0 }]);
  const calls: Call[] = [];
  await syncPending(fakeFetch(200, calls).f, env, db);
  assert.equal(JSON.parse(calls[0].init.body as string).newsletter, false);
});
