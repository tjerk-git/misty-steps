import { acknowledge, authorized, pendingSignups, type Db } from "./export.ts";
import { CONSENT_VERSION, parseSignup } from "./signup.ts";

interface Env {
  DB: Db;
  ASSETS: { fetch(request: Request): Promise<Response> };
  /** The secret Todododo sends as a bearer token to read and confirm sign-ups. */
  TESTERS_EXPORT_TOKEN?: string;
}

const MAX_BODY = 2000;
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  const text = await request.text();
  if (text.length > MAX_BODY * 10) return null;
  try {
    const data = JSON.parse(text);
    return data && typeof data === "object" ? data : null;
  } catch {
    return null;
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (pathname === "/api/pending" || pathname === "/api/ack") return pull(request, env, pathname);
    if (pathname !== "/api/signup") return env.ASSETS.fetch(request);
    if (request.method !== "POST") return new Response(null, { status: 405, headers: { Allow: "POST" } });

    const text = await request.text();
    if (text.length > MAX_BODY) return json({ error: "too_large" }, 413);
    let data: Record<string, unknown>;
    try {
      data = JSON.parse(text);
    } catch {
      return json({ error: "bad_request" }, 400);
    }
    const result = parseSignup(data);
    if ("spam" in result) return json({ ok: true });
    if ("error" in result) return json({ error: result.error }, 400);

    const s = result.signup;
    // Signing up again with the same address updates the choices (and queues it for Todododo again); the answer never reveals whether it was new.
    await env.DB.prepare(
      `INSERT INTO signups (email, platform, newsletter, consent_version, created_at) VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(email) DO UPDATE SET platform = excluded.platform, newsletter = excluded.newsletter, consent_version = excluded.consent_version, synced_at = NULL`,
    )
      .bind(s.email, s.platform, s.newsletter ? 1 : 0, CONSENT_VERSION, new Date().toISOString())
      .run();
    return json({ ok: true });
  },
};

/** The two routes Todododo calls. Without the right bearer token they do not exist (404). */
async function pull(request: Request, env: Env, pathname: string): Promise<Response> {
  if (!authorized(request.headers.get("authorization"), env.TESTERS_EXPORT_TOKEN)) return new Response(null, { status: 404 });
  if (pathname === "/api/pending") {
    if (request.method !== "GET") return new Response(null, { status: 405, headers: { Allow: "GET" } });
    return json({ items: await pendingSignups(env.DB) });
  }
  if (request.method !== "POST") return new Response(null, { status: 405, headers: { Allow: "POST" } });
  const body = await readJson(request);
  if (!body || !Array.isArray(body.emails)) return json({ error: "bad_request" }, 400);
  return json({ ok: true, acknowledged: await acknowledge(env.DB, body.emails) });
}
