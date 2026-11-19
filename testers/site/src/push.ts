// Forwards each sign-up to Todododo (POST /testers/signup, locked by a bearer token). D1 stays the safety copy:
// `synced_at` is set only once Todododo answered 200, and the hourly cron re-pushes whatever is still empty.

export interface PushEnv {
  TODODODO_URL: string;
  TODODODO_TOKEN: string;
}

export interface PushRow {
  email: string;
  platform: string;
  newsletter: boolean;
  consent_version: string;
  created_at: string;
}

export interface Stmt {
  bind(...v: unknown[]): Stmt;
  run(): Promise<unknown>;
  all<T = Record<string, unknown>>(): Promise<{ results: T[] }>;
}
export interface Db {
  prepare(sql: string): Stmt;
}

const TIMEOUT_MS = 5000;
const BATCH = 50;

/** True only when Todododo answered 200. Never throws: a down server, a timeout or a missing setting is just false. */
export async function pushSignup(f: typeof fetch, env: PushEnv, row: PushRow): Promise<boolean> {
  return (await pushResult(f, env, row)).ok;
}

/** Like pushSignup, with a short reason when it did not go through (kept in `sync_note`, so a failing push can be diagnosed). */
export async function pushResult(f: typeof fetch, env: PushEnv, row: PushRow): Promise<{ ok: boolean; why?: string }> {
  if (!env.TODODODO_URL || !env.TODODODO_TOKEN) return { ok: false, why: "not configured" };
  try {
    const res = await f(`${env.TODODODO_URL.replace(/\/$/, "")}/testers/signup`, {
      method: "POST",
      headers: { authorization: `Bearer ${env.TODODODO_TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({
        email: row.email,
        platform: row.platform,
        newsletter: row.newsletter,
        consent_version: row.consent_version,
        signed_up_at: row.created_at,
      }),
      redirect: "manual", // a redirect is a failure to look at, not to follow: the token must not travel to another address
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    return res.status === 200 ? { ok: true } : { ok: false, why: `HTTP ${res.status}${res.headers.get("location") ? ` -> ${res.headers.get("location")}` : ""}` };
  } catch (e) {
    return { ok: false, why: String(e instanceof Error ? `${e.name}: ${e.message}` : e).slice(0, 200) };
  }
}

async function markSynced(db: Db, email: string): Promise<void> {
  await db.prepare("UPDATE signups SET synced_at = ?, sync_note = NULL WHERE email = ?").bind(new Date().toISOString(), email).run();
}

async function noteFailure(db: Db, email: string, why: string): Promise<void> {
  await db.prepare("UPDATE signups SET sync_note = ? WHERE email = ?").bind(why, email).run();
}

/** Push one fresh sign-up and, if it went through, mark it. */
export async function pushAndMark(f: typeof fetch, env: PushEnv, db: Db, row: PushRow): Promise<boolean> {
  const r = await pushResult(f, env, row);
  if (r.ok) await markSynced(db, row.email);
  else await noteFailure(db, row.email, r.why ?? "unknown");
  return r.ok;
}

/** Re-pushes up to 50 sign-ups Todododo has not confirmed yet; returns how many went through. */
export async function syncPending(f: typeof fetch, env: PushEnv, db: Db): Promise<number> {
  const { results } = await db
    .prepare(`SELECT email, platform, newsletter, consent_version, created_at FROM signups WHERE synced_at IS NULL ORDER BY created_at LIMIT ${BATCH}`)
    .all<{ email: string; platform: string; newsletter: number; consent_version: string; created_at: string }>();
  let synced = 0;
  for (const r of results) {
    if (await pushAndMark(f, env, db, { ...r, newsletter: Boolean(r.newsletter) })) synced++;
  }
  return synced;
}
