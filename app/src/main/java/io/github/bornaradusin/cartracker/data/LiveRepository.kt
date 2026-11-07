package io.github.bornaradusin.cartracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap

/**
 * The cars and where they are, kept current by Traccar's live socket (/api/socket) while anyone holds
 * it: the app on screen, or the live-alerts service in the background.
 */
class LiveRepository(private val api: TraccarApi, private val scope: CoroutineScope) {

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _positions = MutableStateFlow<Map<Long, Position>>(emptyMap())
    val positions: StateFlow<Map<Long, Position>> = _positions.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** The server no longer accepts the saved sign-in (token revoked or expired). */
    private val _signInRejected = MutableStateFlow(false)
    val signInRejected: StateFlow<Boolean> = _signInRejected.asStateFlow()

    /** Something new arrived from the server (a position or an event). */
    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val updates: SharedFlow<Unit> = _updates.asSharedFlow()

    private val rawDevices = ConcurrentHashMap<Long, JsonObject>()
    private val holders = mutableSetOf<String>()
    private var loop: Job? = null
    @Volatile private var socket: WebSocket? = null

    fun rawDevice(id: Long): JsonObject? = rawDevices[id]

    @Synchronized
    fun acquire(holder: String) {
        holders += holder
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
    }

    @Synchronized
    fun release(holder: String) {
        holders -= holder
        if (holders.isEmpty()) stopLoop()
    }

    /** After signing in or out: forget everything and start again if anyone is holding on. */
    @Synchronized
    fun reset() {
        stopLoop()
        rawDevices.clear()
        _devices.value = emptyList()
        _positions.value = emptyMap()
        _signInRejected.value = false
        if (holders.isNotEmpty()) loop = scope.launch { runLoop() }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
        socket?.close(1000, null)
        socket = null
        _connected.value = false
    }

    /** Fetch cars and positions now. */
    suspend fun refresh() {
        if (!api.signedIn) return
        val list = api.devices()
        rawDevices.clear()
        list.forEach { (device, raw) -> rawDevices[device.id] = raw }
        _devices.value = list.map { it.first }.sortedBy { it.name.lowercase() }
        val known = _positions.value
        // A tracker's latest "position" may be its reply to a command: keep the real one seen before
        _positions.value = api.positions().associateBy { it.deviceId }.mapValues { (id, p) ->
            if (p.attributes.containsKey("result")) known[id] ?: p else p
        }
    }

    /** A car's details changed in the app (name, icon…): show it straight away. */
    fun replaceDevice(device: Device, raw: JsonObject) {
        rawDevices[device.id] = raw
        _devices.value = _devices.value.map { if (it.id == device.id) device else it }
    }

    private suspend fun runLoop() {
        var backoff = 2_000L
        while (currentCoroutineContext().isActive) {
            if (!api.signedIn) {
                delay(1_000)
                continue
            }
            var openedAt = 0L
            var ws: WebSocket? = null
            try {
                refresh()
                api.sessionFromToken()
                val closed = CompletableDeferred<Unit>()
                ws = api.openSocket(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        openedAt = System.currentTimeMillis()
                        _connected.value = true
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        closed.complete(Unit)
                    }
                })
                socket = ws
                closed.await()
            } catch (e: CancellationException) {
                ws?.close(1000, null)
                throw e
            } catch (e: Exception) {
                // the server is unreachable or the session failed: try again below
                if (e is ApiException && e.code == 401) _signInRejected.value = true
            }
            _connected.value = false
            socket = null
            if (openedAt != 0L && System.currentTimeMillis() - openedAt > 60_000) backoff = 2_000L
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private fun handle(text: String) {
        val message = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        (message["devices"] as? JsonArray)?.let { array ->
            val current = _devices.value.associateBy { it.id }.toMutableMap()
            array.forEach { element ->
                val raw = element.jsonObject
                val device = runCatching { AppJson.decodeFromJsonElement<Device>(raw) }.getOrNull() ?: return@forEach
                rawDevices[device.id] = raw
                current[device.id] = device
            }
            _devices.value = current.values.sortedBy { it.name.lowercase() }
        }
        (message["positions"] as? JsonArray)?.let { array ->
            val current = _positions.value.toMutableMap()
            array.forEach { element ->
                val position = runCatching { AppJson.decodeFromJsonElement<Position>(element) }.getOrNull() ?: return@forEach
                // A tracker's reply to a command comes in as a "position" with only the reply in it, no
                // ignition, battery or fuel state: keep the last real one (the reply shows as an alert)
                if (position.attributes.containsKey("result") && current.containsKey(position.deviceId)) return@forEach
                current[position.deviceId] = position
            }
            _positions.value = current
        }
        if (message.containsKey("positions") || message.containsKey("events")) _updates.tryEmit(Unit)
    }
}
