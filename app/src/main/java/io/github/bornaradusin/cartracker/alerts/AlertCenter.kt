package io.github.bornaradusin.cartracker.alerts

import android.content.Context
import io.github.bornaradusin.cartracker.data.AddressLookup
import io.github.bornaradusin.cartracker.data.AlertKind
import io.github.bornaradusin.cartracker.data.EventText
import io.github.bornaradusin.cartracker.data.LiveRepository
import io.github.bornaradusin.cartracker.data.SettingsStore
import io.github.bornaradusin.cartracker.data.TraccarApi
import io.github.bornaradusin.cartracker.util.Format
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Looks for new events on the server and turns the ones switched on into phone notifications. Traccar
 * stores every event, so asking for what's new since last time works on any server, with nothing to
 * set up there. Called by the live service (seconds after each update) and by the 15-minute check.
 */
class AlertCenter(
    private val context: Context,
    private val api: TraccarApi,
    private val settings: SettingsStore,
    private val live: LiveRepository,
    private val addresses: AddressLookup,
) {
    private val mutex = Mutex()

    /** True while the app is on screen: it shows tracker replies itself, so no notification for those. */
    @Volatile
    var appVisible = false

    suspend fun check() = mutex.withLock {
        val s = settings.current()
        if (!s.signedIn || !api.signedIn) return@withLock
        val devices = live.devices.value.ifEmpty { runCatching { api.devices().map { it.first } }.getOrDefault(emptyList()) }
        if (devices.isEmpty()) return@withLock
        val now = Instant.now()
        // Events carry the tracker's time, so ones uploaded late (out of signal for a while) are older
        // than the last check: look back an hour; the id filter below skips ones already seen
        val since = if (s.lastEventCheck > 0) {
            Instant.ofEpochMilli(s.lastEventCheck).minusSeconds(3_600)
        } else {
            now.minusSeconds(900)
        }
        val events = api.events(devices.map { it.id }, since, now)
        val newest = maxOf(s.lastEventId, events.maxOfOrNull { it.id } ?: 0L)
        settings.setEventCheckpoint(newest, now.toEpochMilli())
        // The very first check only sets the starting point, so signing in doesn't replay old events.
        if (s.lastEventCheck == 0L) return@withLock

        // Looked at across the whole hour, so a repeat of a speeding alert already shown stays quiet
        val fresh = AlertKind.withoutRepeatedSpeeding(events).filter { it.id > s.lastEventId }
            .filter { event -> AlertKind.of(event)?.key?.let { it in s.alertKinds } == true }
            .filter { event -> !(appVisible && event.type == "commandResult") }
            .sortedBy { it.id }
        if (fresh.isEmpty()) return@withLock

        val positions = runCatching { api.positionsById(fresh.map { it.positionId }.filter { it != 0L }.toSet()) }
            .getOrDefault(emptyList()).associateBy { it.id }
        val placeNames = mutableMapOf<Long, String>()
        fresh.filter { it.geofenceId != 0L }.map { it.deviceId }.toSet().forEach { deviceId ->
            runCatching { api.geofences(deviceId) }.getOrDefault(emptyList()).forEach { placeNames[it.id] = it.name }
        }
        val names = devices.associate { it.id to it.name }
        fresh.forEach { event ->
            val position = positions[event.positionId]
            val where = position?.let { addresses.lookup(it.latitude, it.longitude, it.address) }
            val time = Format.time(Format.parse(event.eventTime))
            val title = (names[event.deviceId]?.let { "$it: " } ?: "") + EventText.title(event, placeNames[event.geofenceId])
            val text = listOfNotNull(where, time).joinToString(" · ")
            Notifier.alert(context, event.id, title, text, EventText.isWarning(event))
        }
    }
}
