// What Todododo pulls: the sign-ups it has not confirmed yet (GET /api/pending), then a confirmation
// (POST /api/ack). Todododo upserts by email, so a lost confirmation only means the same rows come again.

export interface Stmt {
  bind(...v: unknown[]): Stmt;
  run(): Promise<unknown>;
  all<T = Record<string, unknown>>(): Promise<{ results: T[] }>;
}
export interface Db {
  prepare(sql: string): Stmt;
}

export interface PendingSignup {
  email: string;
  platform: string;
  newsletter: boolean;
  consent_version: string;
  signed_up_at: string;
}

const BATCH = 100;

/** True only for `Authorization: Bearer <token>` with the exact token; false when no token is set. Compares without stopping at the first difference. */
export function authorized(header: string | null, token: string | undefined): boolean {
  if (!token || !header || !header.startsWith("Bearer ")) return false;
  const given = header.slice(7);
  let diff = given.length ^ token.length;
  for (let i = 0; i < token.length; i++) diff |= (given.charCodeAt(i) || 0) ^ token.charCodeAt(i);
  return diff === 0;
}

/** Up to 100 sign-ups not yet confirmed by Todododo, oldest first. */
export async function pendingSignups(db: Db): Promise<PendingSignup[]> {
  const { results } = await db
    .prepare(`SELECT email, platform, newsletter, consent_version, created_at FROM signups WHERE synced_at IS NULL ORDER BY created_at LIMIT ${BATCH}`)
    .all<{ email: string; platform: string; newsletter: number; consent_version: string; created_at: string }>();
  return results.map((r) => ({
    email: r.email,
    platform: r.platform,
    newsletter: Boolean(r.newsletter),
    consent_version: r.consent_version,
    signed_up_at: r.created_at,
  }));
}

/** Marks the given addresses as pulled (strings up to 254 characters, at most 100); returns how many. */
export async function acknowledge(db: Db, emails: unknown[]): Promise<number> {
  const clean = emails.filter((e): e is string => typeof e === "string" && e.length > 0 && e.length <= 254).slice(0, BATCH);
  const now = new Date().toISOString();
  for (const email of clean) await db.prepare("UPDATE signups SET synced_at = ? WHERE email = ?").bind(now, email).run();
  return clean.length;
}
