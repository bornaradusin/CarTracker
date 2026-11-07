package io.github.bornaradusin.cartracker.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.data.Action
import io.github.bornaradusin.cartracker.data.AlertKind
import io.github.bornaradusin.cartracker.data.TrackerCommands
import io.github.bornaradusin.cartracker.data.TrackerKind
import io.github.bornaradusin.cartracker.data.UpdateRate
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.CarInfo
import io.github.bornaradusin.cartracker.data.PendingChange
import io.github.bornaradusin.cartracker.ui.components.rememberIdentityCheck
import io.github.bornaradusin.cartracker.ui.components.BigButton
import io.github.bornaradusin.cartracker.ui.components.ChevronRow
import io.github.bornaradusin.cartracker.ui.components.QuietButton
import io.github.bornaradusin.cartracker.ui.components.ScreenHeader
import io.github.bornaradusin.cartracker.ui.components.SectionCard
import io.github.bornaradusin.cartracker.ui.components.SettingRow
import io.github.bornaradusin.cartracker.ui.components.Toggle
import io.github.bornaradusin.cartracker.ui.components.ToggleRow
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors

@Composable
fun ControlsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val options by vm.commandOptions.collectAsStateWithLifecycle()
    val pendingChanges by vm.pending.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Action?>(null) }
    var rateDialog by remember { mutableStateOf(false) }
    val checkIdentity = rememberIdentityCheck(settings.fuelFingerprint)

    val info = car
    LaunchedEffect(info?.device?.id) { info?.device?.id?.let { vm.loadCommandOptions(it) } }

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
        ScreenHeader("Controls", info?.device?.name, onBack = onBack)
        if (info == null) {
            Text("No car selected.", color = c.muted)
            return@Column
        }
        val types = options[info.device.id]?.types.orEmpty()
        val micodus = info.kind == TrackerKind.MICODUS
        val pending = pendingChanges[info.device.id]
        val waitingHint = "Waiting for ${info.device.name} to confirm…"
        fun hintFor(key: String, normal: String) = if (pending?.key == key) waitingHint else normal

        // ---- immobiliser
        SectionCard {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("IMMOBILISER", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (info.immobilised) c.warnText else c.good))
                    Text(
                        if (pending?.key == PendingChange.FUEL) waitingHint else if (info.immobilised) "Fuel cut" else "Fuel on",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (info.immobilised) c.warnText else c.good,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigButton("Cut fuel", Modifier.weight(1f), warning = true, icon = Ic.lock) { confirm = Action.CUT_NOW }
                QuietButton("Restore fuel", Modifier.weight(1f), outlined = true, icon = Ic.unlock) {
                    checkIdentity("Restore fuel to ${info.device.name}") { vm.send(info, Action.RESTORE) }
                }
            }
            if (TrackerCommands.supports(info.kind, Action.CUT_SLOW, types)) {
                QuietButton("Cut fuel when slow (safer while driving)", Modifier.fillMaxWidth().padding(top = 10.dp)) { confirm = Action.CUT_SLOW }
            }
            Text(
                "Cutting fuel stops the engine. Use it when the car is stopped or moving slowly.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            ToggleRow(
                "Ask for fingerprint",
                "Before cutting or restoring fuel. Your phone's PIN works too.",
                checked = settings.fuelFingerprint,
                onChange = { vm.setFuelFingerprint(it) },
                divider = false,
            )
        }

        // ---- alarm
        val canArm = TrackerCommands.supports(info.kind, Action.ARM, types)
        if (canArm) {
            SectionCard("Alarm") {
                ToggleRow(
                    "Alarm",
                    hintFor(PendingChange.ALARM, if (info.armed) "Armed: alerts you if the car is moved or shaken" else "Alerts you if the car is moved or shaken"),
                    checked = info.armed,
                    onChange = { on -> vm.send(info, if (on) Action.ARM else Action.DISARM) },
                    divider = micodus,
                    enabled = pending == null,
                )
                if (micodus) {
                    ToggleRow(
                        "Arm automatically",
                        hintFor(PendingChange.AUTO_ARM, "1 minute after the ignition goes off"),
                        checked = info.prefs.autoArm,
                        onChange = { on -> vm.send(info, if (on) Action.AUTO_ARM_ON else Action.AUTO_ARM_OFF) },
                        divider = false,
                        enabled = pending == null,
                    )
                }
            }
        }

        // ---- alerts
        SectionCard("Tracker alerts") {
            val speedOn = AlertKind.SPEEDING.key in settings.alertKinds && info.prefs.speedLimit > 0
            SettingRow("Speeding", if (speedOn) "Alerts when the car goes over ${info.prefs.speedLimit} km/h" else "Alerts when the car goes over a limit you set", divider = !speedOn) {
                Toggle(speedOn, { on ->
                    if (on) vm.setSpeedLimit(info, info.prefs.speedLimit.takeIf { it > 0 } ?: 120)
                    else {
                        vm.setSpeedLimit(info, 0)
                        vm.setAlertFilter(AlertKind.SPEEDING, false)
                    }
                }, "Speeding")
            }
            if (speedOn) {
                SpeedStepper(info.prefs.speedLimit) { vm.setSpeedLimit(info, it) }
            }
            ToggleRow(
                AlertKind.VIBRATION.title,
                // MiCODUS trackers only watch for vibration while the alarm is armed and the car is still
                hintFor(AlertKind.VIBRATION.key, if (micodus) "Bumps, knocks or towing while the alarm is on" else AlertKind.VIBRATION.hint),
                checked = AlertKind.VIBRATION.key in settings.alertKinds,
                onChange = { vm.setAlertKind(AlertKind.VIBRATION, it, info) },
                enabled = !micodus || pending == null,
            )
            ToggleRow(
                AlertKind.IGNITION.title, AlertKind.IGNITION.hint,
                checked = AlertKind.IGNITION.key in settings.alertKinds,
                onChange = { vm.setAlertKind(AlertKind.IGNITION, it, info) },
            )
            val lowOn = AlertKind.LOW_BATTERY.key in settings.alertKinds
            val lowVolts = info.prefs.trackerLowVolts
            ToggleRow(
                AlertKind.LOW_BATTERY.title,
                hintFor(AlertKind.LOW_BATTERY.key, if (micodus) "Below ${TrackerCommands.volts(lowVolts)} V" else AlertKind.LOW_BATTERY.hint),
                checked = lowOn,
                onChange = { vm.setAlertKind(AlertKind.LOW_BATTERY, it, info) },
                divider = !(micodus && lowOn),
                enabled = !micodus || pending == null,
            )
            // The tracker's own alert takes any voltage; the app sends the new one once the taps settle
            if (micodus && lowOn) {
                Stepper(
                    "Alert below",
                    "${TrackerCommands.volts(lowVolts)} V",
                    lower = "Lower by 0.1 V",
                    raise = "Raise by 0.1 V",
                    onLower = { vm.setTrackerLowVolts(info, lowVolts - 0.1) },
                    onRaise = { vm.setTrackerLowVolts(info, lowVolts + 0.1) },
                    divider = true,
                )
            }
            ToggleRow(
                AlertKind.UNPLUGGED.title, hintFor(AlertKind.UNPLUGGED.key, AlertKind.UNPLUGGED.hint),
                checked = AlertKind.UNPLUGGED.key in settings.alertKinds,
                onChange = { vm.setAlertKind(AlertKind.UNPLUGGED, it, info) },
                divider = false,
                enabled = !micodus || pending == null,
            )
        }

        // ---- tracker: the commands chosen for the main screen; the rest are under Advanced commands
        val saved = options[info.device.id]?.saved.orEmpty()
        val canCustom = micodus || "custom" in types
        val commands = trackerCommands(vm, info, types, saved, canCustom, onRate = { rateDialog = true }, onConfirm = { confirm = it })
        val byKey = commands.associateBy { it.key }
        val shown = info.prefs.shownCommands.mapNotNull { key ->
            byKey[key] ?: key.removePrefix("text:").takeIf { key.startsWith("text:") && canCustom }?.let { text ->
                QuickCommand(key, text, "Custom command") { vm.customCommand(info, text) }
            }
        }
        val editable = settings.advancedCommands
        if (shown.isNotEmpty()) {
            SectionCard("Tracker") {
                shown.forEachIndexed { i, cmd ->
                    CommandRow(cmd, divider = i < shown.lastIndex, main = true, editable = editable) { vm.setMainCommand(info, cmd.key, false) }
                }
            }
        }

        if (editable) {
            val shownKeys = shown.map { it.key }.toSet()
            AdvancedCommands(vm, info, commands.filter { it.key !in shownKeys }, canCustom)
        }
    }

    val target = info
    val action = confirm
    if (target != null && action != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = c.surface,
            title = {
                Text(
                    when (action) {
                        Action.RESTART -> "Restart ${target.device.name}'s tracker?"
                        else -> "Cut fuel to ${target.device.name}?"
                    },
                    color = c.ink,
                )
            },
            text = {
                Text(
                    when (action) {
                        Action.RESTART -> "It goes offline for a minute or two while it restarts."
                        Action.CUT_SLOW -> "The engine is cut once the car slows down, and won't restart until you restore fuel."
                        else -> "The engine stops and won't restart until you restore fuel. If the car is moving, use \"Cut fuel when slow\" instead."
                    },
                    color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (action == Action.RESTART) vm.send(target, action)
                    else checkIdentity("Cut fuel to ${target.device.name}") { vm.send(target, action) }
                }) {
                    Text(
                        when (action) {
                            Action.RESTART -> "Restart"
                            Action.CUT_SLOW -> "Cut when slow"
                            else -> "Cut fuel now"
                        },
                        color = if (action == Action.RESTART) c.accentText else c.warnText,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = c.ink2) } },
        )
    }

    if (rateDialog && target != null) {
        AlertDialog(
            onDismissRequest = { rateDialog = false },
            containerColor = c.surface,
            title = { Text("Update rate", color = c.ink) },
            text = {
                Column {
                    UpdateRate.choices.forEach { rate ->
                        Surface(
                            onClick = {
                                rateDialog = false
                                vm.setUpdateRate(target, rate)
                            },
                            color = c.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(rate.label, color = c.ink, modifier = Modifier.padding(vertical = 14.dp))
                        }
                    }
                    Text("Faster updates use more mobile data.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            },
            confirmButton = { TextButton(onClick = { rateDialog = false }) { Text("Close", color = c.ink2) } },
        )
    }
}

@Composable
private fun SpeedStepper(limit: Int, onChange: (Int) -> Unit) {
    Stepper(
        "Alert above",
        "$limit km/h",
        lower = "Lower by 10 km/h",
        raise = "Raise by 10 km/h",
        onLower = { onChange((limit - 10).coerceAtLeast(30)) },
        onRaise = { onChange((limit + 10).coerceAtMost(200)) },
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    lower: String,
    raise: String,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    divider: Boolean = false,
) {
    val c = LocalAppColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StepButton(lower, Ic.minus, onLower)
                Text(value, style = MaterialTheme.typography.titleMedium, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 86.dp))
                StepButton(raise, Ic.plus, onRaise)
            }
        }
        if (divider) HorizontalDivider(color = c.divider, thickness = 1.dp)
    }
}

