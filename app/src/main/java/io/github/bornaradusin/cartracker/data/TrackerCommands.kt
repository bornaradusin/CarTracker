package io.github.bornaradusin.cartracker.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The things the app can ask a tracker to do, and how each is said to it.
 *
 * MiCODUS trackers (JT808, like the MV710G) take their SMS commands over the data connection when
 * Traccar sends them as a "custom" command in hex, so those are sent as the manual's text, hex-encoded.
 * Fuel cut and restore use RELAY too: Traccar's engineStop / engineResume never reach these trackers.
 * Any other tracker gets Traccar's standard commands, where its protocol supports them.
 */
enum class TrackerKind { MICODUS, GENERIC }

enum class Action(val label: String) {
    CUT_NOW("Cut fuel"),
    CUT_SLOW("Cut fuel when slow"),
    RESTORE("Restore fuel"),
    ARM("Alarm on"),
    DISARM("Alarm off"),
    AUTO_ARM_ON("Arm automatically"),
    AUTO_ARM_OFF("Stop arming automatically"),
    VIBRATION_ON("Vibration alert on"),
    VIBRATION_OFF("Vibration alert off"),
    LOW_BATTERY_ON("Low battery alert on"),
    LOW_BATTERY_OFF("Low battery alert off"),
    UNPLUGGED_ON("Unplugged alert on"),
    UNPLUGGED_OFF("Unplugged alert off"),
    LOCATE("Locate now"),
    RESTART("Restart tracker"),
}

/** How often the tracker reports. */
data class UpdateRate(val drivingSeconds: Int, val parkedSeconds: Int) {
    val label: String get() = "${fmt(drivingSeconds)} driving · ${fmt(parkedSeconds)} parked"
    private fun fmt(s: Int) = if (s < 60) "$s s" else "${s / 60} min"

    companion object {
        val choices = listOf(
            UpdateRate(5, 60),
            UpdateRate(10, 180),
            UpdateRate(30, 300),
            UpdateRate(60, 600),
        )
    }
}

object TrackerCommands {

    fun kind(prefs: DevicePrefs, position: Position?): TrackerKind = when (prefs.trackerType) {
        "micodus" -> TrackerKind.MICODUS
        "generic" -> TrackerKind.GENERIC
        else -> if (position?.protocol in setOf("jt808", "huabao")) TrackerKind.MICODUS else TrackerKind.GENERIC
    }

    /** True if [action] can be sent to this tracker. [types] are the command types Traccar offers for it. */
    fun supports(kind: TrackerKind, action: Action, types: Set<String>): Boolean =
        command(kind, action, 0, types) != null

    /** The command JSON for [action], or null when this tracker can't do it. */
    fun command(kind: TrackerKind, action: Action, deviceId: Long, types: Set<String>): JsonObject? {
        if (kind == TrackerKind.MICODUS) {
            val text = when (action) {
                // Traccar's Engine stop/resume never reach MiCODUS trackers: they ignore that JT808 message
                Action.CUT_NOW -> "RELAY,1#"
                Action.RESTORE -> "RELAY,0#"
                Action.CUT_SLOW -> "RELAY,2#"
                Action.ARM -> "ARM#"
                Action.DISARM -> "DISARM#"
                Action.AUTO_ARM_ON -> "ACCARM,ON,60#"
                Action.AUTO_ARM_OFF -> "ACCARM,OFF#"
                Action.VIBRATION_ON -> "SENALM,ON,0#"
                Action.VIBRATION_OFF -> "SENALM,OFF#"
                Action.LOW_BATTERY_ON -> "LVALM,ON,11.5,0#"
                Action.LOW_BATTERY_OFF -> "LVALM,OFF#"
                Action.UNPLUGGED_ON -> "PWRALM,ON,0#"
                Action.UNPLUGGED_OFF -> "PWRALM,OFF#"
                Action.LOCATE -> "WHERE#"
                Action.RESTART -> "RESTART#"
            }
            return custom(deviceId, text)
        }
        val type = when (action) {
            Action.CUT_NOW -> "engineStop"
            Action.RESTORE -> "engineResume"
            Action.ARM -> "alarmArm"
            Action.DISARM -> "alarmDisarm"
            Action.LOCATE -> "positionSingle"
            Action.RESTART -> "rebootDevice"
            else -> return null
        }
        return if (type in types) typed(deviceId, type) else null
    }

