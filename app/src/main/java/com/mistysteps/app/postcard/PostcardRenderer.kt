package com.mistysteps.app.postcard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.mistysteps.app.character.Character
import com.mistysteps.app.data.Mercator
import com.mistysteps.app.data.Session
import com.mistysteps.app.data.TrailPoint
import com.mistysteps.app.fog.FogTexture
import com.mistysteps.app.fog.MistPalette
import com.mistysteps.app.ui.formatArea
import com.mistysteps.app.ui.formatDistance
import com.mistysteps.app.ui.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.text.DateFormat
import java.util.Date
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Draws a walk as a little pixel-style postcard: the map with this walk's clearing cut out
 * of the mist, the trail, your character where you stopped, and the walk's numbers.
 */
object PostcardRenderer {
    private const val W = 1080
    private const val H = 1440
    private const val MARGIN = 54
    private const val MAP_H = 1010
    private const val BORDER = 8f
    /** Room kept free at the map's edges: about the size of the drawn character (18 px × 6). */
    private const val CHARACTER_MARGIN_PX = 120

    private val PAPER = Color.rgb(246, 243, 236)
    private val INK = Color.rgb(46, 53, 80)
    private val DUSK = Color.rgb(91, 107, 140)
    private val EMBER = Color.rgb(180, 83, 106)

    /** Must be called on the main thread (tiles come from osmdroid's tile provider). */
    suspend fun render(context: Context, walk: Session, points: List<TrailPoint>, character: Character?): Bitmap {
        val mapRect = Rect(MARGIN, MARGIN, W - MARGIN, MARGIN + MAP_H)
        val view = View.fit(points, mapRect.width(), mapRect.height())
        val map = Bitmap.createBitmap(mapRect.width(), mapRect.height(), Bitmap.Config.ARGB_8888)
        drawTiles(context, Canvas(map), view)
        return withContext(Dispatchers.Default) { compose(map, mapRect, view, walk, points, character) }
    }

    /** Which part of the world the map shows: mercator centre and pixels per mercator unit. */
    private class View(val cx: Double, val cy: Double, val scale: Double, val w: Int, val h: Int) {
        fun x(lon: Double) = (w / 2.0 + (Mercator.x(lon) - cx) * scale).toFloat()
        fun y(lat: Double) = (h / 2.0 + (Mercator.y(lat) - cy) * scale).toFloat()
        val zoom get() = ln(scale / 256) / ln(2.0)

        companion object {
            fun fit(points: List<TrailPoint>, w: Int, h: Int): View {
                val lat0 = points.firstOrNull()?.lat ?: 0.0
                val minX = points.minOfOrNull { Mercator.x(it.lon) } ?: 0.5
                val maxX = points.maxOfOrNull { Mercator.x(it.lon) } ?: 0.5
                val minY = points.minOfOrNull { Mercator.y(it.lat) } ?: 0.5
                val maxY = points.maxOfOrNull { Mercator.y(it.lat) } ?: 0.5
                // At least ~400 m across, plus room for the clearing around the trail.
                val minSpan = Mercator.metersToUnits(400.0, lat0)
                val pad = Mercator.metersToUnits(120.0, lat0)
                val cx = (minX + maxX) / 2
                val cy = (minY + maxY) / 2
                val spanX = max(maxX - minX + 2 * pad, minSpan)
                val spanY = max(maxY - minY + 2 * pad, minSpan)
                // Keep the character (drawn above and beside the last spot) inside the frame.
                val usableW = (w - 2 * CHARACTER_MARGIN_PX).coerceAtLeast(w / 2)
                val usableH = (h - 2 * CHARACTER_MARGIN_PX).coerceAtLeast(h / 2)
                var scale = min(usableW / spanX, usableH / spanY)
                // Keep to zoom levels the tiles exist for.
                scale = scale.coerceIn(256 * 2.0.pow(3), 256 * 2.0.pow(17.5))
                return View(cx, cy, scale, w, h)
            }
        }
    }

    private suspend fun drawTiles(context: Context, canvas: Canvas, view: View) {
        canvas.drawColor(Color.rgb(242, 239, 233))
        val z = floor(view.zoom).toInt().coerceIn(0, 19)
        val n = 1 shl z
        val tilePx = view.scale / n // on-screen size of one tile
        val left = view.cx - view.w / 2.0 / view.scale
        val top = view.cy - view.h / 2.0 / view.scale
        val tx0 = floor(left * n).toInt().coerceAtLeast(0)
        val tx1 = floor((left + view.w / view.scale) * n).toInt().coerceAtMost(n - 1)
        val ty0 = floor(top * n).toInt().coerceAtLeast(0)
        val ty1 = floor((top + view.h / view.scale) * n).toInt().coerceAtMost(n - 1)
        val wanted = ArrayList<Long>()
        for (tx in tx0..tx1) for (ty in ty0..ty1) wanted += MapTileIndex.getTileIndex(z, tx, ty)

        val provider = MapTileProviderBasic(context.applicationContext, TileSourceFactory.MAPNIK)
        try {
            provider.ensureCapacity(wanted.size + 4)
            val got = HashMap<Long, Drawable>()
            // Tiles come from the phone's cache or are downloaded; wait a little for the rest.
            for (attempt in 0 until 40) {
                for (t in wanted) if (t !in got) provider.getMapTile(t)?.let { got[t] = it }
                if (got.size == wanted.size) break
                delay(250)
            }
            for ((t, d) in got) {
                val x = ((MapTileIndex.getX(t).toDouble() / n - left) * view.scale)
                val y = ((MapTileIndex.getY(t).toDouble() / n - top) * view.scale)
                d.setBounds(floor(x).toInt(), floor(y).toInt(), floor(x + tilePx).toInt() + 1, floor(y + tilePx).toInt() + 1)
                d.draw(canvas)
            }
        } finally {
            provider.detach()
        }
    }