@Composable
private fun StepButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(11.dp), color = c.surface, contentColor = c.ink, shadowElevation = 1.dp, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(20.dp)) }
    }
}

/** A command that can sit with the main controls or under Advanced commands. */
private data class QuickCommand(
    val key: String,
    val title: String,
    val hint: String?,
    /** Asked before sending, for commands that are hard to undo. */
    val warning: String? = null,
    val run: () -> Unit,
)

/** Every command this tracker can be sent from the Tracker card: the app's own, the server's saved ones and Traccar's. */
private fun trackerCommands(
    vm: AppViewModel,
    info: CarInfo,
    types: Set<String>,
    saved: List<io.github.bornaradusin.cartracker.data.SavedCommand>,
    canCustom: Boolean,
    onRate: () -> Unit,
    onConfirm: (Action) -> Unit,
): List<QuickCommand> = buildList {
    val micodus = info.kind == TrackerKind.MICODUS
    if (TrackerCommands.supports(info.kind, Action.LOCATE, types)) {
        // MiCODUS answers WHERE# with its position in words; other trackers send a new position
        val hint = if (micodus) "Asks the tracker where it is right now" else "Asks the tracker for a fresh position"
        add(QuickCommand("locate", "Locate now", hint) { vm.send(info, Action.LOCATE) })
    }
    if (info.kind == TrackerKind.MICODUS || "positionPeriodic" in types) add(QuickCommand("rate", "Update rate", "How often it reports", run = onRate))
    if (TrackerCommands.supports(info.kind, Action.RESTART, types)) add(QuickCommand("restart", "Restart tracker", null) { onConfirm(Action.RESTART) })
    if (micodus) {
        add(QuickCommand("sync", "Send all settings to the tracker", "Makes the tracker match every switch here, one command at a time") { vm.sendAllSettings(info) })
        add(QuickCommand("status", "Tracker status", "Battery, signal, GPS, ignition, fuel and alarm, in the tracker's words") { vm.send(info, Action.CHECK_STATUS) })
        add(QuickCommand("alarms", "Tracker alarm settings", "What each of the tracker's alarms is set to") { vm.send(info, Action.CHECK_ALARMS) })
        add(QuickCommand("settings", "Tracker settings", "Server, update rate and the rest of its setup") { vm.send(info, Action.CHECK_SETTINGS) })
    }
    saved.forEach { cmd ->
        val name = cmd.description?.takeIf { it.isNotBlank() } ?: TrackerCommands.typeName(cmd.type)
        add(
            QuickCommand("saved:${cmd.id}", name, "Saved on the server") {
                vm.sendAdvanced(info, name, kotlinx.serialization.json.buildJsonObject {
                    put("id", kotlinx.serialization.json.JsonPrimitive(cmd.id))
                    put("deviceId", kotlinx.serialization.json.JsonPrimitive(info.device.id))
                    put("type", kotlinx.serialization.json.JsonPrimitive(cmd.type))
                    put("textChannel", kotlinx.serialization.json.JsonPrimitive(false))
                    put("attributes", cmd.attributes)
                })
            },
        )
    }
    TrackerCommands.listableTypes(info.kind, types).forEach { type ->
        val name = TrackerCommands.typeName(type)
        add(
            QuickCommand("type:$type", name, "Traccar command", TrackerCommands.typeWarning(type)) {
                vm.sendAdvanced(info, name, TrackerCommands.typed(info.device.id, type))
            },
        )
    }
}

