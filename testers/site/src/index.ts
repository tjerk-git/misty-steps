import { pushAndMark, syncPending, type Db, type PushEnv } from "./push.ts";
import { CONSENT_VERSION, parseSignup } from "./signup.ts";

interface Env extends PushEnv {
  DB: Db;
  ASSETS: { fetch(request: Request): Promise<Response> };
}

const MAX_BODY = 2000;
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

export default {
  async fetch(request: Request, env: Env, ctx: { waitUntil(p: Promise<unknown>): void }): Promise<Response> {
    const { pathname } = new URL(request.url);
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
    // Signing up again with the same address updates the choices; the answer never reveals whether it was new.
    await env.DB.prepare(
      `INSERT INTO signups (email, platform, newsletter, consent_version, created_at) VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(email) DO UPDATE SET platform = excluded.platform, newsletter = excluded.newsletter, consent_version = excluded.consent_version, synced_at = NULL`,
    )
      .bind(s.email, s.platform, s.newsletter ? 1 : 0, CONSENT_VERSION, new Date().toISOString())
      .run();
    // Todododo gets it too, after the answer has gone out; if that fails the hourly sync picks it up.
    const row = await env.DB.prepare("SELECT created_at FROM signups WHERE email = ?").bind(s.email).all<{ created_at: string }>();
    ctx.waitUntil(
      pushAndMark(fetch, env, env.DB, {
        email: s.email,
        platform: s.platform,
        newsletter: s.newsletter,
        consent_version: CONSENT_VERSION,
        created_at: row.results[0]?.created_at ?? new Date().toISOString(),
      }),
    );
    return json({ ok: true });
  },

  async scheduled(_event: unknown, env: Env, ctx: { waitUntil(p: Promise<unknown>): void }): Promise<void> {
    ctx.waitUntil(syncPending(fetch, env, env.DB));
  },
};
