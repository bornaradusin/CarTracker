package io.github.bornaradusin.cartracker.ui.trips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.ui.map.MapStyle
import io.github.bornaradusin.cartracker.ui.map.MapStyles
import io.github.bornaradusin.cartracker.ui.map.MarkerFactory
import io.github.bornaradusin.cartracker.ui.map.SpeedColors
import io.github.bornaradusin.cartracker.ui.share.GifWriter
import io.github.bornaradusin.cartracker.ui.share.SharedFiles
import io.github.bornaradusin.cartracker.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A trip to share: as a picture of the whole route, or as a looping animation of the car driving it,
 * with the time, speed and distance counting along underneath. The map is drawn off screen, so what's
 * on the phone's screen doesn't matter.
 */
object TripShare {

    /** Width of the picture; the map is square, the trip's numbers go underneath. */
    private const val WIDTH = 720
    private const val MAP_DP = 360
    private const val PIXEL_RATIO = 2f // WIDTH / MAP_DP
    private const val FPS = 20
    private const val DRAW_SECONDS = 8
    private const val START_HOLD_CS = 70
    private const val END_HOLD_CS = 300

    data class Trip(
        val car: String,
        val icon: String,
        val carColor: Color,
        val glowColor: Color,
        val points: List<Position>,
        val from: Instant,
        val to: Instant,
    )

    data class Look(
        val style: MapStyle,
        val dark: Boolean,
        val background: Int,
        val ink: Int,
        val muted: Int,
        val accent: Int,
        val line: Int,
    )

    private val dayFormat get() = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

    fun title(trip: Trip) = "${trip.car} · trip on ${trip.from.atZone(ZoneId.systemDefault()).format(dayFormat)}"

