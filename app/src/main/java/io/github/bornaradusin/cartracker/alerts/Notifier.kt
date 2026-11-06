package io.github.bornaradusin.cartracker.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.bornaradusin.cartracker.MainActivity
import io.github.bornaradusin.cartracker.R
import io.github.bornaradusin.cartracker.data.AlertKind
import io.github.bornaradusin.cartracker.data.AlertSound

/**
 * Notification categories: one per alert type, so each can have its own sound, and the quiet
 * "live connection" one. An alert type's category is made again when its sound changes, because
 * Android fixes a category's sound once it exists.
 */
object Notifier {

    const val CHANNEL_LIVE = "live"
    const val LIVE_NOTIFICATION_ID = 1
    private const val GROUP_ALERTS = "alerts"
    private const val OLD_CHANNEL_ALERTS = "alerts"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ALERTS, "Car alerts"))
        val live = NotificationChannel(CHANNEL_LIVE, "Live connection", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Shown while live alerts keep a connection to your server. You can switch this one off; alerts keep coming."
            setShowBadge(false)
        }
        manager.createNotificationChannel(live)
        // Before 1.0.5 every alert shared one category
        manager.deleteNotificationChannel(OLD_CHANNEL_ALERTS)
    }

    /** The category for [kind]'s alerts with [sound], made if needed. Other sounds' categories for it are removed. */
    fun channelFor(context: Context, kind: AlertKind?, sound: AlertSound): String {
        val key = kind?.key ?: "other"
        val id = "alert_${key}_${sound.key}"
        val manager = context.getSystemService(NotificationManager::class.java) ?: return id
        AlertSound.entries.filter { it != sound }.forEach { manager.deleteNotificationChannel("alert_${key}_${it.key}") }
        if (manager.getNotificationChannel(id) != null) return id
        val importance = if (sound == AlertSound.SILENT) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(id, kind?.title ?: "Other alerts", importance).apply {
            description = kind?.hint ?: "Alerts from your cars"
            group = GROUP_ALERTS
            when (sound) {
                AlertSound.NORMAL -> Unit
                AlertSound.ALARM -> {
                    val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(
                        alarm,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                }
                AlertSound.SILENT -> {
                    setSound(null, null)
                    enableVibration(false)
                }
            }
        }
        manager.createNotificationChannel(channel)
        return id
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

    fun alert(context: Context, id: Long, title: String, text: String, warning: Boolean, channel: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val notification = NotificationCompat.Builder(context, channel)
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
        runCatching { NotificationManagerCompat.from(context).notify((id % Int.MAX_VALUE).toInt() + 100, notification) }
    }
}
