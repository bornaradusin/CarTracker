package io.github.bornaradusin.cartracker.ui.trips

import io.github.bornaradusin.cartracker.ui.theme.Palette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import io.github.bornaradusin.cartracker.ui.map.SpeedColors
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.map.MapCamera
import io.github.bornaradusin.cartracker.ui.map.MapMarker
import io.github.bornaradusin.cartracker.ui.map.MapStyle
import io.github.bornaradusin.cartracker.ui.map.TrackerMap
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format
import kotlinx.coroutines.delay
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ReplayScreen(vm: AppViewModel, deviceId: Long, fromMillis: Long, toMillis: Long, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val density = LocalDensity.current
    val state by vm.replay.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cars by vm.cars.collectAsStateWithLifecycle()
    val car = cars.firstOrNull { it.device.id == deviceId }
    val from = Instant.ofEpochMilli(fromMillis)
    val to = Instant.ofEpochMilli(toMillis)

    LaunchedEffect(deviceId, fromMillis, toMillis) { vm.loadReplay(deviceId, from, to) }

    val points = state.points
    var index by remember(points) { mutableIntStateOf(0) }
    var playing by remember(points) { mutableStateOf(points.size > 1) }
    var speed by remember { mutableIntStateOf(4) }
    var panelHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(playing, speed, points) {
        if (!playing || points.size < 2) return@LaunchedEffect
        if (index >= points.lastIndex) index = 0
        while (playing && index < points.lastIndex) {
            delay((400L / speed).coerceAtLeast(16L))
            index++
        }
        playing = false
    }

    val route = remember(points) { points.map { LatLng(it.latitude, it.longitude) } }
    val speeds = remember(points) { points.map { it.speedKmh } }
    val current = points.getOrNull(index)
    val stats = remember(points) { tripStats(points) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(c.background)) {
        TrackerMap(
            style = MapStyle.named(settings.mapStyle),
            dark = c.dark,
            markers = current?.let {
                listOf(MapMarker(deviceId, it.latitude, it.longitude, it.course, car?.icon ?: "hatchback", car?.carColor ?: Palette.CarDefault, car?.glowColor ?: Palette.CircleDefault))
            }.orEmpty(),
            route = route,
            routeDone = index,
            routeSpeeds = speeds,
            routeColor = c.accentOutline,
            showRouteEnds = true,
            camera = if (route.isNotEmpty()) MapCamera.Fit(route, 1) else null,
            topPadding = topInset + 72.dp,
            bottomPadding = with(density) { panelHeight.toDp() },
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(onClick = onBack, shape = CircleShape, color = c.surface, contentColor = c.ink, shadowElevation = 4.dp, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back to trips" }) {
                Box(contentAlignment = Alignment.Center) { Icon(Ic.back, null, modifier = Modifier.size(22.dp)) }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = c.surface, shadowElevation = 4.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        "${Format.dayTitle(Format.localDate(from))} · ${Format.time(from)} – ${Format.time(to)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.ink,
                    )
                    Text(car?.device?.name ?: "", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { panelHeight = it.height }
                .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.surface)
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accentOutline) }
                state.error != null -> Text(state.error ?: "", color = c.warnText)
                points.size < 2 -> Text("This trip has no recorded route.", color = c.muted)
                else -> {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Stat("Distance", Format.distance(stats.meters))
                        Stat("Duration", Format.duration(toMillis - fromMillis))
                        Stat("Top speed", Format.speedKmh(stats.topKnots))
                    }
                    SpeedLegend()
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(Format.time(Format.parse(current?.fixTime)), style = MaterialTheme.typography.labelLarge, color = c.ink)
                            Text(
                                current?.let { p -> Format.speedKmh(p.speed) + (p.roadLimitKmh?.let { " · limit $it" } ?: "") } ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                color = current?.let { p -> p.roadLimitKmh?.takeIf { p.speedKmh > it + 0.5 } }?.let { c.warnText } ?: c.ink,
                            )
                        }
                        Slider(
                            value = index.toFloat(),
                            onValueChange = {
                                playing = false
                                index = it.toInt().coerceIn(0, points.lastIndex)
                            },
                            valueRange = 0f..points.lastIndex.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = c.accentOutline, activeTrackColor = c.accentOutline, inactiveTrackColor = c.line),
                            modifier = Modifier.semantics { contentDescription = "Position in trip" },
                        )
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(Format.time(from), style = MaterialTheme.typography.bodySmall, color = c.muted)
                            Text(Format.time(to), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            onClick = { playing = !playing },
                            shape = CircleShape,
                            color = c.accent,
                            contentColor = c.onAccent,
                            modifier = Modifier.size(60.dp).semantics { contentDescription = if (playing) "Pause" else "Play" },
                        ) {
                            Box(contentAlignment = Alignment.Center) { Icon(if (playing) Ic.pause else Ic.play, null, modifier = Modifier.size(24.dp)) }
                        }
                        listOf(1, 4, 16).forEach { s ->
                            val on = s == speed
                            Surface(
                                onClick = { speed = s },
                                shape = RoundedCornerShape(14.dp),
                                color = if (on) (if (c.dark) c.surface2 else c.ink) else c.surface,
                                contentColor = if (on) (if (c.dark) c.ink else c.surface) else c.ink,
                                border = if (on) null else androidx.compose.foundation.BorderStroke(1.5.dp, c.line),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text("$s×", fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the route's colours mean. */
@Composable
private fun SpeedLegend() {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(SpeedColors.stops.map { SpeedColors.color(it.first) })),
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            SpeedColors.stops.forEach { (kmh, _) ->
                Text(if (kmh == SpeedColors.stops.last().first) "${kmh.toInt()}+ km/h" else "${kmh.toInt()}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.ink)
    }
}

private data class TripStats(val meters: Double, val topKnots: Double)

private fun tripStats(points: List<Position>): TripStats {
    var meters = 0.0
    for (i in 1 until points.size) meters += distance(points[i - 1], points[i])
    return TripStats(meters, points.maxOfOrNull { it.speed } ?: 0.0)
}

private fun distance(a: Position, b: Position): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(h))
}
