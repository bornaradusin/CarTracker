package io.github.bornaradusin.cartracker.data

import android.content.Context
import io.github.bornaradusin.cartracker.alerts.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** How the app reaches LubeLogger, and which of its vehicles each car is. Kept on the phone. */
@Serializable
data class LubeConfig(
    val server: String = "",
    val apiKey: String = "",
    val user: String = "",
    val password: String = "",
    /** Traccar device id to LubeLogger vehicle id. */
    val vehicles: Map<Long, Int> = emptyMap(),
    /** Tell about reminders as they become due. */
    val notify: Boolean = true,
) {
    val connected: Boolean get() = server.isNotBlank() && (apiKey.isNotBlank() || user.isNotBlank())
}

data class LubeVehicle(val id: Int, val name: String)

enum class LubeKind(val label: String, val path: String) {
    SERVICE("Service", "servicerecords"),
    REPAIR("Repairs", "repairrecords"),
    UPGRADE("Upgrades", "upgraderecords"),
    FUEL("Fuel", "gasrecords"),
    TAX("Tax", "taxrecords"),
}

data class LubeRecord(
    val kind: LubeKind,
    val id: Int,
    val date: LocalDate?,
    val odometer: Int?,
    val description: String,
    val cost: Double,
    val notes: String,
    /** Fuel only: how much went in, and the economy LubeLogger worked out (0 when it couldn't). */
    val fuel: Double = 0.0,
    val economy: Double = 0.0,
    val fillToFull: Boolean = true,
    val missedFuelUp: Boolean = false,
    /** The record as LubeLogger sent it, so a change can send back everything it holds. */
    val raw: JsonObject = JsonObject(emptyMap()),
)

/** What the person typed for a new record, or for a change to one. */
data class LubeRecordEdit(
    val kind: LubeKind,
    val date: LocalDate,
    val odometer: Int?,
    val description: String,
    val cost: Double,
    val notes: String,
    val fuel: Double = 0.0,
    val fillToFull: Boolean = true,
    val missedFuelUp: Boolean = false,
)

data class LubeReminderEdit(val description: String, val dueDate: LocalDate?, val dueOdometer: Int?, val notes: String)

enum class LubeUrgency(val label: String) {
    PAST_DUE("Past due"),
    VERY_URGENT("Very urgent"),
    URGENT("Urgent"),
    NOT_URGENT("Not urgent"),
}

data class LubeReminder(
    val id: Int,
    val description: String,
    val urgency: LubeUrgency,
    val dueDate: LocalDate?,
    val dueOdometer: Int?,
    val notes: String,
    val raw: JsonObject = JsonObject(emptyMap()),
)

data class LubeData(val records: List<LubeRecord>, val reminders: List<LubeReminder>)

class LubeException(message: String) : Exception(message)

/**
 * Reads a LubeLogger server: its vehicles, their records and their reminders. LubeLogger sends
 * numbers and dates the way the account's language writes them unless asked not to, so everything
 * is read leniently.
 */
class LubeLogger(private val context: Context) {

    private val prefs = context.getSharedPreferences("lubelogger", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val checking = Mutex()

    private val _config = MutableStateFlow(
        runCatching { json.decodeFromString(LubeConfig.serializer(), prefs.getString(KEY_CONFIG, null) ?: "{}") }
            .getOrDefault(LubeConfig()),
    )
    val config: StateFlow<LubeConfig> = _config

    fun save(config: LubeConfig) {
        _config.value = config
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(LubeConfig.serializer(), config)).apply()
    }

    fun disconnect() {
        save(LubeConfig())
        prefs.edit().remove(KEY_TOLD).remove(KEY_CHECKED).apply()
    }

