package io.github.bornaradusin.cartracker.ui

import kotlinx.coroutines.flow.drop
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.bornaradusin.cartracker.alerts.AlertMode
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.data.Action
import io.github.bornaradusin.cartracker.data.AlertKind
import io.github.bornaradusin.cartracker.data.ApiException
import io.github.bornaradusin.cartracker.data.CommandOptions
import io.github.bornaradusin.cartracker.data.CommandTarget
import io.github.bornaradusin.cartracker.data.ParkedTime
import io.github.bornaradusin.cartracker.data.PendingChange
import io.github.bornaradusin.cartracker.data.TrackerReply
import io.github.bornaradusin.cartracker.data.AppSettings
import io.github.bornaradusin.cartracker.data.Device
import io.github.bornaradusin.cartracker.data.DevicePrefs
import io.github.bornaradusin.cartracker.data.Event
import io.github.bornaradusin.cartracker.data.EventText
import io.github.bornaradusin.cartracker.data.Geofence
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.data.ServerAddress
import io.github.bornaradusin.cartracker.data.TrackerCommands
import io.github.bornaradusin.cartracker.data.TrackerKind
import io.github.bornaradusin.cartracker.data.Trip
import io.github.bornaradusin.cartracker.data.UpdateRate
import io.github.bornaradusin.cartracker.data.VoltPoint
import kotlinx.coroutines.CancellationException
import io.github.bornaradusin.cartracker.data.str
import io.github.bornaradusin.cartracker.ui.map.CarImages
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
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CarStatus { MOVING, PARKED, OFFLINE }


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

/** A link to follow a car live in a browser, and when it stops working. */
data class LiveLink(val url: String, val until: Instant)

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

data class ReplayState(val key: String = "", val loading: Boolean = true, val points: List<Position> = emptyList(), val error: String? = null)

data class BatteryState(
    val deviceId: Long = 0,
    val hours: Int = 24,
    val loading: Boolean = false,
    val points: List<VoltPoint> = emptyList(),
    val error: String? = null,
)

enum class StatsPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** One bar of the stats chart: a day (week, month) or a month (year). */
data class StatsBar(val label: String, val meters: Double, val millis: Long, val trips: Int)

data class StatsState(
    val deviceId: Long = 0,
    val period: StatsPeriod = StatsPeriod.WEEK,
    val start: LocalDate = LocalDate.now(),
    val title: String = "",
    val loading: Boolean = false,
    val bars: List<StatsBar> = emptyList(),
    val meters: Double = 0.0,
    val millis: Long = 0,
    val trips: Int = 0,
    val topKnots: Double = 0.0,
    val longest: Trip? = null,
    /** Days so far in the period, for the daily average. */
    val days: Int = 1,
    val error: String? = null,
)

data class HeatState(
    val deviceId: Long = 0,
    val weeks: Int = 4,
    val loading: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val points: List<Pair<Double, Double>> = emptyList(),
    val error: String? = null,
)