/**
 * A command row. With [editable], the star moves it: coloured on the main screen (tap to move it under
 * Advanced commands), grey under Advanced (tap to show it with the main controls).
 */
@Composable
private fun CommandRow(cmd: QuickCommand, divider: Boolean, main: Boolean, editable: Boolean, onMove: () -> Unit) {
    val c = LocalAppColors.current
    var asking by remember { mutableStateOf(false) }
    SettingRow(cmd.title, cmd.hint, divider, onClick = { if (cmd.warning != null) asking = true else cmd.run() }) {
        if (editable) {
            IconButton(onClick = onMove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Ic.star,
                    if (main) "Move ${cmd.title} to Advanced commands" else "Show ${cmd.title} with the main controls",
                    tint = if (main) c.accentText else c.line,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Icon(Ic.chevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
    if (asking && cmd.warning != null) {
        AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = c.surface,
            title = { Text("${cmd.title}?", color = c.ink) },
            text = { Text(cmd.warning, color = c.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    cmd.run()
                }) { Text(cmd.title, color = c.warnText, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel", color = c.ink2) } },
        )
    }
}

/** The commands not on the main screen, and a custom box. A star moves a command to the main screen. */
@Composable
private fun AdvancedCommands(vm: AppViewModel, info: CarInfo, commands: List<QuickCommand>, canCustom: Boolean) {
    val c = LocalAppColors.current
    var text by remember { mutableStateOf("") }

    SectionCard("Advanced commands") {
        Text(
            "Tap the star on any command to show it with the main controls, or on one there to move it back here.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        commands.forEachIndexed { i, cmd ->
            CommandRow(cmd, divider = i < commands.lastIndex || canCustom, main = false, editable = true) { vm.setMainCommand(info, cmd.key, true) }
        }
        if (canCustom) {
            Text(
                if (info.kind == TrackerKind.MICODUS) "Custom command (as you'd text it, e.g. STATUS#)" else "Custom command",
                style = MaterialTheme.typography.labelMedium,
                color = c.muted,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("STATUS#") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.customCommand(info, text) }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accentOutline, unfocusedBorderColor = c.line, cursorColor = c.accentOutline),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        vm.setMainCommand(info, "text:" + text.trim(), true)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Icon(Ic.star, "Keep as a button with the main controls", tint = if (text.isNotBlank()) c.accentText else c.line, modifier = Modifier.size(22.dp))
                }
                BigButton("Send", enabled = text.isNotBlank()) { vm.customCommand(info, text) }
            }
            Text(
                "The tracker's answer shows in the Alerts list. The star keeps the command as a button with the main controls.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
