package de.drehtuer.dinfinity.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * Reads the Modernist palette anywhere under [DInfinityTheme]. Material's own
 * colour roles are filled in too, so Material 3 components land on the right
 * colours, but new UI should prefer these tokens.
 */
val LocalModernistColors =
    staticCompositionLocalOf {
        ModernistColors(
            background = ModernistTokens.Light.background,
            surface = ModernistTokens.Light.surface,
            text = ModernistTokens.Light.text,
            accent = ModernistTokens.accent,
            accentPressed = ModernistTokens.accent700,
            divider = ModernistTokens.divider(ModernistTokens.Light.text),
            isDark = false,
        )
    }

/**
 * Archivo is the system's typeface. It is not bundled yet — adding the font
 * files is its own task — so the scale is applied over the platform sans
 * for now. The sizes and weights are already the real ones.
 */
private val ModernistFontFamily = FontFamily.SansSerif

private fun typography(): Typography {
    val t = ModernistTokens.Type

    fun heading(size: TextUnit) =
        TextStyle(
            fontFamily = ModernistFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = size,
            letterSpacing = (-0.015).em,
        )

    fun body(
        size: TextUnit,
        weight: FontWeight = FontWeight.Normal,
    ) = TextStyle(
        fontFamily = ModernistFontFamily,
        fontWeight = weight,
        fontSize = size,
    )
    return Typography(
        displayLarge = heading(t.display),
        headlineLarge = heading(t.heading),
        headlineMedium = heading(t.title),
        titleLarge = heading(t.subtitle),
        bodyLarge = body(t.body),
        labelLarge = body(t.label, FontWeight.SemiBold),
        labelSmall = body(t.caption),
    )
}

/** Zero radius everywhere — `--radius-*` is 0 by design. */
private val ModernistShapes =
    Shapes(
        extraSmall = RoundedCornerShape(ModernistTokens.radius),
        small = RoundedCornerShape(ModernistTokens.radius),
        medium = RoundedCornerShape(ModernistTokens.radius),
        large = RoundedCornerShape(ModernistTokens.radius),
        extraLarge = RoundedCornerShape(ModernistTokens.radius),
    )

@Composable
fun DInfinityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette =
        if (darkTheme) {
            ModernistColors(
                background = ModernistTokens.Dark.background,
                surface = ModernistTokens.Dark.surface,
                text = ModernistTokens.Dark.text,
                accent = ModernistTokens.accent,
                accentPressed = ModernistTokens.accent600,
                divider = ModernistTokens.divider(ModernistTokens.Dark.text),
                isDark = true,
            )
        } else {
            ModernistColors(
                background = ModernistTokens.Light.background,
                surface = ModernistTokens.Light.surface,
                text = ModernistTokens.Light.text,
                accent = ModernistTokens.accent,
                accentPressed = ModernistTokens.accent700,
                divider = ModernistTokens.divider(ModernistTokens.Light.text),
                isDark = false,
            )
        }

    val scheme =
        if (darkTheme) {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.background,
                background = palette.background,
                onBackground = palette.text,
                surface = palette.surface,
                onSurface = palette.text,
                outline = palette.divider,
            )
        } else {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = palette.background,
                background = palette.background,
                onBackground = palette.text,
                surface = palette.surface,
                onSurface = palette.text,
                outline = palette.divider,
            )
        }

    CompositionLocalProvider(LocalModernistColors provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(),
            shapes = ModernistShapes,
            content = content,
        )
    }
}
