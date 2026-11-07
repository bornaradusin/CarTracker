package io.github.bornaradusin.cartracker.ui.trips

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Turns a picture of the heatmap into an image with a caption and key, and hands it to the share sheet. */
object HeatmapShare {

    /** The same colours the map uses, from passed once to driven most often. */
    private val HEAT = intArrayOf(
        android.graphics.Color.rgb(29, 91, 216),
        android.graphics.Color.rgb(0, 188, 220),
        android.graphics.Color.rgb(76, 200, 80),
        android.graphics.Color.rgb(250, 205, 40),
        android.graphics.Color.rgb(226, 49, 43),
    )

    data class Colors(val background: Int, val ink: Int, val muted: Int, val accent: Int)

    fun title(car: String) = "Where $car has been"

    /** "Last 4 weeks · 12 Oct – 7 Nov 2026", matching the weeks the heatmap loads (from a Monday to today). */
    fun period(weeks: Int, today: LocalDate = LocalDate.now()): String {
        val start = today.with(DayOfWeek.MONDAY).minusWeeks(weeks - 1L)
        val name = when (weeks) {
            1 -> "This week"
            4 -> "Last 4 weeks"
            13 -> "Last 3 months"
            52 -> "Last year"
            else -> "Last $weeks weeks"
        }
        val full = DateTimeFormatter.ofPattern("d MMM yyyy")
        val from = start.format(if (start.year == today.year) DateTimeFormatter.ofPattern("d MMM") else full)
        return "$name · $from – ${today.format(full)}"
    }

    /**
     * Crops [map] to the part that isn't covered by the screen's own controls ([top] to [bottom], in pixels)
     * and adds a caption underneath. [scale] is the screen density, so text keeps its size on every phone.
     */
    fun compose(
        map: Bitmap,
        top: Int,
        bottom: Int,
        title: String,
        period: String,
        attribution: String,
        colors: Colors,
        scale: Float,
    ): Bitmap {
        val width = map.width
        var cropTop = top.coerceIn(0, map.height - 1)
        var cropBottom = bottom.coerceIn(cropTop + 1, map.height)
        // A sliver of map isn't worth sharing: fall back to all of it
        if (cropBottom - cropTop < map.height / 3) {
            cropTop = 0
            cropBottom = map.height
        }
        val mapHeight = cropBottom - cropTop

        fun dp(v: Float) = v * scale
        val pad = dp(20f)
        val inner = width - 2 * pad

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink
            textSize = dp(20f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.muted; textSize = dp(14f) }
        val keyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.muted; textSize = dp(12f) }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accent
            textSize = dp(12f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val creditPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.muted; textSize = dp(10f) }

        fun Paint.lineHeight() = fontMetrics.let { it.descent - it.ascent }
        val barHeight = dp(10f)
        val bannerHeight = pad +
            titlePaint.lineHeight() + dp(2f) +
            subPaint.lineHeight() + dp(14f) +
            barHeight + dp(6f) +
            keyPaint.lineHeight() + dp(14f) +
            maxOf(brandPaint.lineHeight(), creditPaint.lineHeight()) +
            pad

        val out = Bitmap.createBitmap(width, mapHeight + bannerHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(colors.background)
        canvas.drawBitmap(Bitmap.createBitmap(map, 0, cropTop, width, mapHeight), 0f, 0f, null)

        var y = mapHeight + pad
        fun line(text: CharSequence, paint: TextPaint, x: Float = pad, room: Float = inner) {
            val fitted = TextUtils.ellipsize(text, paint, room, TextUtils.TruncateAt.END)
            canvas.drawText(fitted, 0, fitted.length, x, y - paint.fontMetrics.ascent, paint)
        }

        line(title, titlePaint)
        y += titlePaint.lineHeight() + dp(2f)
        line(period, subPaint)
        y += subPaint.lineHeight() + dp(14f)

        // Colour key
        val bar = RectF(pad, y, pad + inner, y + barHeight)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bar.left, 0f, bar.right, 0f, HEAT, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(bar, barHeight / 2, barHeight / 2, barPaint)
        y += barHeight + dp(6f)
        val most = "Driven most often"
        line("Passed once", keyPaint, room = inner / 2)
        val mostWidth = keyPaint.measureText(most)
        line(most, keyPaint, x = pad + inner - minOf(mostWidth, inner / 2), room = inner / 2)
        y += keyPaint.lineHeight() + dp(14f)

        // App name on the left, map credit on the right
        val brand = "CarTracker"
        val brandWidth = brandPaint.measureText(brand)
        val footer = maxOf(brandPaint.lineHeight(), creditPaint.lineHeight())
        canvas.drawText(brand, pad, y + (footer - brandPaint.lineHeight()) / 2 - brandPaint.fontMetrics.ascent, brandPaint)
        val creditRoom = inner - brandWidth - dp(16f)
        if (creditRoom > 0) {
            val credit = TextUtils.ellipsize(attribution, creditPaint, creditRoom, TextUtils.TruncateAt.END)
            val creditWidth = creditPaint.measureText(credit, 0, credit.length)
            canvas.drawText(
                credit, 0, credit.length,
                pad + inner - creditWidth,
                y + (footer - creditPaint.lineHeight()) / 2 - creditPaint.fontMetrics.ascent,
                creditPaint,
            )
        }
        return out
    }

    /** Saves the picture where the share sheet can read it, replacing the last one. */
    fun save(context: Context, picture: Bitmap): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "heatmap.png")
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun share(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share heatmap"))
    }
}
