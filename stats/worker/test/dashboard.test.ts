import assert from "node:assert/strict";
import { test } from "node:test";
import { renderDashboard } from "../src/dashboard.ts";

const rows = [
  { day: "2026-11-17", version: "1.4.0", os: "android", count: 5 },
  { day: "2026-11-17", version: "1.3.2", os: "android", count: 2 },
  { day: "2026-11-15", version: "1.4.0", os: "android", count: 3 },
];

test("shows the total per day and zero for days without rows", () => {
  const html = renderDashboard(rows, "2026-11-17");
  assert.match(html, /2026-11-17: 7/);
  assert.match(html, /2026-11-16: 0/);
  assert.match(html, /2026-11-15: 3/);
});

test("covers the last 90 days and no more", () => {
  const html = renderDashboard(rows, "2026-11-17");
  assert.match(html, /2026-08-20: 0/); // 89 days before the 17th
  assert.doesNotMatch(html, /2026-08-19: /);
});

test("a table per version sums every day in the window", () => {
  const html = renderDashboard(rows, "2026-11-17");
  assert.match(html, /1\.4\.0[^]*?<td[^>]*>8<\/td>/);
  assert.match(html, /1\.3\.2[^]*?<td[^>]*>2<\/td>/);
});

test("no scripts and no web addresses", () => {
  const html = renderDashboard(rows, "2026-11-17");
  assert.doesNotMatch(html, /<script/i);
  assert.doesNotMatch(html, /https?:/i);
});

test("says that anyone who knows the address can inflate the counts", () => {
  assert.match(renderDashboard(rows, "2026-11-17"), /inflated by anyone who knows the address/);
});

test("escapes anything that could break the page", () => {
  const html = renderDashboard([{ day: "2026-11-17", version: "<b>x</b>", os: "android", count: 1 }], "2026-11-17");
  assert.doesNotMatch(html, /<b>x<\/b>/);
  assert.match(html, /&lt;b&gt;x&lt;\/b&gt;/);
});

test("works with no rows at all", () => {
  const html = renderDashboard([], "2026-11-17");
  assert.match(html, /2026-11-17: 0/);
});
