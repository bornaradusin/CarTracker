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

    /** What happened last time, for the setting's hint. */
    fun result(): String? = prefs.getString(KEY_RESULT, null)

    suspend fun check(force: Boolean = false) {
        val config = lube.config.value
        if (!config.connected || !config.matchOdometer || config.vehicles.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastLook < LOOK_EVERY_MS) return
        if (!running.tryLock()) return
        try {
            lastLook = now
            config.vehicles.forEach { (deviceId, vehicleId) -> match(deviceId, vehicleId, config) }
        } finally {
            running.unlock()
        }
    }

    private suspend fun match(deviceId: Long, vehicleId: Int, config: LubeConfig) {
        val (at, km) = lube.carDashReading(vehicleId, config) ?: return
        val applied = prefs.getLong(applied(deviceId), 0L)
        if (at.toEpochMilli() <= applied) return

        // The tracker's count when the dashboard was read, and now
        val before = sequenceOf(30L, 6 * 60L).firstNotNullOfOrNull { minutes ->
            runCatching { api.route(deviceId, at.minusSeconds(minutes * 60), at.plusSeconds(60)) }.getOrNull()
                ?.filter { p -> p.totalDistance != null && (Format.parse(p.fixTime)?.let { !it.isAfter(at.plusSeconds(60)) } ?: false) }
                ?.maxByOrNull { Format.parse(it.fixTime) ?: Instant.MIN }
                ?.totalDistance
        }
        val current = runCatching { api.positions() }.getOrNull()?.firstOrNull { it.deviceId == deviceId }?.totalDistance
        if (before == null || current == null) {
            // The tracker wasn't reporting then: wait for a newer reading, unless it's this one forever
            if (Instant.now().isAfter(at.plusSeconds(2 * 86_400))) done(deviceId, at, "Skipped a reading the tracker had no count for.")
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
        prefs.edit().putLong(applied(deviceId), at.toEpochMilli()).putString(KEY_RESULT, result).apply()
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
