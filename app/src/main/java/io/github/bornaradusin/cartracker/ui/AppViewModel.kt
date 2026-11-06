package io.github.bornaradusin.cartracker.ui

import io.github.bornaradusin.cartracker.alerts.Notifier
import io.github.bornaradusin.cartracker.data.AlertSound
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.bornaradusin.cartracker.alerts.AlertMode
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.data.Action
import io.github.bornaradusin.cartracker.data.AlertKind
import io.github.bornaradusin.cartracker.data.ApiException
import io.github.bornaradusin.cartracker.data.AppSettings
import io.github.bornaradusin.cartracker.data.Device
import io.github.bornaradusin.cartracker.data.DevicePrefs
import io.github.bornaradusin.cartracker.data.Event
import io.github.bornaradusin.cartracker.data.EventText
import io.github.bornaradusin.cartracker.data.Geofence
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.data.SavedCommand
import io.github.bornaradusin.cartracker.data.ServerAddress
import io.github.bornaradusin.cartracker.data.TrackerCommands
import io.github.bornaradusin.cartracker.data.TrackerKind
import io.github.bornaradusin.cartracker.data.Trip
import io.github.bornaradusin.cartracker.data.UpdateRate
import io.github.bornaradusin.cartracker.data.str
import io.github.bornaradusin.cartracker.ui.map.CarImages
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.Palette
import androidx.compose.ui.graphics.Color
import io.github.bornaradusin.cartracker.util.Format
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CarStatus { MOVING, PARKED, OFFLINE }

/** What a tracker answered to a command. */
data class TrackerReply(val car: String, val command: String, val text: String)

/** A tracker setting sent to the car, applied in the app only once the tracker confirms it. */
data class PendingChange(val key: String, val on: Boolean) {
    companion object {
        const val ALARM = "alarm"
        const val AUTO_ARM = "auto_arm"
    }
}

/** Everything the screens show about one car. */
data class CarInfo(
    val device: Device,
    val position: Position?,
    val prefs: DevicePrefs,
    val kind: TrackerKind,
    /** One of [CarImages.all]. */
    val icon: String,
    /** The car's paint and the glow of its map pin. */
    val carColor: Color,
    val glowColor: Color,
    val address: String?,
    val status: CarStatus,
    val updated: Instant?,
    val ignition: Boolean?,
    val immobilised: Boolean,
    val armed: Boolean,
    /** True when the app worked out the alarm state rather than the tracker reporting it. */
    val armedInferred: Boolean,
    val todayMeters: Double?,
    val parkedSince: Instant?,
) {
    val speedKmh: Int get() = position?.speedKmh?.toInt() ?: 0
}

data class AlertItem(
    val event: Event,
    val deviceName: String,
    val title: String,
    val time: Instant?,
    val kind: AlertKind?,
    val warning: Boolean,
)

data class AlertsState(val loading: Boolean = false, val items: List<AlertItem> = emptyList(), val error: String? = null)

data class TripsState(
    val deviceId: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val loading: Boolean = false,
    val trips: List<Trip> = emptyList(),
    val route: List<Position> = emptyList(),
    val error: String? = null,
)

data class ReplayState(val loading: Boolean = true, val points: List<Position> = emptyList(), val error: String? = null)

sealed interface ServerCheck {
    data object Idle : ServerCheck
    data object Checking : ServerCheck
    data class Found(val version: String?) : ServerCheck
    data class Problem(val message: String) : ServerCheck
}

/** Commands this account may use for a car: Traccar's types for it and its saved commands. */
data class CommandOptions(val types: Set<String> = emptySet(), val saved: List<SavedCommand> = emptyList(), val loaded: Boolean = false)

