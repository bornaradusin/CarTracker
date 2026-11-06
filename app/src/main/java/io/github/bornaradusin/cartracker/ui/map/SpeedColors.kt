package io.github.bornaradusin.cartracker.ui.map

import androidx.compose.ui.graphics.Color
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/** Route colours by speed: green when slow, through yellow and orange, to red and purple when fast. */
object SpeedColors {

    /** km/h and colour. */
    val stops: List<Pair<Double, Long>> = listOf(
        0.0 to 0xFF2FA84F,
        40.0 to 0xFFC8C21A,
        70.0 to 0xFFF28C1B,
        100.0 to 0xFFE0312B,
        130.0 to 0xFF8E1B5A,
    )

    fun color(kmh: Double): Color {
        val v = kmh.coerceIn(stops.first().first, stops.last().first)
        val upper = stops.indexOfFirst { it.first >= v }.coerceAtLeast(1)
        val (s0, c0) = stops[upper - 1]
        val (s1, c1) = stops[upper]
        val f = ((v - s0) / (s1 - s0)).toFloat().coerceIn(0f, 1f)
        return androidx.compose.ui.graphics.lerp(Color(c0), Color(c1), f)
    }

    private fun hex(color: Color): String {
        val r = (color.red * 255).roundToInt()
        val g = (color.green * 255).roundToInt()
        val b = (color.blue * 255).roundToInt()
        return String.format("#%02X%02X%02X", r, g, b)
    }

    /**
     * The route as coloured pieces for the map: each gap between two points gets the colour of its
     * average speed, rounded to 5 km/h, and neighbouring gaps of the same colour are joined.
     */
    fun segments(route: List<LatLng>, speeds: List<Double>): List<Feature> {
        val features = ArrayList<Feature>()
        var points = ArrayList<Point>()
        var bucket = -1
        for (i in 1 until route.size) {
            val kmh = (speeds[i - 1] + speeds[i]) / 2
            val b = (kmh / 5).roundToInt()
            if (b != bucket && points.size >= 2) {
                features += feature(points, bucket)
                points = arrayListOf(points.last())
            }
            if (points.isEmpty()) points += Point.fromLngLat(route[i - 1].longitude, route[i - 1].latitude)
            points += Point.fromLngLat(route[i].longitude, route[i].latitude)
            bucket = b
        }
        if (points.size >= 2) features += feature(points, bucket)
        return features
    }

    private fun feature(points: List<Point>, bucket: Int): Feature =
        Feature.fromGeometry(LineString.fromLngLats(points)).apply { addStringProperty("color", hex(color(bucket * 5.0))) }
}
