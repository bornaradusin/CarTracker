package io.github.bornaradusin.cartracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import io.github.bornaradusin.cartracker.ui.theme.Palette
import kotlin.math.roundToInt

/** A settings row showing a colour. Tapping it opens the RGB picker. */
@Composable
fun ColorRow(
    title: String,
    hint: String?,
    color: Color,
    default: Color,
    divider: Boolean = true,
    preview: (@Composable (Color) -> Unit)? = null,
    onPick: (Color) -> Unit,
) {
    val c = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    SettingRow(title, hint, divider = divider, onClick = { open = true }) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, if (Palette.isNearWhite(color)) c.line else Color.Transparent, CircleShape)
                .semantics { contentDescription = "$title ${Palette.hex(color)}" },
        )
        Icon(Ic.chevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
    if (open) {
        ColorDialog(title, color, default, preview, onDismiss = { open = false }) {
            open = false
            onPick(it)
        }
    }
}

/** Red, green and blue sliders with a live preview and the colour's hex code. */
@Composable
fun ColorDialog(
    title: String,
    initial: Color,
    default: Color,
    preview: (@Composable (Color) -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (Color) -> Unit,
) {
    val c = LocalAppColors.current
    var r by remember { mutableIntStateOf((initial.red * 255).roundToInt()) }
    var g by remember { mutableIntStateOf((initial.green * 255).roundToInt()) }
    var b by remember { mutableIntStateOf((initial.blue * 255).roundToInt()) }
    val current = Color(r, g, b)
    var hexText by remember { mutableStateOf(Palette.hex(initial)) }
    fun set(color: Color) {
        r = (color.red * 255).roundToInt()
        g = (color.green * 255).roundToInt()
        b = (color.blue * 255).roundToInt()
        hexText = Palette.hex(color)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (preview != null) 120.dp else 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (preview != null) c.surface2 else current)
                        .border(1.dp, c.line, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { preview?.invoke(current) }
                ChannelSlider("R", r, Color(0xFFE53935)) { r = it; hexText = Palette.hex(Color(r, g, b)) }
                ChannelSlider("G", g, Color(0xFF2E9E44)) { g = it; hexText = Palette.hex(Color(r, g, b)) }
                ChannelSlider("B", b, Color(0xFF1E63E9)) { b = it; hexText = Palette.hex(Color(r, g, b)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text.uppercase().take(7)
                            val hex = hexText.removePrefix("#")
                            if (hex.length == 6 && hex.all { it.isDigit() || it in 'A'..'F' }) {
                                val parsed = Palette.parse(hex, current)
                                r = (parsed.red * 255).roundToInt()
                                g = (parsed.green * 255).roundToInt()
                                b = (parsed.blue * 255).roundToInt()
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accentOutline, unfocusedBorderColor = c.line, cursorColor = c.accentOutline),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { set(default) }) { Text("Default", color = c.ink2) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current) }) { Text("Save", color = c.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

@Composable
private fun ChannelSlider(label: String, value: Int, color: Color, onChange: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink2, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.18f),
            ),
            modifier = Modifier.weight(1f).semantics { contentDescription = "$label $value" },
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = c.ink2,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp).padding(start = 4.dp),
        )
    }
}
