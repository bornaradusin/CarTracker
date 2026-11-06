package io.github.bornaradusin.cartracker.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Turns what was typed into a server address. "track.example.com" becomes https://track.example.com;
 * plain http:// is only accepted on the home network (192.168.x, 10.x, 172.16-31.x, Tailscale's
 * 100.64-127.x, *.local, localhost), so passwords never cross the internet unencrypted.
 */
object ServerAddress {

    sealed interface Result {
        data class Ok(val url: String) : Result
        data class Problem(val message: String) : Result
    }

    fun parse(input: String): Result {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return Result.Problem("Enter your Traccar server's address.")
        val withScheme = if ("://" in trimmed) trimmed else {
            val host = trimmed.substringBefore('/').substringBefore(':')
            if (isHomeNetwork(host)) "http://$trimmed" else "https://$trimmed"
        }
        val url: HttpUrl = withScheme.toHttpUrlOrNull()
            ?: return Result.Problem("That doesn't look like a web address.")
        if (url.scheme == "http" && !isHomeNetwork(url.host)) {
            return Result.Problem("Use https:// for a server on the internet. Plain http:// only works at home.")
        }
        return Result.Ok(url.toString().trimEnd('/'))
    }

    fun isHomeNetwork(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true
        val parts = h.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || h.count { it == '.' } != 3) return false
        val (a, b) = parts
        return a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 100 && b in 64..127)
    }
}