sealed interface ServerCheck {
    data object Idle : ServerCheck
    data object Checking : ServerCheck
    data class Found(val version: String?) : ServerCheck
    data class Problem(val message: String) : ServerCheck
}


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

    val commandOptions: StateFlow<Map<Long, CommandOptions>> = c.commands.options

    private val _geofences = MutableStateFlow<Map<Long, List<Geofence>>>(emptyMap())
    val geofences: StateFlow<Map<Long, List<Geofence>>> = _geofences.asStateFlow()

    private val _alerts = MutableStateFlow(AlertsState())
    val alerts: StateFlow<AlertsState> = _alerts.asStateFlow()

    private val _trips = MutableStateFlow(TripsState())
    val trips: StateFlow<TripsState> = _trips.asStateFlow()

    private val _replay = MutableStateFlow(ReplayState())
    val replay: StateFlow<ReplayState> = _replay.asStateFlow()

    private val _battery = MutableStateFlow(BatteryState())
    val battery: StateFlow<BatteryState> = _battery.asStateFlow()

    private val _stats = MutableStateFlow(StatsState())
    val stats: StateFlow<StatsState> = _stats.asStateFlow()

    private val _heat = MutableStateFlow(HeatState())
    val heat: StateFlow<HeatState> = _heat.asStateFlow()

    private val _serverCheck = MutableStateFlow<ServerCheck>(ServerCheck.Idle)
    val serverCheck: StateFlow<ServerCheck> = _serverCheck.asStateFlow()

    /** A tracker reply too long for a snackbar (status, settings), shown in a dialog. */
    val reply: StateFlow<TrackerReply?> = c.commands.reply

    /** Per car: the setting sent to its tracker and not yet confirmed. */
    val pending: StateFlow<Map<Long, PendingChange>> = c.commands.pending

    /** What's being sent right now ("Cut fuel"…), for a spinner. */
    val busy: StateFlow<String?> = c.commands.busy

    val selectedId: StateFlow<Long> = combine(settings, devices) { s, list ->
        list.firstOrNull { it.id == s.selectedDeviceId }?.id ?: list.firstOrNull()?.id ?: 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val now = MutableStateFlow(Instant.now())

    /** Ticks every 30 seconds, so "parked for" and "last seen" keep moving on screen. */
    val clock: StateFlow<Instant> = now.asStateFlow()

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
        viewModelScope.launch { c.commands.messages.collect { _messages.emit(it) } }
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
        // The list and badge follow "Which alerts to get"
        viewModelScope.launch {
            settings.map { it.alertKinds }.distinctUntilChanged().drop(1).collect { refreshAlertsQuietly() }
        }
        viewModelScope.launch {
            // Every 20 s at most while reports keep coming (a debounce would wait for the drive to end)
            live.updates.sample(20_000).collect {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Couldn't reach the server. Check the address and your connection."
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Locally first, so signing out works without a connection; then revoke the session
            val session = c.api.sessionForSignOut()
            c.settings.signedOut()
            _alerts.value = AlertsState()
            _trips.value = TripsState()
            _replay.value = ReplayState()
            _battery.value = BatteryState()
            _stats.value = StatsState()
            _heat.value = HeatState()
            _geofences.value = emptyMap()
            c.commands.clear()
            addresses.value = emptyMap()
            eventPlaces.value = emptyMap()
            todayMeters.value = emptyMap()
            parkedSince.value = emptyMap()
            ignitionChanges.value = emptyMap()
            c.scope.launch { runCatching { api.signOut(session) } }
        }
    }

    // ---------- the car ----------

    fun select(deviceId: Long) = viewModelScope.launch { c.settings.setSelectedDevice(deviceId) }

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
        // A change the tracker confirmed after its last report is newer than what that report says
        val reportedAt = (Format.parse(p?.serverTime) ?: Format.parse(p?.fixTime))?.toEpochMilli() ?: 0L
        val reported = p?.armed
        // A drive since the change (the ignition went on or off, or the car parked again) replaces it
        val lastCycle = maxOf(ignitionChangedAt ?: 0L, parkedFrom?.toEpochMilli() ?: 0L)
        val manual = prefs.manualArmed?.takeIf { prefs.manualAt > lastCycle }
        val confirmedArmed = manual?.takeIf { prefs.manualAt >= reportedAt }
        val fuelConfirmed = prefs.fuelCut?.takeIf { prefs.fuelAt >= reportedAt }
        val armed = when {
            confirmedArmed != null -> confirmedArmed
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
            immobilised = fuelConfirmed ?: p?.blocked ?: prefs.fuelCut ?: false,
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
        // Parked since the last drive ended
        val current = runCatching { ParkedTime.since(api, deviceId, nowInstant) }.getOrElse { return }
        parkedSince.update { if (current != null) it + (deviceId to current) else it - deviceId }
    }

    // ---------- commands ----------

    private fun target(car: CarInfo) = CommandTarget(car.device.id, car.device.name, car.kind, car.device.online)

    fun loadCommandOptions(deviceId: Long) = viewModelScope.launch { c.commands.load(deviceId) }

    fun canDo(car: CarInfo, action: Action): Boolean = c.commands.supports(target(car), action)

    fun send(car: CarInfo, action: Action, quiet: Boolean = false) = c.commands.send(target(car), action, quiet)

    fun setUpdateRate(car: CarInfo, rate: UpdateRate) = c.commands.setUpdateRate(target(car), rate)

    /** A saved command, a Traccar type, or custom text from the advanced list. */
    fun sendAdvanced(car: CarInfo, label: String, command: JsonObject) = c.commands.sendAdvanced(target(car), label, command)

    fun customCommand(car: CarInfo, text: String) = c.commands.customCommand(target(car), text)

    fun dismissReply() = c.commands.dismissReply()

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

    // ---------- live links and the odometer ----------

    /**
     * A link anyone can open to follow [car] live in a browser for [hours]. Null (with a message)
     * when the server says no. Traccar allows one link per car at a time: an older one still
     * running is replaced when this account may remove it (admins).
     */
    suspend fun liveLink(car: CarInfo, hours: Long): LiveLink? {
        val until = Instant.now().plus(hours, ChronoUnit.HOURS)
        val token = try {
            try {
                api.shareDevice(car.device.id, until)
            } catch (e: ApiException) {
                if (e.code == 409 && removeLiveLink(car)) api.shareDevice(car.device.id, until) else throw e
            }
        } catch (e: ApiException) {
            say(
                when (e.code) {
                    409 -> "${car.device.name} already has a live link running. It stops by itself when it ends."
                    400, 401, 403 -> "This server doesn't allow live links, or your account can't make them."
                    else -> e.message ?: "Couldn't make a live link."
                },
            )
            return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            say("Couldn't reach the server.")
            return null
        }
        c.settings.updateDevice(car.device.id) { it.copy(shareToken = token, shareUntil = until.toEpochMilli()) }
        return LiveLink(api.shareUrl(token), until)
    }

    /** The car's current live link, while it lasts. */
    fun currentLiveLink(car: CarInfo): LiveLink? {
        val until = car.prefs.shareUntil
        if (car.prefs.shareToken.isBlank() || until <= System.currentTimeMillis()) return null
        return LiveLink(api.shareUrl(car.prefs.shareToken), Instant.ofEpochMilli(until))
    }

    fun stopLiveLink(car: CarInfo) = viewModelScope.launch {
        val until = car.prefs.shareUntil
        if (removeLiveLink(car)) {
            c.settings.updateDevice(car.device.id) { it.copy(shareToken = "", shareUntil = 0) }
            say("The live link for ${car.device.name} is stopped.")
        } else {
            say("Your account can't stop live links on this server. It stops by itself at ${Format.time(Instant.ofEpochMilli(until))}.")
        }
    }

    /** Ends a live link early by removing the temporary account Traccar made for it. Only admins can. */
    private suspend fun removeLiveLink(car: CarInfo): Boolean {
        // Traccar names the link's account after the sharing user's email (which may differ from the sign-in name)
        val me = runCatching { api.sessionFromToken().email }.getOrNull()?.takeIf { it.isNotBlank() } ?: settings.value.email
        val email = "$me:${car.device.uniqueId}"
        val share = runCatching { api.users() }.getOrNull()?.firstOrNull { it.email.equals(email, ignoreCase = true) } ?: return false
        return runCatching { api.deleteUser(share.id) }.isSuccess
    }

    /** Sets the car's odometer to [km], so trips and totals carry on from the car's real mileage. */
    fun setOdometer(car: CarInfo, km: Double) = viewModelScope.launch {
        try {
            api.setOdometer(car.device.id, km * 1_000)
            runCatching { live.refresh() }
            say("${car.device.name}'s odometer is set to ${Format.kilometres(km * 1_000)}.")
        } catch (e: ApiException) {
            say(if (e.code == 400 || e.code == 403) "Your account can't change the odometer on this server." else e.message ?: "Couldn't set the odometer.")
        } catch (e: Exception) {
            say("Couldn't reach the server.")
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

    /** [radius] null keeps the place's size (and any shape that isn't a circle). */
    fun editPlace(car: CarInfo, fence: Geofence, name: String, radius: Double?, icon: String) = viewModelScope.launch {
        val circle = fence.circle
        val area = if (circle != null && radius != null) "CIRCLE (${circle.latitude} ${circle.longitude}, ${radius.toInt()})" else null
        try {
            api.updateGeofence(fence.id, name.trim().ifBlank { fence.name }, area, icon)
            // Other cars may share the place
            _geofences.value.keys.filter { it != car.device.id }.forEach { loadGeofences(it) }
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
            // Gone for every car that had it
            (_geofences.value.keys + car.device.id).forEach { loadGeofences(it) }
        } catch (e: Exception) {
            say("Couldn't remove ${fence.name}.")
        }
    }

    // ---------- alerts ----------

    /** Where the car was for an alert, for its details. */
    suspend fun alertPosition(event: Event): Position? =
        if (event.positionId == 0L) null else runCatching { api.positionsById(listOf(event.positionId)).firstOrNull() }.getOrNull()

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
            val kinds = settings.value.alertKinds
            val items = events
                .filter { e -> e.id > cleared }
                // Only the alert types switched on, the same as phone notifications
                .filter { e -> AlertKind.of(e)?.key?.let { it in kinds } == true }
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

    private var tripsJob: Job? = null

    fun loadTrips(deviceId: Long, date: LocalDate) {
        tripsJob?.cancel()
        tripsJob = viewModelScope.launch {
            _trips.value = TripsState(deviceId = deviceId, date = date, loading = true)
            val from = Format.startOfDay(date)
            val to = minOf(Format.startOfDay(date.plusDays(1)), Instant.now())
            _trips.value = try {
                // A day at a time (the 25-hour day in two halves), so trips follow the ignition
                val trips = c.history.trips(deviceId, from, to)
                val route = if (trips.isNotEmpty()) runCatching { api.route(deviceId, from, to) }.getOrDefault(emptyList()) else emptyList()
                TripsState(deviceId, date, false, trips, route)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TripsState(deviceId, date, false, error = "Couldn't load trips.")
            }
        }
    }

    private var replayJob: Job? = null

    fun loadReplay(deviceId: Long, from: Instant, to: Instant) {
        val key = "$deviceId/${from.toEpochMilli()}/${to.toEpochMilli()}"
        // Coming back to the same trip (rotation) keeps what's loaded
        if (_replay.value.key == key && !_replay.value.loading && _replay.value.error == null) return
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            _replay.value = ReplayState(key = key, loading = true)
            _replay.value = try {
                ReplayState(key = key, loading = false, points = api.route(deviceId, from, to).filter { it.hasFix })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReplayState(key = key, loading = false, error = "Couldn't load this trip.")
            }
        }
    }

    // ---------- battery history ----------

    private var batteryJob: Job? = null

    fun loadBattery(deviceId: Long, hours: Int) {
        batteryJob?.cancel()
        batteryJob = viewModelScope.launch {
            _battery.value = BatteryState(deviceId, hours, loading = true, points = _battery.value.takeIf { it.deviceId == deviceId && it.hours == hours }?.points.orEmpty())
            val to = Instant.now()
            _battery.value = try {
                BatteryState(deviceId, hours, points = c.history.voltages(deviceId, to.minus(hours.toLong(), ChronoUnit.HOURS), to))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BatteryState(deviceId, hours, error = "Couldn't load the battery readings.")
            }
        }
    }

    /** The app's low-battery warning level for the car, in volts; 0 turns it off. */
    fun setLowVoltage(car: CarInfo, volts: Double) = viewModelScope.launch {
        c.settings.updateDevice(car.device.id) { it.copy(lowVoltage = volts, batteryWarned = false) }
    }

    // ---------- stats ----------

    private var statsJob: Job? = null

    /** Distance, driving time and trips for the week, month or year that includes [anchor]. */
    fun loadStats(deviceId: Long, period: StatsPeriod, anchor: LocalDate) {
        statsJob?.cancel()
        val start = when (period) {
            StatsPeriod.WEEK -> anchor.with(java.time.DayOfWeek.MONDAY)
            StatsPeriod.MONTH -> anchor.withDayOfMonth(1)
            StatsPeriod.YEAR -> anchor.withDayOfYear(1)
        }
        val end = when (period) {
            StatsPeriod.WEEK -> start.plusWeeks(1)
            StatsPeriod.MONTH -> start.plusMonths(1)
            StatsPeriod.YEAR -> start.plusYears(1)
        }
        val title = Format.period(period.name, start, end)
        statsJob = viewModelScope.launch {
            val previous = _stats.value.takeIf { it.deviceId == deviceId && it.period == period && it.start == start }
            _stats.value = (previous ?: StatsState(deviceId, period, start, title)).copy(loading = true, error = null)
            _stats.value = try {
                val trips = c.history.trips(deviceId, Format.startOfDay(start), Format.startOfDay(end))
                statsOf(deviceId, period, start, end, title, trips)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StatsState(deviceId, period, start, title, error = "Couldn't load the trips for this ${period.label.lowercase()}.")
            }
        }
    }

    private fun statsOf(deviceId: Long, period: StatsPeriod, start: LocalDate, end: LocalDate, title: String, trips: List<Trip>): StatsState {
        val byDay = trips.groupBy { t -> Format.parse(t.startTime)?.let { Format.localDate(it) } }
        val bars = if (period == StatsPeriod.YEAR) {
            (0 until 12).map { i ->
                val month = start.plusMonths(i.toLong())
                val list = trips.filter { t -> Format.parse(t.startTime)?.let { Format.localDate(it) }?.let { it.year == month.year && it.month == month.month } == true }
                StatsBar(Format.monthLetter(month), list.sumOf { it.distance }, list.sumOf { it.duration }, list.size)
            }
        } else {
            generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(end) }.map { day ->
                val list = byDay[day].orEmpty()
                StatsBar(if (period == StatsPeriod.WEEK) Format.dayShort(day) else day.dayOfMonth.toString(), list.sumOf { it.distance }, list.sumOf { it.duration }, list.size)
            }.toList()
        }
        val today = LocalDate.now()
        val days = when {
            today.isBefore(start) -> 1
            today.isBefore(end) -> (ChronoUnit.DAYS.between(start, today) + 1).toInt()
            else -> ChronoUnit.DAYS.between(start, end).toInt()
        }.coerceAtLeast(1)
        return StatsState(
            deviceId = deviceId,
            period = period,
            start = start,
            title = title,
            bars = bars,
            meters = trips.sumOf { it.distance },
            millis = trips.sumOf { it.duration },
            trips = trips.size,
            topKnots = trips.maxOfOrNull { it.maxSpeed } ?: 0.0,
            longest = trips.maxByOrNull { it.distance },
            days = days,
        )
    }

    // ---------- heatmap ----------

    private var heatJob: Job? = null

    fun loadHeat(deviceId: Long, weeks: Int) {
        heatJob?.cancel()
        heatJob = viewModelScope.launch {
            val previous = _heat.value.takeIf { it.deviceId == deviceId && it.weeks == weeks }?.points.orEmpty()
            _heat.value = HeatState(deviceId, weeks, loading = true, points = previous)
            _heat.value = try {
                val points = c.history.places(deviceId, weeks) { done, total ->
                    _heat.update { if (it.deviceId == deviceId && it.weeks == weeks) it.copy(done = done, total = total) else it }
                }
                HeatState(deviceId, weeks, points = points)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HeatState(deviceId, weeks, error = "Couldn't load where ${devices.value.firstOrNull { it.id == deviceId }?.name ?: "the car"} has been.")
            }
        }
    }

    // ---------- settings ----------

    fun setTheme(value: String) = viewModelScope.launch { c.settings.setTheme(value) }
    fun setAccent(value: String) = viewModelScope.launch { c.settings.setAccent(value) }
    fun setMapStyle(value: String) = viewModelScope.launch { c.settings.setMapStyle(value) }
    fun setAdvanced(value: Boolean) = viewModelScope.launch { c.settings.setAdvancedCommands(value) }
    fun setFuelFingerprint(value: Boolean) = viewModelScope.launch { c.settings.setFuelFingerprint(value) }
    fun setLiveAlerts(value: Boolean) = viewModelScope.launch { c.settings.setLiveAlerts(value) }
    fun setAlertFilter(kind: AlertKind, on: Boolean) = viewModelScope.launch { c.settings.setAlertKind(kind, on) }

    fun say(message: String) {
        _messages.tryEmit(message)
    }
}
