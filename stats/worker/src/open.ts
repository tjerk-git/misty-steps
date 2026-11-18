// The open count: what a ping may say, and the one thing the Worker does with it (adds one to a counter).
// Nothing here knows who sent the message, and nothing but a count is kept.

const MAX_BODY_BYTES = 200;
const VERSION = /^\d+\.\d+\.\d+(-[a-z]+)?$/;
const PLATFORMS = ["android", "ios"] as const;

export type OpenPing = { version: string; os: (typeof PLATFORMS)[number] };
export type Refused = { error: string };

/** Checks a request against the one message the app sends: `{"v":"1.4.0","os":"android"}`, nothing more. */
export function parseOpen(method: string, contentLength: number | null, body: string): OpenPing | Refused {
  if (method !== "POST") return { error: "method" };
  if (contentLength !== null && contentLength > MAX_BODY_BYTES) return { error: "too big" };
  if (new TextEncoder().encode(body).length > MAX_BODY_BYTES) return { error: "too big" };

  let data: unknown;
  try {
    data = JSON.parse(body);
  } catch {
    return { error: "not json" };
  }
  if (typeof data !== "object" || data === null || Array.isArray(data)) return { error: "shape" };

  const keys = Object.keys(data).sort();
  if (keys.length !== 2 || keys[0] !== "os" || keys[1] !== "v") return { error: "shape" };

  const { v, os } = data as { v: unknown; os: unknown };
  if (typeof v !== "string" || !VERSION.test(v)) return { error: "version" };
  if (typeof os !== "string" || !(PLATFORMS as readonly string[]).includes(os)) return { error: "platform" };
  return { version: v, os: os as OpenPing["os"] };
}

/** The UTC day, `YYYY-MM-DD`: the only time that is kept. */
export function dayOf(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** The part of a D1 database that [recordOpen] needs, so tests can use a plain object. */
export interface Db {
  prepare(sql: string): { bind(...values: unknown[]): { run(): Promise<unknown> } };
}

export async function recordOpen(db: Db, version: string, os: string, day: string): Promise<void> {
  await db
    .prepare(
      "INSERT INTO opens (day, version, os, count) VALUES (?, ?, ?, 1) " +
        "ON CONFLICT(day, version, os) DO UPDATE SET count = count + 1",
    )
    .bind(day, version, os)
    .run();
}
