package io.github.bornaradusin.cartracker

import android.app.Application
import android.content.Context
import io.github.bornaradusin.cartracker.alerts.AlertCenter
import io.github.bornaradusin.cartracker.alerts.Notifier
import io.github.bornaradusin.cartracker.data.AddressLookup
import io.github.bornaradusin.cartracker.data.LiveRepository
import io.github.bornaradusin.cartracker.data.SettingsStore
import io.github.bornaradusin.cartracker.data.TraccarApi
import io.github.bornaradusin.cartracker.data.USER_AGENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.util.concurrent.TimeUnit

class CarTrackerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        MapLibre.getInstance(this)
        // Map tiles say who's asking, as the free map servers ask.
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
                .build(),
        )
        container.start()
    }
}

/** Everything the app shares: settings, the server connection, the live data and the alerts. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val api = TraccarApi()
    val live = LiveRepository(api, scope)
    val addresses = AddressLookup(app)
    val alerts = AlertCenter(app, api, settings, live, addresses)

    fun start() {
        scope.launch {
            settings.flow.map { it.server to it.token }.distinctUntilChanged().collect { (server, token) ->
                val changed = api.baseUrl != server || api.token != token
                api.baseUrl = server
                api.token = token
                if (changed) live.reset()
            }
        }
    }

    /** For work that runs before the app has read its settings (a background check after a restart). */
    suspend fun ready() {
        val s = settings.current()
        api.baseUrl = s.server
        api.token = s.token
    }
}

val Context.container: AppContainer get() = (applicationContext as CarTrackerApp).container
