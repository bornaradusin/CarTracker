package io.github.bornaradusin.cartracker.data

import io.github.bornaradusin.cartracker.util.Format
import java.time.Instant
import java.time.temporal.ChronoUnit

object ParkedTime {

    /**
     * When the car parked after its last drive: the start of Traccar's current stop. Traccar only closes
     * a drive after the car has stood still for a few minutes; until then there's no stop yet (null).
     * Throws when the server can't be asked.
     */
    suspend fun since(api: TraccarApi, deviceId: Long, now: Instant = Instant.now()): Instant? {
        // Up to 24 hours Traccar uses the server's trip settings (ignition, short stops); beyond that it
        // takes a shortcut that ignores them. So the last day first, and the last week only when the car
        // has stood still all day.
        val day = now.minusSeconds(86_340)
        val recent = current(api, deviceId, day, now)
        if (recent != null && recent.isAfter(day.plusSeconds(600))) return recent
        if (recent == null && api.trips(deviceId, day, now).isNotEmpty()) return null
        return current(api, deviceId, now.minus(7, ChronoUnit.DAYS), now)
    }

    /** The start of the stop the car is in now, between [from] and [to]; null while it's still driving. */
    private suspend fun current(api: TraccarApi, deviceId: Long, from: Instant, to: Instant): Instant? {
        val stops = api.stops(deviceId, from, to)
        val trips = api.trips(deviceId, from, to)
        val lastDriveEnd = trips.mapNotNull { Format.parse(it.endTime) }.maxOrNull()
        val stopStart = stops.mapNotNull { Format.parse(it.startTime) }.maxOrNull()
        return stopStart?.takeIf { lastDriveEnd == null || !it.isBefore(lastDriveEnd.minusSeconds(60)) }
    }
}
