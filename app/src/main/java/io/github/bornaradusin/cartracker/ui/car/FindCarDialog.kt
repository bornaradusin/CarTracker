package io.github.bornaradusin.cartracker.ui.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.bornaradusin.cartracker.alerts.ParkingTimer
import io.github.bornaradusin.cartracker.ui.CarInfo
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Find my car: how far it is from the phone, directions to it on foot or by car, and a reminder
 * before paid parking runs out.
 */
@Composable
internal fun FindCarDialog(info: CarInfo, onOpenUrl: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val name = info.device.name
    val spot = info.position?.takeIf { it.hasFix }
    val away = remember(spot?.latitude, spot?.longitude) { spot?.let { distanceFromPhone(context, it.latitude, it.longitude) } }
    var timerEnd by remember { mutableStateOf(ParkingTimer.endsAt(context)) }

    fun directions(mode: String) {
        if (spot != null) onOpenUrl("https://www.google.com/maps/dir/?api=1&destination=${spot.latitude},${spot.longitude}&travelmode=$mode")
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Find $name", color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val where = listOfNotNull(away?.let { "${distanceText(it)} from you" }, info.address).joinToString(" · ")
                if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                ShareOption(Ic.navigation, "Walk to it", "Walking directions in your maps app", enabled = spot != null) { directions("walking") }
                ShareOption(Ic.car, "Drive to it", "Driving directions in your maps app", enabled = spot != null) { directions("driving") }

                Text("PARKING TIMER", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.muted, modifier = Modifier.padding(top = 6.dp))
                val end = timerEnd
                if (end != null) {
                    ShareOption(Ic.clock, "Parking ends at ${Format.time(Instant.ofEpochMilli(end))}", "Tap to stop the timer", enabled = true) {
                        ParkingTimer.cancel(context)
                        timerEnd = null
                    }
                } else {
                    Text(
                        "Paid for parking? Pick how long, and you get a reminder shortly before it runs out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                    val choices = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours")
                    choices.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { (minutes, label) ->
                                Surface(
                                    onClick = {
                                        ParkingTimer.set(context, name, minutes)
                                        timerEnd = ParkingTimer.endsAt(context)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = c.accentTint,
                                    contentColor = c.accentText,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.ink2) } },
    )
}

private fun distanceText(meters: Float): String =
    if (meters < 950) "About ${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} m"
    else String.format(java.util.Locale.US, "About %.1f km", meters / 1000f)

/** From the phone's last known place to the car, when the app may see the phone's location. */
@SuppressLint("MissingPermission")
private fun distanceFromPhone(context: Context, latitude: Double, longitude: Double): Float? {
    val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!allowed) return null
    return runCatching {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val me = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        // An old fix says where the phone was, not where it is
        if (System.currentTimeMillis() - me.time > 10 * 60_000L) return null
        val result = FloatArray(1)
        Location.distanceBetween(me.latitude, me.longitude, latitude, longitude, result)
        result[0]
    }.getOrNull()
}
