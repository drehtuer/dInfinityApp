package de.drehtuer.dinfinity.core.model

/**
 * The ground an accent will be read against: the page, and the ink on it.
 *
 * Two colours rather than one, because both ends of the accent's ramp are
 * mixed and they are mixed against different things — the pale end against the
 * page and the deep end against the ink. Mixing against black and white
 * instead would give a pale chip that glows on a dark page and deep letters
 * that vanish on a light one.
 *
 * The numbers are the Modernist palette's, written here because this is the
 * one module that has no Compose in it and therefore the one a JVM test can
 * measure without a screen. `ModernistTest` asserts they are the same colours
 * `ui/common`'s tokens carry, so the two copies cannot drift.
 */
enum class Ground(
  val backgroundArgb: Int,
  val textArgb: Int,
) {
  /** Ink on paper. */
  Light(0xFFF3F2F2.toInt(), 0xFF201E1D.toInt()),

  /** The design swaps ink and ground and leaves the accent alone. */
  Dark(0xFF201E1D.toInt(), 0xFFF3F2F2.toInt()),
  ;

  /**
   * What a colour too close to this ground is pushed towards to stand off it:
   * black on paper, white on a dark page.
   *
   * The pole rather than the ink, because the ink is only *nearly* black and
   * nearly white and the last stretch is where a stubborn colour needs the
   * room. Both poles are far enough from their ground — 20.4:1 and 14.7:1 —
   * that the push always arrives somewhere legible.
   */
  internal val poleArgb: Int
    get() = if (this == Light) BLACK else WHITE

  private companion object {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
  }
}

/**
 * One accent, resolved against one ground: the colour that is painted and the
 * four steps of its ramp that the interface names.
 *
 * The design system ships exact ramps for two accents, and the app lets the
 * player choose any colour at all — so a step cannot be a pigment somebody
 * looked up. The design of 2026-09-17 settled what it is instead: **both ends
 * are mixed from the accent the player picked**, the pale end into the page
 * and the deep end into the ink (`docs/TODO.md`, 4.4). That is one rule for
 * every accent rather than a table with an exception in it, which is also the
 * only version of it a test can hold to.
 *
 * @param accent `--color-accent`: what the player chose, after [clamp]. This
 *   is the only colour here that is not a mix, and the one every other step is
 *   mixed from.
 * @param v100 `color-mix(accent 16 %, bg)` — what a filled accent tag is
 *   filled with.
 * @param v200 the same at 28 %.
 * @param v600 `color-mix(accent 86 %, text)` — a shade, for a hover.
 * @param v700 the same at 58 %: the pressed step, and what body copy in the
 *   accent is printed in, because the accent itself reaches only 3:1.
 * @param v800 the same at 40 % — what a filled accent tag is lettered in.
 */
data class AccentRamp(
  val accent: Int,
  val v100: Int,
  val v200: Int,
  val v600: Int,
  val v700: Int,
  val v800: Int,
) {
  companion object {
    /** `--color-accent-100`: how much of the accent is in the palest step. */
    const val SHARE_100: Double = 0.16

    /** `--color-accent-200`. */
    const val SHARE_200: Double = 0.28

    /** `--color-accent-600`: barely deepened, for a shade of the accent. */
    const val SHARE_600: Double = 0.86

    /** `--color-accent-700`: the pressed step, and accent body copy. */
    const val SHARE_700: Double = 0.58

    /** `--color-accent-800`: the deep end, for letters on the pale one. */
    const val SHARE_800: Double = 0.40

    /** How many steps the push takes, and therefore how fine it is. */
    private const val STEPS = 10

    private const val RGB = 0xFFFFFF
    private const val OPAQUE = 0xFF shl 24

    /**
     * [chosen], pushed towards [ground]'s pole until it can be told apart from
     * [ground] at [atLeast] — and returned untouched if it already can.
     *
     * This is the whole of what makes a free colour picker safe, and it is a
     * pure function of a colour and a ground so that it can be said of *every*
     * colour rather than of a list. It is the prototype's `legible()`, step
     * for step: ten mixes towards black on a light page or white on a dark
     * one, the first that clears the bar wins, and the pole itself is the last
     * resort. Ten steps rather than a bisection because the design's own
     * implementation takes ten and a clamped colour is a colour somebody will
     * compare against the drawing.
     *
     * The colour that comes back is what feeds [accent] and the ramp. It is
     * **not** what Settings draws on the swatch: the player is shown the
     * colour they chose (`docs/architecture.md`, "Settings").
     *
     * @param atLeast the bar. [Contrast.COMPONENT] by default, which is what
     *   the accent is — chrome and large text, never a paragraph.
     */
    fun clamp(
      chosen: Int,
      ground: Ground,
      atLeast: Double = Contrast.COMPONENT,
    ): Int {
      val opaque = OPAQUE or (chosen and RGB)
      for (step in 0..STEPS) {
        val pushed = Contrast.over(ground.poleArgb, step.toDouble() / STEPS, opaque)
        if (Contrast.meets(pushed, ground.backgroundArgb, atLeast)) return pushed
      }
      return ground.poleArgb
    }

    /** The ramp [chosen] makes on [ground], clamp and all. */
    fun of(
      chosen: Int,
      ground: Ground,
    ): AccentRamp {
      val accent = clamp(chosen, ground)
      return AccentRamp(
        accent = accent,
        v100 = Contrast.over(accent, SHARE_100, ground.backgroundArgb),
        v200 = Contrast.over(accent, SHARE_200, ground.backgroundArgb),
        v600 = Contrast.over(accent, SHARE_600, ground.textArgb),
        v700 = Contrast.over(accent, SHARE_700, ground.textArgb),
        v800 = Contrast.over(accent, SHARE_800, ground.textArgb),
      )
    }
  }
}
