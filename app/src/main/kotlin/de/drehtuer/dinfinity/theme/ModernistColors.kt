package de.drehtuer.dinfinity.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette for one theme, resolved: the ground, the surface, the ink, the
 * accent the player chose and the divider that follows the ink.
 *
 * It lives beside the theme rather than with the tokens in `ui/common`,
 * because it is not a token — it is what [DInfinityTheme] makes *of* the
 * tokens once the ground and the chosen accent are known, and `:app` is the
 * only module that decides either. Nothing below the theme constructs one;
 * screens read the result through Material's colour roles.
 */
@Immutable
data class ModernistColors(
  val background: Color,
  val surface: Color,
  val text: Color,
  val accent: Color,
  val accentPressed: Color,
  val divider: Color,
  val isDark: Boolean,
)