@OptIn(FlowPreview::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val c = application.container
    private val api = c.api
    private val live = c.live

    val settings: StateFlow<AppSettings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings(token = "loading"))
    val devices: StateFlow<List<Device>> = live.devices
    val positions: StateFlow<Map<Long, Position>> = live.positions
    val connected: StateFlow<Boolean> = live.connected
    val signInRejected: StateFlow<Boolean> = live.signInRejected

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val addresses = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val todayMeters = MutableStateFlow<Map<Long, Double>>(emptyMap())
    private val parkedSince = MutableStateFlow<Map<Long, Instant>>(emptyMap())
    private val ignitionChanges = MutableStateFlow<Map<Long, Pair<Boolean?, Long>>>(emptyMap())

    private val _commandOptions = MutableStateFlow<Map<Long, CommandOptions>>(emptyMap())
    val commandOptions: StateFlow<Map<Long, CommandOptions>> = _commandOptions.asStateFlow()

    private val _geofences = MutableStateFlow<Map<Long, List<Geofence>>>(emptyMap())
    val geofences: StateFlow<Map<Long, List<Geofence>>> = _geofences.asStateFlow()

    private val _alerts = MutableStateFlow(AlertsState())
    val alerts: StateFlow<AlertsState> = _alerts.asStateFlow()

    private val _trips = MutableStateFlow(TripsState())
    val trips: StateFlow<TripsState> = _trips.asStateFlow()

    private val _replay = MutableStateFlow(ReplayState())
    val replay: StateFlow<ReplayState> = _replay.asStateFlow()

    private val _serverCheck = MutableStateFlow<ServerCheck>(ServerCheck.Idle)
    val serverCheck: StateFlow<ServerCheck> = _serverCheck.asStateFlow()

    private val _reply = MutableStateFlow<TrackerReply?>(null)

    /** A tracker reply too long for a snackbar (status, settings), shown in a dialog. */
    val reply: StateFlow<TrackerReply?> = _reply.asStateFlow()
    private val replyWatchers = mutableMapOf<Long, Job>()

    private val _pending = MutableStateFlow<Map<Long, PendingChange>>(emptyMap())

    /** Per car: the setting sent to its tracker and not yet confirmed. */
    val pending: StateFlow<Map<Long, PendingChange>> = _pending.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** What's being sent right now ("Cut fuel"…), for a spinner. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    val selectedId: StateFlow<Long> = combine(settings, devices) { s, list ->
        list.firstOrNull { it.id == s.selectedDeviceId }?.id ?: list.firstOrNull()?.id ?: 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val now = MutableStateFlow(Instant.now())

    val cars: StateFlow<List<CarInfo>> = combine(
        combine(devices, positions, settings) { d, p, s -> Triple(d, p, s) },
        combine(addresses, todayMeters, parkedSince) { a, t, ps -> Triple(a, t, ps) },
        ignitionChanges,
        now,
    ) { (devs, pos, s), (addr, today, parked), ignition, clock ->
        devs.map { d -> carInfo(d, pos[d.id], s, addr[d.id], today[d.id], parked[d.id], ignition[d.id]?.second, clock) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedCar: StateFlow<CarInfo?> = combine(cars, selectedId) { list, id -> list.firstOrNull { it.device.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val unreadAlerts: StateFlow<Int> = combine(alerts, settings) { a, s -> a.items.count { it.event.id > s.lastSeenEventId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        // Live alerts or the 15-minute check, whenever sign-in or the setting changes.
        viewModelScope.launch {
            settings.map { it.signedIn to it.liveAlerts }.distinctUntilChanged().collect {
                if (settings.value.token != "loading") AlertMode.apply(getApplication<Application>(), settings.value)
            }
        }
        // Addresses and ignition changes as positions arrive.
        viewModelScope.launch {
            positions.collect { map ->
                map.values.forEach { p ->
                    val previous = ignitionChanges.value[p.deviceId]
                    val ign = p.ignition
                    if (previous == null || previous.first != ign) {
                        val at = if (previous == null) 0L else (Format.parse(p.fixTime) ?: Instant.now()).toEpochMilli()
                        ignitionChanges.update { it + (p.deviceId to (ign to at)) }
                    }
                }
                lookupAddresses(map.values)
            }
        }
        // Today's distance and parked time: on start, then every few minutes and after trips end.
        viewModelScope.launch {
            selectedId.collect { id -> if (id != 0L) refreshDay(id) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                now.value = Instant.now()
            }
        }
        viewModelScope.launch {
            live.updates.debounce(20_000).collect {
                val id = selectedId.value
                if (id != 0L) refreshDay(id)
                refreshAlertsQuietly()
            }
        }
        viewModelScope.launch {
            devices.map { list -> list.map { it.id } }.distinctUntilChanged().collect { ids ->
                if (ids.isNotEmpty()) {
                    refreshAlertsQuietly()
                    ids.forEach { loadGeofences(it) }
                }
            }
        }
    }

    // ---------- signing in ----------

    private var checkJob: Job? = null

    fun checkServer(input: String) {
        checkJob?.cancel()
        if (input.isBlank()) {
            _serverCheck.value = ServerCheck.Idle
            return
        }
        checkJob = viewModelScope.launch {
            delay(600)
            when (val parsed = ServerAddress.parse(input)) {
                is ServerAddress.Result.Problem -> _serverCheck.value = ServerCheck.Problem(parsed.message)
                is ServerAddress.Result.Ok -> {
                    _serverCheck.value = ServerCheck.Checking
                    _serverCheck.value = try {
                        ServerCheck.Found(api.serverInfo(parsed.url).version)
                    } catch (e: Exception) {
                        ServerCheck.Problem("No Traccar server found there.")
                    }
                }
            }
        }
    }

    /** Returns null when signed in, or what went wrong. */
    suspend fun signIn(serverInput: String, email: String, password: String): String? {
        val url = when (val parsed = ServerAddress.parse(serverInput)) {
            is ServerAddress.Result.Problem -> return parsed.message
            is ServerAddress.Result.Ok -> parsed.url
        }
        if (email.isBlank() || password.isEmpty()) return "Enter your email and password."
        return try {
            api.clearSession()
            api.token = null
            val info = api.serverInfo(url)
            api.login(url, email.trim(), password)
            val token = api.createToken(url)
            c.settings.signedIn(url, token, email.trim(), info.version.orEmpty())
            null
        } catch (e: ApiException) {
            if (e.code == 401) "Wrong email or password." else e.message ?: "Couldn't sign in."
        } catch (e: Exception) {
            "Couldn't reach the server. Check the address and your connection."
        }
    }

    fun signOut() {
        viewModelScope.launch {
            api.signOut()
            c.settings.signedOut()
            _alerts.value = AlertsState()
            _trips.value = TripsState()
            _geofences.value = emptyMap()
            _commandOptions.value = emptyMap()
            addresses.value = emptyMap()
            todayMeters.value = emptyMap()
            parkedSince.value = emptyMap()
        }
    }

    // ---------- the car ----------

    fun select(deviceId: Long) = viewModelScope.launch { c.settings.setSelectedDevice(deviceId) }

    fun refreshNow() = viewModelScope.launch {
        runCatching { live.refresh() }.onFailure { say("Couldn't reach the server.") }
        selectedId.value.takeIf { it != 0L }?.let { refreshDay(it) }
    }

    private fun carInfo(
        d: Device,
        p: Position?,
        s: AppSettings,
        address: String?,
        today: Double?,
        parkedFrom: Instant?,
        ignitionChangedAt: Long?,
        clock: Instant,
    ): CarInfo {
        val prefs = s.prefs(d.id)
        val kind = TrackerCommands.kind(prefs, p)
        val moving = p != null && (p.motion == true || p.speedKmh >= 5)
        val status = when {
            !d.online && d.status != null -> CarStatus.OFFLINE
            moving -> CarStatus.MOVING
            else -> CarStatus.PARKED
        }
        val reported = p?.armed
        val manual = prefs.manualArmed?.takeIf { prefs.manualAt > (ignitionChangedAt ?: 0L) }
        val armed = when {
            reported != null -> reported
            manual != null -> manual
            p?.ignition == true -> false
            p?.ignition == false && prefs.autoArm && kind == TrackerKind.MICODUS ->
                ignitionChangedAt == null || ignitionChangedAt == 0L || clock.toEpochMilli() - ignitionChangedAt > 60_000
            else -> prefs.manualArmed ?: false
        }
        return CarInfo(
            device = d,
            position = p,
            prefs = prefs,
            kind = kind,
            icon = CarImages.resolve(prefs.icon, d.category),
            carColor = Palette.parse(prefs.carColor, Palette.CarDefault),
            glowColor = Palette.parse(prefs.color, Palette.CircleDefault),
            address = address,
            status = status,
            updated = Format.parse(p?.fixTime) ?: Format.parse(d.lastUpdate),
            ignition = p?.ignition,
            immobilised = p?.blocked == true,
            armed = armed,
            armedInferred = reported == null,
            todayMeters = today,
            parkedSince = parkedFrom,
        )
    }

    private suspend fun lookupAddresses(list: Collection<Position>) {
        list.forEach { p ->
            val text = c.addresses.lookup(p.latitude, p.longitude, p.address)
            if (text != null && addresses.value[p.deviceId] != text) addresses.update { it + (p.deviceId to text) }
        }
    }

    private suspend fun refreshDay(deviceId: Long) {
        val nowInstant = Instant.now()
        val start = Format.startOfDay(LocalDate.now())
        runCatching { api.summary(deviceId, start, nowInstant) }.getOrNull()?.firstOrNull()?.let { s ->
            todayMeters.update { it + (deviceId to s.distance) }
        }
        runCatching { api.trips(deviceId, nowInstant.minus(7, ChronoUnit.DAYS), nowInstant) }.getOrNull()
            ?.mapNotNull { Format.parse(it.endTime) }?.maxOrNull()
            ?.let { end -> parkedSince.update { it + (deviceId to end) } }
    }

    // ---------- commands ----------

    fun loadCommandOptions(deviceId: Long) = viewModelScope.launch {
        val types = runCatching { api.commandTypes(deviceId).map { it.type }.toSet() }.getOrDefault(emptySet())
        val saved = runCatching { api.savedCommands(deviceId).filter { it.id != 0L } }.getOrDefault(emptyList())
        _commandOptions.update { it + (deviceId to CommandOptions(types, saved, true)) }
    }

    private suspend fun options(deviceId: Long): CommandOptions {
        _commandOptions.value[deviceId]?.takeIf { it.loaded }?.let { return it }
        val types = runCatching { api.commandTypes(deviceId).map { it.type }.toSet() }.getOrDefault(emptySet())
        val saved = runCatching { api.savedCommands(deviceId).filter { it.id != 0L } }.getOrDefault(emptyList())
        return CommandOptions(types, saved, true).also { o -> _commandOptions.update { it + (deviceId to o) } }
    }

    fun canDo(car: CarInfo, action: Action): Boolean {
        val o = _commandOptions.value[car.device.id] ?: return car.kind == TrackerKind.MICODUS
        return TrackerCommands.supports(car.kind, action, o.types)
    }

    fun send(car: CarInfo, action: Action, quiet: Boolean = false) = viewModelScope.launch {
        val o = options(car.device.id)
        val command = TrackerCommands.command(car.kind, action, car.device.id, o.types)
        if (command == null) {
            say("${car.device.name}'s tracker can't do that.")
            return@launch
        }
        val change = when (action) {
            Action.ARM, Action.DISARM -> PendingChange(PendingChange.ALARM, action == Action.ARM)
            Action.AUTO_ARM_ON, Action.AUTO_ARM_OFF -> PendingChange(PendingChange.AUTO_ARM, action == Action.AUTO_ARM_ON)
            Action.VIBRATION_ON, Action.VIBRATION_OFF -> PendingChange(AlertKind.VIBRATION.key, action == Action.VIBRATION_ON)
            Action.LOW_BATTERY_ON, Action.LOW_BATTERY_OFF -> PendingChange(AlertKind.LOW_BATTERY.key, action == Action.LOW_BATTERY_ON)
            Action.UNPLUGGED_ON, Action.UNPLUGGED_OFF -> PendingChange(AlertKind.UNPLUGGED.key, action == Action.UNPLUGGED_ON)
            else -> null
        }
        // MiCODUS trackers answer every command, so their settings wait for that answer.
        // Other trackers may never answer, so theirs change straight away.
        if (change == null || car.kind != TrackerKind.MICODUS) {
            if (deliver(car, action.label, command, quiet) && change != null) applyChange(car, change)
            return@launch
        }
        if (!car.device.online) {
            say("${car.device.name} is offline, so nothing was changed. Try again once it's back online.")
            return@launch
        }
        deliver(car, action.label, command, quiet = false, change = change)
    }

    /** Saves a tracker setting the tracker has confirmed (or that can't be confirmed). */
    private suspend fun applyChange(car: CarInfo, change: PendingChange) {
        when (change.key) {
            PendingChange.ALARM -> c.settings.updateDevice(car.device.id) {
                it.copy(manualArmed = change.on, manualAt = System.currentTimeMillis())
            }
            PendingChange.AUTO_ARM -> c.settings.updateDevice(car.device.id) { it.copy(autoArm = change.on) }
            else -> AlertKind.entries.firstOrNull { it.key == change.key }?.let { c.settings.setAlertKind(it, change.on) }
        }
    }

    fun setUpdateRate(car: CarInfo, rate: UpdateRate) = viewModelScope.launch {
        val o = options(car.device.id)
        val command = TrackerCommands.updateRate(car.kind, rate, car.device.id, o.types)
        if (command == null) say("${car.device.name}'s tracker can't change that from here.")
        else deliver(car, "Update rate", command, quiet = false)
    }

    /** A saved command, a Traccar type, or custom text from the advanced list. */
    fun sendAdvanced(car: CarInfo, label: String, command: JsonObject) = viewModelScope.launch {
        deliver(car, label, command, quiet = false, preferSaved = false)
    }

    fun customCommand(car: CarInfo, text: String) {
        if (text.isBlank()) return
        val command = if (car.kind == TrackerKind.MICODUS) {
            TrackerCommands.custom(car.device.id, text.trim())
        } else {
            kotlinx.serialization.json.buildJsonObject {
                put("deviceId", JsonPrimitive(car.device.id))
                put("type", JsonPrimitive("custom"))
                put("textChannel", JsonPrimitive(false))
                put("attributes", JsonObject(mapOf("data" to JsonPrimitive(text.trim()))))
            }
        }
        sendAdvanced(car, text.trim(), command)
    }

    private suspend fun deliver(
        car: CarInfo,
        label: String,
        command: JsonObject,
        quiet: Boolean,
        preferSaved: Boolean = true,
        change: PendingChange? = null,
    ): Boolean {
        if (_pending.value.containsKey(car.device.id)) {
            say("Still waiting for ${car.device.name} to confirm the last change. Try again in a moment.")
            return false
        }
        _busy.value = label
        return try {
            val o = options(car.device.id)
            val toSend = if (preferSaved) TrackerCommands.preferSaved(command, o.saved, car.device.id) else command
            val sentAt = Instant.now()
            val now = api.sendCommand(toSend)
            if (change != null) _pending.update { it + (car.device.id to change) }
            if (!quiet) {
                say(
                    when {
                        now -> "$label: sent to ${car.device.name}. Waiting for it to confirm…"
                        change != null -> "${car.device.name} went offline. It gets this when it reconnects, and the setting changes once it confirms."
                        else -> "${car.device.name} is offline. It gets this when it next connects."
                    },
                )
            }
            watchReply(car, label, sentAt, if (now) 90_000L else 15 * 60_000L, change)
            true
        } catch (e: ApiException) {
            say(
                when (e.code) {
                    400, 403 -> "Your account isn't allowed to send that. Ask the server's admin, or add it as a saved command."
                    else -> e.message ?: "Couldn't send that."
                },
            )
            false
        } catch (e: Exception) {
            say("Couldn't reach the server.")
            false
        } finally {
            _busy.value = null
        }
    }

    /**
     * Waits for the tracker to answer [label] (Traccar records the answer as a "commandResult"
     * event) and shows it. A [change] is saved only once the tracker confirms it; with no answer
     * in time, nothing changes.
     */
    private fun watchReply(car: CarInfo, label: String, sentAt: Instant, windowMs: Long, change: PendingChange?) {
        val id = car.device.id
        replyWatchers.remove(id)?.cancel()
        replyWatchers[id] = viewModelScope.launch {
            try {
                val from = sentAt.minusSeconds(5)
                val until = System.currentTimeMillis() + windowMs
                while (isActive && System.currentTimeMillis() < until) {
                    delay(3_000)
                    val event = runCatching { api.events(listOf(id), from, Instant.now().plusSeconds(60)) }
                        .getOrNull()
                        ?.lastOrNull { it.type == "commandResult" }
                        ?: continue
                    val raw = event.attributes.str("result").orEmpty()
                    val text = TrackerCommands.readableReply(raw)
                    if (text.isEmpty()) continue
                    val failed = TrackerCommands.isFailure(raw)
                    if (change != null && !failed) applyChange(car, change)
                    val shown = if (change != null && failed) "$text. Nothing was changed." else text
                    if (shown.length <= 60 && '\n' !in shown) say("${car.device.name}: $shown")
                    else _reply.value = TrackerReply(car.device.name, label, shown)
                    return@launch
                }
                if (change != null && isActive) say("${car.device.name} didn't confirm, so nothing was changed.")
            } finally {
                if (change != null) _pending.update { it - id }
            }
        }
    }

    fun dismissReply() {
        _reply.value = null
    }

    // ---------- alerts set on the tracker ----------

    fun setAlertKind(kind: AlertKind, on: Boolean, car: CarInfo? = null) = viewModelScope.launch {
        val action = if (car != null && car.kind == TrackerKind.MICODUS) {
            when (kind) {
                AlertKind.VIBRATION -> if (on) Action.VIBRATION_ON else Action.VIBRATION_OFF
                AlertKind.LOW_BATTERY -> if (on) Action.LOW_BATTERY_ON else Action.LOW_BATTERY_OFF
                AlertKind.UNPLUGGED -> if (on) Action.UNPLUGGED_ON else Action.UNPLUGGED_OFF
                else -> null
            }
        } else {
            null
        }
        // Alerts the tracker raises itself change once it confirms; the rest are Traccar's and change now
        if (car != null && action != null) send(car, action) else c.settings.setAlertKind(kind, on)
    }

    /** Speeding alerts come from Traccar itself: the car's speedLimit attribute (in knots); 0 turns them off. */
    fun setSpeedLimit(car: CarInfo, kmh: Int) = viewModelScope.launch {
        c.settings.updateDevice(car.device.id) { it.copy(speedLimit = kmh) }
        if (kmh > 0) c.settings.setAlertKind(AlertKind.SPEEDING, true)
        val updated = updateDeviceAttributes(car.device.id) { attrs ->
            if (kmh > 0) attrs + ("speedLimit" to JsonPrimitive(kmh / 1.852)) else attrs - "speedLimit"
        }
        if (!updated) say("Saved on this phone. Your account can't change the car on the server, so ask its admin to set the speed limit.")
    }

    // ---------- the car's details ----------

    fun rename(car: CarInfo, name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        if (!updateDevice(car.device.id) { it + ("name" to JsonPrimitive(name.trim())) }) {
            say("Your account can't rename cars on this server.")
        }
    }

    fun setLook(car: CarInfo, icon: String? = null, carColor: Color? = null, glowColor: Color? = null) = viewModelScope.launch {
        c.settings.updateDevice(car.device.id) { p ->
            p.copy(
                icon = icon ?: car.icon,
                carColor = carColor?.let { Palette.hex(it) } ?: p.carColor,
                color = glowColor?.let { Palette.hex(it) } ?: p.color,
            )
        }
        if (icon != null) {
            val category = CarImages.categoryOf[icon]
            if (category != null) updateDevice(car.device.id) { it + ("category" to JsonPrimitive(category)) }
        }
    }

    fun setTrackerType(car: CarInfo, type: String) = viewModelScope.launch {
        c.settings.updateDevice(car.device.id) { it.copy(trackerType = type) }
    }

    private suspend fun updateDeviceAttributes(deviceId: Long, change: (Map<String, kotlinx.serialization.json.JsonElement>) -> Map<String, kotlinx.serialization.json.JsonElement>): Boolean =
        updateDevice(deviceId) { raw ->
            val attrs = (raw["attributes"] as? JsonObject) ?: JsonObject(emptyMap())
            raw + ("attributes" to JsonObject(change(attrs)))
        }

    private suspend fun updateDevice(deviceId: Long, change: (Map<String, kotlinx.serialization.json.JsonElement>) -> Map<String, kotlinx.serialization.json.JsonElement>): Boolean {
        val raw = live.rawDevice(deviceId) ?: return false
        val newRaw = JsonObject(change(raw))
        return try {
            val device = api.updateDevice(newRaw)
            live.replaceDevice(device, newRaw)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- places ----------

    fun loadGeofences(deviceId: Long) = viewModelScope.launch {
        runCatching { api.geofences(deviceId) }.getOrNull()?.let { list -> _geofences.update { it + (deviceId to list) } }
    }

    fun addPlace(car: CarInfo, name: String, radius: Double, icon: String) = viewModelScope.launch {
        val p = car.position
        if (p == null || !p.hasFix) {
            say("${car.device.name} has no position yet.")
            return@launch
        }
        try {
            val fence = api.addGeofence(name.trim().ifBlank { "Home" }, p.latitude, p.longitude, radius, icon)
            api.linkGeofence(car.device.id, fence.id)
            c.settings.setAlertKind(AlertKind.PLACES, true)
            say("${fence.name} added. You'll be told when ${car.device.name} arrives or leaves.")
            loadGeofences(car.device.id)
        } catch (e: ApiException) {
            say(if (e.code == 400 || e.code == 403) "Your account can't add places on this server." else e.message ?: "Couldn't add the place.")
        } catch (e: Exception) {
            say("Couldn't reach the server.")
        }
    }

    fun editPlace(car: CarInfo, fence: Geofence, name: String, radius: Double, icon: String) = viewModelScope.launch {
        val circle = fence.circle
        if (circle == null) {
            say("${fence.name} isn't a circle, so it can only be changed in Traccar.")
            return@launch
        }
        try {
            api.updateGeofence(fence.id, name.trim().ifBlank { fence.name }, circle.latitude, circle.longitude, radius, icon)
            loadGeofences(car.device.id)
        } catch (e: ApiException) {
            say(if (e.code == 400 || e.code == 403) "Your account can't change places on this server." else e.message ?: "Couldn't save ${fence.name}.")
        } catch (e: Exception) {
            say("Couldn't reach the server.")
        }
    }

    fun deletePlace(car: CarInfo, fence: Geofence) = viewModelScope.launch {
        try {
            api.deleteGeofence(fence.id)
            loadGeofences(car.device.id)
        } catch (e: Exception) {
            say("Couldn't remove ${fence.name}.")
        }
    }

    // ---------- alerts ----------

    fun loadAlerts() = viewModelScope.launch { fetchAlerts(showLoading = true) }

    private fun refreshAlertsQuietly() = viewModelScope.launch { fetchAlerts(showLoading = false) }

    private suspend fun fetchAlerts(showLoading: Boolean) {
        val devs = devices.value
        if (devs.isEmpty()) return
        if (showLoading) _alerts.update { it.copy(loading = true, error = null) }
        val nowInstant = Instant.now()
        try {
            val events = api.events(devs.map { it.id }, nowInstant.minus(7, ChronoUnit.DAYS), nowInstant)
            val names = devs.associate { it.id to it.name }
            val places = _geofences.value.values.flatten().associate { it.id to it.name }
            val cleared = settings.value.clearedEventId
            val items = events
                .filter { e -> e.id > cleared }
                .filter { e -> AlertKind.of(e) != null || e.type == "deviceOnline" }
                .sortedByDescending { it.id }
                .take(300)
                .map { e ->
                    AlertItem(
                        event = e,
                        deviceName = names[e.deviceId].orEmpty(),
                        title = EventText.title(e, places[e.geofenceId]),
                        time = Format.parse(e.eventTime),
                        kind = AlertKind.of(e),
                        warning = EventText.isWarning(e),
                    )
                }
            _alerts.value = AlertsState(loading = false, items = items)
        } catch (e: Exception) {
            _alerts.update { it.copy(loading = false, error = if (showLoading) "Couldn't load alerts." else it.error) }
        }
    }

    /** Empties the alert list and the badge. Traccar keeps the events; the app just stops showing them. */
    fun clearAlerts() = viewModelScope.launch {
        val newest = _alerts.value.items.maxOfOrNull { it.event.id } ?: return@launch
        c.settings.clearAlerts(maxOf(newest, settings.value.clearedEventId))
        _alerts.update { it.copy(items = emptyList()) }
    }

    /** Where an alert happened, looked up once and remembered. */
    private val eventPlaces = MutableStateFlow<Map<Long, String>>(emptyMap())
    val alertPlaces: StateFlow<Map<Long, String>> = eventPlaces.asStateFlow()

    fun lookupAlertPlaces(items: List<AlertItem>) = viewModelScope.launch {
        val missing = items.map { it.event }.filter { it.positionId != 0L && it.id !in eventPlaces.value }.take(40)
        if (missing.isEmpty()) return@launch
        val positionsById = runCatching { api.positionsById(missing.map { it.positionId }.toSet()) }.getOrDefault(emptyList()).associateBy { it.id }
        missing.forEach { e ->
            val p = positionsById[e.positionId] ?: return@forEach
            c.addresses.lookup(p.latitude, p.longitude, p.address)?.let { text -> eventPlaces.update { it + (e.id to text) } }
        }
    }

    fun markAlertsSeen() = viewModelScope.launch {
        val newest = _alerts.value.items.maxOfOrNull { it.event.id } ?: return@launch
        if (newest > settings.value.lastSeenEventId) c.settings.setLastSeenEvent(newest)
    }

    // ---------- trips ----------

    fun loadTrips(deviceId: Long, date: LocalDate) = viewModelScope.launch {
        _trips.value = TripsState(deviceId = deviceId, date = date, loading = true)
        val from = Format.startOfDay(date)
        val to = minOf(Format.startOfDay(date.plusDays(1)), Instant.now())
        try {
            val trips = api.trips(deviceId, from, to)
            val route = if (trips.isNotEmpty()) runCatching { api.route(deviceId, from, to) }.getOrDefault(emptyList()) else emptyList()
            _trips.value = TripsState(deviceId, date, false, trips, route)
        } catch (e: Exception) {
            _trips.value = TripsState(deviceId, date, false, error = "Couldn't load trips.")
        }
    }

    fun loadReplay(deviceId: Long, from: Instant, to: Instant) = viewModelScope.launch {
        _replay.value = ReplayState(loading = true)
        _replay.value = try {
            ReplayState(loading = false, points = api.route(deviceId, from, to).filter { it.hasFix })
        } catch (e: Exception) {
            ReplayState(loading = false, error = "Couldn't load this trip.")
        }
    }

    // ---------- settings ----------

    fun setTheme(value: String) = viewModelScope.launch { c.settings.setTheme(value) }
    fun setAccent(value: String) = viewModelScope.launch { c.settings.setAccent(value) }
    fun setMapStyle(value: String) = viewModelScope.launch { c.settings.setMapStyle(value) }
    fun setAdvanced(value: Boolean) = viewModelScope.launch { c.settings.setAdvancedCommands(value) }
    fun setLiveAlerts(value: Boolean) = viewModelScope.launch { c.settings.setLiveAlerts(value) }
    fun setAlertFilter(kind: AlertKind, on: Boolean) = viewModelScope.launch { c.settings.setAlertKind(kind, on) }

    fun setAlertSound(kind: AlertKind, sound: AlertSound) = viewModelScope.launch {
        c.settings.setAlertSound(kind, sound)
        Notifier.channelFor(getApplication<Application>(), kind, sound)
    }

    fun say(message: String) {
        _messages.tryEmit(message)
    }
}