    private fun compose(
        map: Bitmap, mapRect: Rect, view: View, walk: Session, points: List<TrailPoint>, character: Character?,
    ): Bitmap {
        val last = points.lastOrNull()
        val palette = MistPalette.at(walk.endTime ?: walk.startTime, last?.lat ?: 52.0, last?.lon ?: 5.0)

        // Mist over the map, with this walk's clearing cut out.
        val fog = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
        val fc = Canvas(fog)
        fc.drawColor(palette.base)
        fun swirl(seed: Int, tint: Int, alpha: Float, contrast: Float, filter: Int, scale: Float) {
            val tex = FogTexture.generate(256, seed, tint, alpha, contrast)
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
                    it.setLocalMatrix(android.graphics.Matrix().apply { setScale(scale, scale) })
                }
                colorFilter = LightingColorFilter(filter, 0)
            }
            fc.drawPaint(p)
        }
        swirl(7, Color.rgb(176, 184, 206), 0.55f, 1.6f, palette.shadowTint, 3f)
        swirl(23, Color.WHITE, 0.85f, 1.8f, palette.lightTint, 1.7f)
        val brush = brush()
        val cut = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        val r = RectF()
        for (p in points) {
            val rad = (Mercator.metersToUnits(p.radius, p.lat) * view.scale).toFloat().coerceAtLeast(3f)
            val x = view.x(p.lon)
            val y = view.y(p.lat)
            r.set(x - rad, y - rad, x + rad, y + rad)
            fc.drawBitmap(brush, null, r, cut)
        }
        Canvas(map).drawBitmap(fog, 0f, 0f, null)

        // The trail.
        val mc = Canvas(map)
        if (points.size >= 2) {
            val path = Path()
            path.moveTo(view.x(points[0].lon), view.y(points[0].lat))
            for (p in points.drop(1)) path.lineTo(view.x(p.lon), view.y(p.lat))
            mc.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = EMBER
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = 11f
                pathEffect = DashPathEffect(floatArrayOf(0.01f, 22f), 0f)
            })
        }
        // Your character where the walk ended.
        if (character != null && last != null) {
            val size = 18 * 6f
            val x = view.x(last.lon)
            val y = view.y(last.lat)
            mc.drawOval(x - size * 0.28f, y - size * 0.07f, x + size * 0.28f, y + size * 0.07f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 })
            mc.drawBitmap(character.idle[0], null, RectF(x - size / 2, y - size * 0.92f, x + size / 2, y + size * 0.08f), pixelPaint())
        }

        // The card.
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(PAPER)
        c.drawBitmap(map, null, mapRect, Paint(Paint.FILTER_BITMAP_FLAG))
        pixelFrame(c, RectF(mapRect))

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        val y0 = mapRect.bottom + 92f
        text.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        text.textSize = 64f
        text.letterSpacing = 0.06f
        c.drawText("Misty Steps", MARGIN.toFloat(), y0, text)

        text.typeface = Typeface.MONOSPACE
        text.letterSpacing = 0f
        text.textSize = 34f
        text.color = DUSK
        val date = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(walk.startTime))
        c.drawText(date, MARGIN.toFloat(), y0 + 56, text)

        val parts = buildList {
            add(formatDistance(walk.distanceM))
            walk.endTime?.let { add(formatDuration(it - walk.startTime)) }
            walk.newAreaM2?.let { add("${formatArea(it)} new mist") }
        }
        text.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        text.textSize = 44f
        text.color = EMBER
        c.drawText(parts.joinToString("  ·  "), MARGIN.toFloat(), y0 + 132, text)

        text.typeface = Typeface.DEFAULT
        text.textSize = 22f
        text.color = DUSK
        text.textAlign = Paint.Align.RIGHT
        c.drawText("© OpenStreetMap contributors", (W - MARGIN).toFloat(), H - 30f, text)
        return out
    }

    /** A chunky border with notched corners, like an old game's dialogue box. */
    private fun pixelFrame(c: Canvas, r: RectF) {
        val p = Paint().apply { color = INK; style = Paint.Style.STROKE; strokeWidth = BORDER }
        val b = BORDER
        c.drawLine(r.left + b, r.top, r.right - b, r.top, p)
        c.drawLine(r.left + b, r.bottom, r.right - b, r.bottom, p)
        c.drawLine(r.left, r.top + b, r.left, r.bottom - b, p)
        c.drawLine(r.right, r.top + b, r.right, r.bottom - b, p)
        p.style = Paint.Style.FILL
        // Paper-coloured notches so the corners look stepped.
        val paper = Paint().apply { color = PAPER }
        for ((x, y) in listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)) {
            c.drawRect(x - b / 2, y - b / 2, x + b / 2, y + b / 2, paper)
            c.drawRect(x + (if (x == r.left) b / 2 else -b * 1.5f), y + (if (y == r.top) b / 2 else -b * 1.5f),
                x + (if (x == r.left) b * 1.5f else -b / 2), y + (if (y == r.top) b * 1.5f else -b / 2), p)
        }
    }

    private fun pixelPaint() = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    private fun brush(): Bitmap {
        val size = 128
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            val c = size / 2f
            Canvas(it).drawCircle(c, c, c, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    c, c, c,
                    intArrayOf(Color.BLACK, Color.BLACK, Color.argb(160, 0, 0, 0), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.72f, 0.88f, 1f),
                    Shader.TileMode.CLAMP,
                )
            })
        }
    }
}
