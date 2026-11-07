package io.github.bornaradusin.cartracker.ui.map

import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.LocationComponentActivationOptions
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Context
import android.annotation.SuppressLint
import android.Manifest
import android.graphics.PointF
import android.view.Gravity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

data class MapMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val course: Double,
    val icon: String,
    val carColor: Color,
    val glowColor: Color,
)

data class MapCircle(val id: Long, val name: String, val latitude: Double, val longitude: Double, val radius: Double)

/** Where to move the map. A new [stamp] moves it again even to the same place. */
sealed interface MapCamera {
    val stamp: Int

    data class Center(val latitude: Double, val longitude: Double, val zoom: Double?, override val stamp: Int) : MapCamera
    data class Fit(val points: List<LatLng>, override val stamp: Int) : MapCamera

    /** You and the car together (just you if the car has no position yet). */
    data class WithMe(val car: LatLng?, override val stamp: Int) : MapCamera
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * The map: MapLibre with a key-free style, the cars' icons, places as dashed circles and, for trip
 * replay, a route (the part already driven in full colour, the rest faded).
 */
@Composable
fun TrackerMap(
    style: MapStyle,
    dark: Boolean,
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    circles: List<MapCircle> = emptyList(),
    route: List<LatLng> = emptyList(),
    routeDone: Int = -1,
    routeColor: Color = Color(0xFF1D5BD8),
    showRouteEnds: Boolean = false,
    /** Speed in km/h at each point of [route]: draws the route in speed colours (see [SpeedColors]). */
    routeSpeeds: List<Double> = emptyList(),
    /** Where the car has been, drawn as a heatmap. */
    heat: List<Pair<Double, Double>> = emptyList(),
    camera: MapCamera? = null,
    /** Shows where you are, as a blue dot, once location is allowed. */
    showMyLocation: Boolean = false,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    onMarkerClick: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loaded by remember { mutableStateOf<Style?>(null) }
    val markerClick by rememberUpdatedState(onMarkerClick)
    val topPx = with(density) { topPadding.toPx() }
    val bottomPx = with(density) { bottomPadding.toPx() }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.START
                isCompassEnabled = true
                compassGravity = Gravity.TOP or Gravity.START
                isTiltGesturesEnabled = false
            }
            m.addOnMapClickListener { point ->
                val screen: PointF = m.projection.toScreenLocation(point)
                val hit = m.queryRenderedFeatures(screen, LAYER_MARKERS).firstOrNull()
                val id = hit?.getNumberProperty("id")?.toLong()
                if (id != null) {
                    markerClick(id)
                    true
                } else false
            }
            map = m
        }
    }

    LaunchedEffect(map, style, dark) {
        val m = map ?: return@LaunchedEffect
        loaded = null
        m.setStyle(MapStyles.builder(style, dark)) { s ->
            MapStyles.afterLoad(style, dark, s)
            addOverlayLayers(s, routeColor, dark)
            loaded = s
        }
    }

    LaunchedEffect(loaded, showMyLocation) {
        val m = map ?: return@LaunchedEffect
        val s = loaded ?: return@LaunchedEffect
        if (showMyLocation && hasLocationPermission(context)) showMe(context, m, s)
    }

    LaunchedEffect(map, topPx, bottomPx) {
        val m = map ?: return@LaunchedEffect
        val margin = with(density) { 12.dp.toPx() }.toInt()
        m.uiSettings.setAttributionMargins(margin, 0, 0, bottomPx.toInt() + margin)
        m.uiSettings.setCompassMargins(margin, topPx.toInt() + margin, 0, 0)
    }

    LaunchedEffect(loaded, markers) {
        val s = loaded ?: return@LaunchedEffect
        markers.forEach { mk ->
            val key = MarkerFactory.key(mk.icon, mk.carColor, mk.glowColor)
            if (s.getImage(key) == null) s.addImage(key, MarkerFactory.bitmap(context, mk.icon, mk.carColor, mk.glowColor))
        }
        val features = markers.map { mk ->
            Feature.fromGeometry(Point.fromLngLat(mk.longitude, mk.latitude)).apply {
                addNumberProperty("id", mk.id)
                addStringProperty("icon", MarkerFactory.key(mk.icon, mk.carColor, mk.glowColor))
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_MARKERS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(loaded, circles) {
        val s = loaded ?: return@LaunchedEffect
        val shapes = circles.map { c ->
            Feature.fromGeometry(Polygon.fromLngLats(listOf(circlePoints(c.latitude, c.longitude, c.radius))))
        }
        val labels = circles.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.longitude, c.latitude)).apply { addStringProperty("name", c.name) }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_FENCES)?.setGeoJson(FeatureCollection.fromFeatures(shapes))
        s.getSourceAs<GeoJsonSource>(SOURCE_FENCE_LABELS)?.setGeoJson(FeatureCollection.fromFeatures(labels))
    }

    // A route in speed colours doesn't change as a replay moves on, so it's only drawn again when the route does
    val coloured = routeSpeeds.size == route.size && route.size >= 2
    LaunchedEffect(loaded, route, if (coloured) -2 else routeDone, showRouteEnds, routeSpeeds) {
        val s = loaded ?: return@LaunchedEffect
        fun line(points: List<LatLng>): FeatureCollection =
            if (points.size < 2) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })))
        s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_SPEED)?.setGeoJson(
            if (coloured) FeatureCollection.fromFeatures(SpeedColors.segments(route, routeSpeeds)) else FeatureCollection.fromFeatures(emptyList()),
        )
        if (coloured) {
            // The whole trip in speed colours; the car's pin shows how far the replay has got
            s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_DONE)?.setGeoJson(line(emptyList()))
            s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_ALL)?.setGeoJson(line(emptyList()))
        } else if (routeDone < 0) {
            s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_DONE)?.setGeoJson(line(route))
            s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_ALL)?.setGeoJson(line(emptyList()))
        } else {
            s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_DONE)?.setGeoJson(line(route.take(routeDone + 1)))
            s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_ALL)?.setGeoJson(line(route))
        }
        val ends = if (showRouteEnds && route.size >= 2) {
            if (s.getImage(IMAGE_START) == null) s.addImage(IMAGE_START, MarkerFactory.dot(context, 0xFF2F6B3A.toInt()))
            if (s.getImage(IMAGE_END) == null) s.addImage(IMAGE_END, MarkerFactory.dot(context, 0xFF0E1726.toInt()))
            listOf(
                Feature.fromGeometry(Point.fromLngLat(route.first().longitude, route.first().latitude)).apply { addStringProperty("icon", IMAGE_START) },
                Feature.fromGeometry(Point.fromLngLat(route.last().longitude, route.last().latitude)).apply { addStringProperty("icon", IMAGE_END) },
            )
        } else emptyList()
        s.getSourceAs<GeoJsonSource>(SOURCE_ENDS)?.setGeoJson(FeatureCollection.fromFeatures(ends))
    }

    LaunchedEffect(loaded, heat) {
        val s = loaded ?: return@LaunchedEffect
        // Points within about 45 m are counted together, so a year of driving stays light: a spot passed
        // often gets a few points stacked (one more each time the count doubles), not a weight, which
        // crashes the map's renderer. Worked out away from the screen's thread.
        val collection = withContext(Dispatchers.Default) {
            val cells = HashMap<Long, IntArray>()
            heat.forEach { (lat, lon) ->
                val key = Math.round(lat / HEAT_CELL) * 10_000_000L + Math.round(lon / HEAT_CELL)
                cells.getOrPut(key) { IntArray(1) }[0]++
            }
            val features = ArrayList<Feature>()
            cells.forEach { (key, count) ->
                var lonCell = key % 10_000_000L
                var latCell = key / 10_000_000L
                if (lonCell > 5_000_000L) { lonCell -= 10_000_000L; latCell++ }
                if (lonCell < -5_000_000L) { lonCell += 10_000_000L; latCell-- }
                val copies = (1 + (31 - Integer.numberOfLeadingZeros(count[0]))).coerceAtMost(6)
                val point = Point.fromLngLat(lonCell * HEAT_CELL, latCell * HEAT_CELL)
                repeat(copies) { features += Feature.fromGeometry(point) }
            }
            FeatureCollection.fromFeatures(features)
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_HEAT)?.setGeoJson(collection)
    }

    LaunchedEffect(map, camera?.stamp) {
        val m = map ?: return@LaunchedEffect
        when (val c = camera) {
            is MapCamera.Center -> {
                val position = CameraPosition.Builder()
                    .target(LatLng(c.latitude, c.longitude))
                    // Following the car keeps whatever zoom you've chosen
                    .zoom(c.zoom ?: m.cameraPosition.zoom)
                    .padding(0.0, topPx.toDouble(), 0.0, bottomPx.toDouble())
                    .build()
                m.animateCamera(CameraUpdateFactory.newCameraPosition(position), 600)
            }
            is MapCamera.Fit -> fit(m, c.points, topPx, bottomPx, with(density) { 48.dp.toPx() }.toInt())
            is MapCamera.WithMe -> {
                // Where you are can take a moment to come in the first time
                var me: LatLng? = null
                for (attempt in 0 until 12) {
                    me = runCatching { m.locationComponent.lastKnownLocation }.getOrNull()?.let { LatLng(it.latitude, it.longitude) }
                    if (me != null) break
                    delay(500)
                }
                fit(m, listOfNotNull(c.car, me), topPx, bottomPx, with(density) { 64.dp.toPx() }.toInt())
            }
            null -> Unit
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.then(Modifier.fillMaxSize()))
}

