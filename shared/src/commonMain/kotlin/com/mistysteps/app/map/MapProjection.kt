package com.mistysteps.app.map

import com.mistysteps.app.data.toRadians
import com.mistysteps.app.data.Mercator
import kotlin.math.cos
import kotlin.math.pow

data class LatLon(val lat: Double, val lon: Double)

data class ScreenPoint(val x: Float, val y: Float)

/**
 * How a map turns coordinates into pixels of the view it sits in. The fog draws through this, so the
 * same drawing code works on any map; the app's map is [MapLibreProjection].
 */
interface MapProjection {
    val width: Int
    val height: Int
    val zoom: Double

    /** Width of the whole world in pixels at the current zoom. */
    val worldSizePx: Double

    fun toScreen(lat: Double, lon: Double): ScreenPoint
    fun fromScreen(x: Float, y: Float): LatLon
    fun metersToPixels(meters: Double, lat: Double): Float
}

/** A plain web-mercator map centred on a point: the model the adapters are checked against, and what the tests use. */
class MercatorProjection(
    override val width: Int,
    override val height: Int,
    centerLat: Double,
    centerLon: Double,
    override val zoom: Double,
    tileSize: Int = 256,
) : MapProjection {
    private val centerX = Mercator.x(centerLon)
    private val centerY = Mercator.y(centerLat)
    override val worldSizePx: Double = tileSize * 2.0.pow(zoom)

    override fun toScreen(lat: Double, lon: Double) = ScreenPoint(
        (width / 2.0 + (Mercator.x(lon) - centerX) * worldSizePx).toFloat(),
        (height / 2.0 + (Mercator.y(lat) - centerY) * worldSizePx).toFloat(),
    )

    override fun fromScreen(x: Float, y: Float) = LatLon(
        Mercator.lat(centerY + (y - height / 2.0) / worldSizePx),
        Mercator.lon(centerX + (x - width / 2.0) / worldSizePx),
    )

    override fun metersToPixels(meters: Double, lat: Double): Float =
        (meters / (EARTH_CIRCUMFERENCE * cos(toRadians(lat)) / worldSizePx)).toFloat()

    private companion object {
        const val EARTH_CIRCUMFERENCE = 40_075_016.686
    }
}
