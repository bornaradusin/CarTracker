package io.github.bornaradusin.cartracker.shortcuts

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.github.bornaradusin.cartracker.R
import io.github.bornaradusin.cartracker.alerts.Notifier
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.data.ParkedTime
import io.github.bornaradusin.cartracker.ui.map.CarImages
import io.github.bornaradusin.cartracker.ui.map.MarkerFactory
import io.github.bornaradusin.cartracker.ui.theme.Palette
import io.github.bornaradusin.cartracker.util.Format
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** The home-screen widget. Android asks for a refresh every 30 minutes; the app refreshes it more often (see [CarWidget]). */
class CarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        context.container.scope.launch {
            try {
                withTimeoutOrNull(9_000) { CarWidget.refresh(context, fetch = true) }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Draws the widget: the car's pin, name and address, how long it's been parked (or how fast it's
 * going) and the battery voltage. It follows the car chosen in the app. While the app or live alerts
 * are connected it updates with every position, and the "parked for" time every minute.
 */
object CarWidget {

    private data class Parked(val since: Instant?, val checkedAt: Long, val moving: Boolean)

    private val parked = ConcurrentHashMap<Long, Parked>()
    private val mutex = Mutex()

    fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

    private fun ids(context: Context): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CarWidgetProvider::class.java))
    }.getOrDefault(IntArray(0))

    /** [fetch]: ask the server when the app has no live data (the widget's own 30-minute refresh). */
    suspend fun refresh(context: Context, fetch: Boolean) = mutex.withLock {
        val ids = ids(context)
        if (ids.isEmpty()) return@withLock
        val c = context.container
        val s = c.settings.current()
        if (!s.signedIn) {
            render(context, ids, null)
            return@withLock
        }
        if (!c.api.signedIn) c.ready()
        if (fetch && (!c.live.connected.value || c.live.devices.value.isEmpty())) runCatching { c.live.refresh() }

        val devices = c.live.devices.value
        val device = devices.firstOrNull { it.id == s.selectedDeviceId } ?: devices.firstOrNull()
        if (device == null) {
            render(context, ids, null)
            return@withLock
        }
        val p = c.live.positions.value[device.id]
        val prefs = s.prefs(device.id)
        val now = System.currentTimeMillis()
        val offline = !device.online && device.status != null
        val moving = p != null && (p.motion == true || p.speedKmh >= 5)

        // When the car parked only changes when it parks again, so it's asked for rarely
        val known = parked[device.id]
        if (moving) {
            parked[device.id] = Parked(null, now, true)
        } else if (!offline) {
            val ask = known == null || known.moving ||
                (known.since == null && now - known.checkedAt > 3 * 60_000) ||
                now - known.checkedAt > 60 * 60_000
            if (ask) runCatching { ParkedTime.since(c.api, device.id) }.onSuccess { parked[device.id] = Parked(it, now, false) }
        }
        val since = parked[device.id]?.takeIf { !it.moving }?.since

        val fix = p?.takeIf { it.hasFix }
        val address = fix?.let { runCatching { c.addresses.lookup(it.latitude, it.longitude, it.address) }.getOrNull() }
        val updated = Format.parse(p?.fixTime) ?: Format.parse(device.lastUpdate)
        val status = when {
            offline -> "Offline"
            moving -> "Driving · ${p?.speedKmh?.toInt() ?: 0} km/h"
            since != null -> "Parked ${Format.duration(now - since.toEpochMilli())}"
            p != null -> "Parked"
            else -> null
        }
        val volts = p?.power?.takeIf { it > 1.0 }
        val limit = prefs.lowVoltage.takeIf { it > 0 } ?: 11.8
        val pin = MarkerFactory.bitmap(
            context,
            CarImages.resolve(prefs.icon, device.category),
            Palette.parse(prefs.carColor, Palette.CarDefault),
            Palette.parse(prefs.color, Palette.CircleDefault),
            40f,
        )
        render(
            context,
            ids,
            Model(
                name = device.name,
                address = address ?: if (fix == null) "No position yet" else String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude),
                status = status,
                battery = volts?.let { String.format(Locale.US, "%.1f V", it) },
                batteryLow = volts != null && volts < limit,
                updated = if (offline) "Last seen ${Format.ago(updated)}" else updated?.let { "Updated ${Format.time(it)}" },
                pin = pin,
            ),
        )
    }

    private data class Model(
        val name: String,
        val address: String,
        val status: String?,
        val battery: String?,
        val batteryLow: Boolean,
        val updated: String?,
        val pin: Bitmap,
    )

    private fun render(context: Context, ids: IntArray, model: Model?) {
        val views = RemoteViews(context.packageName, R.layout.widget_car)
        views.setOnClickPendingIntent(R.id.widget_root, Notifier.openAppIntent(context, false))
        if (model == null) {
            views.setTextViewText(R.id.widget_name, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_address, context.getString(R.string.widget_placeholder_text))
            views.setViewVisibility(R.id.widget_status, View.GONE)
            views.setViewVisibility(R.id.widget_battery, View.GONE)
            views.setViewVisibility(R.id.widget_updated, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_pin, model.pin)
            views.setTextViewText(R.id.widget_name, model.name)
            views.setTextViewText(R.id.widget_address, model.address)
            show(views, R.id.widget_status, model.status)
            show(views, R.id.widget_battery, model.battery)
            if (model.batteryLow) views.setTextColor(R.id.widget_battery, ContextCompat.getColor(context, R.color.widget_warn))
            show(views, R.id.widget_updated, model.updated)
        }
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
    }

    private fun show(views: RemoteViews, id: Int, text: String?) {
        if (text == null) {
            views.setViewVisibility(id, View.GONE)
        } else {
            views.setTextViewText(id, text)
            views.setViewVisibility(id, View.VISIBLE)
        }
    }
}
