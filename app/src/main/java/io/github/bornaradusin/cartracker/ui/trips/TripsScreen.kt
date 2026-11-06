package io.github.bornaradusin.cartracker.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.data.Trip
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.components.ScreenHeader
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format
import java.time.LocalDate

@Composable
fun TripsScreen(vm: AppViewModel, onOpenTrip: (deviceId: Long, fromMillis: Long, toMillis: Long) -> Unit) {
    val c = LocalAppColors.current
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val cars by vm.cars.collectAsStateWithLifecycle()
    val state by vm.trips.collectAsStateWithLifecycle()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var carMenu by remember { mutableStateOf(false) }
    val deviceId = car?.device?.id ?: 0L

    LaunchedEffect(deviceId, date) { if (deviceId != 0L) vm.loadTrips(deviceId, date) }

    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val trips = if (state.deviceId == deviceId && state.date == date) state.trips else emptyList()

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader("Trips") {
                Box {
                    Surface(onClick = { if (cars.size > 1) carMenu = true }, shape = RoundedCornerShape(999.dp), color = c.surface, contentColor = c.ink) {
                        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(car?.device?.name ?: "–", style = MaterialTheme.typography.labelLarge)
                            if (cars.size > 1) Icon(Ic.chevronDown, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(carMenu, onDismissRequest = { carMenu = false }, containerColor = c.surface) {
                        cars.forEach { info ->
                            DropdownMenuItem(text = { Text(info.device.name, color = c.ink) }, onClick = {
                                carMenu = false
                                vm.select(info.device.id)
                            })
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                days.forEach { d ->
                    val selected = d == date
                    Surface(
                        onClick = { date = d },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) c.accent else c.surface,
                        contentColor = if (selected) c.onAccent else c.ink,
                        modifier = Modifier.weight(1f).height(60.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (d == today) "Today" else Format.dayShort(d), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(d.dayOfMonth.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { Summary(trips) }
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accentOutline) }
            }
            state.error != null -> item { Text(state.error ?: "", color = c.warnText) }
            trips.isEmpty() -> item {
                Text(
                    "No trips ${if (date == today) "today" else "on ${Format.dayTitle(date)}"}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.muted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            else -> items(trips.reversed()) { trip ->
                TripCard(trip, state.route) {
                    val from = Format.parse(trip.startTime)?.toEpochMilli() ?: return@TripCard
                    val to = Format.parse(trip.endTime)?.toEpochMilli() ?: return@TripCard
                    onOpenTrip(deviceId, from, to)
                }
            }
        }
    }
}

@Composable
private fun Summary(trips: List<Trip>) {
    val c = LocalAppColors.current
    val distance = trips.sumOf { it.distance }
    val driving = trips.sumOf { it.duration }
    val top = trips.maxOfOrNull { it.maxSpeed } ?: 0.0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (c.dark) c.surface2 else c.ink).padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SummaryValue("Distance", Format.distance(distance))
        SummaryValue("Driving", if (driving == 0L) "0 min" else Format.duration(driving))
        SummaryValue("Top speed", if (top == 0.0) "–" else Format.speedKmh(top).removeSuffix(" km/h"))
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFB8C2D0))
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
private fun TripCard(trip: Trip, dayRoute: List<Position>, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val start = Format.parse(trip.startTime)
    val end = Format.parse(trip.endTime)
    val points = remember(trip, dayRoute) {
        if (start == null || end == null) emptyList()
        else dayRoute.filter { p ->
            val t = Format.parse(p.fixTime)
            t != null && !t.isBefore(start) && !t.isAfter(end) && p.hasFix
        }
    }
    val places by produceState(initialValue = listOfNotNull(trip.startAddress, trip.endAddress).joinToString(" → ").ifBlank { null }, trip) {
        if (value == null || trip.startAddress == null || trip.endAddress == null) {
            val lookup = context.container.addresses
            val from = lookup.lookup(trip.startLat, trip.startLon, trip.startAddress)?.substringAfterLast(", ")
            val to = lookup.lookup(trip.endLat, trip.endLon, trip.endAddress)?.substringAfterLast(", ")
            value = listOfNotNull(from, to).joinToString(" → ").ifBlank { null }
        }
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                RouteSketch(points, Modifier.size(60.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("${Format.time(start)} – ${Format.time(end)}", style = MaterialTheme.typography.labelLarge, color = c.ink)
                    Text(Format.duration(trip.duration), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                Text(places ?: "Trip", style = MaterialTheme.typography.bodyMedium, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Format.distance(trip.distance)} · top ${Format.speedKmh(trip.maxSpeed)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

/** The trip's shape, scaled into a small square. */
@Composable
fun RouteSketch(points: List<Position>, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Canvas(modifier) {
        if (points.size < 2) {
            drawCircle(c.accentOutline, radius = 4.dp.toPx(), center = center)
            return@Canvas
        }
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
        val lonSpan = ((maxLon - minLon) * kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))).coerceAtLeast(1e-6)
        val span = maxOf(latSpan, lonSpan)
        val pad = 6.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val cosLat = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
        fun at(p: Position): Offset {
            val x = ((p.longitude - minLon) * cosLat) / span
            val y = (p.latitude - minLat) / span
            return Offset(
                (pad + x * w + (w - lonSpan / span * w) / 2).toFloat(),
                (pad + h - y * h - (h - latSpan / span * h) / 2).toFloat(),
            )
        }
        val path = Path()
        points.forEachIndexed { i, p -> val o = at(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, c.accentOutline, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
