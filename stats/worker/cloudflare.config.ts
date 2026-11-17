import { bindings, defineConfig } from "cf/config";

// The opt-in open count for Misty Steps. Stores one number per day, version and platform; logging is off.
export default defineConfig({
  worker: {
    name: "misty-stats",
    compatibilityDate: "2026-09-21",
    entrypoint: "./src/index.ts",
    observability: { enabled: false },
    domains: ["stats.potloodgum.com"],
    env: {
      DB: bindings.d1({ name: "misty-stats" }),
    },
  },
});
