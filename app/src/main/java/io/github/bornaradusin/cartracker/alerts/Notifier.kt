package io.github.bornaradusin.cartracker.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.bornaradusin.cartracker.MainActivity
import io.github.bornaradusin.cartracker.R

/** Two notification categories: the alerts themselves, and the quiet "live connection" one. */
object Notifier {

    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_LIVE = "live"
    const val LIVE_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alerts = NotificationChannel(CHANNEL_ALERTS, "Car alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ignition, places, speeding, alarms and other alerts from your cars"
        }
        val live = NotificationChannel(CHANNEL_LIVE, "Live connection", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Shown while live alerts keep a connection to your server. You can switch this one off; alerts keep coming."
            setShowBadge(false)
        }
        manager.createNotificationChannel(alerts)
        manager.createNotificationChannel(live)
    }

    fun openAppIntent(context: Context, openAlerts: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_ALERTS, openAlerts)
        return PendingIntent.getActivity(
            context,
            if (openAlerts) 1 else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun liveNotification(context: Context, carNames: List<String>): Notification {
        val text = when (carNames.size) {
            0 -> "Watching for alerts"
            1 -> "Watching ${carNames[0]} for alerts"
            else -> "Watching ${carNames.size} cars for alerts"
        }
        return NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle("CarTracker")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(context, false))
            .build()
    }

    fun alert(context: Context, id: Long, title: String, text: String, warning: Boolean) =
        post(context, (id % Int.MAX_VALUE).toInt() + 100, title, text, warning)

    /** A message about a command when the app isn't on screen (sent from the alarm tile). */
    fun commandMessage(context: Context, text: String) =
        post(context, -2_000, text, "", warning = false)

    /** The app's own low-battery warning: one per car, replacing the last. */
    fun batteryLow(context: Context, deviceId: Long, title: String, text: String) =
        post(context, -1_000 - (deviceId % 100_000).toInt(), title, text, warning = true)

    private fun post(context: Context, notificationId: Int, title: String, text: String, warning: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (warning) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (warning) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setColor(if (warning) 0xFFB4540A.toInt() else 0xFF1D5BD8.toInt())
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, true))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }
}
