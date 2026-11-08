package com.mistysteps.app.fog

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.floor
import kotlin.random.Random

/** Seamlessly tiling fractal value noise, used as the soft swirl inside the fog. */
object FogTexture {

    fun generate(size: Int, seed: Int, tint: Int, maxAlpha: Float, contrast: Float): Bitmap {
        val rnd = Random(seed)
        val periods = intArrayOf(3, 6, 12, 24)
        val amps = floatArrayOf(1f, 0.5f, 0.25f, 0.125f)
        val grids = periods.map { p -> FloatArray(p * p) { rnd.nextFloat() } }

        val values = FloatArray(size * size)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (py in 0 until size) {
            for (px in 0 until size) {
                var v = 0f
                for (o in periods.indices) {
                    v += amps[o] * sample(grids[o], periods[o], px.toFloat() / size, py.toFloat() / size)
                }
                values[py * size + px] = v
                if (v < min) min = v
                if (v > max) max = v
            }
        }

        val r = Color.red(tint)
        val g = Color.green(tint)
        val b = Color.blue(tint)
        val pixels = IntArray(size * size)
        for (i in values.indices) {
            val n = (values[i] - min) / (max - min)
            val shaped = smooth(((n - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f))
            val a = (shaped * maxAlpha * 255).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(a, r, g, b)
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Bilinear, smoothstepped lookup into a wrapping [period]x[period] lattice. u,v in [0,1). */
    private fun sample(grid: FloatArray, period: Int, u: Float, v: Float): Float {
        val x = u * period
        val y = v * period
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        val xa = x0 % period
        val xb = (x0 + 1) % period
        val ya = y0 % period
        val yb = (y0 + 1) % period
        val top = lerp(grid[ya * period + xa], grid[ya * period + xb], fx)
        val bottom = lerp(grid[yb * period + xa], grid[yb * period + xb], fx)
        return lerp(top, bottom, fy)
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
