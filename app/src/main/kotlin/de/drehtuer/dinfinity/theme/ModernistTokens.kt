package de.drehtuer.dinfinity.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Modernist design system's tokens, transcribed from
 * `design/_ds/modernist-.../styles.css` — the single source of the look
 * (see `design/README.md`).
 *
 * Flat, architectural, one red, zero corner radius, strong 2 dp rules. The
 * values here must stay equal to the CSS: if the design system is re-imported
 * with different tokens, this file changes with it.
 */
object ModernistTokens {
  /** Light ground: ink on paper. */
  object Light {
    val background = Color(0xFFF3F2F2)
    val surface = Color(0xFFEAE9E9)
    val text = Color(0xFF201E1D)
  }

  /**
   * Dark ground. The design swaps ink and ground and leaves the accent
   * alone (`design/dInfinity.dc.html`, option 2b).
   */
  object Dark {
    val background = Color(0xFF201E1D)
    val surface = Color(0xFF2D2B2B)
    val text = Color(0xFFF3F2F2)
  }

  /** The system's single accent, used sparingly. */
  val accent = Color(0xFFEC3013)
  val accent600 = Color(0xFFDD2B0F)
  val accent700 = Color(0xFFAE1800)

  /**
   * Body copy in the accent must use a deep ramp step: the accent itself
   * reaches only 3:1 against the ground, which is enough for chrome and
   * large text but not for paragraphs.
   */
  val accentOnLightText = accent700

  val neutral100 = Color(0xFFF8F4F4)
  val neutral300 = Color(0xFFD7D3D3)
  val neutral500 = Color(0xFF9B9797)
  val neutral700 = Color(0xFF605D5D)
  val neutral900 = Color(0xFF2D2B2B)

  /** `--color-divider`: the text colour at 40 %. Rules are 2 dp, not hairlines. */
  fun divider(text: Color): Color = text.copy(alpha = 0.4f)

  val ruleThickness: Dp = 2.dp

  /** `--space-1` … `--space-8`. */
  object Space {
    val x1: Dp = 4.dp
    val x2: Dp = 8.dp
    val x3: Dp = 12.dp
    val x4: Dp = 16.dp
    val x6: Dp = 24.dp
    val x8: Dp = 32.dp
  }

  /** `--radius-*` is 0 on purpose. Nothing in this system has a rounded corner. */
  val radius: Dp = 0.dp

  /** The CSS type scale, in sp. */
  object Type {
    val display: TextUnit = 42.sp
    val heading: TextUnit = 32.sp
    val title: TextUnit = 25.sp
    val subtitle: TextUnit = 20.sp
    val body: TextUnit = 15.sp
    val label: TextUnit = 13.sp
    val caption: TextUnit = 11.sp
  }
}

/** The resolved palette for one theme. */
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
