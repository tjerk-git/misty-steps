import { defineConfig } from "cf/config";

// The Potloodgum F-Droid repo: static files only (public/ is filled by tools/publish.sh).
export default defineConfig({
  worker: {
    name: "potloodgum-fdroid",
    compatibilityDate: "2026-09-21",
    observability: { enabled: true },
    domains: ["fdroid.potloodgum.com"],
  },
});
