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
import androidx.compose.material3.Icon
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
import io.github.bornaradusin.cartracker.ui.PendingChange
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
                        if (info.immobilised) "Fuel cut" else "Fuel on",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (info.immobilised) c.warnText else c.good,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigButton("Cut fuel", Modifier.weight(1f), warning = true, icon = Ic.lock) { confirm = Action.CUT_NOW }
                QuietButton("Restore fuel", Modifier.weight(1f), outlined = true, icon = Ic.unlock) { vm.send(info, Action.RESTORE) }
            }
            if (TrackerCommands.supports(info.kind, Action.CUT_SLOW, types)) {
                QuietButton("Cut fuel when slow (safer while driving)", Modifier.fillMaxWidth().padding(top = 10.dp)) { confirm = Action.CUT_SLOW }
            }
            Text(
                "Cutting fuel stops the engine. Use it when the car is stopped or moving slowly.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
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
                AlertKind.VIBRATION.title, hintFor(AlertKind.VIBRATION.key, AlertKind.VIBRATION.hint),
                checked = AlertKind.VIBRATION.key in settings.alertKinds,
                onChange = { vm.setAlertKind(AlertKind.VIBRATION, it, info) },
                enabled = !micodus || pending == null,
            )
            ToggleRow(
                AlertKind.IGNITION.title, AlertKind.IGNITION.hint,
                checked = AlertKind.IGNITION.key in settings.alertKinds,
                onChange = { vm.setAlertKind(AlertKind.IGNITION, it, info) },
            )
            ToggleRow(
                AlertKind.LOW_BATTERY.title, hintFor(AlertKind.LOW_BATTERY.key, if (micodus) "Below 11.5 V" else AlertKind.LOW_BATTERY.hint),
                checked = AlertKind.LOW_BATTERY.key in settings.alertKinds,
                onChange = { vm.setAlertKind(AlertKind.LOW_BATTERY, it, info) },
                enabled = !micodus || pending == null,
            )
            ToggleRow(
                AlertKind.UNPLUGGED.title, hintFor(AlertKind.UNPLUGGED.key, AlertKind.UNPLUGGED.hint),
                checked = AlertKind.UNPLUGGED.key in settings.alertKinds,
                onChange = { vm.setAlertKind(AlertKind.UNPLUGGED, it, info) },
                divider = false,
                enabled = !micodus || pending == null,
            )
        }

        // ---- tracker
        val canLocate = TrackerCommands.supports(info.kind, Action.LOCATE, types)
        val canRate = micodus || "positionPeriodic" in types
        val canRestart = TrackerCommands.supports(info.kind, Action.RESTART, types)
        if (canLocate || canRate || canRestart) {
            SectionCard("Tracker") {
                if (canLocate) ChevronRow("Locate now", "Asks the tracker for a fresh position", divider = canRate || canRestart) { vm.send(info, Action.LOCATE) }
                if (canRate) ChevronRow("Update rate", "How often it reports", divider = canRestart) { rateDialog = true }
                if (canRestart) ChevronRow("Restart tracker", divider = false) { confirm = Action.RESTART }
            }
        }

        if (settings.advancedCommands) AdvancedCommands(vm, info, types)
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
                    vm.send(target, action)
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
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Alert above", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StepButton("Lower by 10 km/h", Ic.minus) { onChange((limit - 10).coerceAtLeast(30)) }
            Text("$limit km/h", style = MaterialTheme.typography.titleMedium, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 86.dp))
            StepButton("Raise by 10 km/h", Ic.plus) { onChange((limit + 10).coerceAtMost(200)) }
        }
    }
}

@Composable
private fun StepButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(11.dp), color = c.surface, contentColor = c.ink, shadowElevation = 1.dp, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(20.dp)) }
    }
}

/** Every command Traccar offers for this tracker, the server's saved commands, and a custom box. */
@Composable
private fun AdvancedCommands(vm: AppViewModel, info: CarInfo, types: Set<String>) {
    val c = LocalAppColors.current
    val options by vm.commandOptions.collectAsStateWithLifecycle()
    val saved = options[info.device.id]?.saved.orEmpty()
    var text by remember { mutableStateOf("") }

    SectionCard("Advanced commands") {
        if (saved.isNotEmpty()) {
            Text("Saved on the server", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            saved.forEach { cmd ->
                val name = cmd.description?.takeIf { it.isNotBlank() } ?: TrackerCommands.typeName(cmd.type)
                ChevronRow(name) {
                    vm.sendAdvanced(info, name, kotlinx.serialization.json.buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(cmd.id))
                        put("deviceId", kotlinx.serialization.json.JsonPrimitive(info.device.id))
                        put("type", kotlinx.serialization.json.JsonPrimitive(cmd.type))
                        put("textChannel", kotlinx.serialization.json.JsonPrimitive(false))
                        put("attributes", cmd.attributes)
                    })
                }
            }
        }
        val simple = types.filter { it != "custom" }.sorted()
        if (simple.isNotEmpty()) {
            Text("Supported by this tracker", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(top = 10.dp))
            simple.forEach { type ->
                ChevronRow(TrackerCommands.typeName(type)) { vm.sendAdvanced(info, TrackerCommands.typeName(type), TrackerCommands.typed(info.device.id, type)) }
            }
        }
        if ("custom" in types || info.kind == TrackerKind.MICODUS) {
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
                BigButton("Send", enabled = text.isNotBlank()) { vm.customCommand(info, text) }
            }
            Text(
                "Replies to custom commands appear in Traccar's own log, not here.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
