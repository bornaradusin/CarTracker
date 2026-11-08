package io.github.bornaradusin.cartracker.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.bornaradusin.cartracker.BuildConfig
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.UpdateState
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import kotlin.math.roundToInt

/** A newer CarTracker from GitHub: what's new, then download, check and install it. */
@Composable
fun UpdateDialog(vm: AppViewModel, state: UpdateState) {
    val c = LocalAppColors.current

    // Back from the "install unknown apps" switch: carry on with the install
    LifecycleResumeEffect(state) {
        if (state is UpdateState.NeedsPermission) vm.continueUpdate()
        onPauseOrDispose { }
    }

    val title = when (state) {
        is UpdateState.Available -> "CarTracker ${state.update.version} is ready"
        is UpdateState.Downloading -> "CarTracker ${state.update.version} is ready"
        is UpdateState.NeedsPermission -> "Allow CarTracker to update itself"
        is UpdateState.Failed -> state.update?.let { "CarTracker ${it.version} is ready" } ?: "Updates"
        UpdateState.Checking -> "Looking for updates…"
        UpdateState.UpToDate -> "CarTracker is up to date"
        UpdateState.Idle -> "Updates"
    }

    AlertDialog(
        onDismissRequest = { vm.closeUpdate() },
        containerColor = c.surface,
        title = { Text(title, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is UpdateState.Available -> Notes(state.update.notes)
                    is UpdateState.Downloading -> {
                        Text("Downloading… ${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                        LinearProgressIndicator(progress = { state.progress }, color = c.accentOutline, trackColor = c.line, modifier = Modifier.fillMaxWidth())
                        Text("Android's installer opens when it's done. The download is deleted once the update is in.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    is UpdateState.NeedsPermission -> Text(
                        "The update is downloaded. Android asks you to allow CarTracker to install apps once: turn on “Allow from this source”, then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink2,
                    )
                    is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = c.warnText)
                    UpdateState.Checking -> LinearProgressIndicator(color = c.accentOutline, trackColor = c.line, modifier = Modifier.fillMaxWidth())
                    UpdateState.UpToDate -> Text("You have the newest version, ${BuildConfig.VERSION_NAME}.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    UpdateState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            Row {
                when (state) {
                    is UpdateState.Available -> {
                        TextButton(onClick = { vm.updateLater() }) { Text("Not now", color = c.ink2) }
                        TextButton(onClick = { vm.startUpdate() }) { Text("Update", color = c.accentText) }
                    }
                    is UpdateState.Downloading -> TextButton(onClick = { vm.cancelUpdate() }) { Text("Cancel", color = c.ink2) }
                    is UpdateState.NeedsPermission -> {
                        TextButton(onClick = { vm.updateLater() }) { Text("Not now", color = c.ink2) }
                        TextButton(onClick = { vm.allowInstalls() }) { Text("Allow", color = c.accentText) }
                    }
                    is UpdateState.Failed -> {
                        TextButton(onClick = { vm.closeUpdate() }) { Text("Close", color = c.ink2) }
                        TextButton(onClick = { if (state.update != null) vm.startUpdate() else vm.checkForUpdate(manual = true) }) {
                            Text("Try again", color = c.accentText)
                        }
                    }
                    UpdateState.Checking, UpdateState.UpToDate, UpdateState.Idle ->
                        TextButton(onClick = { vm.closeUpdate() }) { Text("OK", color = c.accentText) }
                }
            }
        },
    )
}

/** The release notes, scrolling if they're long. */
@Composable
private fun Notes(notes: String) {
    val c = LocalAppColors.current
    Text("You have ${BuildConfig.VERSION_NAME}.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    if (notes.isNotBlank()) {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Text(notes, style = MaterialTheme.typography.bodyMedium, color = c.ink)
        }
    }
}
