package io.github.bornaradusin.cartracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import io.github.bornaradusin.cartracker.ui.CarTrackerRoot

class MainActivity : ComponentActivity() {

    /** Bumped when a notification asks to open the alerts list. */
    private val openAlerts = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALERTS, false) == true) openAlerts.intValue++
        setContent { CarTrackerRoot(openAlertsSignal = openAlerts.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)) openAlerts.intValue++
    }

    override fun onStart() {
        super.onStart()
        container.alerts.appVisible = true
        container.live.acquire(HOLDER)
    }

    override fun onStop() {
        container.alerts.appVisible = false
        container.live.release(HOLDER)
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
        private const val HOLDER = "screen"
    }
}
