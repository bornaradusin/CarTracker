package io.github.bornaradusin.cartracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/** What a tracker answered to a command. */
data class TrackerReply(val car: String, val command: String, val text: String)

/** A tracker setting sent to the car, applied in the app only once the tracker confirms it. */
data class PendingChange(val key: String, val on: Boolean) {
    companion object {
        const val ALARM = "alarm"
        const val AUTO_ARM = "auto_arm"
        const val FUEL = "fuel"
    }
}

/** Commands this account may use for a car: Traccar's types for it and its saved commands. */
data class CommandOptions(val types: Set<String> = emptySet(), val saved: List<SavedCommand> = emptyList(), val loaded: Boolean = false)

/** The car a command goes to. */
data class CommandTarget(val id: Long, val name: String, val kind: TrackerKind, val online: Boolean)

/**
 * Sends commands to the trackers and waits for their answers. Shared by the app's screens and the
 * alarm tile in quick settings, so both see the same "waiting for the tracker" state.
 */
class CommandCenter(
    private val api: TraccarApi,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _options = MutableStateFlow<Map<Long, CommandOptions>>(emptyMap())
    val options: StateFlow<Map<Long, CommandOptions>> = _options.asStateFlow()

    private val _pending = MutableStateFlow<Map<Long, PendingChange>>(emptyMap())

    /** Per car: the setting sent to its tracker and not yet confirmed. */
    val pending: StateFlow<Map<Long, PendingChange>> = _pending.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)

    /** What's being sent right now ("Cut fuel"…), for a spinner. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _reply = MutableStateFlow<TrackerReply?>(null)

    /** A tracker reply too long for a snackbar (status, settings), shown in a dialog. */
    val reply: StateFlow<TrackerReply?> = _reply.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Short messages for the app to show. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val watchers = mutableMapOf<Long, Job>()

    /** Loads (again) the commands this account may send to a car. */
    suspend fun load(deviceId: Long): CommandOptions {
        val types = runCatching { api.commandTypes(deviceId).map { it.type }.toSet() }.getOrDefault(emptySet())
        val saved = runCatching { api.savedCommands(deviceId).filter { it.id != 0L } }.getOrDefault(emptyList())
        return CommandOptions(types, saved, true).also { o -> _options.update { it + (deviceId to o) } }
    }

    suspend fun optionsFor(deviceId: Long): CommandOptions =
        _options.value[deviceId]?.takeIf { it.loaded } ?: load(deviceId)

    fun supports(target: CommandTarget, action: Action): Boolean {
        val o = _options.value[target.id] ?: return target.kind == TrackerKind.MICODUS
        return TrackerCommands.supports(target.kind, action, o.types)
    }

    fun send(target: CommandTarget, action: Action, quiet: Boolean = false): Job = scope.launch {
        val o = optionsFor(target.id)
        val command = TrackerCommands.command(target.kind, action, target.id, o.types)
        if (command == null) {
            say("${target.name}'s tracker can't do that.")
            return@launch
        }
        val change = when (action) {
            Action.CUT_NOW, Action.RESTORE -> PendingChange(PendingChange.FUEL, action == Action.CUT_NOW)
            Action.ARM, Action.DISARM -> PendingChange(PendingChange.ALARM, action == Action.ARM)
            Action.AUTO_ARM_ON, Action.AUTO_ARM_OFF -> PendingChange(PendingChange.AUTO_ARM, action == Action.AUTO_ARM_ON)
            Action.VIBRATION_ON, Action.VIBRATION_OFF -> PendingChange(AlertKind.VIBRATION.key, action == Action.VIBRATION_ON)
            Action.LOW_BATTERY_ON, Action.LOW_BATTERY_OFF -> PendingChange(AlertKind.LOW_BATTERY.key, action == Action.LOW_BATTERY_ON)
            Action.UNPLUGGED_ON, Action.UNPLUGGED_OFF -> PendingChange(AlertKind.UNPLUGGED.key, action == Action.UNPLUGGED_ON)
            else -> null
        }
        // MiCODUS trackers answer every command, so their settings wait for that answer.
        // Other trackers may never answer, so theirs change straight away.
        if (change == null || target.kind != TrackerKind.MICODUS) {
            if (deliver(target, action.label, command, quiet) && change != null) applyChange(target, change)
            return@launch
        }
        // Fuel can wait for the tracker to reconnect (Traccar keeps the command); the rest can't
        if (!target.online && change.key != PendingChange.FUEL) {
            say("${target.name} is offline, so nothing was changed. Try again once it's back online.")
            return@launch
        }
        deliver(target, action.label, command, quiet = false, change = change)
    }

    fun setUpdateRate(target: CommandTarget, rate: UpdateRate): Job = scope.launch {
        val o = optionsFor(target.id)
        val command = TrackerCommands.updateRate(target.kind, rate, target.id, o.types)
        if (command == null) say("${target.name}'s tracker can't change that from here.")
        else deliver(target, "Update rate", command, quiet = false)
    }

    /** A saved command, a Traccar type, or custom text from the advanced list. */
    fun sendAdvanced(target: CommandTarget, label: String, command: JsonObject): Job = scope.launch {
        deliver(target, label, command, quiet = false, preferSaved = false)
    }

    fun customCommand(target: CommandTarget, text: String) {
        if (text.isBlank()) return
        val command = if (target.kind == TrackerKind.MICODUS) {
            TrackerCommands.custom(target.id, text.trim())
        } else {
            buildJsonObject {
                put("deviceId", JsonPrimitive(target.id))
                put("type", JsonPrimitive("custom"))
                put("textChannel", JsonPrimitive(false))
                put("attributes", JsonObject(mapOf("data" to JsonPrimitive(text.trim()))))
            }
        }
        sendAdvanced(target, text.trim(), command)
    }

    /** After signing out: the next server has other cars and commands. */
    fun clear() {
        watchers.values.forEach { it.cancel() }
        watchers.clear()
        _pending.value = emptyMap()
        _reply.value = null
        _options.value = emptyMap()
    }

    fun dismissReply() {
        _reply.value = null
    }

    /** Saves a tracker setting the tracker has confirmed (or that can't be confirmed). */
    private suspend fun applyChange(target: CommandTarget, change: PendingChange) {
        when (change.key) {
            PendingChange.ALARM -> settings.updateDevice(target.id) {
                it.copy(manualArmed = change.on, manualAt = System.currentTimeMillis())
            }
            PendingChange.AUTO_ARM -> settings.updateDevice(target.id) { it.copy(autoArm = change.on) }
            PendingChange.FUEL -> settings.updateDevice(target.id) {
                it.copy(fuelCut = change.on, fuelAt = System.currentTimeMillis())
            }
            else -> AlertKind.entries.firstOrNull { it.key == change.key }?.let { settings.setAlertKind(it, change.on) }
        }
    }

    private suspend fun deliver(
        target: CommandTarget,
        label: String,
        command: JsonObject,
        quiet: Boolean,
        preferSaved: Boolean = true,
        change: PendingChange? = null,
    ): Boolean {
        if (_pending.value.containsKey(target.id)) {
            say("Still waiting for ${target.name} to confirm the last change. Try again in a moment.")
            return false
        }
        // A second tap while the first is still being sent does nothing
        if (_busy.value != null) return false
        // Marked as waiting before anything is sent, so a quick second tap (or the tile) can't send it twice
        if (change != null) _pending.update { it + (target.id to change) }
        _busy.value = label
        var watching = false
        return try {
            val o = optionsFor(target.id)
            val toSend = if (preferSaved) TrackerCommands.preferSaved(command, o.saved, target.id) else command
            val sentAt = Instant.now()
            // Only replies newer than this one count as the answer
            val lastReply = runCatching {
                api.events(listOf(target.id), sentAt.minusSeconds(3_600), sentAt.plusSeconds(60))
                    .filter { it.type == "commandResult" }
                    .maxOfOrNull { it.id }
            }.getOrNull() ?: 0L
            val now = api.sendCommand(toSend)
            if (!quiet && !now) {
                say(
                    if (change != null) "${target.name} went offline. It gets this when it reconnects, and the setting changes once it confirms."
                    else "${target.name} is offline. It gets this when it next connects.",
                )
            }
            // A setting sent straight away is sent again if the tracker doesn't answer: they're safe to repeat
            watchReply(target, label, sentAt, lastReply, if (now) 100_000L else 15 * 60_000L, change, toSend.takeIf { now && change != null })
            watching = true
            true
        } catch (e: ApiException) {
            say(
                when (e.code) {
                    400, 403 -> "Your account isn't allowed to send that. Ask the server's admin, or add it as a saved command."
                    else -> e.message ?: "Couldn't send that."
                },
            )
            false
        } catch (e: Exception) {
            say("Couldn't reach the server.")
            false
        } finally {
            _busy.value = null
            if (change != null && !watching) _pending.update { if (it[target.id] === change) it - target.id else it }
        }
    }

    /**
     * Waits for the tracker to answer [label] (Traccar records the answer as a "commandResult"
     * event) and shows it. A [change] is saved only once the tracker confirms it; with no answer
     * in time, nothing changes.
     */
    private fun watchReply(
        target: CommandTarget,
        label: String,
        sentAt: Instant,
        lastReply: Long,
        windowMs: Long,
        change: PendingChange?,
        resend: JsonObject?,
    ) {
        val id = target.id
        watchers.remove(id)?.cancel()
        watchers[id] = scope.launch {
            try {
                val from = sentAt.minusSeconds(5)
                var until = System.currentTimeMillis() + windowMs
                var resent = 0
                while (isActive && System.currentTimeMillis() < until) {
                    delay(3_000)
                    // Trackers asleep or between connections can miss a command: try again at 20 and 50 seconds
                    val waited = System.currentTimeMillis() - sentAt.toEpochMilli()
                    if (resend != null && resent < RESEND_AFTER.size && waited >= RESEND_AFTER[resent]) {
                        resent++
                        val sent = runCatching { api.sendCommand(resend) }.getOrDefault(true)
                        if (!sent) {
                            // The tracker dropped off: Traccar keeps the command for when it reconnects
                            resent = RESEND_AFTER.size
                            until = System.currentTimeMillis() + 15 * 60_000L
                            say("${target.name} went offline. It gets this when it reconnects, and the app shows the change once it confirms.")
                        }
                    }
                    val raw = runCatching { api.events(listOf(id), from, Instant.now().plusSeconds(60)) }
                        .getOrNull()
                        .orEmpty()
                        .filter { it.type == "commandResult" && it.id > lastReply }
                        .map { it.attributes.str("result").orEmpty() }
                        // An answer about something else (a late reply to an earlier command) isn't this one's
                        .lastOrNull { reply -> change == null || TrackerCommands.replyAbout(reply).let { it == null || it == change.key to change.on } }
                        ?: continue
                    val text = TrackerCommands.friendlyReply(raw)
                    if (text.isEmpty()) continue
                    val failed = TrackerCommands.isFailure(raw)
                    if (change != null && !failed) applyChange(target, change)
                    val shown = if (change != null && failed) "$text. Nothing was changed." else text
                    // Settings show their new state on screen, so only longer answers (status, settings) need a dialog
                    if (shown.length > 60 || '\n' in shown) _reply.value = TrackerReply(target.name, label, shown)
                    else if (change == null || failed) say("${target.name}: $shown")
                    return@launch
                }
                if (change != null && isActive) {
                    say(
                        if (change.key == PendingChange.FUEL) "${target.name} didn't confirm. Check it's online, then try again."
                        else "${target.name} didn't confirm, so nothing was changed.",
                    )
                }
            } finally {
                if (change != null) _pending.update { if (it[id] === change) it - id else it }
            }
        }
    }

    private fun say(message: String) {
        _messages.tryEmit(message)
    }

    private companion object {
        val RESEND_AFTER = longArrayOf(20_000, 50_000)
    }
}
