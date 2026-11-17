import { renderDashboard, type Row } from "./dashboard.ts";
import { dayOf, parseOpen, recordOpen } from "./open.ts";

interface Env {
  DB: {
    prepare(sql: string): {
      bind(...values: unknown[]): { run(): Promise<unknown>; all(): Promise<{ results: unknown[] }> };
    };
  };
}

const MAX_BODY_BYTES = 200;

/** Reads at most [limit] bytes of the body; null if there is more, so a huge body is never held in memory. */
async function readLimited(request: Request, limit: number): Promise<string | null> {
  const reader = request.body?.getReader();
  if (!reader) return "";
  const bytes = new Uint8Array(limit);
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    if (size + value.length > limit) {
      await reader.cancel();
      return null;
    }
    bytes.set(value, size);
    size += value.length;
  }
  return new TextDecoder().decode(bytes.subarray(0, size));
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);

    if (pathname === "/v1/open") {
      if (request.method !== "POST") return new Response(null, { status: 405, headers: { Allow: "POST" } });
      const length = request.headers.get("content-length");
      const declared = length !== null && /^\d+$/.test(length) ? Number(length) : null;
      const body = await readLimited(request, MAX_BODY_BYTES);
      if (body === null) return new Response(null, { status: 400 });
      const ping = parseOpen(request.method, declared, body);
      if ("error" in ping) return new Response(null, { status: 400 });
      await recordOpen(env.DB, ping.version, ping.os, dayOf(new Date()));
      return new Response(null, { status: 204 });
    }

    if (pathname === "/dashboard") {
      if (request.method !== "GET") return new Response(null, { status: 405, headers: { Allow: "GET" } });
      const today = dayOf(new Date());
      const since = dayOf(new Date(Date.now() - 89 * 86_400_000));
      const { results } = await env.DB.prepare("SELECT day, version, os, count FROM opens WHERE day >= ? ORDER BY day")
        .bind(since)
        .all();
      return new Response(renderDashboard(results as Row[], today), {
        headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
      });
    }

    return new Response("Not found", { status: 404 });
  },
};
