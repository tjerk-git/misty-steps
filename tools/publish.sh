#!/bin/sh
# Publishes Misty Steps to the Potloodgum F-Droid repo (https://fdroid.potloodgum.com/repo).
#
#   tools/publish.sh            build the signed release, update the repo index, deploy
#   tools/publish.sh --no-deploy    same, but stop before uploading (check fdroid/repo first)
#
# Before a new release: raise versionCode/versionName in app/build.gradle.kts and add
# fastlane/metadata/android/en-US/changelogs/<versionCode>.txt.
# Needs: keystore.properties (app key) and fdroid/config.yml (repo key), both kept out of git.
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APPID=com.potloodgum.mistysteps
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH="$JAVA_HOME/bin:$PATH"

cd "$ROOT"
[ -f keystore.properties ] || { echo "keystore.properties missing: can't sign the release"; exit 1; }
[ -f fdroid/config.yml ] || { echo "fdroid/config.yml missing: can't sign the repo"; exit 1; }

./gradlew -q testDebugUnitTest assembleRelease
VC=$(sed -n 's/.*versionCode *= *\([0-9]*\).*/\1/p' app/build.gradle.kts)
[ -f "fastlane/metadata/android/en-US/changelogs/$VC.txt" ] || { echo "Add fastlane/metadata/android/en-US/changelogs/$VC.txt first"; exit 1; }

# The APK, named the way F-Droid expects.
mkdir -p fdroid/repo fdroid/archive
cp app/build/outputs/apk/release/app-release.apk "fdroid/repo/${APPID}_${VC}.apk"
# Store texts, icon and screenshots.
rm -rf "fdroid/metadata/$APPID"
cp -R fastlane/metadata/android "fdroid/metadata/$APPID"
# The repo's own icon: fdroid reads `repo_icon` relative to fdroid/ and copies it into the repo.
cp fastlane/metadata/android/en-US/images/icon.png fdroid/potloodgum.png

cd fdroid
fdroid update --create-metadata --pretty --nosign 2>/dev/null || true  # first run may need to add missing bits
fdroid update --pretty
cd ..

# A small landing page for people who open the address in a browser.
SITE=fdroid/site
rm -rf "$SITE/public" && mkdir -p "$SITE/public"
cp -R fdroid/repo "$SITE/public/repo"
# The repo fingerprint: SHA-256 of the certificate that signs the index.
FP=$(keytool -printcert -jarfile fdroid/repo/index-v1.jar | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f')
[ -n "$FP" ] || { echo "Could not read the repo fingerprint"; exit 1; }
sed -e "s/@FINGERPRINT@/$FP/g" -e "s/@APK@/${APPID}_${VC}.apk/g" tools/fdroid-landing.html > "$SITE/public/index.html"

if [ "${1:-}" = "--no-deploy" ]; then echo "Built $SITE/public (not deployed)."; exit 0; fi
(cd "$SITE" && cf deploy)
echo "Published: https://fdroid.potloodgum.com/repo"
