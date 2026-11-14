package io.github.bornaradusin.cartracker.ui.service

import androidx.compose.foundation.Canvas
import io.github.bornaradusin.cartracker.data.ServiceUrgency
import io.github.bornaradusin.cartracker.data.ServiceStatus
import io.github.bornaradusin.cartracker.data.ServiceItems
import io.github.bornaradusin.cartracker.data.ServiceItem
import java.time.ZoneOffset
import java.time.Instant
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.data.LubeReminderEdit
import io.github.bornaradusin.cartracker.data.LubeRecordEdit
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.data.LubeConfig
import io.github.bornaradusin.cartracker.data.LubeData
import io.github.bornaradusin.cartracker.data.LubeException
import io.github.bornaradusin.cartracker.data.LubeKind
import io.github.bornaradusin.cartracker.data.LubeLogger
import io.github.bornaradusin.cartracker.data.LubeRecord
import io.github.bornaradusin.cartracker.data.LubeReminder
import io.github.bornaradusin.cartracker.data.LubeUrgency
import io.github.bornaradusin.cartracker.data.LubeVehicle
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.components.BigButton
import io.github.bornaradusin.cartracker.ui.components.ChevronRow
import io.github.bornaradusin.cartracker.ui.components.ScreenHeader
import io.github.bornaradusin.cartracker.ui.components.SectionCard
import io.github.bornaradusin.cartracker.ui.components.StatTile
import io.github.bornaradusin.cartracker.ui.components.ToggleRow
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * LubeLogger inside the app: the car's reminders, what it has cost, its fuel and every record,
 * read from your own LubeLogger server.
 */
@Composable
fun ServiceScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val lube = LocalContext.current.container.lube
    val config by lube.config.collectAsStateWithLifecycle()
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val info = car
    val deviceId = info?.device?.id ?: 0L
    val vehicleId = config.vehicles[deviceId]

    var data by remember { mutableStateOf<LubeData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var addingRecord by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<LubeRecord?>(null) }
    var addingReminder by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<LubeReminder?>(null) }
    val serviceItems = LocalContext.current.container.serviceItems
    val allItems by serviceItems.items.collectAsStateWithLifecycle()
    var addingItem by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ServiceStatus?>(null) }
    /** An item being marked done: the record to add starts with its name. */
    var doneItem by remember { mutableStateOf<String?>(null) }

    // Another car or server starts empty; after a change the old list stays up until the new one is in
    LaunchedEffect(config.server, config.apiKey, config.user, vehicleId) { data = null }
    LaunchedEffect(config.server, config.apiKey, config.user, vehicleId, reload) {
        error = null
        if (!config.connected || vehicleId == null) return@LaunchedEffect
        loading = true
        try {
            data = lube.load(vehicleId, config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LubeException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't read LubeLogger."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Service and costs", info?.device?.name)
        when {
            !config.connected -> ConnectCard(lube)
            info == null -> Text("No car selected.", color = c.muted)
            vehicleId == null -> VehiclePicker(lube, config, deviceId, info.device.name)
            else -> {
                val loaded = data
                when {
                    loaded != null -> {
                        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.warnText) }
                        BigButton("Add a record", modifier = Modifier.fillMaxWidth(), icon = Ic.plus) { addingRecord = true }
                        val carKm = ServiceItems.odometer(loaded.odometer, info.position?.totalDistance?.let { it / 1000.0 })
                        val statuses = remember(allItems, deviceId, loaded, carKm) {
                            ServiceItems.sorted(serviceItems.forCar(allItems, deviceId).map { ServiceItems.status(it, loaded.records, carKm) })
                        }
                        ItemsCard(statuses, carKm, fromLube = carKm != null && carKm == loaded.odometer, onAdd = { addingItem = true }, onEdit = { editingItem = it })
                        Reminders(loaded.reminders, onAdd = { addingReminder = true }, onEdit = { editingReminder = it })
                        Costs(loaded.records)
                        Fuel(loaded.records.filter { it.kind == LubeKind.FUEL })
                        Records(loaded.records, onEdit = { editingRecord = it })
                    }
                    error != null -> SectionCard {
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
                        BigButton("Try again", modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) { reload++ }
                    }
                    loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accentOutline)
                    }
                }
                SectionCard("LubeLogger") {
                    ChevronRow("Refresh", "Read everything again") { reload++ }
                    ChevronRow("Vehicle", "Pick a different LubeLogger vehicle for ${info.device.name}") {
                        lube.save(config.copy(vehicles = config.vehicles - deviceId))
                    }
                    ToggleRow(
                        "Tell me about reminders",
                        "A notification when one becomes urgent or past due",
                        checked = config.notify,
                        onChange = { on -> lube.save(config.copy(notify = on)) },
                    )
                    ChevronRow("Disconnect", config.server.removePrefix("https://").removePrefix("http://"), divider = false) { lube.disconnect() }
                }
            }
        }
    }

    if (vehicleId != null && (addingRecord || editingRecord != null || doneItem != null)) {
        RecordDialog(
            lube = lube,
            vehicleId = vehicleId,
            existing = editingRecord,
            done = doneItem,
            lastOdometer = data?.odometer,
            onSaved = { reload++ },
            onDismiss = {
                addingRecord = false
                editingRecord = null
                doneItem = null
            },
        )
    }
    if (addingItem || editingItem != null) {
        ItemDialog(
            status = editingItem,
            onSave = { serviceItems.save(deviceId, it) },
            onRemove = { serviceItems.remove(deviceId, it) },
            onDone = { doneItem = it },
            onDismiss = {
                addingItem = false
                editingItem = null
            },
        )
    }
    if (vehicleId != null && (addingReminder || editingReminder != null)) {
        ReminderDialog(
            lube = lube,
            vehicleId = vehicleId,
            existing = editingReminder,
            onSaved = { reload++ },
            onDismiss = {
                addingReminder = false
                editingReminder = null
            },
        )
    }
}

