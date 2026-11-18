package com.mistysteps.app.spike

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.mistysteps.app.data.FogStore
import com.mistysteps.app.fog.FogView
import com.mistysteps.app.map.LatLon
import com.mistysteps.app.map.MistyMap
import com.mistysteps.app.map.TrailLayer
import androidx.lifecycle.lifecycleScope
import com.mistysteps.app.maps.MapsGraph

/**
 * Debug-only host for the new map: MistyMap with the fog above it, no trail, pins or character yet.
 *
 *   adb shell am start -n com.potloodgum.mistysteps.debug/com.mistysteps.app.spike.MistyMapActivity
 */
class MistyMapActivity : ComponentActivity() {
    private lateinit var map: MistyMap
    private lateinit var fog: FogView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        map = MistyMap(this, MapsGraph.packs) { FogStore.lastKnown?.let { LatLon(it.first, it.second) } }
        fog = FogView(this, map.projection)
        map.onCameraMove = { fog.moved() }
        val trail = TrailLayer()
        map.addStyleListener { trail.install(it) }
        trail.watch(lifecycleScope)
        val root = FrameLayout(this)
        root.addView(map.mapView)
        root.addView(fog)
        setContentView(root)
        map.onCreate(savedInstanceState)
    }

    override fun onStart() { super.onStart(); map.onStart() }
    override fun onResume() { super.onResume(); map.onResume(); fog.startDrifting() }
    override fun onPause() { fog.stopDrifting(); map.onPause(); super.onPause() }
    override fun onStop() { map.onStop(); super.onStop() }
    override fun onDestroy() { fog.release(); map.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); map.onSaveInstanceState(outState) }
}
