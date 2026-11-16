package com.mistysteps.app.map

import com.mistysteps.app.data.FogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The trail of little steps you leave behind, as two dotted lines on the map: bright (Ember) for the walk you are on,
 * faint grey-blue for earlier walks. Drawn by MapLibre in the same frame as the map, so the dots never drift off the streets.
 * Install it again after every style reload ([install]).
 */
class TrailLayer {
    private var style: Style? = null
    private var pastKey: Pair<Int, Long?>? = null

    /** Adds the sources and layers to a freshly loaded [style] and fills them. */
    fun install(style: Style) {
        this.style = style
        pastKey = null
        style.addSource(GeoJsonSource(PAST_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(NOW_SOURCE, EMPTY))
        // A dotted line: a near-zero dash with round caps draws a row of dots. Dash lengths are in line widths.
        style.addLayer(
            LineLayer(PAST_LAYER, PAST_SOURCE).withProperties(
                lineColor("#5B6B8C"), lineWidth(3f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineDasharray(arrayOf(0.01f, 8f / 3f)),
            ).also { it.setProperties(org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.55f)) }
        )
        style.addLayer(
            LineLayer(NOW_LAYER, NOW_SOURCE).withProperties(
                lineColor("#B4536A"), lineWidth(4.5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineDasharray(arrayOf(0.01f, 2f)),
            )
        )
        refresh()
    }

    /**
     * Rebuilds the trail from [FogStore.trails]. Earlier walks only when they changed (a walk started or ended); the walk
     * you are on every time. Main thread: the trail is only safe to read there.
     */
    fun refresh() {
        val style = style ?: return
        val trails = FogStore.trails.segments
        val session = FogStore.session?.id
        val (now, past) = trails.partition { session != null && it.sessionId == session }
        val key = past.size to session
        if (key != pastKey) {
            pastKey = key
            style.getSourceAs<GeoJsonSource>(PAST_SOURCE)?.setGeoJson(TrailGeoJson.lines(past))
        }
        style.getSourceAs<GeoJsonSource>(NOW_SOURCE)?.setGeoJson(TrailGeoJson.lines(now))
    }

    /** Refreshes when new ground is cleared, at most a few times a second. */
    fun watch(scope: CoroutineScope): Job = scope.launch(Dispatchers.Main) {
        FogStore.version.collectLatest {
            refresh()
            delay(REFRESH_MS)
        }
    }

    private companion object {
        const val PAST_SOURCE = "trail-past"
        const val NOW_SOURCE = "trail-now"
        const val PAST_LAYER = "trail-past-line"
        const val NOW_LAYER = "trail-now-line"
        const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
        const val REFRESH_MS = 400L
    }
}
