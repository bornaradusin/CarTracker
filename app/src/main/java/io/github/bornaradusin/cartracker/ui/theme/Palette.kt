package io.github.bornaradusin.cartracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Every colour setting in the app (app colour, car colour, circle colour) is any RGB colour,
 * saved as "#RRGGBB". Settings saved before that hold a colour name, which still works.
 */
object Palette {

    val AppDefault = Color(0xFF1D5BD8)
    val CarDefault = Color(0xFFFFFFFF)
    val CircleDefault = Color(0xFF1D5BD8)

    /** Dark ink for text on light colours. */
    val Ink = Color(0xFF0E1726)

    /** The names older versions saved, from their colour lists. */
    private val legacy = mapOf(
        "Blue" to Color(0xFF1D5BD8),
        "Teal" to Color(0xFF0F7B6C),
        "Green" to Color(0xFF2F7D3A),
        "Purple" to Color(0xFF6B4FD8),
        "Pink" to Color(0xFFB83280),
        "Red" to Color(0xFFC62828),
        "Orange" to Color(0xFFE8710A),
        "Yellow" to Color(0xFFF2B705),
        "White" to Color(0xFFFFFFFF),
        "Graphite" to Color(0xFF3B4656),
        "Black" to Color(0xFF0E1726),
    )

    fun parse(value: String?, fallback: Color): Color {
        if (value.isNullOrBlank()) return fallback
        legacy[value]?.let { return it }
        val hex = value.removePrefix("#")
        if (hex.length != 6) return fallback
        return hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback
    }

    fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

    /** Text or symbols drawn on [color]: dark on light colours, white on the rest. */
    fun onColor(color: Color): Color = if (color.luminance() > 0.45f) Ink else Color.White

    /** True for colours too light for text or outlines on a white surface. */
    fun isLight(color: Color): Boolean = color.luminance() > 0.4f

    /** True for colours close to white. */
    fun isNearWhite(color: Color): Boolean = color.luminance() > 0.8f

    /** The app colour in both themes, worked out from one RGB colour. */
    fun accent(base: Color): Accent {
        val outlineLight = if (isLight(base)) lerp(base, Ink, 0.6f) else base
        val dark = if (base.luminance() < 0.08f) lerp(base, Color.White, 0.32f) else base
        val textDark = if (dark.luminance() < 0.3f) lerp(dark, Color.White, 0.45f) else dark
        return Accent(
            name = hex(base),
            light = base,
            onLight = onColor(base),
            tintLight = lerp(base, Color.White, 0.88f),
            outlineLight = outlineLight,
            dark = dark,
            onDark = onColor(dark),
            tintDark = lerp(base, Color(0xFF0B1220), 0.78f),
            textDark = textDark,
        )
    }
}
