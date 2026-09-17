package de.drehtuer.dinfinity.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import de.drehtuer.dinfinity.core.model.AccentColor

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
  // **All fifteen, not the seven that were obviously wanted.** A slot left unset
  // keeps Material's baseline, so every screen drawing in `titleMedium`,
  // `bodyMedium` or `labelMedium` — which is most body copy in this app — was
  // at Material's sizes and weights rather than this scale. The card title's
  // 17 sp at 800 was then being faked with an `ExtraBold` override at each call
  // site, which is the symptom of a missing slot rather than a style
  // (`docs/design-handover.md`).
  //
  // The scale has seven steps and Material has fifteen slots, so the extra
  // eight are the nearest step rather than new sizes: a design system with one
  // size between 15 and 20 does not gain one by Material asking for it.
  return Typography(
    displayLarge = heading(t.display),
    displayMedium = heading(t.heading),
    displaySmall = heading(t.title),
    headlineLarge = heading(t.heading),
    headlineMedium = heading(t.title),
    headlineSmall = heading(t.title),
    titleLarge = heading(t.subtitle),
    // `.card-title` is the heading face at 800, a step below the subtitle.
    titleMedium = heading(t.cardTitle),
    titleSmall = heading(t.label),
    bodyLarge = body(t.body),
    bodyMedium = body(t.label),
    bodySmall = body(t.caption),
    // `.btn` is the *heading* face at 800 and 14 px, not a semibold body — so
    // every button label in the app was the wrong face and the wrong weight.
    labelLarge = heading(t.button),
    labelMedium = body(t.caption),
    labelSmall = body(t.caption),
  )
}

/**
 * Fills in the Material roles this palette has no opinion about, so that none
 * of them keeps Material's baseline.
 *
 * The Modernist palette is a ground, a surface, an ink and one accent. Material
 * wants forty-odd roles, and any this does not set stay lavender — which is how
 * secondary copy across four screens came to print `#49454F` and every delete
 * button came to carry a second red. The mapping is deliberately flat: there is
 * no second hue to map *to*, so every neutral role is the surface or the ink,
 * and everything that would have been a second accent is the one accent.
 *
 * `error` included. A system with one red says a destructive thing in that red
 * or says it in words, and a screen that reaches for `error` should get the
 * accent rather than a colour from outside the palette.
 */
private fun ColorScheme.modernist(palette: ModernistColors): ColorScheme =
  copy(
    primaryContainer = palette.surface,
    onPrimaryContainer = palette.text,
    inversePrimary = palette.accent,
    secondary = palette.accent,
    onSecondary = palette.background,
    secondaryContainer = palette.surface,
    onSecondaryContainer = palette.text,
    tertiary = palette.accent,
    onTertiary = palette.background,
    tertiaryContainer = palette.surface,
    onTertiaryContainer = palette.text,
    surfaceVariant = palette.surface,
    // Dimmed, not full strength. `onSurfaceVariant` is Material's role for
    // *secondary* copy on a surface, and the design system dims secondary copy
    // rather than giving it a colour of its own — `.text-muted` and the
    // prototype's `opacity: .65`. Mapping it to the ink at full strength made
    // every explanation shout as loudly as the thing it explained.
    onSurfaceVariant = palette.text.copy(alpha = MUTED),
    surfaceTint = palette.accent,
    inverseSurface = palette.text,
    inverseOnSurface = palette.background,
    error = palette.accent,
    onError = palette.background,
    errorContainer = palette.surface,
    onErrorContainer = palette.accent,
    outlineVariant = palette.divider,
    scrim = palette.text,
    surfaceBright = palette.surface,
    surfaceDim = palette.surface,
    surfaceContainer = palette.surface,
    surfaceContainerHigh = palette.surface,
    surfaceContainerHighest = palette.surface,
    surfaceContainerLow = palette.surface,
    surfaceContainerLowest = palette.background,
  )

/**
 * How far secondary copy is dimmed: the prototype's `opacity: .65`.
 *
 * A number rather than a second grey, because the palette has one ink and the
 * system dims it rather than mixing a new one.
 */
private const val MUTED = 0.65f

/** Zero radius everywhere — `--radius-*` is 0 by design. */
private val ModernistShapes =
  Shapes(
    extraSmall = RoundedCornerShape(ModernistTokens.radius),
    small = RoundedCornerShape(ModernistTokens.radius),
    medium = RoundedCornerShape(ModernistTokens.radius),
    large = RoundedCornerShape(ModernistTokens.radius),
    extraLarge = RoundedCornerShape(ModernistTokens.radius),
  )

/**
 * @param accent the accent the player chose in Settings. The default is the
 *   design system's own, which is what a preview or a test gets without
 *   saying anything.
 */
@Composable
fun DInfinityTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accent: AccentColor = AccentColor.Default,
  content: @Composable () -> Unit,
) {
  val palette =
    if (darkTheme) {
      ModernistColors(
        background = ModernistTokens.Dark.background,
        surface = ModernistTokens.Dark.surface,
        text = ModernistTokens.Dark.text,
        accent = Color(accent.argb),
        accentPressed = Color(accent.pressedOnDarkArgb),
        divider = ModernistTokens.divider(ModernistTokens.Dark.text),
        isDark = true,
      )
    } else {
      ModernistColors(
        background = ModernistTokens.Light.background,
        surface = ModernistTokens.Light.surface,
        text = ModernistTokens.Light.text,
        accent = Color(accent.argb),
        accentPressed = Color(accent.pressedOnLightArgb),
        divider = ModernistTokens.divider(ModernistTokens.Light.text),
        isDark = false,
      )
    }

  // **Every role, not the seven that were obviously needed.** A role left unset
  // does not fall back to something neutral: it keeps Material's own baseline,
  // which is lavender-tinted and carries a second red. A screen reaching for
  // `onSurfaceVariant` for its secondary copy, or `error` for a delete, was
  // getting those — in a system whose whole point is one red on grey. Found by
  // going through the screens against the prototype
  // (`docs/design-handover.md`).
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
      ).modernist(palette)
    } else {
      lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.background,
        background = palette.background,
        onBackground = palette.text,
        surface = palette.surface,
        onSurface = palette.text,
        outline = palette.divider,
      ).modernist(palette)
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
