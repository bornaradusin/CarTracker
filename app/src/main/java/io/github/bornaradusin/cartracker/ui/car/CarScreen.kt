package io.github.bornaradusin.cartracker.ui.car

import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import io.github.bornaradusin.cartracker.ui.map.CarPin
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.data.Action
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.CarInfo
import io.github.bornaradusin.cartracker.ui.PendingChange
import io.github.bornaradusin.cartracker.ui.CarStatus
import io.github.bornaradusin.cartracker.ui.components.MapButton
import io.github.bornaradusin.cartracker.ui.components.StatTile
import io.github.bornaradusin.cartracker.ui.components.StatusPill
import io.github.bornaradusin.cartracker.ui.map.MapCamera
import io.github.bornaradusin.cartracker.ui.map.MapCircle
import io.github.bornaradusin.cartracker.ui.map.MapMarker
import io.github.bornaradusin.cartracker.ui.map.MapStyle
import io.github.bornaradusin.cartracker.ui.map.TrackerMap
import io.github.bornaradusin.cartracker.ui.theme.AppColors
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.util.Format
import java.time.Instant

@Composable
fun CarScreen(vm: AppViewModel, onOpenControls: () -> Unit, onOpenAlerts: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cars by vm.cars.collectAsStateWithLifecycle()
    val car by vm.selectedCar.collectAsStateWithLifecycle()
    val fences by vm.geofences.collectAsStateWithLifecycle()
    val unread by vm.unreadAlerts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pendingChanges by vm.pending.collectAsStateWithLifecycle()
    val mapStyle = MapStyle.named(settings.mapStyle)

    var sheetHeight by remember { mutableIntStateOf(0) }
    var cameraStamp by remember { mutableIntStateOf(0) }
    var styleMenu by remember { mutableStateOf(false) }
    var carMenu by remember { mutableStateOf(false) }
    var fuelDialog by remember { mutableStateOf(false) }
    val selectedId = car?.device?.id
    val fix = car?.position?.takeIf { it.hasFix }

    // The card slides down to show more map: 0 = where it usually sits, up to [collapseRange] = only its top showing
    val scope = rememberCoroutineScope()
    val sheetOffset = remember { Animatable(0f) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val peekPx = with(density) { 104.dp.toPx() }
    val collapseRange = (sheetHeight - peekPx).coerceAtLeast(0f)
    LaunchedEffect(collapseRange, collapsed) {
        sheetOffset.updateBounds(0f, collapseRange)
        sheetOffset.snapTo(if (collapsed) collapseRange else 0f)
    }
    fun settle(velocity: Float) {
        scope.launch {
            val down = when {
                velocity > 800f -> true
                velocity < -800f -> false
                else -> sheetOffset.value > collapseRange / 2f
            }
            sheetOffset.animateTo(if (down) collapseRange else 0f, initialVelocity = velocity)
            collapsed = down
        }
    }

    // Keep the car in the middle of the map: on every new position, and when the card moves
    LaunchedEffect(selectedId, fix?.latitude, fix?.longitude, collapsed) { if (fix != null) cameraStamp++ }
    LaunchedEffect(selectedId) { selectedId?.let { vm.loadCommandOptions(it) } }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetDp = with(density) { (sheetHeight - sheetOffset.value).coerceAtLeast(0f).toDp() }

    Box(Modifier.fillMaxSize()) {
        TrackerMap(
            style = mapStyle,
            dark = c.dark,
            markers = cars.mapNotNull { info ->
                val p = info.position?.takeIf { it.hasFix } ?: return@mapNotNull null
                MapMarker(info.device.id, p.latitude, p.longitude, p.course, info.icon, info.carColor, info.glowColor)
            },
            circles = selectedId?.let { id ->
                fences[id].orEmpty().mapNotNull { f -> f.circle?.let { MapCircle(f.id, f.name, it.latitude, it.longitude, it.radius) } }
            }.orEmpty(),
            routeColor = c.accentOutline,
            camera = car?.position?.takeIf { it.hasFix }?.let { MapCamera.Center(it.latitude, it.longitude, if (cameraStamp <= 1) 15.0 else null, cameraStamp) },
            topPadding = topInset + 72.dp,
            bottomPadding = sheetDp,
            onMarkerClick = { id -> vm.select(id) },
        )

        // top: car switcher and alerts
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Surface(
                    onClick = { if (cars.size > 1) carMenu = true },
                    shape = RoundedCornerShape(999.dp),
                    color = c.surface,
                    contentColor = c.ink,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        Modifier.heightIn(min = 48.dp).padding(start = 14.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val shown = car
                        if (shown != null) CarPin(shown.icon, shown.carColor, shown.glowColor, 22.dp) else Icon(Ic.car, null, modifier = Modifier.size(20.dp))
                        Text(car?.device?.name ?: "No cars yet", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
                        if (cars.size > 1) Icon(Ic.chevronDown, null, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(carMenu, onDismissRequest = { carMenu = false }, containerColor = c.surface) {
                    cars.forEach { info ->
                        DropdownMenuItem(
                            text = { Text(info.device.name, color = c.ink) },
                            leadingIcon = { CarPin(info.icon, info.carColor, info.glowColor, 20.dp) },
                            trailingIcon = { if (info.device.id == selectedId) Icon(Ic.check, null, tint = c.accentText) },
                            onClick = {
                                carMenu = false
                                vm.select(info.device.id)
                            },
                        )
                    }
                }
            }
            Surface(
                onClick = onOpenAlerts,
                shape = CircleShape,
                color = c.surface,
                contentColor = c.ink,
                shadowElevation = 4.dp,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Alerts, $unread new" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Ic.bell, null, modifier = Modifier.size(22.dp))
                    if (unread > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp).size(18.dp).clip(CircleShape).background(c.warn),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (unread > 9) "9+" else unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // right: map buttons, just above the card
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = sheetDp + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                MapButton(Ic.layers, "Map style", selected = styleMenu) { styleMenu = true }
                DropdownMenu(styleMenu, onDismissRequest = { styleMenu = false }, containerColor = c.surface) {
                    Text("Map style", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    MapStyle.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label, color = c.ink, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { StyleSwatch(s, c) },
                            trailingIcon = { if (s == mapStyle) Icon(Ic.check, null, tint = c.accentText) },
                            onClick = {
                                styleMenu = false
                                vm.setMapStyle(s.name)
                            },
                        )
                    }
                }
            }
            MapButton(Ic.crosshair, "Centre on the car") { cameraStamp++ }
        }

        // bottom: the car card
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> scope.launch { sheetOffset.snapTo(sheetOffset.value + delta) } },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        ) {
            val info = car
            if (info != null) {
                CarCard(
                    info = info,
                    busy = busy,
                    alarmPending = pendingChanges[info.device.id]?.key == PendingChange.ALARM,
                    onImmobiliser = { fuelDialog = true },
                    onAlarm = { vm.send(info, if (info.armed) Action.DISARM else Action.ARM) },
                    onDirections = {
                        info.position?.takeIf { it.hasFix }?.let {
                            openUrl(context, "https://www.google.com/maps/dir/?api=1&destination=${it.latitude},${it.longitude}&travelmode=driving")
                        }
                    },
                    onShare = { share(context, info) },
                    onControls = onOpenControls,
                )
            } else {
                EmptyCard()
            }
        }
    }

    val info = car
    if (fuelDialog && info != null) {
        FuelDialog(
            info = info,
            canCutSlow = vm.canDo(info, Action.CUT_SLOW),
            onDismiss = { fuelDialog = false },
            onAction = { action ->
                fuelDialog = false
                vm.send(info, action)
            },
        )
    }
}

