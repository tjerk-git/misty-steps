package com.mistysteps.app

import android.app.Application
import com.mistysteps.app.character.Characters
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.debug.BatteryLog
import com.mistysteps.app.pin.PinSprites
import org.osmdroid.config.Configuration

class MistyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // OSM tile usage policy requires an identifying user agent.
        Configuration.getInstance().apply {
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = filesDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osmdroid-tiles")
        }
        FogStore.init(this)
        Characters.init(this)
        PinSprites.init(this)
        BatteryLog.init(this)
    }
}
