package io.github.bornaradusin.cartracker.data

/** The kinds of alert someone can switch on or off. Each covers one or more Traccar events. */
enum class AlertKind(val key: String, val title: String, val hint: String, val onByDefault: Boolean) {
    IGNITION("ignition", "Ignition on / off", "Every time the engine starts or stops", true),
    PLACES("places", "Arriving and leaving places", "Home and any other place you add", true),
    SPEEDING("speeding", "Speeding", "Over the limit you set", true),
    UNPLUGGED("unplugged", "Tracker unplugged", "Power to the tracker is cut", true),
    VIBRATION("vibration", "Vibration or movement", "Bumps, knocks or towing while parked", true),
    LOW_BATTERY("low_battery", "Low car battery", "The car's battery is running low", true),
    SOS("sos", "SOS and tampering", "Panic button, tracker removed or tampered with", true),
    OTHER_ALARMS("other_alarms", "Other tracker alarms", "Anything else the tracker raises", true),
    FUEL("fuel", "Tracker replies", "The tracker confirming a command, like fuel cut or alarm on", true),
    OFFLINE("offline", "Tracker offline and back online", "The tracker stops reporting, and when it's back", false),
    MOVING("moving", "Started and stopped moving", "Every trip's start and end", false);

    companion object {
        val defaults: Set<String> = entries.filter { it.onByDefault }.map { it.key }.toSet()

        fun of(event: Event): AlertKind? = when (event.type) {
            "ignitionOn", "ignitionOff" -> IGNITION
            "geofenceEnter", "geofenceExit" -> PLACES
            "deviceOverspeed" -> SPEEDING
            "deviceOffline", "deviceOnline" -> OFFLINE
            "deviceMoving", "deviceStopped" -> MOVING
            "commandResult", "queuedCommandSent" -> FUEL
            "alarm" -> when (event.attributes.str("alarm")) {
                "powerCut", "powerOff", "powerRestored" -> UNPLUGGED
                "vibration", "movement", "shock", "accident", "tow", "parking" -> VIBRATION
                "lowBattery", "lowPower" -> LOW_BATTERY
                "sos", "tampering", "removing", "door" -> SOS
                "overspeed" -> SPEEDING
                else -> OTHER_ALARMS
            }
            else -> null
        }
    }
}

/** Plain words for an event: "Ignition on", "Arrived at Home", "Tracker unplugged"… */
object EventText {

    fun title(event: Event, placeName: String? = null): String = when (event.type) {
        "ignitionOn" -> "Ignition on"
        "ignitionOff" -> "Ignition off"
        "geofenceEnter" -> if (placeName != null) "Arrived at $placeName" else "Arrived at a place"
        "geofenceExit" -> if (placeName != null) "Left $placeName" else "Left a place"
        "deviceOverspeed" -> event.attributes.num("speed")?.let { "Speeding: ${(it * 1.852).toInt()} km/h" } ?: "Speeding"
        "deviceOnline" -> "Tracker back online"
        "deviceOffline" -> "Tracker offline"
        "deviceUnknown" -> "Tracker not heard from"
        "deviceInactive" -> "Tracker inactive"
        "deviceMoving" -> "Started moving"
        "deviceStopped" -> "Stopped"
        "commandResult" -> event.attributes.str("result")?.let { "Tracker replied: ${it.take(80)}" } ?: "Tracker replied"
        "queuedCommandSent" -> "Waiting command sent to the tracker"
        "maintenance" -> "Service due"
        "textMessage" -> "Message from the tracker"
        "alarm" -> alarmTitle(event.attributes.str("alarm"))
        else -> event.type.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }

    fun isWarning(event: Event): Boolean = event.type == "alarm" || event.type == "deviceOverspeed"

    private fun alarmTitle(alarm: String?): String = when (alarm) {
        "powerCut", "powerOff" -> "Tracker unplugged"
        "powerRestored" -> "Tracker power restored"
        "vibration", "shock" -> "Vibration while parked"
        "movement", "tow" -> "Car moved while parked"
        "parking" -> "Moved while parked"
        "accident" -> "Possible accident"
        "lowBattery", "lowPower" -> "Low car battery"
        "sos" -> "SOS button pressed"
        "tampering" -> "Tracker tampered with"
        "removing" -> "Tracker removed"
        "door" -> "Door opened"
        "overspeed" -> "Speeding"
        "geofenceEnter" -> "Entered an area"
        "geofenceExit" -> "Left an area"
        "gpsAntennaCut" -> "GPS antenna cut"
        "jamming" -> "Signal jamming"
        "hardBraking" -> "Hard braking"
        "hardAcceleration" -> "Hard acceleration"
        "hardCornering" -> "Hard cornering"
        "fatigueDriving" -> "Long drive without a break"
        null -> "Tracker alarm"
        else -> "Alarm: " + alarm.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
    }
}
