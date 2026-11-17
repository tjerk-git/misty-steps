// The dashboard page: opens per day for the last 90 days and a table per version. Plain HTML and inline SVG,
// no scripts and no requests to anywhere else.

export type Row = { day: string; version: string; os: string; count: number };

const DAYS = 90;

function escape(text: string): string {
  return text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

/** The [DAYS] days ending at [today] (`YYYY-MM-DD`), oldest first. */
function window(today: string): string[] {
  const end = new Date(`${today}T00:00:00Z`).getTime();
  return Array.from({ length: DAYS }, (_, i) => new Date(end - (DAYS - 1 - i) * 86_400_000).toISOString().slice(0, 10));
}

export function renderDashboard(rows: Row[], today: string): string {
  const days = window(today);
  const inWindow = new Set(days);
  const perDay = new Map<string, number>(days.map((d) => [d, 0]));
  const perVersion = new Map<string, number>();
  for (const r of rows) {
    if (!inWindow.has(r.day)) continue;
    perDay.set(r.day, perDay.get(r.day)! + r.count);
    const key = `${r.version}\u0000${r.os}`;
    perVersion.set(key, (perVersion.get(key) ?? 0) + r.count);
  }

  const max = Math.max(1, ...perDay.values());
  const barW = 8;
  const height = 180;
  const bars = days
    .map((d, i) => {
      const n = perDay.get(d)!;
      const h = Math.round((n / max) * height);
      return `<rect x="${i * (barW + 2)}" y="${height - h}" width="${barW}" height="${h}" fill="#2e3550"><title>${d}: ${n}</title></rect>`;
    })
    .join("");
  const total = [...perDay.values()].reduce((a, b) => a + b, 0);

  const versionRows = [...perVersion.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([key, n]) => {
      const [version, os] = key.split("\u0000");
      return `<tr><td>${escape(version)}</td><td>${escape(os)}</td><td class="n">${n}</td></tr>`;
    })
    .join("");

  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Misty Steps: opens</title>
<style>body{font:16px/1.5 system-ui,sans-serif;margin:2rem auto;max-width:60rem;padding:0 1rem;color:#2e3550}
h1{font-size:1.4rem}table{border-collapse:collapse}td,th{padding:.25rem 1rem .25rem 0;text-align:left}
.n{text-align:right}.note{color:#5b6b8c;font-size:.9rem}svg{max-width:100%;height:auto}</style></head>
<body>
<h1>Misty Steps: opens, last ${DAYS} days</h1>
<p>${total} opens in total, only from people who allowed the count.</p>
<svg viewBox="0 0 ${days.length * (barW + 2)} ${height}" role="img" aria-label="Opens per day">${bars}</svg>
<h2>Per version</h2>
<table><tr><th>Version</th><th>Platform</th><th>Opens</th></tr>${versionRows}</table>
<p class="note">Counts can be inflated by anyone who knows the address. Nothing else is stored: no IP address, no ID, no timestamps finer than a day.</p>
</body></html>`;
}
