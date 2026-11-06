package io.github.bornaradusin.cartracker

import android.content.Intent
import android.os.Build
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
        useHighestRefreshRate()
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALERTS, false) == true) openAlerts.intValue++
        setContent { CarTrackerRoot(openAlertsSignal = openAlerts.intValue) }
    }

    /**
     * Asks for the screen's highest refresh rate (90, 120 Hz…) at its current resolution, so
     * scrolling and the map move as smoothly as the phone allows. The phone can still lower it,
     * for example in battery saver.
     */
    private fun useHighestRefreshRate() {
        val screen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        val current = screen?.mode ?: return
        val best = screen.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.modeId != current.modeId || best.refreshRate > 60f) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
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
