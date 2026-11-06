package io.github.bornaradusin.cartracker.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Street-level addresses ("Main Street, Cappamore") from the phone's own address lookup, which needs no
 * key. The server's address is used when it has one.
 */
class AddressLookup(private val context: Context) {

    private val cache = LruCache<String, String>(300)

    suspend fun lookup(latitude: Double, longitude: Double, serverAddress: String? = null): String? {
        if (!serverAddress.isNullOrBlank()) return shorten(serverAddress)
        if (latitude == 0.0 && longitude == 0.0) return null
        val key = "%.4f,%.4f".format(Locale.ROOT, latitude, longitude)
        cache.get(key)?.let { return it }
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(8_000) { fetch(latitude, longitude) } ?: return null
        val text = format(address) ?: return null
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

    private fun format(a: Address): String? {
        val street = a.thoroughfare ?: a.featureName?.takeIf { it.any(Char::isLetter) }
        val place = a.locality ?: a.subLocality ?: a.subAdminArea ?: a.adminArea
        return listOfNotNull(street, place).distinct().joinToString(", ").ifBlank { null }
    }

    /** Server addresses can be long ("…, The Municipal District of …, County Limerick, Munster, Ireland"). */
    private fun shorten(address: String): String =
        address.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
}