// ---------- connecting ----------

@Composable
private fun ConnectCard(lube: LubeLogger) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var server by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accentOutline,
        unfocusedBorderColor = c.line,
        focusedLabelColor = c.accentText,
        cursorColor = c.accentOutline,
        focusedContainerColor = c.surface,
        unfocusedContainerColor = c.surface,
    )

    SectionCard("Connect LubeLogger") {
        Text(
            "See your LubeLogger reminders, costs, fuel and records here, and add to them. Give the address of your LubeLogger, and either an API key or your username and password. To add and change records an API key needs the Editor role; deleting needs Manager.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("LubeLogger address") },
                placeholder = { Text("lubelogger.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Or, without an API key:", style = MaterialTheme.typography.bodySmall, color = c.muted)
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Username") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.warnText) }
            BigButton(
                if (working) "Connecting…" else "Connect",
                modifier = Modifier.fillMaxWidth(),
                enabled = !working && server.isNotBlank() && (apiKey.isNotBlank() || user.isNotBlank()),
            ) {
                val attempt = LubeConfig(server = server.trim(), apiKey = apiKey.trim(), user = user.trim(), password = password)
                working = true
                problem = null
                scope.launch {
                    try {
                        lube.vehicles(attempt)
                        lube.save(attempt)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: LubeException) {
                        problem = e.message
                    } catch (e: Exception) {
                        problem = "Couldn't connect."
                    } finally {
                        working = false
                    }
                }
            }
        }
    }
}

