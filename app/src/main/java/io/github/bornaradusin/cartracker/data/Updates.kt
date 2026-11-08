package io.github.bornaradusin.cartracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.bornaradusin.cartracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** A newer CarTracker, published on GitHub. */
data class AppUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val sumsUrl: String?,
    val page: String,
)

/**
 * Updates from GitHub: the newest published release (drafts and pre-releases don't count), its APK
 * downloaded into the app's cache and checked against the release's SHA256SUMS.txt, then handed to
 * Android's installer. The APK is deleted once the update is in (see [clean]).
 */
class Updates(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        @SerialName("html_url") val page: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
    )

    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", USER_AGENT)

    /** The newest release if it's newer than this app, or null. Throws if GitHub can't be reached. */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val call = request(LATEST).header("Accept", "application/vnd.github+json").build()
        val release = client.newCall(call).execute().use { res ->
            if (!res.isSuccessful) throw IOException("GitHub answered ${res.code}")
            AppJson.decodeFromString(Release.serializer(), res.body?.string().orEmpty())
        }
        if (release.draft || release.prerelease) return@withContext null
        val version = release.tag.trim().removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug") } ?: return@withContext null
        AppUpdate(
            version = version,
            notes = readable(release.body.orEmpty()),
            apkUrl = apk.url,
            apkName = apk.name,
            apkSize = apk.size,
            sumsUrl = release.assets.firstOrNull { it.name == "SHA256SUMS.txt" }?.url,
            page = release.page,
        )
    }

    /** Downloads the update's APK and checks it. [progress] goes from 0 to 1. */
    suspend fun download(update: AppUpdate, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val expected = update.sumsUrl?.let { url ->
            client.newCall(request(url).build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("GitHub answered ${res.code}")
                res.body?.string().orEmpty().lineSequence()
                    .map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size >= 2 && it.last().removePrefix("*") == update.apkName }
                    ?.first()?.lowercase()
            }
        }
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, update.apkName)
        val partial = File(dir, "${update.apkName}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(request(update.apkUrl).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("GitHub answered ${res.code}")
            val body = res.body ?: throw IOException("Nothing came back")
            val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
            body.byteStream().use { input ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (expected != null && expected != actual) {
            partial.delete()
            throw IOException("The download doesn't match the release's checksum")
        }
        if (!partial.renameTo(file)) throw IOException("Couldn't keep the download")
        file
    }

    /** Whether Android lets CarTracker install apps; if not, [installPermission] opens the switch. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermission(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens Android's installer for the downloaded APK. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/bornaradusin/CarTracker/releases/latest"
        private const val DIR = "updates"

        /** Deletes downloaded APKs, once an update is installed. */
        fun clean(context: Context) {
            File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
        }

        /**
         * When the app starts: deletes APKs of this version or older (already installed) and any
         * left from an install given up a day ago. Not all of them, as Android may start the app
         * just so its installer can read the one it's installing.
         */
        fun cleanLeftovers(context: Context) {
            val dayAgo = System.currentTimeMillis() - 24 * 3600 * 1000L
            File(context.cacheDir, DIR).listFiles()?.forEach { f ->
                val version = Regex("(\\d+(\\.\\d+)+)").find(f.name)?.value
                if (version == null || !isNewer(version, BuildConfig.VERSION_NAME) || f.lastModified() < dayAgo) f.delete()
            }
        }

        /** Whether version [a] is newer than [b] ("1.0.10" is newer than "1.0.9"). */
        fun isNewer(a: String, b: String): Boolean {
            fun parts(v: String) = v.split('.', '-', '+').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val x = parts(a)
            val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }

        /** Release notes as plain text: no Markdown headings or code fences, bullets as dots. */
        fun readable(markdown: String): String = markdown
            .replace("\r", "")
            .lines()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n") { line ->
                val t = line.trimEnd()
                when {
                    t.trimStart().startsWith("#") -> t.trimStart().trimStart('#').trim()
                    t.trimStart().startsWith("- ") || t.trimStart().startsWith("* ") ->
                        " ".repeat((t.length - t.trimStart().length) / 2 * 2) + "• " + t.trimStart().drop(2)
                    else -> t
                }
            }
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
