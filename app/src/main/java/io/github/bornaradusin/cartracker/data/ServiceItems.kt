package io.github.bornaradusin.cartracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * One thing to service on a car: how often (km, months or both, whichever comes first) and when it
 * was last done, when that isn't in LubeLogger. Null = not set.
 */
@Serializable
data class ServiceItem(
    val id: String,
    val name: String,
    val everyKm: Int? = null,
    val everyMonths: Int? = null,
    val lastKm: Int? = null,
    /** The day it was last done, as LocalDate.toEpochDay(). */
    val lastDay: Long? = null,
)

enum class ServiceUrgency { UNSET, OK, SOON, OVERDUE }

/** Where an item stands now. */
data class ServiceStatus(
    val item: ServiceItem,
    /** When it was last done: the later of what was typed in and the newest matching LubeLogger record. */
    val lastKm: Int?,
    val lastDate: LocalDate?,
    /** The last time comes from a LubeLogger record. */
    val fromRecord: Boolean,
    val nextKm: Int?,
    /** Km until it's due (negative = overdue); null when the odometer or the last km isn't known. */
    val kmLeft: Int?,
    val nextDate: LocalDate?,
    /** Days until it's due (negative = overdue). */
    val daysLeft: Long?,
    /** How much of the interval has gone, 0..1 (more when overdue): the further of km and time. */
    val used: Float,
    val urgency: ServiceUrgency,
    /** The km comes first (else the date). */
    val byKm: Boolean,
) {
    val due: Boolean get() = urgency == ServiceUrgency.SOON || urgency == ServiceUrgency.OVERDUE
}

/** The service items of each car, kept on the phone. A new car starts with the usual five, not set up. */
class ServiceItems(context: Context) {

    private val prefs = context.getSharedPreferences("service_items", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(Long.serializer(), ListSerializer(ServiceItem.serializer()))

    private val _items = MutableStateFlow(
        runCatching { json.decodeFromString(serializer, prefs.getString(KEY, null) ?: "{}") }.getOrDefault(emptyMap()),
    )
    val items: StateFlow<Map<Long, List<ServiceItem>>> = _items

    fun forCar(all: Map<Long, List<ServiceItem>>, deviceId: Long): List<ServiceItem> = all[deviceId] ?: defaults

    /** Adds the item, or replaces the one with its id. */
    fun save(deviceId: Long, item: ServiceItem) {
        val list = forCar(_items.value, deviceId)
        val index = list.indexOfFirst { it.id == item.id }
        store(deviceId, if (index < 0) list + item else list.toMutableList().also { it[index] = item })
    }

    fun remove(deviceId: Long, id: String) = store(deviceId, forCar(_items.value, deviceId).filterNot { it.id == id })

    private fun store(deviceId: Long, list: List<ServiceItem>) {
        val all = _items.value + (deviceId to list)
        _items.value = all
        prefs.edit().putString(KEY, json.encodeToString(serializer, all)).apply()
    }

    companion object {
        private const val KEY = "items"

        /** An item counts as due soon once it's this close. */
        const val KM_BEFORE = 1_000
        const val DAYS_BEFORE = 30L

        /** Starting points only: the car's own service book wins. */
        val defaults = listOf(
            ServiceItem("oil", "Engine oil", 15_000, 12),
            ServiceItem("air", "Air filter", 30_000, 24),
            ServiceItem("cabin", "Cabin filter", 15_000, 12),
            ServiceItem("plugs", "Spark plugs", 60_000, 48),
            ServiceItem("brakefluid", "Brake fluid", null, 24),
        )

        fun newId(): String = "c" + System.currentTimeMillis()

        /** Most urgent first; items not set up yet last, in the order they were added. */
        fun sorted(statuses: List<ServiceStatus>): List<ServiceStatus> = statuses.sortedWith(
            compareBy<ServiceStatus> {
                when (it.urgency) {
                    ServiceUrgency.OVERDUE -> 0
                    ServiceUrgency.SOON -> 1
                    ServiceUrgency.OK -> 2
                    ServiceUrgency.UNSET -> 3
                }
            }.thenByDescending { if (it.urgency == ServiceUrgency.UNSET) 0f else it.used },
        )

        /**
         * Where [item] stands at [carKm] on [today]. A service, repair or upgrade in [records] whose
         * description has the item's name in it counts as the item being done then.
         */
        fun status(item: ServiceItem, records: List<LubeRecord>, carKm: Int?, today: LocalDate = LocalDate.now()): ServiceStatus {
            val typedDate = item.lastDay?.let(LocalDate::ofEpochDay)
            val name = item.name.trim()
            val record = if (name.length < 3) null else records
                .filter { it.kind != LubeKind.FUEL && it.kind != LubeKind.TAX && it.date != null && it.description.contains(name, ignoreCase = true) }
                .maxByOrNull { it.date ?: LocalDate.MIN }
            val recordDate = record?.date
            val fromRecord = recordDate != null && (typedDate == null || !recordDate.isBefore(typedDate))
            val lastKm = if (fromRecord) record?.odometer ?: item.lastKm else item.lastKm
            val last = if (fromRecord) recordDate else typedDate

            val nextKm = if (item.everyKm != null && lastKm != null) lastKm + item.everyKm else null
            val kmLeft = if (nextKm != null && carKm != null) nextKm - carKm else null
            val nextDate = if (item.everyMonths != null && last != null) last.plusMonths(item.everyMonths.toLong()) else null
            val daysLeft = nextDate?.let { ChronoUnit.DAYS.between(today, it) }

            val usedKm = if (kmLeft != null && item.everyKm != null && item.everyKm > 0) 1f - kmLeft.toFloat() / item.everyKm else null
            val usedTime = if (last != null && nextDate != null) {
                val span = ChronoUnit.DAYS.between(last, nextDate).coerceAtLeast(1)
                1f - ChronoUnit.DAYS.between(today, nextDate).toFloat() / span
            } else {
                null
            }
            val byKm = when {
                usedKm == null -> false
                usedTime == null -> true
                else -> usedKm >= usedTime
            }
            val urgency = when {
                kmLeft == null && daysLeft == null -> ServiceUrgency.UNSET
                (kmLeft != null && kmLeft < 0) || (daysLeft != null && daysLeft < 0) -> ServiceUrgency.OVERDUE
                (kmLeft != null && kmLeft <= KM_BEFORE) || (daysLeft != null && daysLeft <= DAYS_BEFORE) -> ServiceUrgency.SOON
                else -> ServiceUrgency.OK
            }
            return ServiceStatus(item, lastKm, last, fromRecord, nextKm, kmLeft, nextDate, daysLeft, maxOf(usedKm ?: 0f, usedTime ?: 0f), urgency, byKm)
        }

        /**
         * The car's odometer for the countdown: the newest reading LubeLogger has, or the app's own
         * count for the car ([trackerKm]) when that has carried on a little past it.
         */
        fun odometer(latest: Int?, trackerKm: Double?): Int? {
            val tracker = trackerKm?.roundToInt()?.takeIf { it > 0 }
            return when {
                latest == null -> tracker
                tracker != null && tracker > latest && tracker - latest <= 10_000 -> tracker
                else -> latest
            }
        }
    }
}
