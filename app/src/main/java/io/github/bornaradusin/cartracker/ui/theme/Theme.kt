package io.github.bornaradusin.cartracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One accent colour, as it looks in each theme. */
@Immutable
data class Accent(
    val name: String,
    val light: Color,
    val onLight: Color,
    val tintLight: Color,
    val outlineLight: Color,
    val dark: Color,
    val onDark: Color,
    val tintDark: Color,
    val textDark: Color,
)

object Accents {
    val all = listOf(
        Accent("Blue", Color(0xFF1D5BD8), Color.White, Color(0xFFE8EFFC), Color(0xFF1D5BD8), Color(0xFF3D73E0), Color.White, Color(0xFF1A2B4D), Color(0xFF7FA6F5)),
        Accent("Teal", Color(0xFF0F7B6C), Color.White, Color(0xFFE3F2EF), Color(0xFF0F7B6C), Color(0xFF14907E), Color.White, Color(0xFF123530), Color(0xFF5CC4B3)),
        Accent("Green", Color(0xFF2F7D3A), Color.White, Color(0xFFE6F2E7), Color(0xFF2F7D3A), Color(0xFF3A9447), Color.White, Color(0xFF173322), Color(0xFF79C788)),
        Accent("Purple", Color(0xFF6B4FD8), Color.White, Color(0xFFEEEAFB), Color(0xFF6B4FD8), Color(0xFF7C63E6), Color.White, Color(0xFF261F45), Color(0xFFA99BF0)),
        Accent("Pink", Color(0xFFB83280), Color.White, Color(0xFFF8E6F0), Color(0xFFB83280), Color(0xFFCC3F92), Color.White, Color(0xFF3A1A2C), Color(0xFFEE8CC2)),
        Accent("Red", Color(0xFFC62828), Color.White, Color(0xFFFBE7E6), Color(0xFFC62828), Color(0xFFD9443B), Color.White, Color(0xFF3A1A1A), Color(0xFFF28B82)),
        Accent("Yellow", Color(0xFFF2B705), Color(0xFF1A1206), Color(0xFFFDF3D0), Color(0xFFC99700), Color(0xFFF2B705), Color(0xFF1A1206), Color(0xFF3A2F0C), Color(0xFFF5CA4B)),
        Accent("White", Color(0xFFFFFFFF), Color(0xFF0E1726), Color(0xFFF4F6F8), Color(0xFF3B4656), Color(0xFFFFFFFF), Color(0xFF0E1726), Color(0xFF2A3445), Color(0xFFFFFFFF)),
        Accent("Graphite", Color(0xFF3B4656), Color.White, Color(0xFFE9ECF0), Color(0xFF3B4656), Color(0xFF8C99AB), Color(0xFF0B1220), Color(0xFF243044), Color(0xFFB8C2D0)),
    )

    fun named(name: String): Accent = all.firstOrNull { it.name == name } ?: all.first()
}

/** The app's own colour tokens, beyond Material's. */
@Immutable
data class AppColors(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val line: Color,
    val divider: Color,
    /** Filled accent (buttons, selected tab, switches). */
    val accent: Color,
    val onAccent: Color,
    /** Pale accent behind icons and selected rows. */
    val accentTint: Color,
    /** Accent as text or an outline on the surface. */
    val accentText: Color,
    val accentOutline: Color,
    val warn: Color,
    val warnText: Color,
    val warnTint: Color,
    val good: Color,
    val shadow: Color,
)

fun appColors(dark: Boolean, accent: Accent): AppColors = if (dark) {
    AppColors(
        dark = true,
        background = Color(0xFF0B1220),
        surface = Color(0xFF121A27),
        surface2 = Color(0xFF1A2433),
        ink = Color(0xFFE8EDF4),
        ink2 = Color(0xFFC5CEDA),
        muted = Color(0xFF9AA6B8),
        line = Color(0xFF243044),
        divider = Color(0xFF1F2A3A),
        accent = accent.dark,
        onAccent = accent.onDark,
        accentTint = accent.tintDark,
        accentText = accent.textDark,
        accentOutline = accent.textDark,
        warn = Color(0xFFB4540A),
        warnText = Color(0xFFF0A35E),
        warnTint = Color(0xFF3A2615),
        good = Color(0xFF79C788),
        shadow = Color(0x66000000),
    )
} else {
    AppColors(
        dark = false,
        background = Color(0xFFF4F6F8),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFF4F6F8),
        ink = Color(0xFF0E1726),
        ink2 = Color(0xFF3B4656),
        muted = Color(0xFF5B6676),
        line = Color(0xFFE3E8EE),
        divider = Color(0xFFEEF1F5),
        accent = accent.light,
        onAccent = accent.onLight,
        accentTint = accent.tintLight,
        accentText = if (accent.name == "White" || accent.name == "Yellow") accent.outlineLight else accent.light,
        accentOutline = accent.outlineLight,
        warn = Color(0xFFB4540A),
        warnText = Color(0xFFB4540A),
        warnTint = Color(0xFFFCEEE3),
        good = Color(0xFF2F6B3A),
        shadow = Color(0x220E1726),
    )
}

val LocalAppColors = staticCompositionLocalOf { appColors(false, Accents.all.first()) }

@Composable
fun isDark(themeSetting: String): Boolean = when (themeSetting) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun CarTrackerTheme(themeSetting: String, accentName: String, content: @Composable () -> Unit) {
    val dark = isDark(themeSetting)
    val colors = appColors(dark, Accents.named(accentName))
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            error = colors.warnText,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            error = colors.warn,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

/** Colours for the car's own map icon. */
object MarkerColors {
    val all = listOf(
        "Blue" to Color(0xFF1D5BD8),
        "Red" to Color(0xFFC62828),
        "Green" to Color(0xFF2F7D3A),
        "Orange" to Color(0xFFE8710A),
        "Purple" to Color(0xFF6B4FD8),
        "Black" to Color(0xFF0E1726),
        "White" to Color(0xFFFFFFFF),
        "Yellow" to Color(0xFFF2B705),
    )

    fun named(name: String): Color = all.firstOrNull { it.first == name }?.second ?: all.first().second

    /** Dark symbol on light fills, white on the rest. */
    fun ink(name: String): Color = if (name == "White" || name == "Yellow") Color(0xFF0E1726) else Color.White
}
