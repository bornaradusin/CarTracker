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
    /** The app colour saved in settings ("#RRGGBB", or a colour name from older versions). */
    fun named(value: String): Accent = Palette.accent(Palette.parse(value, Palette.AppDefault))
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
        accentText = if (Palette.isLight(accent.light)) accent.outlineLight else accent.light,
        accentOutline = accent.outlineLight,
        warn = Color(0xFFB4540A),
        warnText = Color(0xFFB4540A),
        warnTint = Color(0xFFFCEEE3),
        good = Color(0xFF2F6B3A),
        shadow = Color(0x220E1726),
    )
}

val LocalAppColors = staticCompositionLocalOf { appColors(false, Palette.accent(Palette.AppDefault)) }

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