    fun updateRate(kind: TrackerKind, rate: UpdateRate, deviceId: Long, types: Set<String>): JsonObject? = when (kind) {
        TrackerKind.MICODUS -> custom(deviceId, "TIMER,${rate.drivingSeconds},${rate.parkedSeconds}#")
        TrackerKind.GENERIC -> if ("positionPeriodic" in types) {
            buildJsonObject {
                put("deviceId", deviceId)
                put("type", "positionPeriodic")
                put("textChannel", false)
                putJsonObject("attributes") { put("frequency", rate.drivingSeconds) }
            }
        } else null
    }

    fun custom(deviceId: Long, text: String): JsonObject = buildJsonObject {
        put("deviceId", deviceId)
        put("type", "custom")
        put("textChannel", false)
        putJsonObject("attributes") { put("data", hex(text)) }
    }

    fun typed(deviceId: Long, type: String): JsonObject = buildJsonObject {
        put("deviceId", deviceId)
        put("type", type)
        put("textChannel", false)
        putJsonObject("attributes") {}
    }

    /**
     * Accounts limited to saved commands can only send those: if the server has a saved command that
     * does the same thing, send that one (by its id) instead.
     */
    fun preferSaved(command: JsonObject, saved: List<SavedCommand>, deviceId: Long): JsonObject {
        val type = command.str("type") ?: return command
        val data = (command["attributes"] as? JsonObject)?.str("data")?.lowercase()
        val match = saved.firstOrNull { s ->
            s.type == type && !s.textChannel &&
                (type != "custom" || s.attributes.str("data")?.lowercase() == data)
        } ?: return command
        return buildJsonObject {
            put("id", match.id)
            put("deviceId", deviceId)
            put("type", match.type)
            put("textChannel", false)
            put("attributes", match.attributes)
        }
    }

    /**
     * A tracker's reply in plain words: MiCODUS answers in capitals ("SET VIBRATE ALARM OK"),
     * which reads better as a sentence ("Set vibrate alarm OK").
     */
    fun readableReply(reply: String): String {
        val text = reply.replace("\u0000", "").trim()
        if (text.isEmpty() || text.any { it.isLowerCase() }) return text
        return text.lines().joinToString("\n") { line ->
            line.lowercase()
                .replaceFirstChar { it.uppercase() }
                .replace(Regex("\\b(ok|gps|acc|sos|apn|imei|iccid|id|gmt|gprs|sms|km/h)\\b")) { it.value.uppercase() }
        }
    }

    /** True when a reply says the command didn't work ("ERROR", "FAILED", "INVALID"…). */
    fun isFailure(reply: String): Boolean =
        Regex("\\b(error|fail|failed|failure|invalid|wrong|not support(ed)?|unknown)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(reply)

    fun hex(text: String): String = text.toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) }

    /** Readable names for Traccar's command types, for the advanced list. */
    fun typeName(type: String): String = when (type) {
        "custom" -> "Custom command"
        "engineStop" -> "Engine stop"
        "engineResume" -> "Engine resume"
        "alarmArm" -> "Arm alarm"
        "alarmDisarm" -> "Disarm alarm"
        "positionSingle" -> "Locate once"
        "positionPeriodic" -> "Report periodically"
        "positionStop" -> "Stop reporting"
        "rebootDevice" -> "Restart tracker"
        "requestPhoto" -> "Take a photo"
        "setTimezone" -> "Set time zone"
        "outputControl" -> "Output control"
        else -> type.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }
}
