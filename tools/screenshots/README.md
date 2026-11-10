# Store screenshots

The screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/` are taken on an
emulator with made-up walks through Amsterdam, so no real trail, home or neighbourhood shows.
Never take them on your own phone with your own trails.

1. Make the demo data (needs the Geofabrik dump in `tools/maps/data/`, see `tools/maps/index.py`):

   ```sh
   uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt \
       python -I tools/screenshots/demo_walks.py tools/maps/data/netherlands-latest.osm.pbf /tmp/misty-demo
   ```

   Five walks through Westerpark, the Jordaan and the canal belt along real streets, eight pins,
   the Trek Horse as character. Writes `trails.db`, `misty.xml` and `here.txt` (where you stand).

2. Start the emulator (AVD `misty_shots`: Pixel 8, Android 36, Google APIs, arm64) and install a
   debug build: `emulator -avd misty_shots -no-snapshot` and `adb -s emulator-5554 install -r app-debug.apk`.

3. Load the data and set the scene:

   ```sh
   e(){ adb -s emulator-5554 "$@"; }; P=com.potloodgum.mistysteps.debug; D=/tmp/misty-demo
   e shell am force-stop $P
   e push $D/trails.db $D/misty.xml /data/local/tmp/ && e shell chmod 644 /data/local/tmp/trails.db /data/local/tmp/misty.xml
   e shell "run-as $P sh -c 'mkdir -p databases shared_prefs; rm -f databases/trails.db-* shared_prefs/camera.xml; cp /data/local/tmp/trails.db databases/; cp /data/local/tmp/misty.xml shared_prefs/'"
   e shell pm grant $P android.permission.ACCESS_FINE_LOCATION
   read LON LAT < $D/here.txt; e emu geo fix $LON $LAT
   # Status bar: 10:00, full battery, wifi only, no notifications.
   e shell settings put global sysui_demo_allowed 1
   e shell am broadcast -a com.android.systemui.demo -e command enter
   e shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
   e shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   e shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
   e shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
   e shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   ```

4. Take the five shots with `adb -s emulator-5554 exec-out screencap -p > N_name.png`: the map
   (zoomed in one step on the character), the stats page, the postcard of the 8 November walk
   (tap it in "Your walks"), the character screen and About. For About, build with the release's
   version name and no `-dev` suffix, and revert that change afterwards.

Map tiles in the shots come from tile.openstreetmap.org while you look at them, like normal app use.
