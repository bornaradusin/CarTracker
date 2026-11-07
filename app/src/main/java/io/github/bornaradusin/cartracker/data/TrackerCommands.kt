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
    SPEEDING_ON("Speeding alert on"),
    SPEEDING_OFF("Speeding alert off"),
    IGNITION_ON("Ignition alert on"),
    IGNITION_OFF("Ignition alert off"),
    CHECK_STATUS("Tracker status"),
    CHECK_SETTINGS("Tracker settings"),
    CHECK_ALARMS("Tracker alarm settings"),
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

    const val DEFAULT_SPEED_KMH = 120
    const val DEFAULT_LOW_VOLTS = 11.5

    /** The tracker's low battery alert takes 9 to 95 V; car batteries need 9 to 30. */
    const val MIN_LOW_VOLTS = 9.0
    const val MAX_LOW_VOLTS = 30.0

    fun volts(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    fun kind(prefs: DevicePrefs, position: Position?): TrackerKind = when (prefs.trackerType) {
        "micodus" -> TrackerKind.MICODUS
        "generic" -> TrackerKind.GENERIC
        else -> if (position?.protocol in setOf("jt808", "huabao")) TrackerKind.MICODUS else TrackerKind.GENERIC
    }

    /** True if [action] can be sent to this tracker. [types] are the command types Traccar offers for it. */
    fun supports(kind: TrackerKind, action: Action, types: Set<String>): Boolean =
        command(kind, action, 0, types) != null

    /**
     * The command JSON for [action], or null when this tracker can't do it. [speedKmh] is the limit for
     * [Action.SPEEDING_ON], [lowVolts] the threshold for [Action.LOW_BATTERY_ON].
     */
    fun command(
        kind: TrackerKind,
        action: Action,
        deviceId: Long,
        types: Set<String>,
        speedKmh: Int = DEFAULT_SPEED_KMH,
        lowVolts: Double = DEFAULT_LOW_VOLTS,
    ): JsonObject? {
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
                Action.LOW_BATTERY_ON -> "LVALM,ON,${volts(lowVolts.coerceIn(MIN_LOW_VOLTS, MAX_LOW_VOLTS))},0#"
                Action.LOW_BATTERY_OFF -> "LVALM,OFF#"
                Action.UNPLUGGED_ON -> "PWRALM,ON,0#"
                Action.UNPLUGGED_OFF -> "PWRALM,OFF#"
                // The last 0 means "report to the server only": the tracker's own default also sends SMS
                Action.SPEEDING_ON -> "SPEED,ON,${speedKmh.coerceIn(1, 255)},0#"
                Action.SPEEDING_OFF -> "SPEED,OFF#"
                // 2: both when the ignition goes on and when it goes off
                Action.IGNITION_ON -> "ACCALM,ON,2,0#"
                Action.IGNITION_OFF -> "ACCALM,OFF#"
                // Questions: the tracker answers in text, shown in a dialog and in the Alerts list
                Action.CHECK_STATUS -> "STATUS#"
                Action.CHECK_SETTINGS -> "PARAM#"
                Action.CHECK_ALARMS -> "ALARM#"
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

    /**
     * Traccar's commands that can be sent from a list: they need nothing but their type (the others
     * need values, like a server address, and fail or send garbage without them), leaving out the
     * ones the app's own buttons already send. For MiCODUS
     * trackers only the ones that work and are safe: Traccar's engine stop / resume and arm / disarm
     * are ignored (the app's own buttons send RELAY and ARM#), its restart duplicates Restart tracker,
     * and factory reset or power off leave the tracker unreachable except by SMS.
     */
    fun listableTypes(kind: TrackerKind, types: Set<String>): List<String> {
        val allowed = if (kind == TrackerKind.MICODUS) micodusTypes else plainTypes
        return types.filter { it in allowed }.sorted()
    }

    // Locate once, restart, engine stop / resume and arm / disarm are the app's own buttons for these trackers
    private val plainTypes = setOf(
        "positionStop", "deviceIdentification", "alarmDismiss", "requestPhoto", "powerOff",
        "factoryReset", "configuration", "getVersion", "getModemStatus", "getDeviceStatus",
    )
    private val micodusTypes = setOf("positionSingle", "alarmDismiss")

    /** Why a Traccar command needs a second tap, or null when it doesn't. */
    fun typeWarning(type: String): String? = when (type) {
        "powerOff" -> "The tracker turns off and stops reporting until it's powered on again by hand."
        "factoryReset" -> "Every setting on the tracker is wiped, including the server address, so it may stop reporting here."
        else -> null
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

    /**
     * A tracker reply as someone would say it: MiCODUS answers in short codes ("SF OK" when the alarm
     * goes on, "CF OK" when it goes off), so the ones the app sends are written out in plain words.
     * Anything else is shown as the tracker wrote it, tidied up.
     */
    fun friendlyReply(reply: String): String {
        val text = reply.replace("\u0000", "").trim()
        // Long answers (status, settings) carry details, so they stay as they are
        if (text.isEmpty() || isFailure(text) || text.length > 48 || '\n' in text) return readableReply(text)
        val upper = text.uppercase()
        return knownReplies.firstOrNull { (pattern, _) -> pattern.containsMatchIn(upper) }?.second ?: readableReply(text)
    }

    private val knownReplies = listOf(
        Regex("^SF\\s*OK") to "Alarm turned on",
        Regex("^CF\\s*OK") to "Alarm turned off",
        Regex("RELAY\\s*[,:=]?\\s*2\\b") to "Fuel will be cut when the car slows down",
        // Restore before cut: some trackers word it "resume fuel" or "cancel cut off fuel"
        Regex(
            "RELAY\\s*[,:=]?\\s*0\\b|(RESTORE|RESUME|RECOVER)\\w*\\s*(THE\\s*)?(FUEL|OIL|POWER)|" +
                "(FUEL|OIL)\\s*(SUPPLY\\s*)?(IS\\s*)?(RESTORED|RESUMED|RECOVERED)|(CANCEL|RELEASE)\\w*\\s*(THE\\s*)?(CUT|FUEL|OIL)",
        ) to "Fuel restored",
        Regex("RELAY\\s*[,:=]?\\s*1\\b|CUT\\s*OFF\\s*(THE\\s*)?(FUEL|OIL|POWER)|(FUEL|OIL)\\s*(SUPPLY\\s*)?(IS\\s*)?CUT") to "Fuel cut",
        Regex("ACCARM\\s*[,:=]?\\s*ON") to "Arms automatically after the ignition goes off",
        Regex("ACCARM\\s*[,:=]?\\s*OFF") to "Stopped arming automatically",
        Regex("SENALM\\s*[,:=]?\\s*ON|VIBRAT\\w*\\s*ALARM\\s*(IS\\s*)?(ON|OPEN|ENABLE)") to "Vibration alert turned on",
        Regex("SENALM\\s*[,:=]?\\s*OFF|VIBRAT\\w*\\s*ALARM\\s*(IS\\s*)?(OFF|CLOSE|DISABLE)") to "Vibration alert turned off",
        Regex("LVALM\\s*[,:=]?\\s*ON") to "Low battery alert turned on",
        Regex("LVALM\\s*[,:=]?\\s*OFF") to "Low battery alert turned off",
        Regex("PWRALM\\s*[,:=]?\\s*ON") to "Unplugged alert turned on",
        Regex("PWRALM\\s*[,:=]?\\s*OFF") to "Unplugged alert turned off",
        Regex("^(SET\\s*)?(OVER\\s*)?SPEED\\w*\\s*(ALARM\\s*)?(IS\\s*)?[,:=]?\\s*(ON|OPEN|ENABLE)") to "Speeding alert turned on",
        Regex("^(SET\\s*)?(OVER\\s*)?SPEED\\w*\\s*(ALARM\\s*)?(IS\\s*)?[,:=]?\\s*(OFF|CLOSE|DISABLE)") to "Speeding alert turned off",
        Regex("ACCALM\\s*[,:=]?\\s*ON|ACC\\s*ALARM\\s*(IS\\s*)?(ON|OPEN|ENABLE)") to "Ignition alert turned on",
        Regex("ACCALM\\s*[,:=]?\\s*OFF|ACC\\s*ALARM\\s*(IS\\s*)?(OFF|CLOSE|DISABLE)") to "Ignition alert turned off",
        Regex("^TIMER\\b.*OK") to "Update rate changed",
        Regex("^RESTART\\b|^RESET\\b.*OK") to "Tracker restarting",
    )

    /**
     * What a known reply says was changed, as the change key and its new state ("SF OK" →
     * alarm on); null for replies that don't say.
     */
    fun replyAbout(reply: String): Pair<String, Boolean>? = when (friendlyReply(reply)) {
        "Alarm turned on" -> PendingChange.ALARM to true
        "Alarm turned off" -> PendingChange.ALARM to false
        "Fuel cut" -> PendingChange.FUEL to true
        "Fuel restored" -> PendingChange.FUEL to false
        "Arms automatically after the ignition goes off" -> PendingChange.AUTO_ARM to true
        "Stopped arming automatically" -> PendingChange.AUTO_ARM to false
        "Vibration alert turned on" -> AlertKind.VIBRATION.key to true
        "Vibration alert turned off" -> AlertKind.VIBRATION.key to false
        "Low battery alert turned on" -> AlertKind.LOW_BATTERY.key to true
        "Low battery alert turned off" -> AlertKind.LOW_BATTERY.key to false
        "Unplugged alert turned on" -> AlertKind.UNPLUGGED.key to true
        "Unplugged alert turned off" -> AlertKind.UNPLUGGED.key to false
        "Speeding alert turned on" -> AlertKind.SPEEDING.key to true
        "Speeding alert turned off" -> AlertKind.SPEEDING.key to false
        "Ignition alert turned on" -> AlertKind.IGNITION.key to true
        "Ignition alert turned off" -> AlertKind.IGNITION.key to false
        else -> null
    }

    /** True when a reply says the command didn't work ("ERROR", "FAILED", "INVALID"…). */
    fun isFailure(reply: String): Boolean =
        Regex("\\b(error|fail|failed|failure|invalid|wrong|not support(ed)?|unknown)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(reply)

    /** The text a custom command sends (as typed or texted to the tracker), or null for other commands. */
    fun customText(command: JsonObject): String? {
        if (command.str("type") != "custom") return null
        val data = (command["attributes"] as? JsonObject)?.str("data") ?: return null
        if (data.length % 2 != 0 || !data.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return data
        return runCatching { String(data.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.US_ASCII) }.getOrNull()
    }

    fun hex(text: String): String = text.toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) }

    /** Readable names for Traccar's command types, for the advanced list. */
    fun typeName(type: String): String = when (type) {
        "custom" -> "Custom command"
        "engineStop" -> "Engine stop"
        "engineResume" -> "Engine resume"
        "alarmArm" -> "Arm alarm"
        "alarmDisarm" -> "Disarm alarm"
        "positionSingle" -> "Locate once (position on the map)"
        "alarmDismiss" -> "Clear the tracker's alarm"
        "deviceIdentification" -> "Identify tracker"
        "configuration" -> "Read settings"
        "getVersion" -> "Firmware version"
        "getModemStatus" -> "Modem status"
        "getDeviceStatus" -> "Tracker status"
        "factoryReset" -> "Factory reset"
        "powerOff" -> "Turn tracker off"
        "positionPeriodic" -> "Report periodically"
        "positionStop" -> "Stop reporting"
        "rebootDevice" -> "Restart tracker"
        "requestPhoto" -> "Take a photo"
        "setTimezone" -> "Set time zone"
        "outputControl" -> "Output control"
        else -> type.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }
}
