package io.github.bornaradusin.cartracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.ui.theme.Palette

/** A white rounded card holding a settings or controls section. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(horizontal = contentPadding, vertical = 6.dp),
    ) {
        if (title != null) SectionTitle(title)
        content()
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
fun SectionTitle(text: String) {
    val c = LocalAppColors.current
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
        color = c.muted,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** The switch from the design: 52 × 32, accent when on. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) {
    val c = LocalAppColors.current
    val track by animateColorAsState(if (checked) c.accentOutline.takeIf { Palette.isNearWhite(c.accent) } ?: c.accent else if (c.dark) Color(0xFF3A4558) else Color(0xFFC9D1DB), label = "track")
    val knob by animateDpAsState(if (checked) 24.dp else 4.dp, label = "knob")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(track.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 4.dp)
                .size(24.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** A row with a title, an optional hint and something on the right. */
@Composable
fun SettingRow(
    title: String,
    hint: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAppColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.ink)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            trailing()
        }
        if (divider) HorizontalDivider(color = c.divider, thickness = 1.dp)
    }
}

@Composable
fun ToggleRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = true, enabled: Boolean = true) {
    SettingRow(title, hint, divider) { Toggle(checked, onChange, title, enabled) }
}

@Composable
fun ChevronRow(title: String, hint: String? = null, value: String? = null, divider: Boolean = true, onClick: () -> Unit) {
    val c = LocalAppColors.current
    SettingRow(title, hint, divider, onClick) {
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Icon(io.github.bornaradusin.cartracker.ui.theme.Ic.chevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

/** Small grey tile: label above a value. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round 44 dp map button. */
@Composable
fun MapButton(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.accent else c.surface,
        contentColor = if (selected) c.onAccent else c.ink,
        shadowElevation = 4.dp,
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
    }
}

/** A pill with a coloured dot: "Parked", "Moving", "Offline". */
@Composable
fun StatusPill(text: String, dot: Color, background: Color, textColor: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = textColor)
    }
}

/** Full-width button in the accent colour (or the warning colour). */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, warning: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg = if (warning) c.warn else c.accent
    val fg = if (warning) Color.White else c.onAccent
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = bg.copy(alpha = if (enabled) 1f else 0.5f),
        contentColor = fg,
        border = if (!warning && Palette.isNearWhite(c.accent)) BorderStroke(1.5.dp, c.accentOutline) else null,
        modifier = modifier.heightIn(min = 54.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Outlined or grey secondary button. */
@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, outlined: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (outlined) c.surface else c.surface2,
        contentColor = c.ink,
        border = if (outlined) BorderStroke(1.5.dp, c.line) else null,
        modifier = modifier.heightIn(min = 50.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Round colour swatch with a selection ring. */
@Composable
fun Swatch(color: Color, label: String, selected: Boolean, ring: Color, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(2.5.dp, if (selected) ring else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .then(if (color == Color.White) Modifier.border(1.5.dp, c.line, CircleShape) else Modifier),
        )
    }
}

/** Screen header: optional back arrow, title, optional subtitle, optional right-hand content. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(io.github.bornaradusin.cartracker.ui.theme.Ic.back, null, tint = c.ink, modifier = Modifier.size(22.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                color = c.ink,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        trailing()
    }
}