    suspend fun vehicles(config: LubeConfig = _config.value): List<LubeVehicle> =
        array(config, "/api/vehicles", null).mapNotNull { v ->
            val id = v.int("id") ?: return@mapNotNull null
            val name = listOf(v.text("year").takeIf { it != "0" }.orEmpty(), v.text("make"), v.text("model"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val plate = v.text("licensePlate")
            LubeVehicle(id, listOf(name, plate).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Vehicle $id" })
        }

    suspend fun load(vehicleId: Int, config: LubeConfig = _config.value): LubeData {
        val records = LubeKind.entries.flatMap { kind ->
            array(config, "/api/vehicle/${kind.path}", vehicleId).map { r ->
                LubeRecord(
                    kind = kind,
                    id = r.int("id") ?: 0,
                    date = date(r.text("date")),
                    odometer = r.int("odometer")?.takeIf { it > 0 },
                    description = r.text("description"),
                    cost = r.number("cost") ?: 0.0,
                    notes = r.text("notes"),
                    fuel = r.number("fuelConsumed") ?: 0.0,
                    economy = r.number("fuelEconomy") ?: 0.0,
                    fillToFull = r.text("isFillToFull").lowercase() != "false",
                    missedFuelUp = r.text("missedFuelUp").lowercase() == "true",
                    raw = r,
                )
            }
        }
        return LubeData(records.sortedByDescending { it.date ?: LocalDate.MIN }, reminders(vehicleId, config))
    }

    suspend fun reminders(vehicleId: Int, config: LubeConfig = _config.value): List<LubeReminder> =
        array(config, "/api/vehicle/reminders", vehicleId).map { r ->
            LubeReminder(
                id = r.int("id") ?: 0,
                description = r.text("description"),
                urgency = urgency(r.text("urgency")),
                dueDate = date(r.text("dueDate")),
                dueOdometer = r.int("dueOdometer")?.takeIf { it > 0 },
                notes = r.text("notes"),
                raw = r,
            )
        }.sortedWith(compareBy<LubeReminder> { it.urgency.ordinal }.thenBy { it.dueDate ?: LocalDate.MAX })

    // ---- writing ----

    /** Adds a record, or with [existing] changes that one. It goes straight to LubeLogger. */
    suspend fun saveRecord(vehicleId: Int, edit: LubeRecordEdit, existing: LubeRecord?, config: LubeConfig = _config.value) {
        // A change sends back everything the record holds: LubeLogger replaces the whole record
        val fields = LinkedHashMap<String, String>()
        if (existing != null && existing.kind == edit.kind) flatten(existing.raw, fields)
        fields["date"] = edit.date.toString()
        fields["cost"] = decimal(edit.cost)
        fields["notes"] = edit.notes
        if (edit.kind != LubeKind.TAX) fields["odometer"] = (edit.odometer ?: 0).toString()
        if (edit.kind == LubeKind.FUEL) {
            fields["fuelConsumed"] = decimal(edit.fuel)
            fields["isFillToFull"] = edit.fillToFull.toString()
            fields["missedFuelUp"] = edit.missedFuelUp.toString()
        } else {
            fields["description"] = edit.description
        }
        if (existing != null && existing.kind == edit.kind) {
            fields["id"] = existing.id.toString()
            write(config, "PUT", "/api/vehicle/${edit.kind.path}/update", null, fields)
        } else {
            write(config, "POST", "/api/vehicle/${edit.kind.path}/add", vehicleId, fields)
            // Saved as another type: the old one goes
            if (existing != null) deleteRecord(existing, config)
        }
    }

    suspend fun deleteRecord(record: LubeRecord, config: LubeConfig = _config.value) {
        write(config, "DELETE", "/api/vehicle/${record.kind.path}/delete", null, emptyMap(), id = record.id)
    }

    suspend fun saveReminder(vehicleId: Int, edit: LubeReminderEdit, existing: LubeReminder?, config: LubeConfig = _config.value) {
        val fields = LinkedHashMap<String, String>()
        if (existing != null) flatten(existing.raw, fields)
        fields["description"] = edit.description
        fields["notes"] = edit.notes
        fields["metric"] = when {
            edit.dueDate != null && edit.dueOdometer != null -> "Both"
            edit.dueOdometer != null -> "Odometer"
            else -> "Date"
        }
        fields["dueDate"] = (edit.dueDate ?: existing?.dueDate ?: LocalDate.now()).toString()
        fields["dueOdometer"] = (edit.dueOdometer ?: 0).toString()
        // Worked out by LubeLogger, not something to send back
        listOf("urgency", "dueDays", "dueDistance").forEach { fields.remove(it) }
        if (existing != null) {
            fields["id"] = existing.id.toString()
            write(config, "PUT", "/api/vehicle/reminders/update", null, fields)
        } else {
            write(config, "POST", "/api/vehicle/reminders/add", vehicleId, fields)
        }
    }

    /**
     * Twice a day at most: a notification for each reminder that has become urgent or past due
     * since the last look. [names] gives the car's name for each Traccar device id.
     */
    suspend fun checkReminders(names: Map<Long, String>) {
        val config = _config.value
        if (!config.connected || !config.notify || config.vehicles.isEmpty()) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_CHECKED, 0L) < CHECK_EVERY_MS) return
        if (!checking.tryLock()) return
        try {
            val told = prefs.getStringSet(KEY_TOLD, emptySet()).orEmpty()
            val now = HashSet<String>()
            config.vehicles.forEach { (deviceId, vehicleId) ->
                reminders(vehicleId, config).filter { it.urgency != LubeUrgency.NOT_URGENT }.forEach { r ->
                    val key = "$vehicleId:${r.id}:${r.urgency.name}"
                    now.add(key)
                    if (key !in told) {
                        val car = names[deviceId]
                        Notifier.reminder(
                            context,
                            vehicleId * 1000 + r.id,
                            (car?.let { "$it: " } ?: "") + r.description.ifBlank { "Reminder" },
                            listOfNotNull(r.urgency.label, dueText(r)).joinToString(" · "),
                        )
                    }
                }
            }
            prefs.edit().putStringSet(KEY_TOLD, now).putLong(KEY_CHECKED, System.currentTimeMillis()).apply()
        } finally {
            checking.unlock()
        }
    }

    // ---- reading ----

    private suspend fun array(config: LubeConfig, path: String, vehicleId: Int?): List<JsonObject> {
        val body = call(config, "GET", path, vehicleId, null, null)
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: throw LubeException("That address answered, but not as LubeLogger. Check it and the sign-in.")
        return (parsed as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: throw LubeException((parsed as? JsonObject)?.text("message")?.ifBlank { null } ?: "LubeLogger sent something unexpected.")
    }

    private suspend fun write(config: LubeConfig, method: String, path: String, vehicleId: Int?, fields: Map<String, String>, id: Int? = null) {
        val body = call(config, method, path, vehicleId, fields, id)
        // LubeLogger answers a change with whether it worked, and why not
        val answer = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return
        if (answer.text("success").lowercase() == "false") {
            throw LubeException(answer.text("message").ifBlank { "LubeLogger didn't save it." })
        }
    }

    private suspend fun call(config: LubeConfig, method: String, path: String, vehicleId: Int?, fields: Map<String, String>?, id: Int?): String = withContext(Dispatchers.IO) {
        val base = baseUrl(config.server) ?: throw LubeException("That doesn't look like a server address.")
        val url = base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + path).apply {
            if (vehicleId != null) addQueryParameter("vehicleId", vehicleId.toString())
            if (id != null) addQueryParameter("id", id.toString())
            if (config.apiKey.isNotBlank()) addQueryParameter("apiKey", config.apiKey.trim())
        }.build()
        val form = fields?.let { f -> FormBody.Builder().apply { f.forEach { (key, value) -> add(key, value) } }.build() }
        val request = Request.Builder().url(url).header("Accept", "application/json").header("culture-invariant", "true").apply {
            if (config.apiKey.isNotBlank()) header("x-api-key", config.apiKey.trim())
            else header("Authorization", Credentials.basic(config.user.trim(), config.password))
        }.method(method, if (method == "GET") null else form ?: FormBody.Builder().build()).build()
        val changing = method != "GET"
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    (response.code == 401 || response.code == 403) && changing ->
                        throw LubeException("LubeLogger didn't allow that. An API key needs the Editor role to add and change, and Manager to delete.")
                    response.code == 401 || response.code == 403 -> throw LubeException("LubeLogger didn't accept the sign-in.")
                    response.code == 404 -> throw LubeException("No LubeLogger found at that address.")
                    !response.isSuccessful -> throw LubeException(
                        runCatching { (json.parseToJsonElement(text) as? JsonObject)?.text("message") }.getOrNull()?.ifBlank { null }
                            ?: "LubeLogger answered with an error (${response.code}).",
                    )
                    else -> text
                }
            }
        } catch (e: LubeException) {
            throw e
        } catch (e: java.io.IOException) {
            throw LubeException("Couldn't reach LubeLogger.")
        }
    }

    private fun baseUrl(server: String): HttpUrl? {
        val trimmed = server.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        return withScheme.toHttpUrlOrNull()
    }

    companion object {
        private const val KEY_CONFIG = "config"
        private const val KEY_TOLD = "told"
        private const val KEY_CHECKED = "checked"
        private const val CHECK_EVERY_MS = 12 * 60 * 60_000L

        private val dayMonth = DateTimeFormatter.ofPattern("d MMM yyyy")

        fun dueText(r: LubeReminder): String? = listOfNotNull(
            r.dueDate?.let { "due ${it.format(dayMonth)}" },
            r.dueOdometer?.let { "at ${String.format(java.util.Locale.US, "%,d", it)}" },
        ).joinToString(" · ").ifBlank { null }

        private fun decimal(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

        /**
         * A record as form fields: plain values by name, and lists (attached files, extra fields)
         * as name[0].field, the way LubeLogger reads a form.
         */
        private fun flatten(record: JsonObject, into: MutableMap<String, String>) {
            record.forEach { (key, value) ->
                when (value) {
                    is JsonNull -> Unit
                    is JsonPrimitive -> {
                        into[key] = value.content
                    }
                    is JsonArray -> value.forEachIndexed { index, item ->
                        when (item) {
                            is JsonObject -> item.forEach { (field, inner) ->
                                if (inner is JsonPrimitive && inner !is JsonNull) {
                                    into["$key[$index].$field"] = inner.content
                                }
                            }
                            is JsonPrimitive -> {
                                if (item !is JsonNull) {
                                    into["$key[$index]"] = item.content
                                }
                            }
                            else -> Unit
                        }
                    }
                    else -> Unit
                }
            }
        }

        private fun JsonObject.find(key: String): JsonElement? =
            this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

        private fun JsonObject.text(key: String): String = when (val e = find(key)) {
            null, is JsonNull -> ""
            is JsonPrimitive -> e.content.trim()
            else -> ""
        }

        private fun JsonObject.number(key: String): Double? = parseNumber(text(key))

        private fun JsonObject.int(key: String): Int? = number(key)?.let { Math.round(it).toInt() }

        /** "1234.5", "1,234.50", "1.234,50", "12,5" and "€ 40.00" all mean what they say. */
        fun parseNumber(raw: String): Double? {
            var s = raw.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
            if (s.isEmpty() || s == "-") return null
            val comma = s.lastIndexOf(',')
            val dot = s.lastIndexOf('.')
            s = when {
                comma >= 0 && dot >= 0 -> if (comma > dot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
                comma >= 0 -> if (Regex("-?\\d{1,3}(,\\d{3})+").matches(s)) s.replace(",", "") else s.replace(',', '.')
                else -> s
            }
            return s.toDoubleOrNull()
        }

        private val datePatterns = listOf("M/d/yyyy", "d/M/yyyy", "d.M.yyyy", "d-M-yyyy", "yyyy/M/d").map { DateTimeFormatter.ofPattern(it) }

        /** A date as "2026-03-14", with or without a time after it, or the way a language writes it. */
        fun date(raw: String): LocalDate? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            runCatching { LocalDate.parse(s.take(10)) }.getOrNull()?.let { return it }
            val day = s.substringBefore(' ').substringBefore('T')
            datePatterns.forEach { pattern -> runCatching { LocalDate.parse(day, pattern) }.getOrNull()?.let { return it } }
            return null
        }

        private fun urgency(raw: String): LubeUrgency = when (raw.lowercase().filter { it.isLetter() }) {
            "pastdue" -> LubeUrgency.PAST_DUE
            "veryurgent" -> LubeUrgency.VERY_URGENT
            "urgent" -> LubeUrgency.URGENT
            else -> LubeUrgency.NOT_URGENT
        }
    }
}
