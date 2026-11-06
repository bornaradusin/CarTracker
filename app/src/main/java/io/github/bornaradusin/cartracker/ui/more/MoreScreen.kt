package io.github.bornaradusin.cartracker.ui.more

import io.github.bornaradusin.cartracker.data.AlertSound
import kotlin.math.roundToInt
import io.github.bornaradusin.cartracker.ui.theme.Palette
import io.github.bornaradusin.cartracker.ui.map.CarPin
import io.github.bornaradusin.cartracker.ui.map.CarImages
import io.github.bornaradusin.cartracker.ui.components.ColorRow
import io.github.bornaradusin.cartracker.data.Geofence
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.foundation.border
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
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors

@Composable
fun MoreScreen(vm: AppViewModel, onOpenAlertTypes: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val fences by vm.geofences.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var addingPlace by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<Geofence?>(null) }
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
                Text("Vehicle", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                CarImages.all.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        row.forEach { id ->
                            val selected = info.icon == id
                            Surface(
                                onClick = { vm.setLook(info, icon = id) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) c.accentTint else c.surface2,
                                border = BorderStroke(2.dp, if (selected) c.accentOutline else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(84.dp)
                                    .semantics { contentDescription = (CarImages.labels[id] ?: id) + if (selected) ", chosen" else "" },
                            ) {
                                Box(contentAlignment = Alignment.Center) { CarPin(id, info.carColor, info.glowColor, 44.dp) }
                            }
                        }
                    }
                }
                ColorRow(
                    "Car colour",
                    "The car's paint on the map",
                    info.carColor,
                    Palette.CarDefault,
                    preview = { CarPin(info.icon, it, info.glowColor, 72.dp) },
                ) { vm.setLook(info, carColor = it) }
                ColorRow(
                    "Glow colour",
                    "The ring and point around the car",
                    info.glowColor,
                    Palette.CircleDefault,
                    preview = { CarPin(info.icon, info.carColor, it, 72.dp) },
                ) { vm.setLook(info, glowColor = it) }
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
                        onClick = { editingPlace = fence },
                        leading = {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.accentTint), contentAlignment = Alignment.Center) {
                                Icon(Ic.place(fence.icon), null, tint = c.accentText, modifier = Modifier.size(18.dp))
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
            ColorRow(
                "App colour",
                "Buttons, switches, highlights and routes",
                Palette.parse(settings.accent, Palette.AppDefault),
                Palette.AppDefault,
                divider = false,
                preview = { AppColourPreview(it) },
            ) { vm.setAccent(Palette.hex(it)) }
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
        PlaceDialog(
            title = "Add a place",
            subtitle = "Around where ${info.device.name} is now${info.address?.let { " ($it)" } ?: ""}.",
            initialName = "Home",
            initialRadius = 150.0,
            initialIcon = "home",
            confirm = "Add",
            onDismiss = { addingPlace = false },
        ) { name, radius, icon ->
            addingPlace = false
            vm.addPlace(info, name, radius, icon)
        }
    }
    val placeToEdit = editingPlace
    if (placeToEdit != null && info != null) {
        PlaceDialog(
            title = "Edit ${placeToEdit.name}",
            subtitle = null,
            initialName = placeToEdit.name,
            initialRadius = placeToEdit.circle?.radius ?: 150.0,
            initialIcon = placeToEdit.icon,
            confirm = "Save",
            onDismiss = { editingPlace = null },
        ) { name, radius, icon ->
            editingPlace = null
            vm.editPlace(info, placeToEdit, name, radius, icon)
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
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(124.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (c.dark) Color(0xFF1F2124) else Color(0xFFF1F3F4))
            .semantics { contentDescription = "Preview of ${info.device.name} on the map" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(if (c.dark) Color(0xFF3C4043) else Color.White))
        CarPin(info.icon, info.carColor, info.glowColor, 72.dp)
    }
}

