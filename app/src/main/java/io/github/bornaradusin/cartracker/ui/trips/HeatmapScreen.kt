package io.github.bornaradusin.cartracker.ui.trips

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.map.MapCamera
import io.github.bornaradusin.cartracker.ui.map.MapStyle
import io.github.bornaradusin.cartracker.ui.map.TrackerMap
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import org.maplibre.android.geometry.LatLng

/** Every trip over a stretch of time on one map: the more often a road is driven, the hotter it shows. */
@Composable
fun HeatmapScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val density = LocalDensity.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val state by vm.heat.collectAsStateWithLifecycle()
    var weeks by rememberSaveable { mutableIntStateOf(4) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val deviceId = car?.device?.id ?: 0L

    LaunchedEffect(deviceId, weeks) { if (deviceId != 0L) vm.loadHeat(deviceId, weeks) }

    val current = state.takeIf { it.deviceId == deviceId && it.weeks == weeks }
    val points = current?.points.orEmpty()
    // Fit the map to where the car has been: the corners are enough
    val corners = remember(points) {
        if (points.isEmpty()) emptyList()
        else listOf(
            LatLng(points.minOf { it.first }, points.minOf { it.second }),
            LatLng(points.maxOf { it.first }, points.maxOf { it.second }),
        )
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(c.background)) {
        TrackerMap(
            style = MapStyle.named(settings.mapStyle),
            dark = c.dark,
            heat = points,
            camera = if (corners.isNotEmpty()) MapCamera.Fit(corners, corners.hashCode()) else null,
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
                    Text("Where ${car?.device?.name ?: "the car"} has been", style = MaterialTheme.typography.labelLarge, color = c.ink)
                    Text("Blue: passed once · red: driven most often", style = MaterialTheme.typography.bodySmall, color = c.muted)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(1 to "Week", 4 to "4 weeks", 13 to "3 months", 52 to "Year").forEach { (w, label) ->
                    val selected = w == weeks
                    Surface(
                        onClick = { weeks = w },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) c.accent else c.surface2,
                        contentColor = if (selected) c.onAccent else c.ink,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                    }
                }
            }
            when {
                current == null || current.loading -> {
                    val total = current?.total ?: 0
                    val done = current?.done ?: 0
                    Text(
                        if (total > 1) "Loading week $done of $total… Weeks already loaded open straight away next time." else "Loading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                    if (total > 0) {
                        LinearProgressIndicator(progress = { done / total.toFloat() }, color = c.accentOutline, trackColor = c.line, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(color = c.accentOutline, trackColor = c.line, modifier = Modifier.fillMaxWidth())
                    }
                }
                current.error != null -> Text(current.error, style = MaterialTheme.typography.bodyMedium, color = c.warnText)
                points.isEmpty() -> Text("No trips in this time.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                else -> Text("Zoom in to see single streets.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}