/** Shows where you are as a blue dot, with the way your phone is facing. Needs location allowed first. */
@SuppressLint("MissingPermission")
private fun showMe(context: Context, map: MapLibreMap, style: Style) {
    runCatching {
        val component = map.locationComponent
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(true).build(),
        )
        component.isLocationComponentEnabled = true
        component.cameraMode = CameraMode.NONE
        component.renderMode = RenderMode.COMPASS
    }
}

/** Moves the map to show every point, or zooms in on one. */
private fun fit(m: MapLibreMap, points: List<LatLng>, topPx: Float, bottomPx: Float, pad: Int) {
    val distinct = points.distinct()
    if (distinct.size == 1) {
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(distinct.first()).zoom(15.0)
                    .padding(0.0, topPx.toDouble(), 0.0, bottomPx.toDouble()).build(),
            ),
            600,
        )
    } else if (distinct.size > 1) {
        val bounds = LatLngBounds.Builder().includes(distinct).build()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, topPx.toInt() + pad, pad, bottomPx.toInt() + pad), 600)
    }
}

private const val SOURCE_MARKERS = "ct-markers"
private const val LAYER_MARKERS = "ct-markers-layer"
private const val SOURCE_FENCES = "ct-fences"
private const val SOURCE_FENCE_LABELS = "ct-fence-labels"
private const val SOURCE_ROUTE_ALL = "ct-route-all"
private const val SOURCE_ROUTE_DONE = "ct-route-done"
private const val SOURCE_ENDS = "ct-route-ends"
private const val SOURCE_ROUTE_SPEED = "ct-route-speed"
private const val SOURCE_HEAT = "ct-heat"
private const val HEAT_CELL = 0.0004
private const val IMAGE_START = "ct-start"
private const val IMAGE_END = "ct-end"

