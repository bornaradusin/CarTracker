package io.github.bornaradusin.cartracker.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import io.github.bornaradusin.cartracker.R
import io.github.bornaradusin.cartracker.ui.theme.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The vehicles a car can show as on the map, all seen from above and facing up, in white.
 * [bitmap] repaints the body in any colour and leaves glass, lights and tyres as they are.
 */
object CarImages {

    val all = listOf("hatchback", "estate", "compact", "suv", "van", "pickup", "truck", "motorbike")

    val labels = mapOf(
        "hatchback" to "Hatchback",
        "estate" to "Estate",
        "compact" to "Small car",
        "suv" to "SUV",
        "van" to "Van",
        "pickup" to "Pickup",
        "truck" to "Truck",
        "motorbike" to "Motorbike",
    )

    /** Traccar's device categories, so the choice also shows in Traccar's own apps. */
    val categoryOf = mapOf(
        "hatchback" to "car", "estate" to "car", "compact" to "car", "suv" to "offroad",
        "van" to "van", "pickup" to "pickup", "truck" to "truck", "motorbike" to "motorcycle",
    )

    /** Icon names saved by older versions. */
    private val legacy = mapOf(
        "Car" to "hatchback", "SUV" to "suv", "Van" to "van", "Pickup" to "pickup", "Motorbike" to "motorbike", "Truck" to "truck",
    )

    fun forCategory(category: String?): String = when (category) {
        "offroad" -> "suv"
        "van" -> "van"
        "pickup" -> "pickup"
        "truck" -> "truck"
        "motorcycle" -> "motorbike"
        else -> "hatchback"
    }

    /** The saved icon, or the one that fits Traccar's category for the car. */
    fun resolve(saved: String, category: String?): String = when {
        saved in all -> saved
        legacy[saved] != null -> legacy.getValue(saved)
        else -> forCategory(category)
    }

    private fun res(id: String): Int = when (id) {
        "estate" -> R.drawable.car_estate
        "compact" -> R.drawable.car_compact
        "suv" -> R.drawable.car_suv
        "van" -> R.drawable.car_van
        "pickup" -> R.drawable.car_pickup
        "truck" -> R.drawable.car_truck
        "motorbike" -> R.drawable.car_motorbike
        else -> R.drawable.car_hatchback
    }

    private val originals = HashMap<String, Bitmap>()
    private val painted = LruCache<String, Bitmap>(40)

    @Synchronized
    fun bitmap(context: Context, id: String, color: Color): Bitmap {
        val key = "$id-${Palette.hex(color)}"
        painted.get(key)?.let { return it }
        val original = originals.getOrPut(id) {
            BitmapFactory.decodeResource(context.resources, res(id), BitmapFactory.Options().apply { inScaled = false })
        }
        return recolour(original, color).also { painted.put(key, it) }
    }

    /**
     * Repaints the light, colourless parts (the white body) in [color], keeping their shading and
     * shine. Dark parts (glass, tyres) and coloured ones (lights) stay as they are.
     */
    private fun recolour(source: Bitmap, color: Color): Bitmap {
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val tr = color.red
        val tg = color.green
        val tb = color.blue
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val sat = if (hi > 0f) (hi - lo) / hi else 0f
            val weight = smooth(0.42f, 0.58f, lum) * (1f - smooth(0.10f, 0.22f, sat))
            if (weight <= 0f) continue
            val shade = lum / 0.84f
            val k = min(shade, 1f)
            val shine = ((shade - 1f) / 0.19f).coerceIn(0f, 1f) * 0.55f
            fun paint(target: Float, original: Float): Int {
                val base = target * k
                val v = base + (1f - base) * shine
                return ((original * (1f - weight) + v * weight) * 255f).roundToInt().coerceIn(0, 255)
            }
            px[i] = (a shl 24) or (paint(tr, r) shl 16) or (paint(tg, g) shl 8) or paint(tb, b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
