package io.github.bornaradusin.cartracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Format {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dayName = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val longDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(value) }.getOrNull()
    }

    fun time(instant: Instant?): String = instant?.atZone(zone)?.format(clock) ?: "–"

    fun dayShort(date: LocalDate): String = date.format(dayName)

    /** "Today", "Yesterday" or "Wednesday 4 November". */
    fun dayTitle(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(longDay)
        }
    }

    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    /** "34.8 km", "850 m". */
    fun distance(meters: Double): String = when {
        meters < 1_000 -> "${meters.roundToInt()} m"
        meters < 100_000 -> String.format(Locale.getDefault(), "%.1f km", meters / 1_000)
        else -> "${(meters / 1_000).roundToInt()} km"
    }

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    /** A stats period's title: "2 – 8 Nov", "November 2026", "2026". [end] is the day after it. */
    fun period(kind: String, start: LocalDate, end: LocalDate): String = when (kind) {
        "WEEK" -> {
            val last = end.minusDays(1)
            if (start.month == last.month) "${start.dayOfMonth} – ${last.format(dayMonth)}" else "${start.format(dayMonth)} – ${last.format(dayMonth)}"
        }
        "MONTH" -> start.format(monthYear)
        else -> start.year.toString()
    }

    /** "J", "F", "M"… for a year's chart. */
    fun monthLetter(date: LocalDate): String =
        date.month.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

    /** "123,456 km", for odometers. */
    fun kilometres(meters: Double): String = String.format(Locale.getDefault(), "%,d km", (meters / 1_000).roundToInt())

    /** "until 22:15" today, "until Sat 7 Nov 22:15" on another day. */
    fun until(instant: Instant): String {
        val at = instant.atZone(zone)
        return if (at.toLocalDate() == LocalDate.now(zone)) "until ${at.format(clock)}"
        else "until ${at.format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault()))}"
    }

    fun speedKmh(knots: Double): String = "${(knots * 1.852).roundToInt()} km/h"

    /** "6 h 22 m", "33 min", "45 s". */
    fun duration(millis: Long): String {
        val totalMinutes = millis / 60_000
        return when {
            millis < 60_000 -> "${millis / 1_000} s"
            totalMinutes < 60 -> "$totalMinutes min"
            totalMinutes < 48 * 60 -> "${totalMinutes / 60} h ${totalMinutes % 60} m"
            else -> "${totalMinutes / (24 * 60)} days"
        }
    }

    /** "just now", "5 min ago", "2 h ago", "3 days ago". */
    fun ago(instant: Instant?): String {
        if (instant == null) return "never"
        val millis = Instant.now().toEpochMilli() - instant.toEpochMilli()
        return when {
            millis < 60_000 -> "just now"
            millis < 3_600_000 -> "${millis / 60_000} min ago"
            millis < 48 * 3_600_000L -> "${millis / 3_600_000} h ago"
            else -> "${millis / (24 * 3_600_000L)} days ago"
        }
    }
}