@Composable
private fun VehiclePicker(lube: LubeLogger, config: LubeConfig, deviceId: Long, carName: String) {
    val c = LocalAppColors.current
    var vehicles by remember { mutableStateOf<List<LubeVehicle>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(config.server, config.apiKey, config.user) {
        try {
            vehicles = lube.vehicles(config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = (e as? LubeException)?.message ?: "Couldn't read LubeLogger."
        }
    }
    SectionCard("Which vehicle is $carName?") {
        val list = vehicles
        when {
            problem != null -> Text(problem.orEmpty(), color = c.warnText, modifier = Modifier.padding(vertical = 12.dp))
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accentOutline) }
            list.isEmpty() -> Text("This LubeLogger account has no vehicles.", color = c.muted, modifier = Modifier.padding(vertical = 12.dp))
            else -> list.forEachIndexed { index, vehicle ->
                ChevronRow(vehicle.name, divider = index < list.lastIndex) {
                    lube.save(config.copy(vehicles = config.vehicles + (deviceId to vehicle.id)))
                }
            }
        }
        ChevronRow("Disconnect", config.server.removePrefix("https://").removePrefix("http://"), divider = false) { lube.disconnect() }
    }
}

// ---------- what LubeLogger holds ----------

@Composable
private fun Reminders(reminders: List<LubeReminder>, onAdd: () -> Unit, onEdit: (LubeReminder) -> Unit) {
    val c = LocalAppColors.current
    SectionCard("Reminders") {
        if (reminders.isEmpty()) {
            Text("No reminders set in LubeLogger.", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(vertical = 12.dp))
        }
        reminders.forEach { r ->
            val dot = when (r.urgency) {
                LubeUrgency.PAST_DUE -> Color(0xFFD64545)
                LubeUrgency.VERY_URGENT -> c.warn
                LubeUrgency.URGENT -> Color(0xFFE0B13A)
                LubeUrgency.NOT_URGENT -> c.good
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onEdit(r) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.description.ifBlank { "Reminder" }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    Text(
                        listOfNotNull(r.urgency.label, LubeLogger.dueText(r), dueIn(r.dueDate)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
            }
            HorizontalDivider(color = c.divider, thickness = 1.dp)
        }
        ChevronRow("Add a reminder", divider = false, onClick = onAdd)
    }
}

private fun dueIn(date: LocalDate?): String? {
    val days = date?.let { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), it) } ?: return null
    return when {
        days < -1 -> "${-days} days ago"
        days == -1L -> "yesterday"
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        else -> "in $days days"
    }
}

