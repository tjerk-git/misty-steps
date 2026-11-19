# Tester sign-up (testers.potloodgum.com)

Cloudflare Worker `misty-testers` (config in `site/cloudflare.config.ts`): static page in `site/public/`, `POST /api/signup` into D1 `misty-testers` (EU jurisdiction).

- Deploy: `cd testers/site && cf deploy`
- Read sign-ups: `cf d1 query <db id> --sql="SELECT email, platform, newsletter, created_at FROM signups"` (find the id with `cf d1 list`)
- Mark invited: `UPDATE signups SET invited_at = datetime('now') WHERE email = '...'`
- Delete someone on request, **in two places**: Todododo (Testers page, Delete) and here: `DELETE FROM signups WHERE email = '...'`
- Todododo copy: Todododo **pulls** (`php artisan testers:pull`, every five minutes): `GET /api/pending` returns sign-ups with `synced_at` empty, then `POST /api/ack` marks them. Both need `Authorization: Bearer <TESTERS_EXPORT_TOKEN>` (Worker secret, same value as `TESTERS_TOKEN` in the Todododo env) and are a 404 otherwise. (Pushing from the Worker does not work: `todo.potloodgum.com` is a grey-cloud name in the same zone, so the Worker reaches nginx over plain HTTP and gets a redirect.) Rows waiting: `SELECT email, created_at FROM signups WHERE synced_at IS NULL`
- Set or rotate the secret with `cf workers secrets update` (find usage with `cf cli search "set worker secret"`), or `cf deploy --secrets-file <dotenv file>`.
- The iPhone TestFlight public link is in `site/public/index.html` (shown when iPhone is picked and on the thank-you screen); change it there if the link is ever replaced.
- The demo video (`demo.mp4`, captions `demo.en.vtt` and `demo.nl.vtt`) was recorded in the iOS simulator with `simctl io recordVideo`, using the launch args `-onboarding_done YES -analytics_asked YES -picker_seen YES -autostart 1 -droppin 1` and `simctl location start` for the walk. Stop the recorder with SIGINT and `wait` for it, or the simulator keeps recording.
- Consent texts are in `index.html`; change `CONSENT_VERSION` in `src/signup.ts` when you change them.
