package io.github.bornaradusin.cartracker.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.MarkerColors

/**
 * Draws a car's map icon: its shape (circle, square, pin or arrow) in its colour, with a white border
 * and the vehicle symbol. Arrows point north here; the map turns them to the car's heading.
 */
object MarkerFactory {

    fun key(icon: String, color: String, shape: String) = "marker-$icon-$color-$shape"

    fun bitmap(context: Context, icon: String, color: String, shape: String, sizeDp: Float = 42f): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = sizeDp * density
        val canvasSize = (size * 1.7f).toInt()
        val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = canvasSize / 2f
        val cy = canvasSize / 2f
        val half = size / 2f

        val fill = MarkerColors.named(color).toArgb()
        val ink = MarkerColors.ink(color).toArgb()
        val border = if (color == "White") 0xFF3B4656.toInt() else 0xFFFFFFFF.toInt()
        val borderWidth = 3f * density

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = fill
            setShadowLayer(4f * density, 0f, 2f * density, 0x45000000)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            this.color = border
        }

        val body = Path()
        when (shape) {
            "Square" -> body.addRoundRect(RectF(cx - half, cy - half, cx + half, cy + half), size * 0.28f, size * 0.28f, Path.Direction.CW)
            "Pin" -> {
                val r = half
                val small = size * 0.1f
                body.addRoundRect(
                    RectF(cx - half, cy - half, cx + half, cy + half),
                    floatArrayOf(r, r, r, r, r, r, small, small),
                    Path.Direction.CW,
                )
                body.transform(Matrix().apply { setRotate(-45f, cx, cy) })
            }
            else -> body.addCircle(cx, cy, half, Path.Direction.CW)
        }

        if (shape == "Arrow") {
            val pointer = Path().apply {
                moveTo(cx, cy - half - 13f * density)
                lineTo(cx - 9f * density, cy - half + 2f * density)
                lineTo(cx + 9f * density, cy - half + 2f * density)
                close()
            }
            canvas.drawPath(pointer, fillPaint.apply { this.color = if (color == "White") border else fill })
            fillPaint.color = fill
        }
        canvas.drawPath(body, fillPaint)
        canvas.drawPath(body, borderPaint)

        val symbol = runCatching { PathParser.createPathFromPathData(Ic.vehiclePaths[icon] ?: Ic.vehiclePaths.getValue("Car")) }.getOrNull()
        if (symbol != null) {
            val iconSize = size * 0.52f
            val scale = iconSize / 24f
            symbol.transform(Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - iconSize / 2f, cy - iconSize / 2f)
            })
            val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                this.color = ink
            }
            canvas.drawPath(symbol, symbolPaint)
        }
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
