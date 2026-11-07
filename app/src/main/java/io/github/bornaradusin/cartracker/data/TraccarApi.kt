package io.github.bornaradusin.cartracker.data

import io.github.bornaradusin.cartracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

const val USER_AGENT = "CarTracker/${BuildConfig.VERSION_NAME} (+https://github.com/bornaradusin/CarTracker)"

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Traccar's REST API. Signing in exchanges the password once for a long-lived token; after that every
 * request carries the token, and the password is never kept. The live socket needs a server session,
 * which the token opens (GET /api/session?token=…).
 */
class TraccarApi {

    @Volatile var baseUrl: String = ""
    @Volatile var token: String? = null

    val signedIn: Boolean get() = baseUrl.isNotBlank() && token != null

    private val cookies = object : CookieJar {
        private val store = mutableMapOf<String, Cookie>()
        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store[it.name + "@" + it.domain] = it }
        }
        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.values.removeAll { it.expiresAt < now }
            return store.values.filter { it.matches(url) }
        }
        @Synchronized
        fun clear() = store.clear()
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder().header("User-Agent", USER_AGENT).removeHeader(NO_TOKEN)
            val t = token
            // Signing-in requests (marked NO_TOKEN) go with the password only.
            if (t != null && original.header(NO_TOKEN) == null && original.url.encodedPath.contains("/api/")) {
                builder.header("Authorization", "Bearer $t")
            }
            chain.proceed(builder.build())
        }
        .build()

    fun clearSession() = cookies.clear()

    // ---- signing in ----

    suspend fun serverInfo(base: String = baseUrl): ServerInfo =
        AppJson.decodeFromString(execute(request(base, "/api/server").get().build()).body)

    /** Email and password → the user; also leaves a session cookie for the token request. */
    suspend fun login(base: String, email: String, password: String): User {
        val form = FormBody.Builder().add("email", email).add("password", password).build()
        val response = execute(request(base, "/api/session", auth = false).post(form).build())
        return AppJson.decodeFromString(response.body)
    }

    /** A token that lasts ten years, from the session login() just opened. */
    suspend fun createToken(base: String): String {
        val expiration = Instant.now().plus(3650, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString()
        val form = FormBody.Builder().add("expiration", expiration).build()
        val body = execute(request(base, "/api/session/token", auth = false).post(form).build()).body
        return body.trim().trim('"')
    }

    /** Opens a server session from the token: needed by the live socket. Returns the user. */
    suspend fun sessionFromToken(): User {
        val t = token ?: throw ApiException(401, "Not signed in")
        val url = url(baseUrl, "/api/session", listOf("token" to t))
        return AppJson.decodeFromString(execute(Request.Builder().url(url).header("Accept", JSON).get().build()).body)
    }

    /** The server and token being signed out of, kept so they can be revoked after the app has moved on. */
    data class Session(val base: String, val token: String?)

    fun sessionForSignOut() = Session(baseUrl, token)

    /** Revokes [session]'s token on its server. */
    suspend fun signOut(session: Session) {
        val t = session.token
        if (session.base.isNotBlank() && t != null) {
            runCatching {
                execute(
                    request(session.base, "/api/session/token/revoke", auth = false)
                        .header("Authorization", "Bearer $t")
                        .post(FormBody.Builder().add("token", t).build())
                        .build(),
                )
            }
        }
        clearSession()
    }

    // ---- cars ----

    /** Devices with their raw JSON, so an update can send back every field the server knows. */
    suspend fun devices(): List<Pair<Device, JsonObject>> {
        val array = AppJson.parseToJsonElement(get("/api/devices")) as? JsonArray ?: return emptyList()
        return array.map { element ->
            val raw = element.jsonObject
            AppJson.decodeFromJsonElement<Device>(raw) to raw
        }
    }

    suspend fun updateDevice(raw: JsonObject): Device {
        val id = raw.num("id")?.toLong() ?: throw ApiException(400, "No device id")
        val body = AppJson.encodeToString(JsonObject.serializer(), raw).toRequestBody(JSON_TYPE)
        return AppJson.decodeFromString(execute(request(baseUrl, "/api/devices/$id").put(body).build()).body)
    }

    /** Sets the car's odometer ([meters]) from now on. */
    suspend fun setOdometer(deviceId: Long, meters: Double) {
        val json = buildJsonObject {
            put("deviceId", deviceId)
            put("totalDistance", meters)
        }
        val body = AppJson.encodeToString(JsonObject.serializer(), json).toRequestBody(JSON_TYPE)
        execute(request(baseUrl, "/api/devices/$deviceId/accumulators").put(body).build())
    }

    // ---- live links ----

    /**
     * A link anyone can open in a browser to follow the car until [until]: Traccar makes a temporary,
     * read-only account for it that can't send commands, and removes it after it ends.
     */
    suspend fun shareDevice(deviceId: Long, until: Instant): String {
        val form = FormBody.Builder().add("deviceId", deviceId.toString()).add("expiration", iso(until)).build()
        val token = execute(request(baseUrl, "/api/share/device").post(form).build()).body.trim().trim('"')
        if (token.isEmpty()) throw ApiException(500, "The server didn't make a link.")
        return token
    }

    fun shareUrl(token: String): String =
        url(baseUrl, "/", listOf("token" to token)).toString()

    suspend fun users(): List<User> = AppJson.decodeFromString(get("/api/users"))

    suspend fun deleteUser(id: Long) {
        execute(request(baseUrl, "/api/users/$id").delete().build())
    }

    suspend fun positions(): List<Position> = AppJson.decodeFromString(get("/api/positions"))

    suspend fun positionsById(ids: Collection<Long>): List<Position> {
        if (ids.isEmpty()) return emptyList()
        return AppJson.decodeFromString(get("/api/positions", ids.map { "id" to it.toString() }))
    }

    // ---- reports ----

    suspend fun trips(deviceId: Long, from: Instant, to: Instant): List<Trip> =
        AppJson.decodeFromString(get("/api/reports/trips", range(listOf(deviceId), from, to)))

    suspend fun stops(deviceId: Long, from: Instant, to: Instant): List<Stop> =
        AppJson.decodeFromString(get("/api/reports/stops", range(listOf(deviceId), from, to)))

    suspend fun route(deviceId: Long, from: Instant, to: Instant): List<Position> =
        AppJson.decodeFromString(get("/api/reports/route", range(listOf(deviceId), from, to)))

    /** Every position in the range as plain coordinates (Traccar's KML export): far smaller than the full route. */
    suspend fun coordinates(deviceId: Long, from: Instant, to: Instant): List<Pair<Double, Double>> {
        val kml = execute(
            Request.Builder().url(url(baseUrl, "/api/positions/kml", range(listOf(deviceId), from, to))).header("Accept", "*/*").get().build(),
        ).body
        val result = ArrayList<Pair<Double, Double>>()
        var start = kml.indexOf("<coordinates>")
        while (start >= 0) {
            val end = kml.indexOf("</coordinates>", start)
            if (end < 0) break
            kml.substring(start + "<coordinates>".length, end).trim().split(Regex("\\s+")).forEach { triple ->
                val parts = triple.split(',')
                val lon = parts.getOrNull(0)?.toDoubleOrNull()
                val lat = parts.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) result += lat to lon
            }
            start = kml.indexOf("<coordinates>", end)
        }
        return result
    }

    suspend fun summary(deviceId: Long, from: Instant, to: Instant): List<Summary> =
        AppJson.decodeFromString(get("/api/reports/summary", range(listOf(deviceId), from, to)))

    suspend fun events(deviceIds: Collection<Long>, from: Instant, to: Instant): List<Event> {
        if (deviceIds.isEmpty()) return emptyList()
        return AppJson.decodeFromString(get("/api/reports/events", range(deviceIds, from, to)))
    }

    // ---- places ----

    suspend fun geofences(deviceId: Long): List<Geofence> =
        AppJson.decodeFromString(get("/api/geofences", listOf("deviceId" to deviceId.toString())))

    suspend fun addGeofence(name: String, latitude: Double, longitude: Double, radius: Double, icon: String): Geofence {
        val json = buildJsonObject {
            put("name", name)
            put("area", "CIRCLE ($latitude $longitude, ${radius.toInt()})")
            putJsonObject("attributes") { put("icon", icon) }
        }
        val body = AppJson.encodeToString(JsonObject.serializer(), json).toRequestBody(JSON_TYPE)
        return AppJson.decodeFromString(execute(request(baseUrl, "/api/geofences").post(body).build()).body)
    }

    /** Changes a place's name, size and icon, keeping everything else Traccar stores for it. */
    /** Saves a place's name, icon and, for a circle, its size ([area] null keeps the shape it has). */
    suspend fun updateGeofence(id: Long, name: String, area: String?, icon: String): Geofence {
        val current = AppJson.parseToJsonElement(get("/api/geofences/$id")) as? JsonObject
            ?: throw ApiException(404, "That place no longer exists.")
        val attributes = (current["attributes"] as? JsonObject).orEmpty() + ("icon" to JsonPrimitive(icon))
        val changes = mutableMapOf<String, JsonElement>("name" to JsonPrimitive(name), "attributes" to JsonObject(attributes))
        if (area != null) changes["area"] = JsonPrimitive(area)
        val updated = JsonObject(current + changes)
        val body = AppJson.encodeToString(JsonObject.serializer(), updated).toRequestBody(JSON_TYPE)
        return AppJson.decodeFromString(execute(request(baseUrl, "/api/geofences/$id").put(body).build()).body)
    }

    suspend fun deleteGeofence(id: Long) {
        execute(request(baseUrl, "/api/geofences/$id").delete().build())
    }

    suspend fun linkGeofence(deviceId: Long, geofenceId: Long) {
        val json = buildJsonObject {
            put("deviceId", deviceId)
            put("geofenceId", geofenceId)
        }
        val body = AppJson.encodeToString(JsonObject.serializer(), json).toRequestBody(JSON_TYPE)
        execute(request(baseUrl, "/api/permissions").post(body).build())
    }

    // ---- commands ----

    suspend fun commandTypes(deviceId: Long): List<CommandType> =
        AppJson.decodeFromString(get("/api/commands/types", listOf("deviceId" to deviceId.toString())))

    /** Saved commands this account may send to the car. */
    suspend fun savedCommands(deviceId: Long): List<SavedCommand> =
        AppJson.decodeFromString(get("/api/commands/send", listOf("deviceId" to deviceId.toString())))

    /** True when the car got it now, false when Traccar queued it until the car next connects. */
    suspend fun sendCommand(command: JsonObject): Boolean {
        val body = AppJson.encodeToString(JsonObject.serializer(), command).toRequestBody(JSON_TYPE)
        return execute(request(baseUrl, "/api/commands/send").post(body).build()).code != 202
    }

    // ---- live ----

    fun openSocket(listener: WebSocketListener): WebSocket {
        val http = (baseUrl.trimEnd('/') + "/api/socket").toHttpUrl()
        val wsUrl = http.toString().replaceFirst("http", "ws")
        return client.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    // ---- plumbing ----

    class Reply(val code: Int, val body: String)

    private fun request(base: String, path: String, auth: Boolean = true): Request.Builder {
        val builder = Request.Builder().url(url(base, path)).header("Accept", JSON)
        if (!auth) builder.header(NO_TOKEN, "1")
        return builder
    }

    private fun url(base: String, path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl {
        val builder = (base.trimEnd('/') + path).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun range(deviceIds: Collection<Long>, from: Instant, to: Instant): List<Pair<String, String>> =
        deviceIds.map { "deviceId" to it.toString() } +
            listOf("from" to iso(from), "to" to iso(to))

    private fun iso(instant: Instant) = instant.truncatedTo(ChronoUnit.SECONDS).toString()

    private suspend fun get(path: String, query: List<Pair<String, String>> = emptyList()): String =
        execute(Request.Builder().url(url(baseUrl, path, query)).header("Accept", JSON).get().build()).body

    private suspend fun execute(request: Request): Reply = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "The server didn't accept the sign-in."
                    403 -> "Your account isn't allowed to do that on this server."
                    404 -> "The server doesn't know that address."
                    else -> body.lineSequence().firstOrNull { it.isNotBlank() }?.take(200) ?: "Server error ${response.code}"
                }
                throw ApiException(response.code, message)
            }
            Reply(response.code, body)
        }
    }

    private companion object {
        const val JSON = "application/json"
        const val NO_TOKEN = "X-CarTracker-No-Token"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
