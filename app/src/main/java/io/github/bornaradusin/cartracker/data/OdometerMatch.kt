package io.github.bornaradusin.cartracker.data

import android.content.Context
import io.github.bornaradusin.cartracker.util.Format
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Keeps Traccar's odometer on the car's own. CarDash puts the dashboard's kilometres in LubeLogger
 * with the moment they were read; the tracker's count at that moment is looked up, and the
 * difference is taken off (or added to) the count now. Optional, and off until it's turned on.
 */
class OdometerMatch(context: Context, private val lube: LubeLogger, private val api: TraccarApi) {

    private val prefs = context.getSharedPreferences("odometer_match", Context.MODE_PRIVATE)
    private val running = Mutex()
    private var lastLook = 0L

    private val _result = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString(KEY_RESULT, null))

    /** What happened last time, for the setting's hint. */
    val result: kotlinx.coroutines.flow.StateFlow<String?> = _result

    private fun say(text: String) {
        prefs.edit().putString(KEY_RESULT, text).apply()
        _result.value = text
    }

    suspend fun check(force: Boolean = false) {
        val config = lube.config.value
        if (!config.connected || !config.matchOdometer || config.vehicles.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastLook < LOOK_EVERY_MS) return
        if (!running.tryLock()) return
        try {
            lastLook = now
            config.vehicles.forEach { (deviceId, vehicleId) ->
                try {
                    match(deviceId, vehicleId, config)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: LubeException) {
                    say("Couldn't read LubeLogger: ${e.message}")
                } catch (e: Exception) {
                    say("Couldn't check: the server didn't answer. Tries again in half an hour.")
                }
            }
        } finally {
            running.unlock()
        }
    }

    private suspend fun match(deviceId: Long, vehicleId: Int, config: LubeConfig) {
        val reading = lube.carDashReading(vehicleId, config)
        if (reading == null) {
            say("No reading from CarDash in LubeLogger yet. CarDash sends one as the first drive of the day starts.")
            return
        }
        val (at, km) = reading
        val applied = prefs.getLong(applied(deviceId), 0L)
        if (at.toEpochMilli() <= applied) return

        // The tracker's count when the dashboard was read: its last report before then, which can be
        // hours earlier while the car was parked
        val before = sequenceOf(30L, 6 * 60L, 2 * 24 * 60L, 7 * 24 * 60L).firstNotNullOfOrNull { minutes ->
            runCatching { api.route(deviceId, at.minusSeconds(minutes * 60), at.plusSeconds(60)) }.getOrNull()
                ?.filter { p -> p.totalDistance != null && (Format.parse(p.fixTime)?.let { !it.isAfter(at.plusSeconds(60)) } ?: false) }
                ?.maxByOrNull { Format.parse(it.fixTime) ?: Instant.MIN }
                ?.totalDistance
        }
        val current = runCatching { api.positions() }.getOrNull()?.firstOrNull { it.deviceId == deviceId }?.totalDistance
        if (before == null || current == null) {
            // The tracker wasn't reporting then: wait for a newer reading, unless it's this one forever
            if (Instant.now().isAfter(at.plusSeconds(2 * 86_400))) done(deviceId, at, "Skipped a reading the tracker had no count for.")
            else say("Found CarDash's ${km} km from ${day(at)}, but not the tracker's count then yet. Tries again in half an hour.")
            return
        }
        val target = km * 1000.0 + (current - before)
        val change = target - current
        when {
            abs(change) < SMALL_M -> done(deviceId, at, "Matches the dashboard (${Format.kilometres(target)} on ${day(at)}).")
            abs(change) > LARGE_M -> done(deviceId, at, "Left alone: CarDash's ${km} km is too far from the tracker's count.")
            else -> try {
                api.setOdometer(deviceId, target.roundToLong().toDouble())
                done(deviceId, at, "Set to ${Format.kilometres(target)} from CarDash's reading on ${day(at)}.")
            } catch (e: ApiException) {
                done(deviceId, at, if (e.code == 400 || e.code == 403) "Your Traccar account can't change the odometer." else "Couldn't set the odometer.")
            }
        }
    }

    private fun done(deviceId: Long, at: Instant, result: String) {
        prefs.edit().putLong(applied(deviceId), at.toEpochMilli()).apply()
        say(result)
    }

    private fun applied(deviceId: Long) = "applied_$deviceId"

    private fun day(at: Instant) = at.atZone(ZoneId.systemDefault()).format(DAY)

    private companion object {
        const val KEY_RESULT = "result"
        const val LOOK_EVERY_MS = 30 * 60_000L
        const val SMALL_M = 200.0
        const val LARGE_M = 5_000_000.0
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
