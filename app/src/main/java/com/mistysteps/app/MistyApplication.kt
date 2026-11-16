package com.mistysteps.app

import android.app.Application
import com.mistysteps.app.character.Characters
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
        Characters.init(this)
        PinSprites.init(this)
        BatteryLog.init(this)
        MapsGraph.init(this)
        Analytics.pingIfAllowed(this)
    }
}
