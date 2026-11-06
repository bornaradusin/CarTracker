package io.github.bornaradusin.cartracker.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.LruCache
import io.github.bornaradusin.cartracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Street-level addresses ("Main Street, Cappamore"), with no key needed. In order: the server's own
 * address when it has one, the phone's address lookup, then OpenStreetMap. If none answers, the
 * coordinates, so the app never sits on "Finding the address…".
 */
class AddressLookup(private val context: Context) {

    private val cache = LruCache<String, String>(500)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // OpenStreetMap asks for at most one lookup a second from each app
    private val osmLock = Mutex()
    private var lastOsmCall = 0L

    suspend fun lookup(latitude: Double, longitude: Double, serverAddress: String? = null): String? {
        if (!serverAddress.isNullOrBlank()) return shorten(serverAddress)
        if (latitude == 0.0 && longitude == 0.0) return null
        val key = "%.4f,%.4f".format(Locale.ROOT, latitude, longitude)
        cache.get(key)?.let { return it }
        val phone = if (Geocoder.isPresent()) withTimeoutOrNull(8_000) { fetch(latitude, longitude) }?.let(::format) else null
        val text = phone
            ?: openStreetMap(latitude, longitude)
            ?: return "%.5f, %.5f".format(Locale.ROOT, latitude, longitude)
        cache.put(key, text)
        return text
    }

    private suspend fun fetch(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    /** OpenStreetMap's Nominatim, for phones whose own lookup gives nothing. */
    private suspend fun openStreetMap(latitude: Double, longitude: Double): String? = osmLock.withLock {
        val wait = 1_100 - (System.currentTimeMillis() - lastOsmCall)
        if (wait > 0) delay(wait)
        lastOsmCall = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
                    .addQueryParameter("format", "jsonv2")
                    .addQueryParameter("zoom", "17")
                    .addQueryParameter("lat", latitude.toString())
                    .addQueryParameter("lon", longitude.toString())
                    .addQueryParameter("accept-language", Locale.getDefault().toLanguageTag())
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "CarTracker/${BuildConfig.VERSION_NAME} (https://github.com/bornaradusin/CarTracker)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val address = AppJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject["address"] as? JsonObject
                        ?: return@use null
                    val street = address.str("road") ?: address.str("pedestrian") ?: address.str("neighbourhood")
                    val place = address.str("town") ?: address.str("city") ?: address.str("village") ?: address.str("suburb")
                        ?: address.str("hamlet") ?: address.str("county")
                    listOfNotNull(street, place).distinct().joinToString(", ").ifBlank { null }
                }
            }.getOrNull()
        }
    }

    private fun format(a: Address): String? {
        val street = a.thoroughfare ?: a.featureName?.takeIf { it.any(Char::isLetter) }
        val place = a.locality ?: a.subLocality ?: a.subAdminArea ?: a.adminArea
        return listOfNotNull(street, place).distinct().joinToString(", ").ifBlank { null }
    }

    /** Server addresses can be long ("…, The Municipal District of …, County Limerick, Munster, Ireland"). */
    private fun shorten(address: String): String =
        address.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
}