/** How the app colour looks on a button and as text, while picking it. */
@Composable
private fun AppColourPreview(color: Color) {
    val c = LocalAppColors.current
    val accent = Palette.accent(color)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (c.dark) accent.dark else accent.light)
                .border(1.5.dp, if (Palette.isNearWhite(color) && !c.dark) accent.outlineLight else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text("Button", style = MaterialTheme.typography.labelLarge, color = if (c.dark) accent.onDark else accent.onLight)
        }
        Text(
            "Link",
            style = MaterialTheme.typography.labelLarge,
            color = if (c.dark) accent.textDark else if (Palette.isLight(color)) accent.outlineLight else accent.light,
        )
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

/** Name, size and icon of a place, for adding a new one or changing a saved one. */
@Composable
private fun PlaceDialog(
    title: String,
    subtitle: String?,
    initialName: String,
    initialRadius: Double,
    initialIcon: String,
    confirm: String,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit,
) {
    val c = LocalAppColors.current
    var name by remember { mutableStateOf(initialName) }
    var radius by remember { mutableStateOf(initialRadius.coerceIn(50.0, 2000.0)) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (subtitle != null) Text(subtitle, color = c.ink2)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accentOutline, unfocusedBorderColor = c.line, cursorColor = c.accentOutline),
                    shape = RoundedCornerShape(14.dp),
                )
                Text("Size: ${radius.toInt()} m around it", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Slider(
                    value = radius.toFloat(),
                    onValueChange = { radius = ((it / 25f).roundToInt() * 25).toDouble() },
                    valueRange = 50f..2000f,
                    colors = SliderDefaults.colors(thumbColor = c.accentOutline, activeTrackColor = c.accentOutline, inactiveTrackColor = c.line),
                    modifier = Modifier.semantics { contentDescription = "Size ${radius.toInt()} metres" },
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Ic.places.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (key, vector) ->
                            val selected = icon == key
                            Surface(
                                onClick = { icon = key },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) c.accent else c.surface2,
                                contentColor = if (selected) c.onAccent else c.ink2,
                                modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = key + if (selected) ", chosen" else "" },
                            ) {
                                Box(contentAlignment = Alignment.Center) { Icon(vector, null, modifier = Modifier.size(22.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, radius, icon) }, enabled = name.isNotBlank()) { Text(confirm, color = c.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

private fun openChannelSettings(context: Context, channel: String = Notifier.CHANNEL_LIVE) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
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
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var choosingSound by remember { mutableStateOf<AlertKind?>(null) }
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
                val on = kind.key in settings.alertKinds
                val last = i == AlertKind.entries.lastIndex
                ToggleRow(
                    kind.title,
                    kind.hint,
                    checked = on,
                    onChange = { vm.setAlertFilter(kind, it) },
                    divider = !on && !last,
                )
                if (on) {
                    val sound = AlertSound.of(settings.alertSounds[kind.key])
                    SettingRow(
                        "Sound",
                        null,
                        divider = !last,
                        onClick = { choosingSound = kind },
                        leading = { Icon(Ic.bell, null, tint = c.muted, modifier = Modifier.size(18.dp)) },
                    ) {
                        Text(sound.title, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                        Icon(Ic.chevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Text(
            "Speeding needs a limit, set under All controls. Places need a place, added under More. " +
                "Alarm sound plays at your phone's alarm volume, so you hear it even with the ringer down.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    val kind = choosingSound
    if (kind != null) {
        val current = AlertSound.of(settings.alertSounds[kind.key])
        AlertDialog(
            onDismissRequest = { choosingSound = null },
            containerColor = c.surface,
            title = { Text("${kind.title}: sound", color = c.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertSound.entries.forEach { sound ->
                        Surface(
                            onClick = {
                                choosingSound = null
                                vm.setAlertSound(kind, sound)
                            },
                            color = if (sound == current) c.accentTint else c.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(sound.title, color = c.ink, modifier = Modifier.weight(1f))
                                if (sound == current) Icon(Ic.check, null, tint = c.accentText, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            choosingSound = null
                            openChannelSettings(context, Notifier.channelFor(context, kind, current))
                        },
                        color = c.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose any sound on your phone…", color = c.accentText, modifier = Modifier.padding(14.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingSound = null }) { Text("Close", color = c.ink2) } },
        )
    }
}
