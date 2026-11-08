package io.github.bornaradusin.cartracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The line icons from the design, drawn from the same paths (24 × 24, 2 px stroke). */
object Ic {

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun solid(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        }.build()

    val car = line("car", "M5 16V12l2-5h10l2 5v4zM5 16v2M19 16v2")
    val chevronDown = line("chevronDown", "M6 9l6 6 6-6")
    val chevronRight = line("chevronRight", "M9 6l6 6-6 6")
    val chevronLeft = line("chevronLeft", "M15 6l-6 6 6 6")
    val chart = line("chart", "M4 20V10M10 20V4M16 20v-7M22 20H2")
    val flame = line("flame", "M12 22c4 0 7-3 7-7 0-5-4-7-5-12-2 2-3 4-3 6-1-1-2-2-2-4-2 2-4 5-4 10 0 4 3 7 7 7z")
    val back = line("back", "M19 12H5M12 19l-7-7 7-7")
    val bell = line("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val crosshair = line("crosshair", circle(12f, 12f, 3f), "M12 2v3M12 19v3M2 12h3M19 12h3")
    val layers = line("layers", "M12 2l10 5-10 5L2 7z", "M2 17l10 5 10-5M2 12l10 5 10-5")
    val pin = line("pin", "M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12z", circle(12f, 9f, 2.5f))
    val lock = line("lock", "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4")
    val unlock = line("unlock", "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 7.8-1.3")
    val shieldCheck = line("shieldCheck", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", "M9 12l2 2 4-4")
    val navigation = line("navigation", "M3 11l19-9-9 19-2-8z")
    val image = line("image", "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", circle(9f, 9.5f, 1.5f), "M21 15l-5-5L5 20")
    val share = line("share", circle(18f, 5f, 3f), circle(6f, 12f, 3f), circle(18f, 19f, 3f), "M8.6 13.5l6.8 4M15.4 6.5l-6.8 4")
    val route = line("route", circle(6f, 19f, 2f), circle(18f, 5f, 2f), "M8 19h8.5a3.5 3.5 0 0 0 0-7h-9a3.5 3.5 0 0 1 0-7H16")
    val menu = line("menu", "M4 6h16M4 12h16M4 18h16")
    val check = line("check", "M20 6L9 17l-5-5")
    val plus = line("plus", "M12 5v14M5 12h14")
    val minus = line("minus", "M5 12h14")
    val close = line("close", "M6 6l12 12M18 6L6 18")
    val sliders = line("sliders", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")
    val key = line("key", "M15 7a4 4 0 1 1-4 4M11 11L3 19M5 17l2 2M7 15l2 2")
    val zap = line("zap", "M13 2L3 14h9l-1 8 10-12h-9z")
    val power = line("power", "M12 2v10", "M18.4 6.6a9 9 0 1 1-12.8 0")
    val home = line("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val clock = line("clock", circle(12f, 12f, 10f), "M12 6v6l4 2")
    val refresh = line("refresh", "M21 12a9 9 0 1 1-3-6.7L21 8", "M21 3v5h-5")
    val trash = line("trash", "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14")
    val server = line("server", "M4 3h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M4 13h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2z", "M6 7h.01M6 17h.01")
    val terminal = line("terminal", "M4 17l6-5-6-5M12 19h8")
    val eyeOff = line("eyeOff", "M3 3l18 18", "M10.6 10.6a2 2 0 0 0 2.8 2.8", "M9.9 5.1A10 10 0 0 1 12 5c6 0 10 7 10 7a17 17 0 0 1-3.2 3.9M6.6 6.6A17 17 0 0 0 2 12s4 7 10 7a9.7 9.7 0 0 0 5.4-1.6")
    val battery = line("battery", "M3 7h15a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M23 11v2")
    val warning = line("warning", "M12 3l10 18H2z", "M12 10v4M12 17h.01")
    val play = solid("play", "M7 4l13 8-13 8z")
    val pause = line("pause", "M8 5v14M16 5v14")

    val briefcase = line("briefcase", "M3 7h18v13H3z", "M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M3 12h18")
    val school = line("school", "M2 9l10-5 10 5-10 5z", "M6 11v5c0 1.7 2.7 3 6 3s6-1.3 6-3v-5", "M22 9v6")
    val cart = line("cart", "M3 4h2l2.5 11h11L21 7H6.2", circle(9f, 20f, 1.5f), circle(18f, 20f, 1.5f))
    val parking = line("parking", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M10 17V7h3a3 3 0 0 1 0 6h-3")
    val heart = line("heart", "M12 20s-7-4.5-9-9a4.5 4.5 0 0 1 9-3a4.5 4.5 0 0 1 9 3c-2 4.5-9 9-9 9z")
    val star = line("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z")

    /** Icons for saved places, by the key saved in the place's "icon" attribute. */
    val places: Map<String, ImageVector> by lazy {
        linkedMapOf(
            "home" to home, "work" to briefcase, "pin" to pin, "school" to school,
            "shop" to cart, "parking" to parking, "heart" to heart, "star" to star,
        )
    }

    fun place(key: String): ImageVector = places[key] ?: pin
}
