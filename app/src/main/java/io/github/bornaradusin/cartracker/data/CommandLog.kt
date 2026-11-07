package io.github.bornaradusin.cartracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** A command sent from this phone, and what came of it. */
@Serializable
data class CommandEntry(
    /** When it was sent, in milliseconds; also its id. */
    val id: Long,
    val deviceId: Long,
    val car: String,
    val label: String,
    /** The app's own button it came from, so it can be sent again. */
    val action: String? = null,
    val state: String = SENDING,
    /** How many times it was sent: it's sent again when the tracker doesn't answer. */
    val attempts: Int = 1,
    /** The tracker's answer, in its words. */
    val reply: String? = null,
    val replyAt: Long = 0,
    val replyEventId: Long = 0,
    /** Confirmed by the car's own report rather than an answer. */
    val byReport: Boolean = false,
    /** Why it couldn't be sent. */
    val problem: String? = null,
) {
    val open: Boolean get() = state == SENDING || state == WAITING || state == QUEUED

    companion object {
        const val SENDING = "sending"
        const val WAITING = "waiting"
        const val QUEUED = "queued"
        const val DONE = "done"
        const val FAILED = "failed"
        const val NO_ANSWER = "no_answer"
        const val NOT_SENT = "not_sent"
    }
}

/** The commands sent from this phone over the last week, kept on the phone for the Alerts list. */
class CommandLog(private val file: File, private val scope: CoroutineScope) {

    private val _entries = MutableStateFlow<List<CommandEntry>>(emptyList())
    val entries: StateFlow<List<CommandEntry>> = _entries.asStateFlow()
    private val saving = Mutex()
    private var lastId = 0L

    init {
        scope.launch(Dispatchers.IO) {
            val saved = runCatching { AppJson.decodeFromString(ListSerializer(CommandEntry.serializer()), file.readText()) }.getOrNull().orEmpty()
            // Anything still open when the app last stopped has no watcher any more
            val loaded = saved.map { if (it.open && it.state != CommandEntry.QUEUED) it.copy(state = CommandEntry.NO_ANSWER) else it }
            _entries.update { current -> (current + loaded).distinctBy { it.id }.sortedByDescending { it.id } }
        }
    }

    /** Adds a command just being sent; returns its id. */
    fun add(deviceId: Long, car: String, label: String, action: String?): Long {
        val id = maxOf(System.currentTimeMillis(), lastId + 1).also { lastId = it }
        _entries.update { listOf(CommandEntry(id, deviceId, car, label, action)) + it }
        save()
        return id
    }

    fun update(id: Long, change: (CommandEntry) -> CommandEntry) {
        _entries.update { list -> list.map { if (it.id == id) change(it) else it } }
        save()
    }

    fun clear() {
        _entries.update { list -> list.filter { it.open } }
        save()
    }

    private fun save() {
        scope.launch(Dispatchers.IO) {
            saving.withLock {
                // The latest list, read here, so saves can't land out of order
                val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
                val keep = _entries.value.filter { it.id > weekAgo }.take(300)
                runCatching {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(AppJson.encodeToString(ListSerializer(CommandEntry.serializer()), keep))
                    temp.renameTo(file)
                }
            }
        }
    }
}
