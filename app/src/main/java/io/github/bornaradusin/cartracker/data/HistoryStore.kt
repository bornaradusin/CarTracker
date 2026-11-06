package io.github.bornaradusin.cartracker.data

import io.github.bornaradusin.cartracker.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.sqrt

/** One battery reading: when, how many volts, and whether the engine was on. */
data class VoltPoint(val time: Long, val volts: Float, val ignition: Boolean)

/**
 * Longer stretches of history (battery readings, trips, where the car has been), fetched in pieces
 * and kept on the phone once a piece is complete, so going back to a week or a year is quick and
 * only today's part comes from the server again.
 */
class HistoryStore(private val api: TraccarApi, private val root: File) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Each server gets its own folder: car ids only mean something on their own server. */
    private fun dir(kind: String, deviceId: Long): File =
        File(root, "${api.baseUrl.lowercase().hashCode().toUInt()}/$kind/$deviceId").apply { mkdirs() }

    /** Finished means over an hour ago, so late positions from the tracker have arrived. */
    private fun finished(end: Instant) = end.isBefore(Instant.now().minusSeconds(3_600))

    // ---- battery ----

    suspend fun voltages(deviceId: Long, from: Instant, to: Instant): List<VoltPoint> = coroutineScope {
        val first = from.atZone(zone).toLocalDate()
        val last = to.atZone(zone).toLocalDate()
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val gate = Semaphore(3)
        days.map { day -> async { gate.withPermit { voltDay(deviceId, day) } } }
            .awaitAll()
            .flatten()
            .filter { it.time in from.toEpochMilli()..to.toEpochMilli() }
            .sortedBy { it.time }
    }

    private suspend fun voltDay(deviceId: Long, day: LocalDate): List<VoltPoint> {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        val file = File(dir("volts", deviceId), "$day.csv")
        val complete = finished(end)
        if (complete) readVolts(file)?.let { return it }
        val points = api.route(deviceId, start, minOf(end, Instant.now())).mapNotNull { p ->
            val volts = p.power?.takeIf { it > 1.0 } ?: return@mapNotNull null
            val time = Format.parse(p.fixTime) ?: return@mapNotNull null
            VoltPoint(time.toEpochMilli(), volts.toFloat(), p.ignition == true)
        }
        if (complete) write(file, points.joinToString("\n") { "${it.time},${it.volts},${if (it.ignition) 1 else 0}" })
        return points
    }

    private suspend fun readVolts(file: File): List<VoltPoint>? = read(file)?.lineSequence()?.mapNotNull { line ->
        val parts = line.split(',')
        val time = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val volts = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        VoltPoint(time, volts, parts.getOrNull(2) == "1")
    }?.toList()

    // ---- trips ----

    /** Trips in the range, a month at a time; finished months come from the phone. */
    suspend fun trips(deviceId: Long, from: Instant, to: Instant): List<Trip> = coroutineScope {
        val end = minOf(to, Instant.now())
        val months = generateSequence(YearMonth.from(from.atZone(zone))) { it.plusMonths(1) }
            .takeWhile { !it.atDay(1).atStartOfDay(zone).toInstant().isAfter(end) }
            .toList()
        val gate = Semaphore(3)
        months.map { month ->
            async {
                gate.withPermit {
                    val monthStart = month.atDay(1).atStartOfDay(zone).toInstant()
                    val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
                    val segFrom = maxOf(from, monthStart)
                    val segTo = minOf(end, monthEnd)
                    if (!segFrom.isBefore(segTo)) emptyList<Trip>()
                    else if (segFrom == monthStart && segTo == monthEnd && finished(monthEnd)) monthTrips(deviceId, month, monthStart, monthEnd)
                    else api.trips(deviceId, segFrom, segTo)
                }
            }
        }.awaitAll().flatten().sortedBy { it.startTime }
    }

    private suspend fun monthTrips(deviceId: Long, month: YearMonth, start: Instant, end: Instant): List<Trip> {
        val file = File(dir("trips", deviceId), "$month.json")
        read(file)?.let { text -> runCatching { AppJson.decodeFromString(ListSerializer(Trip.serializer()), text) }.getOrNull()?.let { return it } }
        val trips = api.trips(deviceId, start, end)
        write(file, AppJson.encodeToString(ListSerializer(Trip.serializer()), trips))
        return trips
    }

    // ---- where the car has been ----

    /**
     * Where the car has been over [weeks] weeks up to this one, as points along its routes, at most
     * one every [SPACING] metres: roads driven often end up with many points, a parked car with one.
     */
    suspend fun places(deviceId: Long, weeks: Int, onProgress: (done: Int, total: Int) -> Unit): List<Pair<Double, Double>> = coroutineScope {
        val thisWeek = LocalDate.now(zone).with(java.time.DayOfWeek.MONDAY)
        val starts = (weeks - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
        val done = AtomicInteger(0)
        onProgress(0, starts.size)
        val gate = Semaphore(3)
        starts.map { week ->
            async {
                gate.withPermit {
                    weekPlaces(deviceId, week).also { onProgress(done.incrementAndGet(), starts.size) }
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun weekPlaces(deviceId: Long, week: LocalDate): List<Pair<Double, Double>> {
        val start = week.atStartOfDay(zone).toInstant()
        val end = week.plusWeeks(1).atStartOfDay(zone).toInstant()
        val file = File(dir("places", deviceId), "$week.csv")
        val complete = finished(end)
        if (complete) {
            read(file)?.let { text ->
                return text.lineSequence().mapNotNull { line ->
                    val parts = line.split(',')
                    val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                    lat to lon
                }.toList()
            }
        }
        // Traccar's export stops at 50,000 positions, so a week goes in two halves
        val middle = start.plusSeconds(3L * 86_400 + 43_200)
        val now = Instant.now()
        val raw = buildList {
            if (start.isBefore(now)) addAll(api.coordinates(deviceId, start, minOf(middle, now)))
            if (middle.isBefore(now)) addAll(api.coordinates(deviceId, middle, minOf(end, now)))
        }
        val thinned = thin(raw)
        if (complete) write(file, thinned.joinToString("\n") { String.format(Locale.US, "%.5f,%.5f", it.first, it.second) })
        return thinned
    }

    private fun thin(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val kept = ArrayList<Pair<Double, Double>>()
        var last: Pair<Double, Double>? = null
        points.forEach { p ->
            val previous = last
            if (previous == null || metres(previous, p) >= SPACING) {
                kept += p
                last = p
            }
        }
        return kept
    }

    private fun metres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val x = Math.toRadians(b.second - a.second) * cos(Math.toRadians((a.first + b.first) / 2))
        val y = Math.toRadians(b.first - a.first)
        return sqrt(x * x + y * y) * 6_371_000.0
    }

    // ---- files ----

    private suspend fun read(file: File): String? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    }

    private suspend fun write(file: File, text: String) = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(text)
            temp.renameTo(file)
        }
    }

    private companion object {
        const val SPACING = 40.0
    }
}