private fun addOverlayLayers(style: Style, routeColor: Color, dark: Boolean) {
    val accent = routeColor.toArgb()
    listOf(SOURCE_HEAT, SOURCE_FENCES, SOURCE_FENCE_LABELS, SOURCE_ROUTE_ALL, SOURCE_ROUTE_DONE, SOURCE_ROUTE_SPEED, SOURCE_ENDS, SOURCE_MARKERS).forEach { id ->
        if (style.getSource(id) == null) style.addSource(GeoJsonSource(id))
    }
    style.addLayer(
        HeatmapLayer("ct-heat", SOURCE_HEAT).withProperties(
            PropertyFactory.heatmapRadius(
                Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(4, 3f), Expression.stop(10, 8f), Expression.stop(15, 18f)),
            ),
            PropertyFactory.heatmapIntensity(
                Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(4, 0.5f), Expression.stop(15, 1.6f)),
            ),
            PropertyFactory.heatmapColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.heatmapDensity(),
                    Expression.stop(0, Expression.rgba(0, 0, 0, 0)),
                    Expression.stop(0.1, Expression.rgba(29, 91, 216, 0.55)),
                    Expression.stop(0.3, Expression.rgba(0, 188, 220, 0.75)),
                    Expression.stop(0.5, Expression.rgba(76, 200, 80, 0.85)),
                    Expression.stop(0.7, Expression.rgba(250, 205, 40, 0.9)),
                    Expression.stop(1, Expression.rgba(226, 49, 43, 0.95)),
                ),
            ),
            PropertyFactory.heatmapOpacity(0.9f),
        ),
    )
    style.addLayer(
        FillLayer("ct-fences-fill", SOURCE_FENCES).withProperties(
            PropertyFactory.fillColor(accent),
            PropertyFactory.fillOpacity(0.08f),
        ),
    )
    style.addLayer(
        LineLayer("ct-fences-line", SOURCE_FENCES).withProperties(
            PropertyFactory.lineColor(accent),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        ),
    )
    style.addLayer(
        SymbolLayer("ct-fence-labels", SOURCE_FENCE_LABELS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor(accent),
            // A dark halo on dark maps, so light place names stay readable
            PropertyFactory.textHaloColor(if (dark) 0xFF0B1220.toInt() else android.graphics.Color.WHITE),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOffset(arrayOf(0f, -2.2f)),
            PropertyFactory.textAllowOverlap(true),
        ),
    )
    style.addLayer(
        LineLayer("ct-route-all", SOURCE_ROUTE_ALL).withProperties(
            PropertyFactory.lineColor(accent),
            PropertyFactory.lineOpacity(0.35f),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("ct-route-done", SOURCE_ROUTE_DONE).withProperties(
            PropertyFactory.lineColor(accent),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("ct-route-speed", SOURCE_ROUTE_SPEED).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        SymbolLayer("ct-route-ends", SOURCE_ENDS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_MARKERS, SOURCE_MARKERS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            // The pin's point marks the position
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}

/** A circle on the map as a 64-sided polygon. */
private fun circlePoints(latitude: Double, longitude: Double, radius: Double): List<Point> {
    val earth = 6_371_000.0
    val lat = Math.toRadians(latitude)
    val lon = Math.toRadians(longitude)
    val d = radius / earth
    return (0..64).map { i ->
        val bearing = 2 * Math.PI * i / 64
        val pLat = kotlin.math.asin(sin(lat) * cos(d) + cos(lat) * sin(d) * cos(bearing))
        val pLon = lon + kotlin.math.atan2(sin(bearing) * sin(d) * cos(lat), cos(d) - sin(lat) * sin(pLat))
        Point.fromLngLat(Math.toDegrees(pLon), Math.toDegrees(pLat))
    }
}