    /** The whole route with the trip's numbers, as a PNG ready to share. */
    suspend fun picture(context: Context, trip: Trip, look: Look): File {
        val scene = Scene.load(context, trip, look)
        val frame = withContext(Dispatchers.Default) { scene.render(1f, final = true) }
        return withContext(Dispatchers.IO) {
            SharedFiles.newFile(context, "trip.png").also { file ->
                file.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** The car driving the route, as a looping GIF ready to share. [progress] goes from 0 to 1. */
    suspend fun animation(context: Context, trip: Trip, look: Look, progress: (Float) -> Unit): File {
        val scene = Scene.load(context, trip, look)
        return withContext(Dispatchers.Default) {
            val frames = FPS * DRAW_SECONDS
            val pixels = IntArray(scene.width * scene.height)

            // The palette comes from the finished picture: map, whole route, pin and numbers
            val finished = scene.render(1f, final = true)
            finished.getPixels(pixels, 0, scene.width, 0, 0, scene.width, scene.height)
            val sample = pixels.copyOf()
            scene.restart()

            val file = SharedFiles.newFile(context, "trip.gif")
            file.outputStream().buffered(1 shl 16).use { out ->
                val gif = GifWriter(out, scene.width, scene.height, scene.exactColors(), sample)
                for (k in 0..frames) {
                    ensureActive()
                    val final = k == frames
                    val frame = scene.render(k / frames.toFloat(), final)
                    frame.getPixels(pixels, 0, scene.width, 0, 0, scene.width, scene.height)
                    gif.frame(pixels, if (k == 0) START_HOLD_CS else 100 / FPS)
                    progress((k + 1) / (frames + 1f))
                }
                gif.finish(END_HOLD_CS)
            }
            file
        }
    }

    /** Everything needed to draw the trip at any moment. */
    private class Scene(
        context: Context,
        private val trip: Trip,
        private val look: Look,
        private val map: Bitmap,
        private val spots: Array<PointF>,
    ) {
        private val s = PIXEL_RATIO
        private fun dp(v: Float) = v * s

        private val points = trip.points
        private val kmh = DoubleArray(points.size) { points[it].speedKmh }
        private val travelled = DoubleArray(points.size).also { d ->
            for (i in 1 until points.size) d[i] = d[i - 1] + metres(points[i - 1], points[i])
        }
        private val totalMetres = travelled.last()
        private val topKmh = kmh.maxOrNull() ?: 0.0

        /** When each point was recorded, or null if the times are missing. */
        private val recorded: LongArray? = run {
            val parsed = points.map { Format.parse(it.fixTime)?.toEpochMilli() }
            if (parsed.any { it == null }) return@run null
            val t = LongArray(points.size)
            for (i in points.indices) t[i] = if (i == 0) parsed[0]!! else max(parsed[i]!!, t[i - 1])
            t.takeIf { it.last() - it.first() >= 1_000 }
        }
        private val timed = recorded != null

        /** The car moves through the points in step with their times, or evenly if there are none. */
        private val times: LongArray = recorded ?: LongArray(points.size) { it.toLong() }

        // Text sizes and the panel underneath
        private val pad = dp(18f)
        private val titlePaint = text(17f, look.ink, bold = true)
        private val subPaint = text(13f, look.muted)
        private val brandPaint = text(12f, look.accent, bold = true)
        private val labelPaint = text(12f, look.muted)
        private val valuePaint = text(22f, look.ink, bold = true)
        private val keyPaint = text(11f, look.muted)
        private val barHeight = dp(5f)

        private fun TextPaint.lineHeight() = fontMetrics.let { it.descent - it.ascent }

        val width = WIDTH
        private val mapHeight = map.height
        val height: Int = run {
            val panel = pad + titlePaint.lineHeight() + dp(2f) + subPaint.lineHeight() + dp(16f) +
                labelPaint.lineHeight() + valuePaint.lineHeight() + dp(14f) +
                barHeight + dp(16f) + barHeight + dp(5f) + keyPaint.lineHeight() + pad
            (mapHeight + panel.roundToInt() + 1) and 1.inv()
        }

        // The map with the whole route faintly drawn on it, so you can see where the car is heading
        private val base: Bitmap = map.copy(Bitmap.Config.ARGB_8888, true).also { b ->
            val canvas = Canvas(b)
            val ahead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(3.5f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = ColorUtils.setAlphaComponent(if (look.dark) 0xFFFFFFFF.toInt() else 0xFF1D2433.toInt(), 95)
            }
            val path = android.graphics.Path()
            spots.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            canvas.drawPath(path, ahead)
        }

        // What's been driven so far, drawn a piece at a time: an outline under the coloured line
        private val casingLayer = Bitmap.createBitmap(width, mapHeight, Bitmap.Config.ARGB_8888)
        private val colourLayer = Bitmap.createBitmap(width, mapHeight, Bitmap.Config.ARGB_8888)
        private val casingCanvas = Canvas(casingLayer)
        private val colourCanvas = Canvas(colourLayer)
        private val casingColor = if (look.dark) 0xFF0D1220.toInt() else 0xFFFFFFFF.toInt()
        private val casingPaint = stroke(dp(8f), casingColor)
        private val colourPaint = stroke(dp(5f), 0)
        private var drawnTo = 0
        private var drawnPoint = PointF(spots[0].x, spots[0].y)

        private val pin: Bitmap = run {
            val density = context.resources.displayMetrics.density
            MarkerFactory.bitmap(context, trip.icon, trip.carColor, trip.glowColor, sizeDp = 40f * s / density)
        }
        private val startDot = MarkerFactory.dot(context, START_RING)
        private val endDot = MarkerFactory.dot(context, END_RING)

        private val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(out)
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun restart() {
            casingLayer.eraseColor(0)
            colourLayer.eraseColor(0)
            drawnTo = 0
            drawnPoint = PointF(spots[0].x, spots[0].y)
        }

        /** The trip at [progress] (0 to 1) of its time. [final] shows the trip's totals. */
        fun render(progress: Float, final: Boolean): Bitmap {
            val (i, f) = if (final) points.lastIndex to 0f else locate(progress)
            advance(i, f)

            canvas.drawColor(look.background, PorterDuff.Mode.SRC)
            canvas.drawBitmap(base, 0f, 0f, null)
            canvas.drawBitmap(casingLayer, 0f, 0f, null)
            canvas.drawBitmap(colourLayer, 0f, 0f, null)
            dot(startDot, spots[0])
            if (final) dot(endDot, spots.last())
            val at = spotAt(i, f)
            canvas.drawBitmap(
                pin,
                null,
                RectF(at.x - pin.width / 2f, at.y - pin.height, at.x + pin.width / 2f, at.y),
                bitmapPaint,
            )

            if (final) {
                panel(
                    listOf(
                        "Duration" to Format.duration(trip.to.toEpochMilli() - trip.from.toEpochMilli()),
                        "Top speed" to "${topKmh.roundToInt()} km/h",
                        "Distance" to Format.distance(totalMetres),
                    ),
                    1f,
                )
            } else {
                val next = (i + 1).coerceAtMost(points.lastIndex)
                val speed = kmh[i] + (kmh[next] - kmh[i]) * f
                val metres = travelled[i] + (travelled[next] - travelled[i]) * f
                val clock = if (timed) {
                    Instant.ofEpochMilli(times[i] + ((times[next] - times[i]) * f).toLong())
                } else {
                    Instant.ofEpochMilli(trip.from.toEpochMilli() + ((trip.to.toEpochMilli() - trip.from.toEpochMilli()) * progress).toLong())
                }
                panel(
                    listOf(
                        "Time" to Format.time(clock),
                        "Speed" to "${speed.roundToInt()} km/h",
                        "Distance" to Format.distance(metres),
                    ),
                    progress,
                )
            }
            return out
        }

        /** The colours the animation needs exactly: the route's, the text's and the panel's. */
        fun exactColors(): IntArray {
            val colours = ArrayList<Int>()
            colours += look.background
            colours += look.ink
            colours += look.muted
            colours += look.accent
            colours += look.line
            colours += casingColor
            colours += START_RING
            colours += END_RING
            colours += 0xFFFFFFFF.toInt()
            for (k in 0..130 step 5) colours += SpeedColors.color(k.toDouble()).toArgb()
            // Smooth text edges on the panel
            for (step in 1..5) {
                val t = step / 6f
                colours += ColorUtils.blendARGB(look.background, look.ink, t)
                colours += ColorUtils.blendARGB(look.background, look.muted, t)
                colours += ColorUtils.blendARGB(look.background, look.accent, t)
            }
            return colours.toIntArray()
        }

        /** Which point the car is at, and how far on to the next, at [progress] of the trip's time. */
        private fun locate(progress: Float): Pair<Int, Float> {
            val target = times.first() + ((times.last() - times.first()) * progress.toDouble()).toLong()
            var i = drawnTo.coerceIn(0, points.lastIndex)
            if (times[i] > target) i = 0
            while (i < points.lastIndex && times[i + 1] <= target) i++
            if (i >= points.lastIndex) return points.lastIndex to 0f
            val span = times[i + 1] - times[i]
            val f = if (span <= 0) 0f else ((target - times[i]).toFloat() / span).coerceIn(0f, 1f)
            return i to f
        }

        private fun spotAt(i: Int, f: Float): PointF {
            val a = spots[i]
            val b = spots[(i + 1).coerceAtMost(spots.lastIndex)]
            return PointF(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        }

        /** Draws the route on from where it was last drawn to point [i] and [f] of the way to the next. */
        private fun advance(i: Int, f: Float) {
            if (i < drawnTo) restart()
            for (j in drawnTo + 1..i) piece(spots[j], j - 1)
            if (f > 0f && i < points.lastIndex) piece(spotAt(i, f), i)
            drawnTo = i
        }

        private fun piece(to: PointF, segment: Int) {
            val from = drawnPoint
            if (hypot(to.x - from.x, to.y - from.y) < 0.5f) return
            val next = (segment + 1).coerceAtMost(points.lastIndex)
            colourPaint.color = SpeedColors.color((kmh[segment] + kmh[next]) / 2).toArgb()
            casingCanvas.drawLine(from.x, from.y, to.x, to.y, casingPaint)
            colourCanvas.drawLine(from.x, from.y, to.x, to.y, colourPaint)
            drawnPoint = PointF(to.x, to.y)
        }

        private fun dot(bitmap: Bitmap, at: PointF) {
            val r = dp(9f)
            canvas.drawBitmap(bitmap, null, RectF(at.x - r, at.y - r, at.x + r, at.y + r), bitmapPaint)
        }

        private fun panel(stats: List<Pair<String, String>>, progress: Float) {
            val inner = width - 2 * pad
            var y = mapHeight + pad

            // Car and brand on one line, the day and times under it
            val brand = "CarTracker"
            val brandWidth = brandPaint.measureText(brand)
            val titleRoom = inner - brandWidth - dp(12f)
            write(trip.car, titlePaint, pad, y, titleRoom)
            write(brand, brandPaint, pad + inner - brandWidth, y + (titlePaint.lineHeight() - brandPaint.lineHeight()) / 2, brandWidth + 1)
            y += titlePaint.lineHeight() + dp(2f)
            val day = trip.from.atZone(ZoneId.systemDefault()).format(dayFormat)
            write("$day · ${Format.time(trip.from)} – ${Format.time(trip.to)}", subPaint, pad, y, inner)
            y += subPaint.lineHeight() + dp(16f)

            val column = inner / stats.size
            stats.forEachIndexed { n, (label, value) ->
                val x = pad + column * n
                write(label, labelPaint, x, y, column - dp(8f))
                write(value, valuePaint, x, y + labelPaint.lineHeight(), column - dp(8f))
            }
            y += labelPaint.lineHeight() + valuePaint.lineHeight() + dp(14f)

            // How far through the trip
            val track = RectF(pad, y, pad + inner, y + barHeight)
            canvas.drawRoundRect(track, barHeight / 2, barHeight / 2, fill(look.line))
            if (progress > 0f) {
                val done = RectF(pad, y, pad + max(barHeight, inner * progress), y + barHeight)
                canvas.drawRoundRect(done, barHeight / 2, barHeight / 2, fill(look.accent))
            }
            y += barHeight + dp(16f)

            // What the route's colours mean
            val key = RectF(pad, y, pad + inner, y + barHeight)
            val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    key.left, 0f, key.right, 0f,
                    SpeedColors.stops.map { SpeedColors.color(it.first).toArgb() }.toIntArray(),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(key, barHeight / 2, barHeight / 2, gradient)
            y += barHeight + dp(5f)
            val stops = SpeedColors.stops
            stops.forEachIndexed { n, (speed, _) ->
                val label = if (n == stops.lastIndex) "${speed.toInt()}+ km/h" else "${speed.toInt()}"
                val w = keyPaint.measureText(label)
                val x = when (n) {
                    0 -> pad
                    stops.lastIndex -> pad + inner - w
                    else -> pad + inner * n / stops.lastIndex - w / 2
                }
                write(label, keyPaint, x, y, w + 1)
            }
        }

        private fun write(text: String, paint: TextPaint, x: Float, top: Float, room: Float) {
            val fitted = TextUtils.ellipsize(text, paint, max(room, 1f), TextUtils.TruncateAt.END)
            canvas.drawText(fitted, 0, fitted.length, x, top - paint.fontMetrics.ascent, paint)
        }

        private fun text(sizeSp: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = dp(sizeSp)
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

        private fun stroke(width: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        companion object {
            private const val START_RING = 0xFF2F6B3A.toInt()
            private const val END_RING = 0xFF0E1726.toInt()

            /** Draws the map for the trip off screen, then sets up the scene. */
            suspend fun load(context: Context, trip: Trip, look: Look): Scene {
                require(trip.points.size >= 2) { "A trip needs at least two points" }
                val latLngs = trip.points.map { LatLng(it.latitude, it.longitude) }
                val (map, spots) = snapshot(context, look, area(latLngs), latLngs)
                return withContext(Dispatchers.Default) { Scene(context, trip, look, map, spots) }
            }

            /** The route's area with room around it, so the pin and the route's ends aren't at the edge. */
            private fun area(points: List<LatLng>): LatLngBounds {
                var south = points.minOf { it.latitude }
                var north = points.maxOf { it.latitude }
                var west = points.minOf { it.longitude }
                var east = points.maxOf { it.longitude }
                val midLat = (south + north) / 2
                // Short trips: at least about 600 m across, so the map isn't zoomed in past street level
                val minLat = 0.0055
                val minLon = minLat / cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
                if (north - south < minLat) {
                    val mid = (north + south) / 2
                    south = mid - minLat / 2
                    north = mid + minLat / 2
                }
                if (east - west < minLon) {
                    val mid = (east + west) / 2
                    west = mid - minLon / 2
                    east = mid + minLon / 2
                }
                val latPad = (north - south) * 0.18
                val lonPad = (east - west) * 0.18
                // A little more room at the top for the pin, which stands above its spot
                return LatLngBounds.from(
                    (north + latPad * 1.4).coerceAtMost(85.0),
                    east + lonPad,
                    (south - latPad).coerceAtLeast(-85.0),
                    west - lonPad,
                )
            }

            private suspend fun snapshot(
                context: Context,
                look: Look,
                area: LatLngBounds,
                route: List<LatLng>,
            ): Pair<Bitmap, Array<PointF>> = withContext(Dispatchers.Main) {
                val (bitmap, spots) = withTimeoutOrNull(45_000) {
                    suspendCancellableCoroutine<Pair<Bitmap, Array<PointF>>> { cont ->
                        val options = MapSnapshotter.Options(MAP_DP, MAP_DP)
                            .withPixelRatio(PIXEL_RATIO)
                            .withRegion(area)
                            .withLogo(false)
                            .withStyleBuilder(MapStyles.builder(look.style, look.dark))
                        val snapshotter = MapSnapshotter(context.applicationContext, options)
                        cont.invokeOnCancellation {
                            Handler(Looper.getMainLooper()).post { runCatching { snapshotter.cancel() } }
                        }
                        snapshotter.start(
                            { shot ->
                                if (cont.isActive) {
                                    val spots = route.map { shot.pixelForLatLng(it) }.toTypedArray()
                                    cont.resume(shot.bitmap.copy(Bitmap.Config.ARGB_8888, true) to spots)
                                }
                            },
                            { error -> if (cont.isActive) cont.resumeWithException(IOException("Map couldn't be drawn: $error")) },
                        )
                    }
                } ?: throw IOException("The map took too long to load")
                // Black & white is Positron recoloured on screen; here the picture is turned grey instead
                if (look.style == MapStyle.BlackWhite) monochrome(bitmap, look.dark)
                bitmap to spots
            }

            private fun monochrome(bitmap: Bitmap, dark: Boolean) {
                val grey = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 1.35f
                val shift = 128f * (1 - contrast)
                grey.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, shift,
                            0f, contrast, 0f, 0f, shift,
                            0f, 0f, contrast, 0f, shift,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
                if (dark) {
                    grey.postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f,
                            ),
                        ),
                    )
                }
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                Canvas(bitmap).drawBitmap(copy, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(grey) })
            }

            private fun metres(a: Position, b: Position): Double {
                val r = 6_371_000.0
                val dLat = Math.toRadians(b.latitude - a.latitude)
                val dLon = Math.toRadians(b.longitude - a.longitude)
                val h = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
                return 2 * r * asin(sqrt(h))
            }
        }
    }
}
