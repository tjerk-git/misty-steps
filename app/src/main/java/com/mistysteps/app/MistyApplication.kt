package com.mistysteps.app

import android.app.Application
import com.mistysteps.app.character.Characters
import com.mistysteps.app.character.SharedPreferencesStore
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.maps.MapsGraph
import com.mistysteps.app.pin.PinSprites
import com.mistysteps.app.stats.Analytics

class MistyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The online map is gone: delete what it left on the phone.
        cacheDir.resolve("osmdroid-tiles").deleteRecursively()
        filesDir.resolve("osmdroid").deleteRecursively()
        FogStore.init(this)
        Characters.init(
            assets.open("characters.txt").bufferedReader().use { it.readLines() },
            SharedPreferencesStore(getSharedPreferences("misty", MODE_PRIVATE)),
        )
        PinSprites.init(assets.open("pins.txt").bufferedReader().use { it.readLines() })
        BatteryLog.init(this)
        MapsGraph.init(this)
        Analytics.pingIfAllowed(this)
    }
}
