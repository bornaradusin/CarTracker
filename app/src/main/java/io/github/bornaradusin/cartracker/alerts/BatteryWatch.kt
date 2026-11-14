package io.github.bornaradusin.cartracker.alerts

import android.content.Context
import io.github.bornaradusin.cartracker.data.Device
import io.github.bornaradusin.cartracker.data.HistoryStore
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.data.SettingsStore
import io.github.bornaradusin.cartracker.data.VoltPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The app's own battery warnings.
 *
 * Low: while a car is parked (ignition off), a reading below the level set for it sends one alert.
 * It can warn again once the battery has recovered (a drive charges it). Readings with the engine
 * running are skipped: the alternator lifts them, and starting dips them.
 *
 * Getting weaker: once a day, the level the battery rests at on each of the last days is compared.
 * A steady fall sends one alert, well before the battery is low enough for the first warning.
 * With a dashcam that records while parked, the battery is drawn down to the dashcam's cut-off
 * every time: only a fall to clearly below that cut-off counts.
 */
class BatteryWatch(private val context: Context, private val settings: SettingsStore, private val history: HistoryStore) {

    private val mutex = Mutex()
    private val trendTried = HashMap<Long, Long>()

    suspend fun check(devices: List<Device>, positions: Collection<Position>) = mutex.withLock {
        val s = settings.current()
        if (!s.signedIn) return@withLock
        positions.forEach { p ->
            val prefs = s.prefs(p.deviceId)
            val limit = prefs.lowVoltage
            val volts = prefs.volts(p.power) ?: return@forEach
            if (limit <= 0.0 || volts < 1.0) return@forEach
            if (prefs.batteryWarned) {
                if (volts >= limit + RECOVERED) settings.updateDevice(p.deviceId) { it.copy(batteryWarned = false) }
            } else if (p.ignition == false && volts < limit) {
                settings.updateDevice(p.deviceId) { it.copy(batteryWarned = true) }
                val name = devices.firstOrNull { it.id == p.deviceId }?.name
                Notifier.batteryLow(
                    context,
                    p.deviceId,
                    (name?.let { "$it: " } ?: "") + "car battery low",
                    String.format(Locale.US, "%.1f V while parked. It warns below %.1f V.", volts, limit),
                )
            }
        }
        positions.forEach { p ->
            try {
                checkTrend(devices, p.deviceId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Tried again later
            }
        }
    }

    private suspend fun checkTrend(devices: List<Device>, deviceId: Long) {
        val prefs = settings.current().prefs(deviceId)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        if (!prefs.batteryTrend || prefs.trendCheckedDay == today.toEpochDay()) return
        // When the server can't be reached, not on every reading that comes in
        val now = System.currentTimeMillis()
        if (now - (trendTried[deviceId] ?: 0L) < RETRY_MS) return
        trendTried[deviceId] = now

        val from = today.minusDays(DAYS).atStartOfDay(zone).toInstant()
        val to = today.atStartOfDay(zone).toInstant().minusSeconds(1)
        val resting = restingByDay(history.voltages(deviceId, from, to), zone)
            .mapValues { (_, volts) -> prefs.volts(volts) ?: volts }
        settings.updateDevice(deviceId) { it.copy(trendCheckedDay = today.toEpochDay()) }

        val fall = steadyFall(resting, prefs.dashcamCutoff) ?: return
        if (today.toEpochDay() - prefs.trendWarnedDay < QUIET_DAYS) return
        settings.updateDevice(deviceId) { it.copy(trendWarnedDay = today.toEpochDay()) }
        val name = devices.firstOrNull { it.id == deviceId }?.name
        Notifier.batteryWeaker(
            context,
            deviceId,
            (name?.let { "$it: " } ?: "") + "car battery getting weaker",
            String.format(
                Locale.US,
                "Parked, it now rests at %.1f V, down from %.1f V %d days earlier. A longer drive or a charger should bring it back up.",
                fall.last,
                fall.first,
                fall.days,
            ),
        )
    }

    private class Fall(val first: Double, val last: Double, val days: Long)

    private companion object {
        const val RECOVERED = 0.3
        const val DAYS = 7L
        const val QUIET_DAYS = 5L
        const val RETRY_MS = 30 * 60_000L

        /** Readings this soon after the engine stops are still lifted by the charge it just got. */
        const val SETTLE_MS = 60 * 60_000L
        const val MIN_READINGS = 3
        const val MIN_DAYS = 4

        /** A fall this large over the days, ending at or below [WEAK], is worth a warning. */
        const val FALL_VOLTS = 0.25
        const val WEAK = 12.4

        /** How far under a dashcam's cut-off the battery must rest before the fall isn't the dashcam's doing. */
        const val UNDER_CUTOFF = 0.15

        /** The level the battery sat at on each day: the middle one of its settled, parked readings. */
        fun restingByDay(points: List<VoltPoint>, zone: ZoneId): Map<LocalDate, Double> {
            val byDay = HashMap<LocalDate, MutableList<Float>>()
            var lastRunning = Long.MIN_VALUE
            points.sortedBy { it.time }.forEach { p ->
                if (p.ignition) {
                    lastRunning = p.time
                } else if (lastRunning == Long.MIN_VALUE || p.time - lastRunning >= SETTLE_MS) {
                    byDay.getOrPut(Instant.ofEpochMilli(p.time).atZone(zone).toLocalDate()) { ArrayList() }.add(p.volts)
                }
            }
            return byDay.filterValues { it.size >= MIN_READINGS }
                .mapValues { (_, volts) -> volts.sorted()[volts.size / 2].toDouble() }
        }

        /** A fall across the days that is steady (a line through them slopes down), not one odd day. */
        fun steadyFall(resting: Map<LocalDate, Double>, dashcamCutoff: Double): Fall? {
            if (resting.size < MIN_DAYS) return null
            val days = resting.keys.sorted()
            val x = days.map { (it.toEpochDay() - days.first().toEpochDay()).toDouble() }
            val y = days.map { resting.getValue(it) }
            val meanX = x.average()
            val meanY = y.average()
            val spread = x.sumOf { (it - meanX) * (it - meanX) }
            if (spread == 0.0) return null
            val slope = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) } / spread
            val span = x.last()
            val first = y.first()
            val last = y.last()
            val weak = if (dashcamCutoff > 0.0) minOf(WEAK, dashcamCutoff - UNDER_CUTOFF) else WEAK
            val steady = -slope * span >= FALL_VOLTS && first - last >= FALL_VOLTS && last <= weak
            return if (steady) Fall(first, last, span.toLong()) else null
        }
    }
}
