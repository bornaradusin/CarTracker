package io.github.bornaradusin.cartracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** One JSON setup for everything the server sends: unknown fields are ignored, nulls fall back to defaults. */
val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

private val EmptyObject = JsonObject(emptyMap())

@Serializable
data class ServerInfo(
    val id: Long = 0,
    val registration: Boolean = false,
    val readonly: Boolean = false,
    val deviceReadonly: Boolean = false,
    val limitCommands: Boolean = false,
    val version: String? = null,
    val attributes: JsonObject = EmptyObject,
)

@Serializable
data class User(
    val id: Long = 0,
    val name: String = "",
    val email: String = "",
    val administrator: Boolean = false,
    val readonly: Boolean = false,
    val deviceReadonly: Boolean = false,
    val limitCommands: Boolean = false,
)

@Serializable
data class Device(
    val id: Long = 0,
    val name: String = "",
    val uniqueId: String = "",
    val status: String? = null,
    val disabled: Boolean = false,
    val lastUpdate: String? = null,
    val positionId: Long = 0,
    val model: String? = null,
    val category: String? = null,
    val attributes: JsonObject = EmptyObject,
) {
    val online: Boolean get() = status == "online"
}

@Serializable
data class Position(
    val id: Long = 0,
    val deviceId: Long = 0,
    val protocol: String? = null,
    val serverTime: String? = null,
    val deviceTime: String? = null,
    val fixTime: String? = null,
    val outdated: Boolean = false,
    val valid: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    /** Knots, as Traccar keeps it. */
    val speed: Double = 0.0,
    val course: Double = 0.0,
    val address: String? = null,
    val attributes: JsonObject = EmptyObject,
) {
    val ignition: Boolean? get() = attributes.bool("ignition")
    /** The tracker's fuel/engine cut-off relay is on: the car can't start. */
    val blocked: Boolean? get() = attributes.bool("blocked")
    val motion: Boolean? get() = attributes.bool("motion")
    val armed: Boolean? get() = attributes.bool("armed")

    /** The car battery's voltage, as the tracker measures it. */
    val power: Double? get() = attributes.num("power")

    /** Traccar's odometer for the car, in metres. */
    val totalDistance: Double? get() = attributes.num("totalDistance")

    /** The road's speed limit in km/h, when the server looks them up (Traccar's speedLimit, in knots). */
    val roadLimitKmh: Int? get() = attributes.num("speedLimit")?.takeIf { it > 0 }?.let { kotlin.math.round(it * 1.852).toInt() }
    val speedKmh: Double get() = speed * 1.852
    val hasFix: Boolean get() = latitude != 0.0 || longitude != 0.0
}

@Serializable
data class Event(
    val id: Long = 0,
    val type: String = "",
    val eventTime: String? = null,
    val deviceId: Long = 0,
    val positionId: Long = 0,
    val geofenceId: Long = 0,
    val attributes: JsonObject = EmptyObject,
)

/** One stop from Traccar's stops report. The last one runs to the newest position while the car is still parked. */
@Serializable
data class Stop(
    val deviceId: Long = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    /** Milliseconds. */
    val duration: Long = 0,
)

@Serializable
data class Trip(
    val deviceId: Long = 0,
    /** Metres. */
    val distance: Double = 0.0,
    /** Knots. */
    val maxSpeed: Double = 0.0,
    val startTime: String? = null,
    val endTime: String? = null,
    /** Milliseconds. */
    val duration: Long = 0,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0,
)

@Serializable
data class Summary(
    val deviceId: Long = 0,
    /** Metres. */
    val distance: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
data class Geofence(
    val id: Long = 0,
    val name: String = "",
    val description: String? = null,
    val area: String = "",
    val attributes: JsonObject = EmptyObject,
) {
    /** The place's icon ("home", "work", "pin"…), saved in its attributes. */
    val icon: String
        get() = attributes.str("icon") ?: if (name.equals("home", ignoreCase = true)) "home" else "pin"

    /** A circle as Traccar writes it: CIRCLE (lat lon, radius). Other shapes return null. */
    val circle: Circle?
        get() {
            val m = Regex("""CIRCLE\s*\(\s*(-?[\d.]+)\s+(-?[\d.]+)\s*,\s*([\d.]+)\s*\)""").find(area) ?: return null
            val (lat, lon, r) = m.destructured
            return Circle(lat.toDouble(), lon.toDouble(), r.toDouble())
        }

    data class Circle(val latitude: Double, val longitude: Double, val radius: Double)
}

@Serializable
data class SavedCommand(
    val id: Long = 0,
    val deviceId: Long = 0,
    val description: String? = null,
    val type: String = "",
    val textChannel: Boolean = false,
    val attributes: JsonObject = EmptyObject,
)

@Serializable
data class CommandType(val type: String = "")

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

fun JsonObject.num(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

/**
 * A real drive, not the engine running while the car stood still (idling, waiting in a car park):
 * Traccar counts those as trips when trips follow the ignition.
 */
val Trip.isDrive: Boolean get() = distance >= 300
