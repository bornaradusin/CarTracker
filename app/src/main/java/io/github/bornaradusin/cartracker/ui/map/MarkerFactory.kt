package io.github.bornaradusin.cartracker.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.ColorUtils
import io.github.bornaradusin.cartracker.ui.theme.Palette

/**
 * Draws a car's map pin: a dark circle with a glowing ring and a small point at the bottom, in the
 * glow colour, and the vehicle inside in the car colour. The point's tip is the car's position.
 */
object MarkerFactory {

    fun key(icon: String, carColor: Color, glowColor: Color) = "pin-$icon-${Palette.hex(carColor)}-${Palette.hex(glowColor)}"

    /** [sizeDp] is the circle's diameter; the glow and the point add to it. */
    fun bitmap(context: Context, icon: String, carColor: Color, glowColor: Color, sizeDp: Float = 46f): Bitmap {
        val dp = context.resources.displayMetrics.density
        val d = sizeDp * dp
        val r = d / 2f
        val glow = d * 0.16f
        val pointerH = d * 0.17f
        val pointerW = d * 0.24f
        val width = (d + glow * 2).toInt()
        val height = (glow + d + pointerH + 2 * dp).toInt()
        val cx = width / 2f
        val cy = glow + r
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val gc = glowColor.toArgb()
        val bright = ColorUtils.blendARGB(gc, android.graphics.Color.WHITE, 0.45f)

        val pointer = Path().apply {
            moveTo(cx - pointerW / 2f, cy + r - d * 0.05f)
            lineTo(cx + pointerW / 2f, cy + r - d * 0.05f)
            lineTo(cx, cy + r + pointerH)
            close()
        }

        // Neon glow around the ring and the point
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = d * 0.09f
            color = ColorUtils.setAlphaComponent(gc, 230)
            maskFilter = BlurMaskFilter(d * 0.08f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawPath(pointer, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(gc, 190)
            maskFilter = BlurMaskFilter(d * 0.05f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawPath(pointer, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx, cy + r - d * 0.05f, cx, cy + r + pointerH, bright, gc, Shader.TileMode.CLAMP)
        })

        // Dark disc with the glow fading in from its edge
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0D1220.toInt() })
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(0, 0, ColorUtils.setAlphaComponent(gc, 140)),
                floatArrayOf(0f, 0.66f, 1f),
                Shader.TileMode.CLAMP,
            )
        })

        // The ring, with a lighter core so it reads as lit
        val ringW = d * 0.04f
        canvas.drawCircle(cx, cy, r - ringW / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringW
            color = gc
        })
        canvas.drawCircle(cx, cy, r - ringW / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringW * 0.4f
            color = ColorUtils.setAlphaComponent(bright, 220)
        })

        // The vehicle
        val car = CarImages.bitmap(context, icon, carColor)
        val ch = d * 0.74f
        val cw = car.width * ch / car.height
        canvas.drawBitmap(
            car,
            null,
            RectF(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return bitmap
    }

    /** A small ring for the start and end of a trip. */
    fun dot(context: Context, ringColor: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (18 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = size / 2f
        canvas.drawCircle(r, r, r - 1, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        canvas.drawCircle(r, r, r - 3 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            color = ringColor
        })
        return bitmap
    }
}

/** The car's map pin, for use outside the map. [size] is the circle's diameter. */
@Composable
fun CarPin(icon: String, carColor: Color, glowColor: Color, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizeDp = with(LocalDensity.current) { size.toPx() / density }
    val bitmap = remember(icon, carColor, glowColor, sizeDp) {
        MarkerFactory.bitmap(context, icon, carColor, glowColor, sizeDp).asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = modifier)
}
