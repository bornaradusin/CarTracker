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
        val from = now.minus(7, ChronoUnit.DAYS)
        val stops = api.stops(deviceId, from, now)
        val trips = api.trips(deviceId, from, now)
        val lastDriveEnd = trips.mapNotNull { Format.parse(it.endTime) }.maxOrNull()
        val stopStart = stops.mapNotNull { Format.parse(it.startTime) }.maxOrNull()
        return stopStart?.takeIf { lastDriveEnd == null || !it.isBefore(lastDriveEnd.minusSeconds(60)) }
    }
}
