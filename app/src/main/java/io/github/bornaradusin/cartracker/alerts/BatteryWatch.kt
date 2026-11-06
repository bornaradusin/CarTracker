package io.github.bornaradusin.cartracker.alerts

import android.content.Context
import io.github.bornaradusin.cartracker.data.Device
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.data.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * The app's own low-battery warning: while a car is parked (ignition off), a reading below the level
 * set for it sends one alert. It can warn again once the battery has recovered (a drive charges it).
 * Readings with the engine running are skipped: the alternator lifts them, and starting dips them.
 */
class BatteryWatch(private val context: Context, private val settings: SettingsStore) {

    private val mutex = Mutex()

    suspend fun check(devices: List<Device>, positions: Collection<Position>) = mutex.withLock {
        val s = settings.current()
        if (!s.signedIn) return@withLock
        positions.forEach { p ->
            val prefs = s.prefs(p.deviceId)
            val limit = prefs.lowVoltage
            val volts = p.power ?: return@forEach
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
    }

    private companion object {
        const val RECOVERED = 0.3
    }
}
