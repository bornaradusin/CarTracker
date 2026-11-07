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
        if (recent.driving) return null
        // A drive in the last day: parked since it ended
        if (recent.sawDrive && recent.since != null && recent.since.isAfter(day.plusSeconds(600))) return recent.since
        return current(api, deviceId, now.minus(7, ChronoUnit.DAYS), now).since ?: recent.since
    }

    private data class Parked(val since: Instant?, val driving: Boolean, val sawDrive: Boolean)

    /**
     * Since when the car has stood still, between [from] and [to]: the end of its last drive, so time
     * with the engine running while parked (idling) counts as parked. Null while it's driving.
     */
    private suspend fun current(api: TraccarApi, deviceId: Long, from: Instant, to: Instant): Parked {
        val stops = api.stops(deviceId, from, to)
        val trips = api.trips(deviceId, from, to)
        val sawDrive = trips.any { it.isDrive }
        val lastTripEnd = trips.mapNotNull { Format.parse(it.endTime) }.maxOrNull()
        val stopStart = stops.mapNotNull { Format.parse(it.startTime) }.maxOrNull()
            ?: return Parked(null, driving = lastTripEnd != null && trips.last().isDrive, sawDrive = sawDrive)
        val lastDriveEnd = trips.filter { it.isDrive }.mapNotNull { Format.parse(it.endTime) }.maxOrNull()
        // In a trip right now: a drive, or only the engine running without moving yet
        if (lastTripEnd != null && stopStart.isBefore(lastTripEnd.minusSeconds(60))) {
            val ongoing = trips.firstOrNull { Format.parse(it.endTime) == lastTripEnd }
            if (ongoing == null || ongoing.isDrive) return Parked(null, driving = true, sawDrive = sawDrive)
        }
        return Parked(lastDriveEnd?.takeIf { it.isBefore(stopStart.plusSeconds(60)) } ?: stopStart, driving = false, sawDrive = sawDrive)
    }
}
