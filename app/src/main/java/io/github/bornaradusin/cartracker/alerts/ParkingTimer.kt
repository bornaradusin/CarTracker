package io.github.bornaradusin.cartracker.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.bornaradusin.cartracker.util.Format
import java.time.Instant

/**
 * A reminder that paid parking is about to run out: one at a time, set from "Find my car". The
 * reminder comes a little before the end, so there's time to walk back.
 */
object ParkingTimer {

    private const val PREFS = "parking"
    private const val KEY_END = "end"
    private const val KEY_NAME = "name"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** How long before the end the reminder comes. */
    fun leadMinutes(minutes: Int): Int = if (minutes <= 15) 5 else 10

    /** When the running timer ends, or null when none is running. */
    fun endsAt(context: Context): Long? =
        prefs(context).getLong(KEY_END, 0L).takeIf { it > System.currentTimeMillis() }

    fun set(context: Context, carName: String, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        prefs(context).edit().putLong(KEY_END, end).putString(KEY_NAME, carName).apply()
        schedule(context, end - leadMinutes(minutes) * 60_000L)
    }

    fun cancel(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(intent(context)) }
    }

    /** After a restart of the phone, which forgets alarms. */
    fun restore(context: Context) {
        val end = endsAt(context) ?: return
        val minutesLeft = ((end - System.currentTimeMillis()) / 60_000L).toInt()
        schedule(context, maxOf(end - leadMinutes(minutesLeft) * 60_000L, System.currentTimeMillis() + 1_000L))
    }

    internal fun remind(context: Context) {
        val end = endsAt(context) ?: return
        val name = prefs(context).getString(KEY_NAME, null).orEmpty()
        val minutesLeft = ((end - System.currentTimeMillis() + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
        Notifier.parking(
            context,
            (if (name.isNotEmpty()) "$name: " else "") + "parking ends at ${Format.time(Instant.ofEpochMilli(end))}",
            if (minutesLeft == 1) "That's in a minute." else "That's in $minutesLeft minutes.",
        )
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ParkingReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(context: Context, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
        val set = runCatching {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context))
        }
        if (set.isFailure) runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context)) }
    }
}

class ParkingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ParkingTimer.remind(context)
    }
}