@Composable
private fun StyleSwatch(style: MapStyle, c: AppColors) {
    val colors = if (c.dark) style.swatchDark else style.swatch
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(colors[0]).border(1.dp, c.line, RoundedCornerShape(9.dp)),
    ) {
        Box(Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(18.dp).clip(CircleShape).background(colors[2]))
        Box(Modifier.align(Alignment.CenterStart).offset((-4).dp, 0.dp).width(44.dp).height(5.dp).rotate(-20f).background(colors[1]))
    }
}

@Composable
private fun CarCard(
    info: CarInfo,
    busy: String?,
    alarmPending: Boolean,
    onImmobiliser: () -> Unit,
    onAlarm: () -> Unit,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    onControls: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(c.surface)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))

        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.device.name, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.pin, null, tint = c.muted, modifier = Modifier.size(16.dp))
                    Text(info.address ?: "Finding the address…", style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (info.status) {
                    CarStatus.MOVING -> StatusPill("Moving", c.accentOutline, c.accentTint, c.accentText)
                    CarStatus.PARKED -> StatusPill("Parked", c.muted, c.surface2, c.ink2)
                    CarStatus.OFFLINE -> StatusPill("Offline", c.warnText, c.warnTint, c.warnText)
                }
                Text(
                    if (info.status == CarStatus.OFFLINE) "Last seen ${Format.ago(info.updated)}" else "Updated ${Format.time(info.updated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                "Ignition",
                when (info.ignition) {
                    true -> "On"
                    false -> "Off"
                    null -> "–"
                },
                Modifier.weight(1f),
            )
            if (info.status == CarStatus.MOVING) {
                StatTile("Speed", "${info.speedKmh} km/h", Modifier.weight(1f))
            } else {
                StatTile("Parked for", info.parkedSince?.let { Format.duration(Instant.now().toEpochMilli() - it.toEpochMilli()) } ?: "–", Modifier.weight(1f))
            }
            StatTile("Today", info.todayMeters?.let { Format.distance(it) } ?: "–", Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            QuickAction(
                icon = Ic.lock,
                label = if (info.immobilised) "Immobilised" else "Immobilise",
                tile = if (info.immobilised) c.warn else c.surface2,
                iconColor = if (info.immobilised) Color.White else c.ink,
                labelColor = if (info.immobilised) c.warnText else c.ink,
                bold = info.immobilised,
                working = busy != null && (busy.contains("fuel", ignoreCase = true)),
                onClick = onImmobiliser,
            )
            QuickAction(
                icon = Ic.shieldCheck,
                label = if (alarmPending) "Confirming…" else if (info.armed) "Armed" else "Alarm off",
                tile = if (info.armed) c.accent else c.surface2,
                iconColor = if (info.armed) c.onAccent else c.ink,
                labelColor = if (info.armed) c.accentText else c.ink,
                bold = info.armed,
                working = alarmPending || (busy != null && busy.startsWith("Alarm")),
                onClick = onAlarm,
            )
            QuickAction(Ic.navigation, "Directions", c.accentTint, c.accentText, c.ink, false, false, onDirections)
            QuickAction(Ic.share, "Share", c.accentTint, c.accentText, c.ink, false, false, onShare)
        }

        Surface(onClick = onControls, shape = RoundedCornerShape(14.dp), color = c.surface2, contentColor = c.ink, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.sliders, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("All controls", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(Ic.chevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    tile: Color,
    iconColor: Color,
    labelColor: Color,
    bold: Boolean,
    working: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(tile), contentAlignment = Alignment.Center) {
            if (working) CircularProgressIndicator(color = iconColor, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium), color = labelColor, maxLines = 1)
    }
}

@Composable
private fun EmptyCard() {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.surface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No cars yet", style = MaterialTheme.typography.titleLarge, color = c.ink)
        Text(
            "Your account has no trackers on this server. Add one in Traccar's web page, or ask the server's admin to share one with you.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )
    }
}

@Composable
private fun FuelDialog(info: CarInfo, canCutSlow: Boolean, onDismiss: () -> Unit, onAction: (Action) -> Unit) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        icon = {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.warnTint), contentAlignment = Alignment.Center) {
                Icon(if (info.immobilised) Ic.unlock else Ic.lock, null, tint = c.warnText, modifier = Modifier.size(26.dp))
            }
        },
        title = { Text(if (info.immobilised) "Restore fuel to ${info.device.name}?" else "Cut fuel to ${info.device.name}?", color = c.ink) },
        text = {
            Text(
                if (info.immobilised) "The car can be started again once the tracker confirms."
                else "The engine stops and won't restart until you restore fuel." +
                    if (canCutSlow) " If the car is moving, use \"Cut when slow\" instead." else "",
                color = c.ink2,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (info.immobilised) {
                    TextButton(onClick = { onAction(Action.RESTORE) }) { Text("Restore fuel", color = c.accentText, fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = { onAction(Action.CUT_NOW) }) { Text("Cut fuel now", color = c.warnText, fontWeight = FontWeight.Bold) }
                    if (canCutSlow) TextButton(onClick = { onAction(Action.CUT_SLOW) }) { Text("Cut when slow", color = c.warnText) }
                    // In case the car is already cut but the tracker hasn't said so yet.
                    TextButton(onClick = { onAction(Action.RESTORE) }) { Text("Restore fuel", color = c.ink2) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun share(context: Context, info: CarInfo) {
    val p = info.position?.takeIf { it.hasFix } ?: return
    val where = info.address?.let { " at $it" } ?: ""
    val text = "${info.device.name} is$where: https://www.google.com/maps/search/?api=1&query=${p.latitude},${p.longitude}"
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(intent, "Share where ${info.device.name} is").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
