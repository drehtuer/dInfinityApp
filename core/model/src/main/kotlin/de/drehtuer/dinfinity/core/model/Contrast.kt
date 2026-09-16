package de.drehtuer.dinfinity.core.model

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Whether two colours can be told apart, in the arithmetic WCAG 2.2 defines
 * (`docs/architecture.md`, "Accessibility").
 *
 * It is here rather than in the theme because it is arithmetic and nothing
 * else: no Android type, no composition, no screen. That is what lets the
 * palette be *measured* by a JVM test instead of squinted at, which is the
 * only way a claim like "legible on both grounds" stays true after somebody
 * edits a hex value.
 *
 * Every colour is a packed `0xAARRGGBB`; the alpha byte is ignored, because a
 * contrast ratio is between two colours that are actually on screen. A
 * translucent one has to be laid over its ground first — that is what [over]
 * is for.
 */
object Contrast {
  /** The bar for body copy: WCAG 2.2 success criterion 1.4.3, level AA. */
  const val BODY_TEXT: Double = 4.5

  /**
   * The bar for text at 18.66 px bold or 24 px plain (1.4.3), and the same
   * number for the boundary of a control that has to be found (1.4.11).
   */
  const val LARGE_TEXT: Double = 3.0

  /** 1.4.11: a user-interface component's own colour against what is behind it. */
  const val COMPONENT: Double = LARGE_TEXT

  /**
   * The relative luminance of [argb], `0.0` for black and `1.0` for white.
   *
   * Straight from the specification, gamma expansion and all. The weights are
   * not equal because the eye is not: green carries most of what is perceived
   * as brightness and blue almost none, which is why a saturated blue on black
   * is unreadable at a ratio a saturated yellow would pass.
   */
  fun relativeLuminance(argb: Int): Double =
    RED_WEIGHT * linear(channel(argb, RED_SHIFT)) +
      GREEN_WEIGHT * linear(channel(argb, GREEN_SHIFT)) +
      BLUE_WEIGHT * linear(channel(argb, BLUE_SHIFT))

  /**
   * The contrast ratio between [a] and [b], from `1.0` (the same colour) to
   * `21.0` (black against white).
   *
   * Symmetric: which of the two is the ink and which the ground does not
   * change the answer, so a caller never has to get the order right.
   */
  fun ratio(
    a: Int,
    b: Int,
  ): Double {
    val one = relativeLuminance(a)
    val other = relativeLuminance(b)
    return (maxOf(one, other) + OFFSET) / (minOf(one, other) + OFFSET)
  }

  /** True when [a] on [b] reaches [atLeast] — [BODY_TEXT], [LARGE_TEXT] or [COMPONENT]. */
  fun meets(
    a: Int,
    b: Int,
    atLeast: Double,
  ): Boolean = ratio(a, b) >= atLeast

  /**
   * [foreground] at [alpha] laid over [background], as the one opaque colour
   * that is actually on screen.
   *
   * A translucent colour has no contrast ratio of its own — the divider is the
   * text colour at 40 %, and what an eye has to tell apart is the grey that
   * makes on the ground behind it. This is also the design system's
   * `color-mix(in srgb, a <alpha>%, b)`, which is how the derived accents are
   * built (`AccentColor`), so the two cannot come to disagree.
   *
   * @param alpha `0.0` for none of [foreground], `1.0` for all of it. Anything
   *   outside that is clamped rather than producing a colour that is not one.
   */
  fun over(
    foreground: Int,
    alpha: Double,
    background: Int,
  ): Int {
    val share = alpha.coerceIn(0.0, 1.0)

    fun blend(shift: Int): Int {
      val mixed = channel(foreground, shift) * share + channel(background, shift) * (1 - share)
      return mixed.roundToInt().coerceIn(0, FULL)
    }

    return OPAQUE or (blend(RED_SHIFT) shl RED_SHIFT) or
      (blend(GREEN_SHIFT) shl GREEN_SHIFT) or blend(BLUE_SHIFT)
  }

  private fun channel(
    argb: Int,
    shift: Int,
  ): Int = (argb shr shift) and FULL

  /** One channel, gamma-expanded from sRGB to linear light. */
  private fun linear(value: Int): Double {
    val scaled = value / FULL.toDouble()
    return if (scaled <= KNEE) scaled / KNEE_SLOPE else ((scaled + SHIFT) / (1 + SHIFT)).pow(GAMMA)
  }

  private const val FULL = 0xFF
  private const val OPAQUE = 0xFF shl 24
  private const val RED_SHIFT = 16
  private const val GREEN_SHIFT = 8
  private const val BLUE_SHIFT = 0
  private const val RED_WEIGHT = 0.2126
  private const val GREEN_WEIGHT = 0.7152
  private const val BLUE_WEIGHT = 0.0722
  private const val OFFSET = 0.05
  private const val KNEE = 0.04045
  private const val KNEE_SLOPE = 12.92
  private const val SHIFT = 0.055
  private const val GAMMA = 2.4
}
