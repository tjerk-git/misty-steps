#!/bin/sh
# Releases Misty Steps to the Potloodgum F-Droid repo (https://fdroid.potloodgum.com/repo).
#
#   tools/release.sh patch|minor|major            release for real
#   tools/release.sh patch|minor|major --dry-run  run every check and build the repo, but don't
#                                                 deploy, commit or tag (the version is put back)
#
# Before running: write the changelog for the NEXT versionCode (the script tells you the file),
# fastlane/metadata/android/en-US/changelogs/<versionCode>.txt, max 500 characters.
#
# What it does, stopping at the first problem:
#   1. checks: on main, nothing uncommitted except that changelog, keys present, newer than what's live
#   2. bumps version.properties (versionCode +1, versionName by patch/minor/major)
#   3. unit tests, lint, signed release build
#   4. verifies the APK: app id, version, not debuggable, signed with the Potloodgum release key
#   5. updates the F-Droid repo index (signed with the repo key) and the landing page
#   6. deploys to Cloudflare and checks the new version is live
#   7. commits, tags v<versionName> and pushes
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
APPID=com.potloodgum.mistysteps
REPO_URL=https://fdroid.potloodgum.com/repo
# SHA-256 of the release signing certificate (~/Keystores/potloodgum/mistysteps-release.jks).
RELEASE_CERT=7cf2c477f3f731e8210217f18138cff1fb9631725d47ad975e2ca89bb40b6515
CHANGELOGS=fastlane/metadata/android/en-US/changelogs

export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH="$JAVA_HOME/bin:$PATH"
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mStopped: %s\033[0m\n' "$*" >&2; exit 1; }

TYPE=${1:-}
DRY=0
[ "${2:-}" = "--dry-run" ] && DRY=1
case "$TYPE" in patch|minor|major) ;; *) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;; esac

cd "$ROOT"

# ---- 1. checks ----------------------------------------------------------------------------
say "Checks"
CODE=$(sed -n 's/^versionCode=//p' version.properties)
NAME=$(sed -n 's/^versionName=//p' version.properties)
NEWCODE=$((CODE + 1))
NEWNAME=$(echo "$NAME" | awk -F. -v t="$TYPE" '{
  if (t == "major") { $1++; $2 = 0; $3 = 0 } else if (t == "minor") { $2++; $3 = 0 } else { $3++ }
  print $1 "." $2 "." $3 }')
CHANGELOG="$CHANGELOGS/$NEWCODE.txt"
echo "Release: $NAME ($CODE) -> $NEWNAME ($NEWCODE)"

[ "$(git branch --show-current)" = main ] || die "not on the main branch"
DIRTY=$(git status --porcelain | grep -v " $CHANGELOG\$" || true)
if [ -n "$DIRTY" ]; then
  [ $DRY = 1 ] && echo "Warning: uncommitted changes (ignored for a dry run):" && echo "$DIRTY" \
    || die "uncommitted changes besides the changelog:
$DIRTY"
fi
[ -f keystore.properties ] || die "keystore.properties missing (release signing key)"
[ -f fdroid/config.yml ] || die "fdroid/config.yml missing (repo signing key)"
for t in fdroid cf; do command -v $t >/dev/null || die "'$t' not installed"; done
git rev-parse -q --verify "refs/tags/v$NEWNAME" >/dev/null && die "tag v$NEWNAME already exists"
[ -s "$CHANGELOG" ] || die "write the changelog first: $CHANGELOG"
[ "$(wc -c < "$CHANGELOG")" -le 500 ] || die "$CHANGELOG is over F-Droid's 500 character limit"