@Composable
private fun Costs(records: List<LubeRecord>) {
    val c = LocalAppColors.current
    var period by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val from = when (period) {
        0 -> today.minusMonths(12).plusDays(1)
        1 -> today.withDayOfYear(1)
        else -> null
    }
    val shown = remember(records, period) { records.filter { from == null || (it.date != null && !it.date.isBefore(from)) } }
    val total = shown.sumOf { it.cost }

    SectionCard("Costs") {
        Chips(listOf("12 months", "This year", "All time"), period) { period = it }
        Text(money(total), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp), color = c.ink, modifier = Modifier.padding(top = 12.dp))
        Text(
            if (shown.isEmpty()) "Nothing recorded in this time" else "${shown.size} records",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
        )

        // ---- by type
        val byKind = LubeKind.entries.map { kind -> kind to shown.filter { it.kind == kind }.sumOf { it.cost } }.filter { it.second > 0 }
        val most = byKind.maxOfOrNull { it.second } ?: 0.0
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            byKind.forEach { (kind, sum) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(kind.label, style = MaterialTheme.typography.bodyMedium, color = c.ink2, modifier = Modifier.weight(0.9f))
                    Box(Modifier.weight(2f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                        Box(Modifier.fillMaxWidth((sum / most).toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(kindColor(kind)))
                    }
                    Text(money(sum), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.weight(1.1f), maxLines = 1)
                }
            }
        }

        // ---- month by month
        val months = remember(records, period) {
            val last = YearMonth.from(today)
            val first = when (period) {
                0 -> last.minusMonths(11)
                1 -> YearMonth.of(today.year, 1)
                else -> records.mapNotNull { it.date }.minOrNull()?.let { YearMonth.from(it) }?.coerceAtLeast(last.minusMonths(35)) ?: last
            }
            generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
        }
        val perMonth = remember(records, months) {
            months.map { month -> LubeKind.entries.map { kind -> records.filter { it.kind == kind && it.date != null && YearMonth.from(it.date) == month }.sumOf { r -> r.cost } } }
        }
        if (perMonth.any { it.sum() > 0 }) {
            Text("Month by month", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.muted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            val top = perMonth.maxOf { it.sum() }
            val gridColor = c.line
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val slot = size.width / perMonth.size
                val bar = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                drawLine(gridColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
                perMonth.forEachIndexed { index, sums ->
                    var y = size.height - 1f
                    sums.forEachIndexed { k, sum ->
                        if (sum > 0) {
                            val h = (sum / top).toFloat() * (size.height - 8.dp.toPx())
                            drawRoundRect(
                                kindColor(LubeKind.entries[k]),
                                topLeft = Offset(index * slot + (slot - bar) / 2f, y - h),
                                size = Size(bar, h),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                            y -= h
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val step = if (months.size > 18) 6 else if (months.size > 8) 3 else 1
                months.forEachIndexed { index, month ->
                    Text(
                        if (index % step == 0) month.format(monthLabel) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.muted,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text("Tallest month: ${money(top)}", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        } else {
            Box(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Fuel(fills: List<LubeRecord>) {
    if (fills.isEmpty()) return
    val c = LocalAppColors.current
    val year = remember(fills) { fills.filter { it.date != null && !it.date.isBefore(LocalDate.now().minusMonths(12)) } }
    val amount = year.sumOf { it.fuel }
    val cost = year.sumOf { it.cost }
    val economy = year.map { it.economy }.filter { it > 0 }
    SectionCard("Fuel, last 12 months") {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Fill-ups", year.size.toString(), Modifier.weight(1f))
            StatTile("Fuel", plain(amount), Modifier.weight(1f))
            StatTile("Per unit", if (amount > 0) money(cost / amount, 3) else "–", Modifier.weight(1f))
            StatTile("Economy", if (economy.isNotEmpty()) plain(economy.average()) else "–", Modifier.weight(1f))
        }
        // Oldest first, as far back as there is, the last 30 fills at most
        val line = remember(fills) { fills.filter { it.economy > 0 && it.date != null }.sortedBy { it.date }.takeLast(30) }
        if (line.size >= 2) {
            Text("Fuel economy, fill by fill", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.muted, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            val low = line.minOf { it.economy }
            val high = line.maxOf { it.economy }
            val lineColor = c.accentOutline
            val gridColor = c.line
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val pad = 6.dp.toPx()
                val range = (high - low).takeIf { it > 0 } ?: 1.0
                val path = Path()
                line.forEachIndexed { index, r ->
                    val x = pad + (size.width - 2 * pad) * index / (line.size - 1)
                    val y = pad + (size.height - 2 * pad) * (1f - ((r.economy - low) / range).toFloat())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawLine(gridColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
                drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(line.first().date?.format(monthYear).orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.muted)
                Text("${plain(low)} to ${plain(high)}", style = MaterialTheme.typography.labelSmall, color = c.muted)
                Text(line.last().date?.format(monthYear).orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
        }
        Text(
            "Amounts and economy are in the units you use in LubeLogger.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun Records(records: List<LubeRecord>, onEdit: (LubeRecord) -> Unit) {
    val c = LocalAppColors.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var limit by rememberSaveable { mutableIntStateOf(20) }
    val kinds = LubeKind.entries
    val shown = remember(records, filter) { if (filter == 0) records else records.filter { it.kind == kinds[filter - 1] } }
    SectionCard("Records") {
        Chips(listOf("All") + kinds.map { it.label }, filter) {
            filter = it
            limit = 20
        }
        if (shown.isEmpty()) {
            Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(vertical = 12.dp))
            return@SectionCard
        }
        shown.take(limit).forEachIndexed { index, r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onEdit(r) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(kindColor(r.kind)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        r.description.ifBlank { if (r.kind == LubeKind.FUEL) "Fuel" + (if (r.fuel > 0) ", ${plain(r.fuel)}" else "") else r.kind.label },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            r.date?.format(dayLabel),
                            r.kind.label.takeIf { filter == 0 },
                            r.odometer?.let { String.format(Locale.US, "%,d", it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
                Text(money(r.cost), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            }
            if (index < minOf(limit, shown.size) - 1) HorizontalDivider(color = c.divider, thickness = 1.dp)
        }
        if (shown.size > limit) {
            ChevronRow("Show more", "${shown.size - limit} older", divider = false) { limit += 30 }
        }
    }
}

// ---------- service items ----------

/** What to service and how far off each is, each with a health bar. */
@Composable
private fun ItemsCard(statuses: List<ServiceStatus>, carKm: Int?, fromLube: Boolean, onAdd: () -> Unit, onEdit: (ServiceStatus) -> Unit) {
    val c = LocalAppColors.current
    SectionCard("Service items") {
        Text(
            when {
                carKm == null -> "No odometer reading yet: add a record with one, and items count down from it."
                fromLube -> "Counting from ${thousands(carKm)} km, the newest reading in LubeLogger."
                else -> "Counting from ${thousands(carKm)} km, the app's own count since the newest reading in LubeLogger."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        statuses.forEach { st ->
            val overdue = st.urgency == ServiceUrgency.OVERDUE
            Column(Modifier.fillMaxWidth().clickable { onEdit(st) }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        st.item.name.ifBlank { "Item" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (st.due) {
                        Text(
                            if (overdue) "OVERDUE" else "DUE SOON",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (overdue) Color.White else RED,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (overdue) RED else RED.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Box(Modifier.weight(1f))
                    Text(
                        leftText(st),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (st.due) RED else if (st.urgency == ServiceUrgency.UNSET) c.muted else c.ink,
                        maxLines = 1,
                    )
                }
                HealthBar(st)
                Text(
                    listOf(intervalText(st.item), nextText(st)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = c.divider, thickness = 1.dp)
        }
        ChevronRow("Add an item", divider = false, onClick = onAdd)
    }
}

/**
 * What's left of an item's interval as a game's health bar: a row of squares, all lit just after it's
 * done, going out one by one down to none when it's due. Green, amber under half, red once it's due
 * soon; overdue, the empty squares are red too.
 */
@Composable
private fun HealthBar(st: ServiceStatus) {
    val c = LocalAppColors.current
    val unset = st.urgency == ServiceUrgency.UNSET
    val left = if (unset) 0f else (1f - st.used).coerceIn(0f, 1f)
    val lit = if (unset) 0 else kotlin.math.ceil(left * SQUARES).toInt()
    val on = when {
        st.due -> RED
        left > 0.5f -> c.good
        else -> Color(0xFFFFB020)
    }
    val off = if (st.urgency == ServiceUrgency.OVERDUE) RED.copy(alpha = 0.35f) else c.surface2
    Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(SQUARES) { i ->
            Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(2.dp)).background(if (i < lit) on else off))
        }
    }
}

private const val SQUARES = 20
private val RED = Color(0xFFD64545)

private fun thousands(value: Int): String = String.format(Locale.US, "%,d", value)

private fun intervalText(item: ServiceItem): String = listOfNotNull(
    item.everyKm?.let { "every ${thousands(it)} km" },
    item.everyMonths?.let { if (it % 12 == 0) (if (it == 12) "every year" else "every ${it / 12} years") else "every $it months" },
).joinToString(" or ").replaceFirstChar { it.uppercase() }

private fun leftText(st: ServiceStatus): String {
    if (st.urgency == ServiceUrgency.UNSET) return "Not set yet"
    val kmLeft = st.kmLeft
    val daysLeft = st.daysLeft
    return if (st.byKm && kmLeft != null) {
        if (kmLeft >= 0) "${thousands(kmLeft)} km left" else "${thousands(-kmLeft)} km overdue"
    } else if (daysLeft != null) {
        val days = kotlin.math.abs(daysLeft)
        val span = if (days < 62) (if (days == 1L) "1 day" else "$days days") else "${days / 30} months"
        if (daysLeft >= 0) "$span left" else "$span overdue"
    } else {
        "Not set yet"
    }
}

private fun nextText(st: ServiceStatus): String {
    if (st.urgency == ServiceUrgency.UNSET) return "tap to set when it was last done"
    val word = if (st.urgency == ServiceUrgency.OVERDUE) "was due" else "next"
    return listOfNotNull(
        st.nextKm?.let { "at ${thousands(it)} km" },
        st.nextDate?.let { "by ${it.format(monthYear)}" },
    ).joinToString(" or ").let { if (it.isBlank()) "" else "$word $it" }
}

@Composable
private fun ItemDialog(status: ServiceStatus?, onSave: (ServiceItem) -> Unit, onRemove: (String) -> Unit, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val item = status?.item
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var everyKm by remember { mutableStateOf(item?.everyKm?.toString().orEmpty()) }
    var everyMonths by remember { mutableStateOf(item?.everyMonths?.toString().orEmpty()) }
    var lastKm by remember { mutableStateOf(item?.lastKm?.toString().orEmpty()) }
    var lastDate by remember { mutableStateOf(item?.lastDay?.let(LocalDate::ofEpochDay)) }
    var problem by remember { mutableStateOf<String?>(null) }
    val colors = fieldColors()

    /** What's typed, as an item; null (with the reason shown) when it can't be one. */
    fun typed(): ServiceItem? {
        val km = LubeLogger.parseNumber(everyKm)?.let { Math.round(it).toInt() }?.takeIf { it > 0 }
        val months = LubeLogger.parseNumber(everyMonths)?.let { Math.round(it).toInt() }?.takeIf { it > 0 }
        problem = when {
            name.isBlank() -> "Give it a name."
            km == null && months == null -> "Say how often: kilometres, months or both."
            else -> null
        }
        if (problem != null) return null
        return ServiceItem(
            id = item?.id ?: ServiceItems.newId(),
            name = name.trim(),
            everyKm = km,
            everyMonths = months,
            lastKm = LubeLogger.parseNumber(lastKm)?.let { Math.round(it).toInt() }?.takeIf { it > 0 },
            lastDay = lastDate?.toEpochDay(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(if (item == null) "Add an item" else item.name.ifBlank { "Item" }, color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = everyKm,
                        onValueChange = { everyKm = it },
                        label = { Text("Every km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = everyMonths,
                        onValueChange = { everyMonths = it },
                        label = { Text("Every months") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    if (status?.fromRecord == true) {
                        "Last done ${status.lastDate?.format(dayLabel).orEmpty()}" + (status.lastKm?.let { " at ${thousands(it)} km" } ?: "") +
                            ", from the LubeLogger record with this name in it. The app uses the newest such record, or what you type below if that's later."
                    } else {
                        "When it was last done. A LubeLogger service, repair or upgrade with this item's name in its description counts too, so name the item the way you write it there."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
                OutlinedTextField(
                    value = lastKm,
                    onValueChange = { lastKm = it },
                    label = { Text("Last done at km") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateRow("Last done on", lastDate, clearable = true) { lastDate = it }
                problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.warnText) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        typed()?.let {
                            onSave(it)
                            onDismiss()
                            onDone(it.name)
                        }
                    }) { Text("Done today…", color = c.accentText) }
                    if (item != null) {
                        TextButton(onClick = {
                            onRemove(item.id)
                            onDismiss()
                        }) { Text("Remove", color = c.warnText) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                typed()?.let {
                    onSave(it)
                    onDismiss()
                }
            }) { Text("Save", color = c.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

// ---------- adding and changing ----------

@Composable
private fun RecordDialog(lube: LubeLogger, vehicleId: Int, existing: LubeRecord?, done: String?, lastOdometer: Int?, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(existing?.kind ?: if (done != null) LubeKind.SERVICE else LubeKind.FUEL) }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var odometer by remember { mutableStateOf(existing?.odometer?.toString().orEmpty()) }
    var description by remember { mutableStateOf(existing?.description ?: done.orEmpty()) }
    var cost by remember { mutableStateOf(existing?.let { field(it.cost) }.orEmpty()) }
    var fuel by remember { mutableStateOf(existing?.takeIf { it.fuel > 0 }?.let { field(it.fuel) }.orEmpty()) }
    var full by remember { mutableStateOf(existing?.fillToFull ?: true) }
    var missed by remember { mutableStateOf(existing?.missedFuelUp ?: false) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var sureToDelete by remember { mutableStateOf(false) }
    val colors = fieldColors()

    fun submit(action: suspend () -> Unit) {
        working = true
        problem = null
        scope.launch {
            try {
                action()
                onSaved()
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = (e as? LubeException)?.message ?: "Couldn't save it."
            } finally {
                working = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = c.surface,
        title = { Text(if (done != null) "Done: $done" else if (existing == null) "Add a record" else "Change the record", color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LubeKind.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { k ->
                            val picked = kind == k
                            Surface(
                                onClick = { kind = k },
                                shape = RoundedCornerShape(12.dp),
                                color = if (picked) c.accent else c.surface2,
                                contentColor = if (picked) c.onAccent else c.ink,
                                modifier = Modifier.weight(1f).height(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(k.label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                            }
                        }
                    }
                }
                DateRow("Date", date, clearable = false) { it?.let { picked -> date = picked } }
                if (kind != LubeKind.TAX) {
                    OutlinedTextField(
                        value = odometer,
                        onValueChange = { odometer = it },
                        label = { Text("Odometer") },
                        placeholder = { lastOdometer?.let { Text("Last recorded: $it") } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == LubeKind.FUEL) {
                    OutlinedTextField(
                        value = fuel,
                        onValueChange = { fuel = it },
                        label = { Text("Fuel added") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("What was done") },
                        singleLine = true,
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == LubeKind.FUEL) {
                    ToggleRow("Filled to full", null, checked = full, onChange = { full = it })
                    ToggleRow("Missed a fill-up before this", "LubeLogger then leaves this one out of the economy", checked = missed, onChange = { missed = it }, divider = false)
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.warnText) }
                if (existing != null) {
                    TextButton(onClick = { if (sureToDelete) submit { lube.deleteRecord(existing) } else sureToDelete = true }, enabled = !working) {
                        Text(if (sureToDelete) "Tap again to delete it from LubeLogger" else "Delete", color = c.warnText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    val costValue = if (cost.isBlank()) 0.0 else LubeLogger.parseNumber(cost)
                    val odometerValue = LubeLogger.parseNumber(odometer)?.let { Math.round(it).toInt() }
                    val fuelValue = LubeLogger.parseNumber(fuel)
                    when {
                        costValue == null -> problem = "The cost isn't a number."
                        kind != LubeKind.TAX && odometerValue == null -> problem = "Fill in the odometer."
                        kind == LubeKind.FUEL && (fuelValue == null || fuelValue <= 0) -> problem = "Fill in how much fuel went in."
                        kind != LubeKind.FUEL && description.isBlank() -> problem = "Say what was done."
                        else -> submit {
                            lube.saveRecord(
                                vehicleId,
                                LubeRecordEdit(kind, date, odometerValue, description.trim(), costValue, notes.trim(), fuelValue ?: 0.0, full, missed),
                                existing,
                            )
                        }
                    }
                },
            ) { Text(if (working) "Saving…" else "Save", color = c.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel", color = c.ink2) } },
    )
}

@Composable
private fun ReminderDialog(lube: LubeLogger, vehicleId: Int, existing: LubeReminder?, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var date by remember { mutableStateOf(existing?.dueDate) }
    var odometer by remember { mutableStateOf(existing?.dueOdometer?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var sureToDelete by remember { mutableStateOf(false) }
    val colors = fieldColors()

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = c.surface,
        title = { Text(if (existing == null) "Add a reminder" else "Change the reminder", color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What for") },
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateRow("Due on", date, clearable = true) { date = it }
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it },
                    label = { Text("Or due at this odometer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Give a date, an odometer reading, or both: with both, whichever comes first counts.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.warnText) }
                if (existing != null) {
                    TextButton(
                        enabled = !working,
                        onClick = {
                            if (!sureToDelete) {
                                sureToDelete = true
                            } else {
                                working = true
                                problem = null
                                scope.launch {
                                    try {
                                        lube.deleteReminder(existing)
                                        onSaved()
                                        onDismiss()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        problem = (e as? LubeException)?.message ?: "Couldn't delete it."
                                    } finally {
                                        working = false
                                    }
                                }
                            }
                        },
                    ) { Text(if (sureToDelete) "Tap again to delete it from LubeLogger" else "Delete", color = c.warnText) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    val odometerValue = LubeLogger.parseNumber(odometer)?.let { Math.round(it).toInt() }?.takeIf { it > 0 }
                    val due = date
                    when {
                        description.isBlank() -> problem = "Say what the reminder is for."
                        due == null && odometerValue == null -> problem = "Give a date or an odometer reading."
                        else -> {
                            working = true
                            problem = null
                            scope.launch {
                                try {
                                    lube.saveReminder(vehicleId, LubeReminderEdit(description.trim(), due, odometerValue, notes.trim()), existing)
                                    onSaved()
                                    onDismiss()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    problem = (e as? LubeException)?.message ?: "Couldn't save it."
                                } finally {
                                    working = false
                                }
                            }
                        }
                    }
                },
            ) { Text(if (working) "Saving…" else "Save", color = c.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel", color = c.ink2) } },
    )
}

/** A date to tap and pick from a calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(label: String, date: LocalDate?, clearable: Boolean, onPick: (LocalDate?) -> Unit) {
    val c = LocalAppColors.current
    var picking by remember { mutableStateOf(false) }
    Surface(onClick = { picking = true }, shape = RoundedCornerShape(12.dp), color = c.surface2, contentColor = c.ink, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
            Text(date?.format(dayLabel) ?: "No date", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (clearable) onPick(null)
                    picking = false
                }) { Text(if (clearable) "No date" else "Cancel") }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun fieldColors() = LocalAppColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accentOutline,
        unfocusedBorderColor = c.line,
        focusedLabelColor = c.accentText,
        cursorColor = c.accentOutline,
        focusedContainerColor = c.surface,
        unfocusedContainerColor = c.surface,
    )
}

/** A number as it goes into a text field: no trailing zeros. */
private fun field(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

// ---------- pieces ----------

@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val picked = selected == index
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(11.dp),
                color = if (picked) c.surface else Color.Transparent,
                contentColor = if (picked) c.ink else c.muted,
                shadowElevation = if (picked) 1.dp else 0.dp,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private fun kindColor(kind: LubeKind): Color = when (kind) {
    LubeKind.SERVICE -> Color(0xFF3B82F6)
    LubeKind.REPAIR -> Color(0xFFE5484D)
    LubeKind.UPGRADE -> Color(0xFF8B5CF6)
    LubeKind.FUEL -> Color(0xFFF59E0B)
    LubeKind.TAX -> Color(0xFF10B981)
}

private val monthLabel = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
private val dayLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private val currencySymbol: String = runCatching { Currency.getInstance(Locale.getDefault()).symbol }.getOrDefault("")

private fun money(value: Double, decimals: Int = 2): String =
    (currencySymbol + String.format(Locale.US, "%,.${decimals}f", value)).trim()

private fun plain(value: Double): String =
    if (value >= 100) String.format(Locale.US, "%,.0f", value) else String.format(Locale.US, "%.1f", value)
