package io.github.bornaradusin.cartracker.alerts

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.shortcuts.CarWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live alerts: keeps the connection to the user's own Traccar server open while the app is closed, and
 * checks for new events a few seconds after anything arrives. Android needs a notification to keep it
 * running; it's on its own quiet category, which can be switched off.
 */
class AlertService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        val names = container.live.devices.value.map { it.name }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.LIVE_NOTIFICATION_ID, Notifier.liveNotification(this, names), type)

        val c = container
        c.live.acquire(HOLDER)
        scope.launch {
            c.live.updates.debounce(3_000).collect { runCatching { c.alerts.check() } }
        }
        scope.launch {
            while (isActive) {
                runCatching { c.alerts.check() }
                delay(5 * 60_000L)
            }
        }
        // The widget's "parked for" time moves on every minute
        scope.launch {
            while (isActive) {
                delay(60_000L)
                if (CarWidget.hasWidgets(this@AlertService)) runCatching { CarWidget.refresh(this@AlertService, fetch = false) }
            }
        }
        scope.launch {
            c.live.devices.collect { devices ->
                runCatching {
                    NotificationManagerCompat.from(this@AlertService).notify(
                        Notifier.LIVE_NOTIFICATION_ID,
                        Notifier.liveNotification(this@AlertService, devices.map { it.name }),
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        container.live.release(HOLDER)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val HOLDER = "service"
    }
}
