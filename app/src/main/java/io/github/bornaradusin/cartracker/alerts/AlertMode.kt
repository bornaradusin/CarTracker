package io.github.bornaradusin.cartracker.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.data.AppSettings
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Live alerts on: the live service. Off: a check every 15 minutes (the shortest Android allows).
 * Signed out: neither.
 */
object AlertMode {

    private const val WORK_NAME = "alert-check"

    fun apply(context: Context, settings: AppSettings) {
        val app = context.applicationContext
        val work = WorkManager.getInstance(app)
        if (!settings.signedIn) {
            app.stopService(Intent(app, AlertService::class.java))
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        if (settings.liveAlerts) {
            work.cancelUniqueWork(WORK_NAME)
            // Android only lets this start while the app is on screen or just after the phone starts;
            // otherwise it starts next time the app opens.
            runCatching { ContextCompat.startForegroundService(app, Intent(app, AlertService::class.java)) }
        } else {
            app.stopService(Intent(app, AlertService::class.java))
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.ready()
        return runCatching { c.alerts.check() }.fold({ Result.success() }, { Result.retry() })
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                AlertMode.apply(context, c.settings.current())
            } finally {
                pending.finish()
            }
        }
    }
}
