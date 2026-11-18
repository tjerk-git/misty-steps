import { bindings, defineConfig } from "cf/config";

// The Misty Steps tester sign-up page: static files in public/ plus POST /api/signup into D1.
export default defineConfig({
  worker: {
    name: "misty-testers",
    compatibilityDate: "2026-09-21",
    entrypoint: "./src/index.ts",
    observability: { enabled: false },
    domains: ["testers.potloodgum.com"],
    assets: { runWorkerFirst: ["/api/*"] },
    env: {
      DB: bindings.d1({ name: "misty-testers" }),
      ASSETS: bindings.assets(),
    },
  },
});
