# Tester sign-up (testers.potloodgum.com)

Cloudflare Worker `misty-testers` (config in `site/cloudflare.config.ts`): static page in `site/public/`, `POST /api/signup` into D1 `misty-testers` (EU jurisdiction).

- Deploy: `cd testers/site && cf deploy`
- Read sign-ups: `cf d1 query <db id> --sql="SELECT email, platform, newsletter, created_at FROM signups"` (find the id with `cf d1 list`)
- Mark invited: `UPDATE signups SET invited_at = datetime('now') WHERE email = '...'`
- Delete someone on request, **in two places**: Todododo (Testers page, Delete) and here: `DELETE FROM signups WHERE email = '...'`
- Todododo copy: each sign-up is also pushed to `POST /testers/signup` on todo.potloodgum.com (Worker setting `TODODODO_URL`, secret `TODODODO_TOKEN` = Todododo's `TESTERS_TOKEN`). `synced_at` is set once Todododo answered 200; an hourly cron (`triggers.scheduled` in the config) re-pushes rows where it is still empty. See rows waiting: `SELECT email, created_at FROM signups WHERE synced_at IS NULL`
- Set or rotate the secret with `cf workers secrets` (find the exact subcommand with `cf cli search "set worker secret"`), and the same value as `TESTERS_TOKEN` in the Todododo site environment.
- The iPhone TestFlight public link is in `site/public/index.html` (shown when iPhone is picked and on the thank-you screen); change it there if the link is ever replaced.
- The demo video (`demo.mp4`, captions `demo.en.vtt` and `demo.nl.vtt`) was recorded in the iOS simulator with `simctl io recordVideo`, using the launch args `-onboarding_done YES -analytics_asked YES -picker_seen YES -autostart 1 -droppin 1` and `simctl location start` for the walk. Stop the recorder with SIGINT and `wait` for it, or the simulator keeps recording.
- Consent texts are in `index.html`; change `CONSENT_VERSION` in `src/signup.ts` when you change them.
