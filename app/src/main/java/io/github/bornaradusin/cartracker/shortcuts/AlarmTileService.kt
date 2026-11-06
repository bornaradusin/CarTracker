package io.github.bornaradusin.cartracker.shortcuts

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.bornaradusin.cartracker.R
import io.github.bornaradusin.cartracker.container
import io.github.bornaradusin.cartracker.data.Action
import io.github.bornaradusin.cartracker.data.AppSettings
import io.github.bornaradusin.cartracker.data.CommandTarget
import io.github.bornaradusin.cartracker.data.Device
import io.github.bornaradusin.cartracker.data.PendingChange
import io.github.bornaradusin.cartracker.data.Position
import io.github.bornaradusin.cartracker.data.TrackerCommands
import io.github.bornaradusin.cartracker.data.TrackerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The "Car alarm" tile in quick settings: shows whether the alarm is on and turns it on or off.
 * The tile changes once the tracker confirms, the same as in the app. A locked phone is unlocked first.
 */
class AlarmTileService : TileService() {

    private var scope: CoroutineScope? = null

    private data class State(val target: CommandTarget?, val armed: Boolean, val supported: Boolean, val pending: Boolean)

    @Volatile private var state = State(null, armed = false, supported = false, pending = false)

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope?.cancel()
        scope = s
        val c = container
        s.launch {
            if (!c.api.signedIn) c.ready()
            if (c.api.signedIn && !c.live.connected.value) runCatching { c.live.refresh() }
            val settings = c.settings.current()
            val devices = c.live.devices.value
            (devices.firstOrNull { it.id == settings.selectedDeviceId } ?: devices.firstOrNull())?.let { device ->
                launch { runCatching { c.commands.optionsFor(device.id) } }
            }
            combine(c.live.devices, c.live.positions, c.settings.flow, c.commands.pending, c.commands.options) { devices, positions, settings, pending, _ ->
                stateOf(devices, positions, settings, pending)
            }.collect { show(it) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        val current = state
        val target = current.target ?: return
        if (!current.supported || current.pending || !target.online) return
        val c = container
        c.scope.launch {
            c.commands.optionsFor(target.id)
            c.commands.send(target, if (current.armed) Action.DISARM else Action.ARM)
        }
    }

    private fun stateOf(devices: List<Device>, positions: Map<Long, Position>, settings: AppSettings, pending: Map<Long, PendingChange>): State {
        if (!settings.signedIn) return State(null, armed = false, supported = false, pending = false)
        val device = devices.firstOrNull { it.id == settings.selectedDeviceId } ?: devices.firstOrNull()
            ?: return State(null, armed = false, supported = false, pending = false)
        val p = positions[device.id]
        val prefs = settings.prefs(device.id)
        val kind = TrackerCommands.kind(prefs, p)
        // The tracker's own report when it sends one; otherwise the last change it confirmed
        val armed = p?.armed ?: prefs.manualArmed ?: (kind == TrackerKind.MICODUS && prefs.autoArm && p?.ignition == false)
        val target = CommandTarget(device.id, device.name, kind, device.online || device.status == null)
        return State(
            target = target,
            armed = armed,
            supported = container.commands.supports(target, Action.ARM),
            pending = pending[device.id]?.key == PendingChange.ALARM,
        )
    }

    private fun show(s: State) {
        state = s
        val tile = qsTile ?: return
        val target = s.target
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_alarm)
        tile.label = target?.name ?: getString(R.string.tile_alarm)
        val subtitle = when {
            target == null -> "Sign in to CarTracker"
            !s.supported -> "Not available"
            s.pending -> "Waiting for the tracker…"
            !target.online -> "Offline"
            s.armed -> "Alarm on"
            else -> "Alarm off"
        }
        tile.contentDescription = "${tile.label}: $subtitle"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
        tile.state = when {
            target == null || !s.supported || !target.online -> Tile.STATE_UNAVAILABLE
            s.armed -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
