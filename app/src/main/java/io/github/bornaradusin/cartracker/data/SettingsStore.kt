package io.github.bornaradusin.cartracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** How one car looks and behaves in the app. Kept on the phone, per car. */
@Serializable
data class DevicePrefs(
    val icon: String = "",
    /** Glow colour of the car's map pin, "#RRGGBB" (older versions saved a colour name). */
    val color: String = "",
    /** Paint colour of the car on its map pin, "#RRGGBB". */
    val carColor: String = "",
    /** The tracker arms itself a minute after the ignition goes off. */
    val autoArm: Boolean = true,
    /** Last arm/disarm sent from the app, and when. */
    val manualArmed: Boolean? = null,
    val manualAt: Long = 0,
    /** "auto", "micodus" or "generic": which command set the tracker understands. */
    val trackerType: String = "auto",
    /** Speed alert limit in km/h; 0 = off. */
    val speedLimit: Int = 0,
    /** Warn when the car battery drops below this many volts while parked; 0 = off. */
    val lowVoltage: Double = 11.8,
    /** A low-battery warning was sent and the battery hasn't recovered since. */
    val batteryWarned: Boolean = false,
    /** The fuel state the tracker last confirmed (true = cut), and when. */
    val fuelCut: Boolean? = null,
    val fuelAt: Long = 0,
    /** The last live link made for this car, and when it ends. */
    val shareToken: String = "",
    val shareUntil: Long = 0,
)

data class AppSettings(
    val server: String = "",
    val token: String? = null,
    val email: String = "",
    val serverVersion: String = "",
    val theme: String = "system",
    val accent: String = "Blue",
    val mapStyle: String = "Standard",
    val liveAlerts: Boolean = true,
    val advancedCommands: Boolean = false,
    val alertKinds: Set<String> = AlertKind.defaults,
    val devicePrefs: Map<Long, DevicePrefs> = emptyMap(),
    val selectedDeviceId: Long = 0,
    val lastEventId: Long = 0,
    val lastEventCheck: Long = 0,
    val lastSeenEventId: Long = 0,
    /** Alerts up to this event were cleared from the list (Traccar itself keeps them). */
    val clearedEventId: Long = 0,
    /** Ask for a fingerprint (or the phone's PIN) before cutting or restoring fuel. */
    val fuelFingerprint: Boolean = true,
) {
    val signedIn: Boolean get() = token != null && server.isNotBlank()
    fun prefs(deviceId: Long): DevicePrefs = devicePrefs[deviceId] ?: DevicePrefs()
}

class SettingsStore(private val context: Context) {

    private object K {
        val server = stringPreferencesKey("server")
        val token = stringPreferencesKey("token")
        val email = stringPreferencesKey("email")
        val serverVersion = stringPreferencesKey("server_version")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val mapStyle = stringPreferencesKey("map_style")
        val liveAlerts = booleanPreferencesKey("live_alerts")
        val advanced = booleanPreferencesKey("advanced_commands")
        val alertKinds = stringSetPreferencesKey("alert_kinds")
        val devicePrefs = stringPreferencesKey("device_prefs")
        val selected = longPreferencesKey("selected_device")
        val lastEventId = longPreferencesKey("last_event_id")
        val lastEventCheck = longPreferencesKey("last_event_check")
        val lastSeenEventId = longPreferencesKey("last_seen_event_id")
        val clearedEventId = longPreferencesKey("cleared_event_id")
        val fuelFingerprint = booleanPreferencesKey("fuel_fingerprint")
    }

    private val prefsSerializer = MapSerializer(Long.serializer(), DevicePrefs.serializer())

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            server = p[K.server].orEmpty(),
            token = p[K.token],
            email = p[K.email].orEmpty(),
            serverVersion = p[K.serverVersion].orEmpty(),
            theme = p[K.theme] ?: "system",
            accent = p[K.accent] ?: "Blue",
            mapStyle = p[K.mapStyle] ?: "Standard",
            liveAlerts = p[K.liveAlerts] ?: true,
            advancedCommands = p[K.advanced] ?: false,
            alertKinds = p[K.alertKinds] ?: AlertKind.defaults,
            devicePrefs = p[K.devicePrefs]?.let { runCatching { AppJson.decodeFromString(prefsSerializer, it) }.getOrNull() }
                ?: emptyMap(),
            selectedDeviceId = p[K.selected] ?: 0,
            lastEventId = p[K.lastEventId] ?: 0,
            lastEventCheck = p[K.lastEventCheck] ?: 0,
            lastSeenEventId = p[K.lastSeenEventId] ?: 0,
            clearedEventId = p[K.clearedEventId] ?: 0,
            fuelFingerprint = p[K.fuelFingerprint] ?: true,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun signedIn(server: String, token: String, email: String, version: String) = context.dataStore.edit {
        it[K.server] = server
        it[K.token] = token
        it[K.email] = email
        it[K.serverVersion] = version
        it.remove(K.lastEventId)
        it.remove(K.lastEventCheck)
        it.remove(K.lastSeenEventId)
        it.remove(K.clearedEventId)
        it.remove(K.selected)
    }

    suspend fun signedOut() = context.dataStore.edit {
        it.remove(K.token)
        it.remove(K.lastEventId)
        it.remove(K.lastEventCheck)
        it.remove(K.lastSeenEventId)
    }

    suspend fun setTheme(value: String) = context.dataStore.edit { it[K.theme] = value }
    suspend fun setAccent(value: String) = context.dataStore.edit { it[K.accent] = value }
    suspend fun setMapStyle(value: String) = context.dataStore.edit { it[K.mapStyle] = value }
    suspend fun setLiveAlerts(value: Boolean) = context.dataStore.edit { it[K.liveAlerts] = value }
    suspend fun setAdvancedCommands(value: Boolean) = context.dataStore.edit { it[K.advanced] = value }
    suspend fun setSelectedDevice(id: Long) = context.dataStore.edit { it[K.selected] = id }
    suspend fun setFuelFingerprint(value: Boolean) = context.dataStore.edit { it[K.fuelFingerprint] = value }
    suspend fun setLastSeenEvent(id: Long) = context.dataStore.edit { it[K.lastSeenEventId] = id }

    /** Hides every alert up to [id] and marks them seen. */
    suspend fun clearAlerts(id: Long) = context.dataStore.edit {
        it[K.clearedEventId] = id
        it[K.lastSeenEventId] = maxOf(id, it[K.lastSeenEventId] ?: 0)
    }

    suspend fun setAlertKind(kind: AlertKind, on: Boolean) = context.dataStore.edit { p ->
        val now = p[K.alertKinds] ?: AlertKind.defaults
        p[K.alertKinds] = if (on) now + kind.key else now - kind.key
    }

    suspend fun setEventCheckpoint(lastId: Long, checkedAt: Long) = context.dataStore.edit {
        it[K.lastEventId] = lastId
        it[K.lastEventCheck] = checkedAt
    }

    suspend fun updateDevice(deviceId: Long, change: (DevicePrefs) -> DevicePrefs) = context.dataStore.edit { p ->
        val all = p[K.devicePrefs]?.let { runCatching { AppJson.decodeFromString(prefsSerializer, it) }.getOrNull() }
            ?: emptyMap()
        val updated = all + (deviceId to change(all[deviceId] ?: DevicePrefs()))
        p[K.devicePrefs] = AppJson.encodeToString(prefsSerializer, updated)
    }
}
