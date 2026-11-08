package io.github.bornaradusin.cartracker.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pictures and animations made for sharing. They live in the app's cache only until they've been sent:
 * once you're back in the app after sharing they're deleted, and anything left over (Android closed the
 * app in the meantime) goes the next time the app is opened. Nothing is kept on the phone.
 *
 * Not on every app start: Android may start the app just so the receiving app can read the file.
 */
object SharedFiles {

    /** The app that received the file reads it in its own time, so it's left a little while after you're back. */
    private const val KEEP_AFTER_RETURN_MS = 90_000L
    private const val LEFTOVER_AGE_MS = 10 * 60_000L

    @Volatile private var handedOver = false
    private var cleanup: Job? = null

    private fun dir(context: Context) = File(context.cacheDir, "shared")

    /** A new file to share; anything made before is deleted first. */
    fun newFile(context: Context, name: String): File {
        cleanup?.cancel()
        clear(context)
        return File(dir(context).apply { mkdirs() }, name)
    }

    fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        handedOver = true
        cleanup?.cancel()
        context.startActivity(Intent.createChooser(send, title))
    }

    /** The app is in front again: if something was shared, delete it shortly. */
    fun appBack(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        if (!handedOver) {
            // Left from before Android closed the app
            scope.launch(Dispatchers.IO) {
                val old = System.currentTimeMillis() - LEFTOVER_AGE_MS
                dir(app).listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
            }
            return
        }
        cleanup?.cancel()
        cleanup = scope.launch {
            delay(KEEP_AFTER_RETURN_MS)
            withContext(Dispatchers.IO) { clear(app) }
            handedOver = false
        }
    }

    /** Deletes everything made for sharing. */
    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
