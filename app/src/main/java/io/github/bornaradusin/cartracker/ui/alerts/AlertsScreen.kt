package io.github.bornaradusin.cartracker.ui.alerts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.data.TrackerCommands
import io.github.bornaradusin.cartracker.data.num
import io.github.bornaradusin.cartracker.data.str
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.data.AlertKind
import io.github.bornaradusin.cartracker.ui.AlertItem
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.components.ScreenHeader
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format

private enum class Filter(val label: String, val kinds: Set<AlertKind>?) {
    All("All", null),
    Ignition("Ignition", setOf(AlertKind.IGNITION, AlertKind.MOVING)),
    Places("Places", setOf(AlertKind.PLACES)),
    Speed("Speed", setOf(AlertKind.SPEEDING)),
    Alarm("Alarm", setOf(AlertKind.UNPLUGGED, AlertKind.VIBRATION, AlertKind.LOW_BATTERY, AlertKind.SOS, AlertKind.OTHER_ALARMS)),
}

@Composable
fun AlertsScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val state by vm.alerts.collectAsStateWithLifecycle()
    val places by vm.alertPlaces.collectAsStateWithLifecycle()
    val cars by vm.cars.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var open by remember { mutableStateOf<AlertItem?>(null) }

    LaunchedEffect(Unit) { vm.loadAlerts() }
    // Leaving the list marks everything in it as read.
    DisposableEffect(Unit) { onDispose { vm.markAlertsSeen() } }

    val kinds = filter.kinds
    val shown = state.items.filter { item -> kinds == null || item.kind?.let { it in kinds } == true }
    LaunchedEffect(shown.take(40).map { it.event.id }) { vm.lookupAlertPlaces(shown.take(40)) }
    val groups = shown.groupBy { it.time?.let(Format::localDate) }
    val manyCars = cars.size > 1

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Alerts") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.items.isNotEmpty()) {
                        Surface(onClick = { vm.clearAlerts() }, shape = RoundedCornerShape(999.dp), color = c.surface, contentColor = c.ink2) {
                            Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Ic.trash, null, modifier = Modifier.size(16.dp))
                                Text("Clear", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Surface(onClick = { vm.loadAlerts() }, shape = RoundedCornerShape(999.dp), color = c.surface, contentColor = c.accentText) {
                        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Ic.refresh, null, modifier = Modifier.size(16.dp))
                            Text("Refresh", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f ->
                    val on = f == filter
                    Surface(
                        onClick = { filter = f },
                        shape = RoundedCornerShape(999.dp),
                        color = if (on) (if (c.dark) c.surface2 else c.ink) else c.surface,
                        contentColor = if (on) (if (c.dark) c.ink else c.surface) else c.ink,
                        border = if (on) null else androidx.compose.foundation.BorderStroke(1.5.dp, c.line),
                    ) {
                        Text(f.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
        when {
            state.loading && state.items.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accentOutline) }
            }
            state.error != null && state.items.isEmpty() -> item { Text(state.error ?: "", color = c.warnText) }
            shown.isEmpty() -> item {
                Text("Nothing in the last 7 days.", style = MaterialTheme.typography.bodyLarge, color = c.muted, modifier = Modifier.padding(vertical = 24.dp))
            }
            else -> groups.forEach { (day, items) ->
                item(key = "day-$day") {
                    Text(
                        day?.let { Format.dayTitle(it) } ?: "Earlier",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.muted,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                item(key = "list-$day") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        items.forEachIndexed { i, item ->
                            AlertRow(item, places[item.event.id], manyCars) { open = item }
                            if (i < items.lastIndex) HorizontalDivider(color = c.divider)
                        }
                    }
                }
            }
        }
    }
    open?.let { item -> AlertDetails(vm, item, places[item.event.id]) { open = null } }
}

/** Everything about one alert: the full text (a tracker's whole reply), when, where, and a map link. */
@Composable
private fun AlertDetails(vm: AppViewModel, item: AlertItem, place: String?, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val position by produceState<Position?>(initialValue = null, item.event.id) { value = vm.alertPosition(item.event) }
    val reply = item.event.attributes.str("result")?.let { TrackerCommands.readableReply(it) }
    val link = reply?.let { Regex("https?://\\S+").find(it)?.value }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(item.title, color = c.ink) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    listOfNotNull(item.deviceName.takeIf { it.isNotBlank() }, item.time?.let { "${Format.dayTitle(Format.localDate(it))}, ${Format.time(it)}" })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                )
                if (reply != null) {
                    Text("WHAT THE TRACKER SAID", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
                    SelectionContainer {
                        Text(
                            reply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(12.dp),
                        )
                    }
                }
                val where = place ?: position?.address
                if (where != null) Text(where, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                position?.takeIf { it.hasFix }?.let { p ->
                    val speed = item.event.attributes.num("speed") ?: p.speed.takeIf { item.kind == AlertKind.SPEEDING }
                    if (speed != null && speed > 0) Text("Speed ${Format.speedKmh(speed)}", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reply != null) TextButton(onClick = { clipboard.setText(AnnotatedString(reply)) }) { Text("Copy", color = c.ink2) }
                val target = link ?: position?.takeIf { it.hasFix }?.let { "https://www.google.com/maps/search/?api=1&query=${it.latitude},${it.longitude}" }
                if (target != null) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Open in Maps", color = c.accentText) }
                }
                TextButton(onClick = onDismiss) { Text("Close", color = c.ink2) }
            }
        },
    )
}

@Composable
private fun AlertRow(item: AlertItem, place: String?, showCar: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val icon: ImageVector = when (item.kind) {
        AlertKind.IGNITION -> Ic.key
        AlertKind.PLACES -> Ic.pin
        AlertKind.SPEEDING -> Ic.zap
        AlertKind.UNPLUGGED -> Ic.power
        AlertKind.VIBRATION, AlertKind.SOS -> Ic.shieldCheck
        AlertKind.LOW_BATTERY -> Ic.battery
        AlertKind.FUEL -> Ic.lock
        AlertKind.MOVING -> Ic.route
        AlertKind.OFFLINE -> Ic.server
        else -> Ic.warning
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = "Show details", onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (item.warning) c.warnTint else c.accentTint),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (item.warning) c.warnText else c.accentText, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(item.deviceName.takeIf { showCar && it.isNotBlank() }, place).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Format.time(item.time), style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}
