package io.github.bornaradusin.cartracker.ui.more

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.BuildConfig
import io.github.bornaradusin.cartracker.alerts.Notifier
import io.github.bornaradusin.cartracker.data.AlertKind
import io.github.bornaradusin.cartracker.data.TrackerKind
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.CarInfo
import io.github.bornaradusin.cartracker.ui.components.ChevronRow
import io.github.bornaradusin.cartracker.ui.components.ScreenHeader
import io.github.bornaradusin.cartracker.ui.components.SectionCard
import io.github.bornaradusin.cartracker.ui.components.SettingRow
import io.github.bornaradusin.cartracker.ui.components.Swatch
import io.github.bornaradusin.cartracker.ui.components.ToggleRow
import io.github.bornaradusin.cartracker.ui.map.MarkerFactory
import io.github.bornaradusin.cartracker.ui.theme.Accents
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.ui.theme.MarkerColors

@Composable
fun MoreScreen(vm: AppViewModel, onOpenAlertTypes: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val fences by vm.geofences.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var addingPlace by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var trackerTypeDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("More")

        val info = car
        if (info != null) {
            SectionCard("Your car") {
                ChevronRow("Name", value = info.device.name) { renaming = true }
                MarkerPreview(info)
                Text("Icon on the map", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Ic.vehicles.keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        row.forEach { name ->
                            val selected = info.icon == name
                            Surface(
                                onClick = { vm.setLook(info, icon = name) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) c.accentTint else c.surface,
                                contentColor = c.ink,
                                border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) c.accentOutline else c.line),
                                modifier = Modifier.weight(1f).heightIn(min = 76.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).background(if (selected) c.accent else c.surface2),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Ic.vehicle(name), null, tint = if (selected) c.onAccent else c.ink2, modifier = Modifier.size(22.dp)) }
                                    Text(name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
                Text("Icon colour", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                MarkerColors.all.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        row.forEach { (name, color) ->
                            Swatch(color, name, info.prefs.color == name, if (name == "White") Color(0xFF3B4656) else color) { vm.setLook(info, color = name) }
                        }
                    }
                }
                Text("Icon shape", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    listOf("Circle", "Square", "Pin", "Arrow").forEach { shape ->
                        val selected = info.prefs.shape == shape
                        val bitmap = remember(shape, info.icon, info.prefs.color) { MarkerFactory.bitmap(context, info.icon, info.prefs.color, shape, 20f).asImageBitmap() }
                        Surface(
                            onClick = { vm.setLook(info, shape = shape) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) c.accentTint else c.surface,
                            contentColor = c.ink,
                            border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) c.accentOutline else c.line),
                            modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Image(bitmap, null, modifier = Modifier.size(34.dp))
                                Text(shape, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
                ChevronRow(
                    "Tracker type",
                    "Which commands the app sends",
                    value = when (info.prefs.trackerType) {
                        "micodus" -> "MiCODUS"
                        "generic" -> "Other"
                        else -> if (info.kind == TrackerKind.MICODUS) "Automatic (MiCODUS)" else "Automatic"
                    },
                    divider = false,
                ) { trackerTypeDialog = true }
            }

            SectionCard("Places") {
                val list = fences[info.device.id].orEmpty()
                list.forEach { fence ->
                    SettingRow(
                        fence.name,
                        fence.circle?.let { "Within ${it.radius.toInt()} m · alerts when ${info.device.name} arrives or leaves" } ?: "Alerts when ${info.device.name} arrives or leaves",
                        leading = {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.accentTint), contentAlignment = Alignment.Center) {
                                Icon(if (fence.name.equals("home", true)) Ic.home else Ic.pin, null, tint = c.accentText, modifier = Modifier.size(18.dp))
                            }
                        },
                    ) {
                        Surface(onClick = { vm.deletePlace(info, fence) }, shape = CircleShape, color = Color.Transparent, contentColor = c.muted, modifier = Modifier.size(40.dp).semantics { contentDescription = "Remove ${fence.name}" }) {
                            Box(contentAlignment = Alignment.Center) { Icon(Ic.trash, null, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
                Surface(onClick = { addingPlace = true }, color = Color.Transparent, contentColor = c.accentText, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Ic.plus, null, modifier = Modifier.size(20.dp))
                        Text("Add a place where ${info.device.name} is now", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        SectionCard("Notifications") {
            ToggleRow(
                "Live alerts",
                "Within seconds, even when the app is closed. Off: checks every 15 minutes.",
                checked = settings.liveAlerts,
                onChange = { vm.setLiveAlerts(it) },
            )
            if (settings.liveAlerts) {
                ChevronRow("Hide the connection notification", "Alerts keep coming") { openChannelSettings(context) }
            }
            ChevronRow(
                "Which alerts to get",
                value = "${AlertKind.entries.count { it.key in settings.alertKinds }} of ${AlertKind.entries.size}",
                divider = needsBatteryExemption(context),
            ) { onOpenAlertTypes() }
            if (needsBatteryExemption(context)) {
                ChevronRow("Keep running in the background", "Stops the phone pausing live alerts to save battery", divider = false) { askBatteryExemption(context) }
            }
        }

        SectionCard("Theme") {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("light" to "Light", "dark" to "Dark", "system" to "Follow phone").forEach { (value, label) ->
                    val selected = settings.theme == value
                    Surface(
                        onClick = { vm.setTheme(value) },
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) c.surface else Color.Transparent,
                        contentColor = if (selected) c.ink else c.muted,
                        shadowElevation = if (selected) 1.dp else 0.dp,
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            Text("Colour", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Accents.all.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    row.forEach { accent ->
                        Swatch(accent.light, accent.name, settings.accent == accent.name, accent.outlineLight) { vm.setAccent(accent.name) }
                    }
                }
            }
        }

        SectionCard("Commands") {
            ToggleRow(
                "Advanced commands",
                "Every command Traccar offers for your tracker, the server's saved commands and a custom command box, under All controls",
                checked = settings.advancedCommands,
                onChange = { vm.setAdvanced(it) },
                divider = false,
            )
        }

        SectionCard("Server") {
            SettingRow(
                settings.server.removePrefix("https://").removePrefix("http://"),
                listOfNotNull(
                    settings.serverVersion.takeIf { it.isNotBlank() }?.let { "Traccar ${it.substringBefore('-')}" },
                    settings.email.takeIf { it.isNotBlank() }?.let { "signed in as $it" },
                ).joinToString(" · "),
            )
            Surface(onClick = { signingOut = true }, color = Color.Transparent, contentColor = c.warnText, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) { Text("Sign out", style = MaterialTheme.typography.labelLarge) }
            }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CarTracker ${BuildConfig.VERSION_NAME} · made by bornaradusin", style = MaterialTheme.typography.bodySmall, color = c.muted)
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bornaradusin/cartracker")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("github.com/bornaradusin/cartracker", color = c.accentText) }
        }
    }

    val info = car
    if (renaming && info != null) {
        TextDialog(
            title = "Rename",
            initial = info.device.name,
            confirm = "Save",
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                vm.rename(info, name)
            },
        )
    }
    if (addingPlace && info != null) {
        AddPlaceDialog(info, onDismiss = { addingPlace = false }) { name, radius ->
            addingPlace = false
            vm.addPlace(info, name, radius)
        }
    }
    if (trackerTypeDialog && info != null) {
        AlertDialog(
            onDismissRequest = { trackerTypeDialog = false },
            containerColor = c.surface,
            title = { Text("Tracker type", color = c.ink) },
            text = {
                Column {
                    listOf(
                        "auto" to "Automatic (MiCODUS for JT808 trackers)",
                        "micodus" to "MiCODUS (MV710G and similar)",
                        "generic" to "Other: Traccar's standard commands",
                    ).forEach { (value, label) ->
                        Surface(
                            onClick = {
                                trackerTypeDialog = false
                                vm.setTrackerType(info, value)
                            },
                            color = if (info.prefs.trackerType == value) c.accentTint else c.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, color = c.ink, modifier = Modifier.padding(14.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { trackerTypeDialog = false }) { Text("Close", color = c.ink2) } },
        )
    }
    if (signingOut) {
        AlertDialog(
            onDismissRequest = { signingOut = false },
            containerColor = c.surface,
            title = { Text("Sign out?", color = c.ink) },
            text = { Text("Alerts stop until you sign in again.", color = c.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    signingOut = false
                    vm.signOut()
                }) { Text("Sign out", color = c.warnText, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { signingOut = false }) { Text("Cancel", color = c.ink2) } },
        )
    }
}

@Composable
private fun MarkerPreview(info: CarInfo) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val bitmap = remember(info.icon, info.prefs.color, info.prefs.shape) {
        MarkerFactory.bitmap(context, info.icon, info.prefs.color, info.prefs.shape).asImageBitmap()
    }
    Box(
        Modifier.fillMaxWidth().padding(top = 12.dp).height(112.dp).clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color(0xFF1F2124) else Color(0xFFF1F3F4))
            .semantics { contentDescription = "Preview of ${info.device.name} on the map" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(if (c.dark) Color(0xFF3C4043) else Color.White))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(bitmap, null, modifier = Modifier.size(72.dp))
            Text(
                info.device.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = c.ink,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.surface).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = LocalAppColors.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accentOutline, unfocusedBorderColor = c.line, cursorColor = c.accentOutline),
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm, color = c.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

@Composable
private fun AddPlaceDialog(info: CarInfo, onDismiss: () -> Unit, onAdd: (String, Double) -> Unit) {
    val c = LocalAppColors.current
    var name by remember { mutableStateOf("Home") }
    var radius by remember { mutableStateOf(150.0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Add a place", color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Around where ${info.device.name} is now${info.address?.let { " ($it)" } ?: ""}.", color = c.ink2)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accentOutline, unfocusedBorderColor = c.line, cursorColor = c.accentOutline),
                    shape = RoundedCornerShape(14.dp),
                )
                Text("Size", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(75.0, 150.0, 300.0, 600.0).forEach { r ->
                        val selected = radius == r
                        Surface(
                            onClick = { radius = r },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) c.accent else c.surface2,
                            contentColor = if (selected) c.onAccent else c.ink,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text("${r.toInt()} m", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, radius) }, enabled = name.isNotBlank()) { Text("Add", color = c.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

private fun openChannelSettings(context: Context) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_LIVE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun needsBatteryExemption(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun askBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
fun AlertTypesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Which alerts to get", onBack = onBack)
        SectionCard {
            AlertKind.entries.forEachIndexed { i, kind ->
                ToggleRow(
                    kind.title,
                    kind.hint,
                    checked = kind.key in settings.alertKinds,
                    onChange = { vm.setAlertFilter(kind, it) },
                    divider = i < AlertKind.entries.lastIndex,
                )
            }
        }
        Text(
            "Speeding needs a limit, set under All controls. Places need a place, added under More.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
