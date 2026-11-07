package io.github.bornaradusin.cartracker.ui.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.data.VoltPoint
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.components.ScreenHeader
import io.github.bornaradusin.cartracker.ui.components.SectionCard
import io.github.bornaradusin.cartracker.ui.components.ToggleRow
import io.github.bornaradusin.cartracker.ui.theme.AppColors
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** The car battery: its voltage now and over the last day or week, and the app's low-battery warning. */
@Composable
fun BatteryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val state by vm.battery.collectAsStateWithLifecycle()
    var hours by rememberSaveable { mutableIntStateOf(24) }
    val info = car
    val deviceId = info?.device?.id ?: 0L

    LaunchedEffect(deviceId, hours) { if (deviceId != 0L) vm.loadBattery(deviceId, hours) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Battery", info?.device?.name, onBack = onBack)
        if (info == null) {
            Text("No car selected.", color = c.muted)
            return@Column
        }
        val volts = info.prefs.volts(info.position?.power)
        val limit = info.prefs.lowVoltage
        val warnAt = limit.takeIf { it > 0 } ?: 11.8

        // ---- now
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (c.dark) c.surface2 else c.ink).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Now", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8C2D0))
            Text(
                volts?.let { String.format(Locale.US, "%.1f V", it) } ?: "No reading",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp),
                color = if (volts != null && volts < warnAt) Color(0xFFF0A35E) else Color.White,
            )
            Text(health(volts, info.ignition, warnAt), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB8C2D0))
        }

        // ---- history
        SectionCard {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(24 to "24 hours", 72 to "3 days", 168 to "7 days").forEach { (h, label) ->
                    val selected = hours == h
                    Surface(
                        onClick = { hours = h },
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) c.surface else Color.Transparent,
                        contentColor = if (selected) c.ink else c.muted,
                        shadowElevation = if (selected) 1.dp else 0.dp,
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            // Stored as the tracker read them; corrected here, so changing the correction redraws the history too
            val offset = info.prefs.voltOffset.toFloat()
            val points = remember(state, deviceId, hours, offset) {
                val raw = if (state.deviceId == deviceId && state.hours == hours) state.points else emptyList()
                if (offset == 0f) raw else raw.map { it.copy(volts = it.volts + offset) }
            }
            val to = remember(points) { Instant.now() }
            val from = to.minusSeconds(hours * 3_600L)
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                when {
                    points.size >= 2 -> VoltChart(points, from.toEpochMilli(), to.toEpochMilli(), warnAt, c, hours)
                    state.loading -> Box(Modifier.height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accentOutline) }
                    state.error != null -> Text(state.error ?: "", color = c.warnText, modifier = Modifier.padding(vertical = 40.dp))
                    else -> Text("No battery readings in this time.", color = c.muted, modifier = Modifier.padding(vertical = 40.dp))
                }
            }
            if (points.size >= 2) {
                val parked = points.filter { !it.ignition }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Figure("Lowest parked", parked.minOfOrNull { it.volts }?.let { v(it) } ?: "–")
                    Figure("Highest", v(points.maxOf { it.volts }))
                    Figure("Engine on", Format.duration(engineMillis(points)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Legend(c.accentTint, "Engine running")
                    Legend(c.warnText, "Warning level", line = true)
                }
            }
        }

        // ---- warning
        SectionCard("Low battery warning") {
            var level by remember(limit) { mutableFloatStateOf((if (limit > 0) limit else 11.8).toFloat()) }
            ToggleRow(
                "Warn me when it's low",
                if (limit > 0) "When the car is parked and the battery drops below ${v(level)}" else "Off",
                checked = limit > 0,
                onChange = { on -> vm.setLowVoltage(info, if (on) 11.8 else 0.0) },
                divider = limit > 0,
            )
            if (limit > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Warn below", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    Text(v(level), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.accentText)
                }
                Slider(
                    value = level,
                    onValueChange = { level = (it * 10).roundToInt() / 10f },
                    onValueChangeFinished = { vm.setLowVoltage(info, (level * 10).roundToInt() / 10.0) },
                    valueRange = 11.0f..12.4f,
                    steps = 13,
                    colors = SliderDefaults.colors(thumbColor = c.accentOutline, activeTrackColor = c.accentOutline, inactiveTrackColor = c.line),
                    modifier = Modifier.semantics { contentDescription = "Warning level" },
                )
            }
            Text(
                "The app checks the tracker's readings while the car is parked and sends one alert, then waits until the battery recovers. " +
                    "A healthy 12 V battery reads about 12.4 to 12.7 V parked, and 13.8 to 14.4 V with the engine running.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
        }

        // ---- correction
        SectionCard("Reading correction") {
            val saved = info.prefs.voltOffset.toFloat()
            var offset by remember(saved) { mutableFloatStateOf(saved) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Add to every reading", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink)
                Text(
                    String.format(Locale.US, "%+.1f V", offset).replace("-0.0", "0.0").replace("+0.0", "0.0"),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accentText,
                )
            }
            Slider(
                value = offset,
                onValueChange = { offset = (it * 10).roundToInt() / 10f },
                onValueChangeFinished = { vm.setVoltOffset(info, (offset * 10).roundToInt() / 10.0) },
                valueRange = -1f..1f,
                steps = 19,
                colors = SliderDefaults.colors(thumbColor = c.accentOutline, activeTrackColor = c.accentOutline, inactiveTrackColor = c.line),
                modifier = Modifier.semantics { contentDescription = "Reading correction" },
            )
            Text(
                "If the tracker reads a little low or high, measure the battery with a multimeter and add the difference here. " +
                    "It changes every reading in the app, the warning and the tracker's own low battery alert.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
        }
    }
}

