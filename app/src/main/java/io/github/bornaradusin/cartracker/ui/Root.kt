package io.github.bornaradusin.cartracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.bornaradusin.cartracker.ui.car.CarScreen
import io.github.bornaradusin.cartracker.ui.controls.ControlsScreen
import io.github.bornaradusin.cartracker.ui.alerts.AlertsScreen
import io.github.bornaradusin.cartracker.ui.login.LoginScreen
import io.github.bornaradusin.cartracker.ui.more.AlertTypesScreen
import io.github.bornaradusin.cartracker.ui.more.MoreScreen
import io.github.bornaradusin.cartracker.ui.theme.CarTrackerTheme
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.ui.trips.ReplayScreen
import io.github.bornaradusin.cartracker.ui.trips.TripsScreen
import io.github.bornaradusin.cartracker.ui.trips.StatsScreen
import io.github.bornaradusin.cartracker.ui.trips.HeatmapScreen
import io.github.bornaradusin.cartracker.ui.battery.BatteryScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("car", "Car", Ic.car),
    Tab("trips", "Trips", Ic.route),
    Tab("alerts", "Alerts", Ic.bell),
    Tab("more", "More", Ic.menu),
)

@Composable
fun CarTrackerRoot(openAlertsSignal: Int) {
    val vm: AppViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    CarTrackerTheme(settings.theme, settings.accent) {
        val c = LocalAppColors.current
        Box(Modifier.fillMaxSize().background(c.background)) {
            when {
                settings.token == "loading" -> Unit
                !settings.signedIn -> LoginScreen(vm)
                else -> MainShell(vm, openAlertsSignal)
            }
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel, openAlertsSignal: Int) {
    val c = LocalAppColors.current
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val unread by vm.unreadAlerts.collectAsStateWithLifecycle()
    val rejected by vm.signInRejected.collectAsStateWithLifecycle()
    val reply by vm.reply.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(openAlertsSignal) { if (openAlertsSignal > 0) nav.goTab("alerts") }
    NotificationPermission()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = "car") {
                composable("car") {
                    CarScreen(
                        vm,
                        onOpenControls = { nav.navigate("controls") },
                        onOpenAlerts = { nav.goTab("alerts") },
                        onOpenBattery = { nav.navigate("battery") },
                    )
                }
                composable("trips") {
                    TripsScreen(
                        vm,
                        onOpenTrip = { id, from, to -> nav.navigate("replay/$id/$from/$to") },
                        onOpenStats = { nav.navigate("stats") },
                        onOpenHeatmap = { nav.navigate("heatmap") },
                    )
                }
                composable("alerts") { AlertsScreen(vm) }
                composable("more") { MoreScreen(vm, onOpenAlertTypes = { nav.navigate("alert-types") }) }
                composable("controls") { ControlsScreen(vm, onBack = { nav.popBackStack() }) }
                composable("alert-types") { AlertTypesScreen(vm, onBack = { nav.popBackStack() }) }
                composable("battery") { BatteryScreen(vm, onBack = { nav.popBackStack() }) }
                composable("stats") { StatsScreen(vm, onBack = { nav.popBackStack() }) }
                composable("heatmap") { HeatmapScreen(vm, onBack = { nav.popBackStack() }) }
                composable(
                    "replay/{id}/{from}/{to}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("from") { type = NavType.LongType },
                        navArgument("to") { type = NavType.LongType },
                    ),
                ) { back ->
                    val args = back.arguments
                    ReplayScreen(
                        vm,
                        deviceId = args?.getLong("id") ?: 0L,
                        fromMillis = args?.getLong("from") ?: 0L,
                        toMillis = args?.getLong("to") ?: 0L,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (c.dark) c.surface2 else c.ink,
                    contentColor = if (c.dark) c.ink else c.surface,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
        if (route in tabs.map { it.route }) {
            NavigationBar(containerColor = c.surface, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.goTab(tab.route) },
                        icon = {
                            if (tab.route == "alerts" && unread > 0) {
                                BadgedBox(badge = { Badge(containerColor = c.warn) { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                    Icon(tab.icon, null, modifier = Modifier.size(22.dp))
                                }
                            } else {
                                Icon(tab.icon, null, modifier = Modifier.size(22.dp))
                            }
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accentText,
                            selectedTextColor = c.accentText,
                            indicatorColor = c.accentTint,
                            unselectedIconColor = c.muted,
                            unselectedTextColor = c.muted,
                        ),
                    )
                }
            }
        }
    }

    if (rejected) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Signed out by the server") },
            text = { Text("The server no longer accepts this phone's sign-in. Please sign in again.") },
            confirmButton = { TextButton(onClick = { vm.signOut() }) { Text("Sign in again") } },
        )
    }

    reply?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.dismissReply() },
            containerColor = c.surface,
            title = { Text("${r.car} replied", color = c.ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(r.command, style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
                    Text(r.text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissReply() }) { Text("OK", color = c.accentText) } },
        )
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo("car") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Asks once for permission to show alerts (Android 13 and later). */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
