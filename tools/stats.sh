#!/bin/sh
# How Misty Steps is doing: downloads and phones checking the Potloodgum F-Droid repo.
#
#   tools/stats.sh        last 7 days
#   tools/stats.sh 30     last 30 days (as far back as Cloudflare keeps the data)
#
# Reads Cloudflare's own request analytics for fdroid.potloodgum.com (path, user agent, status:
# no IP addresses, no locations). Nothing is added to the app or the site for this. Needs the `cf` CLI
# signed in to the Potloodgum Cloudflare account.
set -eu
DAYS=${1:-7}
HOST=fdroid.potloodgum.com
ROOT=$(cd "$(dirname "$0")/.." && pwd)
FROM=$(date -u -v-"${DAYS}"d +%Y-%m-%dT%H:%M:%SZ)
TO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
ZONE=$(cf zones list --name potloodgum.com 2>/dev/null | python3 -c "
import json, sys; d = json.load(sys.stdin); d = d if isinstance(d, list) else d.get('result', d); print(d[0]['id'])") \
  || { echo "Can't reach Cloudflare. Is cf signed in? (cf auth login)"; exit 1; }

cf analytics sql post --scope-zone-tag "$ZONE" --time-range-start "$FROM" --time-range-end "$TO" --query "
  SELECT toDate(timestamp) AS day, clientRequestPath AS path, userAgent AS ua, edgeResponseStatus AS status,
         sum(sampleInterval) AS n
  FROM events.httpRequests
  WHERE clientRequestHttpHost = '$HOST'
  GROUP BY day, path, ua, status
  ORDER BY day
  LIMIT 10000" 2>/dev/null | python3 "$ROOT/tools/stats_report.py" "$DAYS" "$ROOT/fdroid/repo/index-v2.json"