LIVE=$(curl -fsS "$REPO_URL/index-v2.json" | python3 -c "
import json, sys
p = json.load(sys.stdin)['packages'].get('$APPID', {}).get('versions', {})
print(max([v['manifest']['versionCode'] for v in p.values()] or [0]))") || die "can't read the live repo"
echo "Live in the repo: versionCode $LIVE"
[ "$NEWCODE" -gt "$LIVE" ] || die "versionCode $NEWCODE isn't newer than the live $LIVE"

# ---- 2. bump ------------------------------------------------------------------------------
say "Version $NEWNAME ($NEWCODE)"
cp version.properties /tmp/misty-version.$$
restore() { [ -f /tmp/misty-version.$$ ] && mv /tmp/misty-version.$$ version.properties; }
trap 'restore' EXIT
sed -i '' -e "s/^versionCode=.*/versionCode=$NEWCODE/" -e "s/^versionName=.*/versionName=$NEWNAME/" version.properties

# ---- 3. tests and build -------------------------------------------------------------------
say "Tests, lint and release build"
./gradlew -q clean testDebugUnitTest lintRelease assembleRelease
APK=app/build/outputs/apk/release/app-release.apk

# ---- 4. verify the APK --------------------------------------------------------------------
say "Checking the APK"
BADGING=$("$BT/aapt2" dump badging "$APK")
echo "$BADGING" | grep -q "package: name='$APPID' versionCode='$NEWCODE' versionName='$NEWNAME'" \
  || die "APK has the wrong id or version: $(echo "$BADGING" | head -1)"
echo "$BADGING" | grep -q "application-debuggable" && die "APK is debuggable (built with -PdebuggableRelease?)"
CERT=$("$BT/apksigner" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
[ "$CERT" = "$RELEASE_CERT" ] || die "APK isn't signed with the Potloodgum release key (got $CERT)"
echo "OK: $APPID $NEWNAME ($NEWCODE), release key, not debuggable"

# ---- 5. repo ------------------------------------------------------------------------------
say "Updating the F-Droid repo"
mkdir -p fdroid/repo fdroid/archive
cp "$APK" "fdroid/repo/${APPID}_${NEWCODE}.apk"
rm -rf "fdroid/metadata/$APPID"
cp -R fastlane/metadata/android "fdroid/metadata/$APPID"
cp fastlane/metadata/android/en-US/images/icon.png fdroid/potloodgum.png
LOG=$(mktemp)
(cd fdroid && fdroid update --pretty) > "$LOG" 2>&1 || { cat "$LOG"; die "fdroid update failed"; }
grep -E "WARNING|ERROR" "$LOG" || true

SITE=fdroid/site
rm -rf "$SITE/public" && mkdir -p "$SITE/public"
cp -R fdroid/repo "$SITE/public/repo"
rm -f "$SITE/public/repo/index_unsigned.jar"
FP=$(keytool -printcert -jarfile fdroid/repo/index-v1.jar | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f')
[ -n "$FP" ] || die "couldn't read the repo fingerprint"
sed -e "s/@FINGERPRINT@/$FP/g" -e "s/@APK@/${APPID}_${NEWCODE}.apk/g" tools/fdroid-landing.html > "$SITE/public/index.html"

if [ $DRY = 1 ]; then
  # Take the test build back out of the local repo, so it can't be published by accident.
  rm -f "fdroid/repo/${APPID}_${NEWCODE}.apk"
  (cd fdroid && fdroid update --pretty) > "$LOG" 2>&1 || true
  say "Dry run done: everything passed. Not deployed, committed or tagged; version put back."
  echo "Look at $SITE/public, then run without --dry-run."
  exit 0
fi

# ---- 6. deploy ----------------------------------------------------------------------------
say "Deploying to fdroid.potloodgum.com"
(cd "$SITE" && cf deploy < /dev/null) > "$LOG" 2>&1 || { cat "$LOG"; die "cf deploy failed"; }
tail -3 "$LOG"
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  NOW=$(curl -fsS "$REPO_URL/index-v2.json?$i" | python3 -c "
import json, sys
p = json.load(sys.stdin)['packages'].get('$APPID', {}).get('versions', {})
print(max([v['manifest']['versionCode'] for v in p.values()] or [0]))" || echo 0)
  [ "$NOW" = "$NEWCODE" ] && break
  sleep 5
done
[ "$NOW" = "$NEWCODE" ] || die "deployed, but the live repo still shows versionCode $NOW. Check https://fdroid.potloodgum.com"
echo "Live: versionCode $NEWCODE"

# ---- 7. git -------------------------------------------------------------------------------
say "Commit, tag and push"
rm -f /tmp/misty-version.$$   # keep the bumped version
trap - EXIT
git add version.properties "$CHANGELOG"
git commit -q -m "Release $NEWNAME ($NEWCODE)"
git tag -a "v$NEWNAME" -m "Misty Steps $NEWNAME ($NEWCODE)"
git push -q --follow-tags
say "Released Misty Steps $NEWNAME. F-Droid picks it up on its next refresh."
