#!/bin/sh
# Battery test runs for Misty Steps (debug builds only).
#
#   debug/run.sh start                 reset Android's battery stats and clear the app's logs
#   debug/run.sh pull [label]          copy logs + battery stats into debug/runs/<date>_<label>/
#
# Pick the phone with ADB_SERIAL=<ip:port> (wireless debugging), e.g.
#   ADB_SERIAL=<phone> debug/run.sh pull pocket-walk
set -eu

PKG=com.potloodgum.mistysteps.debug   # battery logging only exists in the Dev build
HERE=$(cd "$(dirname "$0")" && pwd)
adb_() { if [ -n "${ADB_SERIAL:-}" ]; then adb -s "$ADB_SERIAL" "$@"; else adb "$@"; fi; }
# run-as reads the app's private files; it works because the debug APK is debuggable.
app_cat() { adb_ exec-out "run-as $PKG cat files/debug/$1 2>/dev/null" || true; }

case "${1:-}" in
start)
    adb_ shell dumpsys batterystats --reset
    adb_ shell run-as "$PKG" rm -f files/debug/battery.csv files/debug/sessions.csv
    echo "Battery stats reset and app logs cleared."
    echo "Unplug the phone (charging hides the drain), then start the test."
    echo "Afterwards: debug/run.sh pull <label>"
    ;;
pull)
    label=${2:-run}
    out="$HERE/runs/$(date +%Y-%m-%d_%H%M)_$label"
    mkdir -p "$out"
    app_cat battery.csv > "$out/battery.csv"
    app_cat sessions.csv > "$out/sessions.csv"
    adb_ shell dumpsys batterystats "$PKG" > "$out/batterystats.txt"
    adb_ shell dumpsys battery > "$out/battery_now.txt"
    {
        echo "model: $(adb_ shell getprop ro.product.model | tr -d '\r')"
        echo "android: $(adb_ shell getprop ro.build.version.release | tr -d '\r')"
        echo "app: $(adb_ shell dumpsys package "$PKG" | grep -m1 versionName | tr -d ' \r')"
        echo "pulled: $(date -Iseconds)"
    } > "$out/device.txt"
    [ -f "$out/notes.md" ] || cp "$HERE/notes-template.md" "$out/notes.md"
    if [ -s "$out/battery.csv" ]; then
        python3 "$HERE/summarize.py" "$out/battery.csv" "$out/sessions.csv" | tee "$out/summary.txt"
    else
        echo "No battery.csv on the phone. Is the debug build installed and was the app opened?"
    fi
    echo
    echo "Saved to $out (fill in notes.md)."
    ;;
*)
    sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
