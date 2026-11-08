package io.github.bornaradusin.cartracker.ui.car

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.CarInfo
import io.github.bornaradusin.cartracker.ui.LiveLink
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** Share the car: where it is now, or a live link that others can follow in a browser for a while. */
@Composable
internal fun ShareDialog(vm: AppViewModel, info: CarInfo, onSendSpot: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var working by remember { mutableStateOf(false) }
    val running = vm.currentLiveLink(info)
    val name = info.device.name

    fun send(link: LiveLink) {
        shareText(context, "Follow $name live ${Format.until(link.until)}: ${link.url}", "Share the live link")
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = c.surface,
        title = { Text("Share $name", color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShareOption(Ic.pin, "Where it is now", "A map link to the spot it's at", enabled = !working && info.position?.hasFix == true, onClick = onSendSpot)
                Text("LIVE LINK", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.muted, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Anyone with the link can follow $name on a map in their browser until it ends. They can't control the car or see its trips.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
                if (running != null) {
                    ShareOption(Ic.share, "Share the running link", "Works ${Format.until(running.until)}", enabled = !working) { send(running) }
                    ShareOption(Ic.close, "Stop the link now", "Whoever has it can't follow $name any more", enabled = !working) {
                        vm.stopLiveLink(info)
                        onDismiss()
                    }
                }
                val choices = listOf(1L to "1 hour", 4L to "4 hours", 24L to "1 day", 168L to "1 week")
                choices.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (hours, label) ->
                            Surface(
                                onClick = {
                                    working = true
                                    // In the app's scope, so closing the dialog or rotating doesn't lose a link the server made
                                    vm.viewModelScope.launch {
                                        val link = vm.liveLink(info, hours)
                                        working = false
                                        if (link != null) send(link)
                                    }
                                },
                                enabled = !working,
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
                if (working) LinearProgressIndicator(color = c.accentOutline, trackColor = c.line, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Close", color = c.ink2) } },
    )
}

@Composable
internal fun ShareOption(icon: ImageVector, title: String, hint: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), color = c.surface2, contentColor = c.ink, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = c.accentText, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

private fun shareText(context: Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