private fun v(volts: Float): String = String.format(Locale.US, "%.1f V", volts)

private fun health(volts: Double?, ignition: Boolean?, warnAt: Double): String = when {
    volts == null -> "The tracker hasn't sent a battery reading yet"
    ignition == true && volts >= 13.2 -> "Charging: the engine is running"
    volts >= 12.4 -> "Good"
    volts >= warnAt -> "OK, a little low"
    else -> "Low: the car may not start"
}

private fun engineMillis(points: List<VoltPoint>): Long {
    var total = 0L
    for (i in 1 until points.size) {
        val gap = points[i].time - points[i - 1].time
        if (points[i - 1].ignition && gap < GAP) total += gap
    }
    return total
}

/** Readings further apart than this have a gap between them (no line, no shading). */
private const val GAP = 30 * 60_000L

@Composable
private fun Figure(label: String, value: String) {
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.ink)
    }
}

@Composable
private fun Legend(color: Color, label: String, line: Boolean = false) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (line) Box(Modifier.width(14.dp).height(2.dp).background(color))
        else Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

/** Voltage over time: the line, the warning level dashed, and the times the engine ran shaded. */
@Composable
private fun VoltChart(points: List<VoltPoint>, from: Long, to: Long, warnAt: Double, c: AppColors, hours: Int) {
    val shown = remember(points) { thinOut(points, 600) }
    val low = minOf(shown.minOf { it.volts }.toDouble(), warnAt) - 0.3
    val high = maxOf(shown.maxOf { it.volts }.toDouble(), warnAt) + 0.3
    val top = maxOf(high, low + 1.0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(200.dp)) {
            Column(Modifier.width(44.dp).height(200.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(String.format(Locale.US, "%.1f", top), style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(String.format(Locale.US, "%.1f", (top + low) / 2), style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(String.format(Locale.US, "%.1f", low), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Canvas(Modifier.weight(1f).height(200.dp).semantics { contentDescription = "Battery voltage chart" }) {
                val span = (to - from).coerceAtLeast(1L).toFloat()
                fun x(t: Long) = ((t - from) / span) * size.width
                fun y(volts: Double) = ((top - volts) / (top - low) * size.height).toFloat()

                // engine running
                for (i in 1 until shown.size) {
                    val a = shown[i - 1]
                    val b = shown[i]
                    if (a.ignition && b.time - a.time < GAP) {
                        drawRect(c.accentTint, topLeft = Offset(x(a.time), 0f), size = Size((x(b.time) - x(a.time)).coerceAtLeast(1f), size.height))
                    }
                }
                // grid
                listOf(0f, 0.5f, 1f).forEach { f -> drawLine(c.line, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f) }
                // warning level
                drawLine(
                    c.warnText,
                    Offset(0f, y(warnAt)),
                    Offset(size.width, y(warnAt)),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
                // the readings, with gaps where none came in
                val path = Path()
                shown.forEachIndexed { i, p ->
                    val px = x(p.time)
                    val py = y(p.volts.toDouble())
                    if (i == 0 || p.time - shown[i - 1].time >= GAP) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, c.accentOutline, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val labels = if (hours <= 24) {
                listOf(from, (from + to) / 2, to).map { Format.time(Instant.ofEpochMilli(it)) }
            } else {
                listOf(from, (from + to) / 2, to).map { Format.dayShort(Format.localDate(Instant.ofEpochMilli(it))) }
            }
            labels.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
        }
    }
}

/** At most about [max] points to draw: each slice keeps its lowest and highest reading, so dips still show. */
private fun thinOut(points: List<VoltPoint>, max: Int): List<VoltPoint> {
    if (points.size <= max) return points
    val slice = (points.size + max / 2 - 1) / (max / 2)
    return points.chunked(slice).flatMap { chunk ->
        val lowest = chunk.minBy { it.volts }
        val highest = chunk.maxBy { it.volts }
        listOf(lowest, highest).distinct().sortedBy { it.time }
    }
}
