package io.github.bornaradusin.cartracker.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.StatsBar
import io.github.bornaradusin.cartracker.ui.StatsPeriod
import io.github.bornaradusin.cartracker.ui.StatsState
import io.github.bornaradusin.cartracker.ui.components.ScreenHeader
import io.github.bornaradusin.cartracker.ui.components.SectionCard
import io.github.bornaradusin.cartracker.ui.components.SettingRow
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format
import java.time.LocalDate

/** Distance, driving time and trips for a week, month or year, with a bar for each day or month. */
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val state by vm.stats.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEK) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val deviceId = car?.device?.id ?: 0L
    val today = LocalDate.now()
    val anchor = when (period) {
        StatsPeriod.WEEK -> today.plusWeeks(offset.toLong())
        StatsPeriod.MONTH -> today.plusMonths(offset.toLong())
        StatsPeriod.YEAR -> today.plusYears(offset.toLong())
    }

    LaunchedEffect(deviceId, period, anchor) { if (deviceId != 0L) vm.loadStats(deviceId, period, anchor) }

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
        ScreenHeader("Stats", car?.device?.name, onBack = onBack)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatsPeriod.entries.forEach { p ->
                val selected = p == period
                Surface(
                    onClick = {
                        period = p
                        offset = 0
                    },
                    shape = RoundedCornerShape(11.dp),
                    color = if (selected) c.accent else Color.Transparent,
                    contentColor = if (selected) c.onAccent else c.muted,
                    modifier = Modifier.weight(1f).height(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(p.label, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        val shown = state.takeIf { it.deviceId == deviceId && it.period == period }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Arrow(Ic.chevronLeft, "Earlier") { offset-- }
            Text(
                shown?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Arrow(Ic.chevronRight, "Later", enabled = offset < 0) { offset++ }
        }

        Totals(shown)

        SectionCard {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Distance", true to "Driving time").forEach { (time, label) ->
                    val selected = showTime == time
                    Surface(
                        onClick = { showTime = time },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) c.accentTint else c.surface2,
                        contentColor = if (selected) c.accentText else c.ink2,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                when {
                    shown == null || (shown.loading && shown.bars.isEmpty()) ->
                        Box(Modifier.height(190.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accentOutline) }
                    shown.error != null -> Text(shown.error, color = c.warnText, modifier = Modifier.padding(vertical = 40.dp))
                    else -> Bars(shown.bars, showTime, period)
                }
            }
        }

        if (shown != null && shown.trips > 0) {
            SectionCard {
                SettingRow("Per day on average", "${Format.distance(shown.meters / shown.days)} · ${Format.duration(shown.millis / shown.days)} driving")
                shown.longest?.let { t ->
                    val start = Format.parse(t.startTime)
                    SettingRow(
                        "Longest trip",
                        "${Format.distance(t.distance)} · ${start?.let { "${Format.dayTitle(Format.localDate(it))}, ${Format.time(it)}" } ?: ""}",
                        divider = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) c.ink else c.line, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun Totals(state: StatsState?) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (c.dark) c.surface2 else c.ink).padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Total("Distance", state?.let { Format.distance(it.meters) } ?: "–")
        Total("Driving", state?.let { if (it.millis == 0L) "0 min" else Format.duration(it.millis) } ?: "–")
        Total("Trips", state?.trips?.toString() ?: "–")
        Total("Top speed", state?.topKnots?.takeIf { it > 0 }?.let { Format.speedKmh(it).removeSuffix(" km/h") } ?: "–")
    }
}

@Composable
private fun Total(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8C2D0))
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = Color.White)
    }
}

/** One bar per day or month; tap one to see its numbers. */
@Composable
private fun Bars(bars: List<StatsBar>, showTime: Boolean, period: StatsPeriod) {
    val c = LocalAppColors.current
    var picked by remember(bars, showTime) { mutableIntStateOf(-1) }
    val values = bars.map { if (showTime) it.millis.toDouble() else it.meters }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(Modifier.fillMaxWidth()) {
        val selected = bars.getOrNull(picked)
        Text(
            when {
                selected != null -> "${selected.label}: ${Format.distance(selected.meters)} · ${Format.duration(selected.millis)} · ${selected.trips} trip${if (selected.trips == 1) "" else "s"}"
                values.all { it == 0.0 } -> "No trips"
                else -> "Tap a bar for its numbers"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (selected != null) c.ink else c.muted,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 2.dp else 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, _ ->
                val fraction = (values[i] / max).toFloat()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { picked = if (picked == i) -1 else i }
                        .semantics { contentDescription = bars[i].label },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (values[i] > 0) fraction.coerceAtLeast(0.03f) else 0.015f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (i == picked) c.accentText else if (values[i] > 0) c.accentOutline else c.line),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 2.dp else 6.dp)) {
            bars.forEachIndexed { i, bar ->
                // A month has too many days to label each one
                val label = if (period == StatsPeriod.MONTH && i % 5 != 0) "" else bar.label
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
