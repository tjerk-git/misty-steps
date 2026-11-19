#!/bin/sh
# Builds the iOS app for TestFlight.
#
#   tools/ios-testflight.sh            archive and export an .ipa locally (checks signing, uploads nothing)
#   tools/ios-testflight.sh --upload   archive, then upload to App Store Connect (the app must exist there)
#
# Signing is automatic: Xcode must be signed in to the Apple Developer account (Xcode > Settings > Accounts).
# The build number is the current UTC time (yymmddHHMM), so every upload is higher than the last.
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export DEVELOPER_DIR=${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}
UPLOAD=0
[ "${1:-}" = "--upload" ] && UPLOAD=1

BUILD=$(date -u +%y%m%d%H%M)
OUT="$ROOT/iosApp/build/testflight"
rm -rf "$OUT" && mkdir -p "$OUT"

cd "$ROOT/iosApp"
xcodegen generate -q
cat > "$OUT/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>method</key><string>app-store-connect</string>
  <key>teamID</key><string>P9KK68PA63</string>
  <key>signingStyle</key><string>automatic</string>
  <key>destination</key><string>$([ $UPLOAD = 1 ] && echo upload || echo export)</string>
  <key>uploadSymbols</key><true/>
</dict></plist>
PLIST

echo "== Archive (build $BUILD)"
xcodebuild -project MistySteps.xcodeproj -scheme MistySteps -configuration Release -destination 'generic/platform=iOS' \
  -archivePath "$OUT/MistySteps.xcarchive" CODE_SIGNING_ALLOWED=NO CURRENT_PROJECT_VERSION="$BUILD" archive -quiet

echo "== Export"
xcodebuild -exportArchive -archivePath "$OUT/MistySteps.xcarchive" -exportOptionsPlist "$OUT/ExportOptions.plist" \
  -exportPath "$OUT/ipa" -allowProvisioningUpdates

if [ $UPLOAD = 1 ]; then echo "Uploaded build $BUILD. It shows in TestFlight after Apple has processed it (a few minutes)."
else echo "Exported: $OUT/ipa (not uploaded)"; fi
