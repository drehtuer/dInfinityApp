package de.drehtuer.dinfinity.core.model

/**
 * What the player chose the interface to be painted with: one of the presets,
 * or a colour of their own.
 *
 * The accent used to be a closed palette, and its whole defence was that every
 * entry had been measured against both grounds by a test — a slider that
 * offers a pale yellow produces an app whose most important control is
 * invisible. The design of 2026-09-17 opens it and keeps the defence by moving
 * it: **whatever colour arrives goes through [AccentRamp.clamp] before it is
 * painted**, so the guarantee is a property of every colour rather than a list
 * of six (`docs/architecture.md`, "Settings").
 *
 * What is stored is [id], and what is painted is [argb] *after* the clamp.
 * The two are deliberately different: the swatch and the hex label show the
 * colour the player actually chose, because a picker that silently shows
 * something else is a picker nobody believes.
 */
sealed interface AccentChoice {
  /**
   * The stable key written to storage.
   *
   * Never rename one: an unknown id read back falls to [AccentColor.Default],
   * so a rename silently resets everybody who had picked it. When one has to
   * go, it goes into `AccentColor.RETIRED` instead.
   */
  val id: String

  /** The colour the player picked, `0xAARRGGBB` and always opaque. */
  val argb: Int

  /** `#RRGGBB`, upper case — what Settings prints above the swatch grid. */
  val hex: String
    get() = "#%06X".format(argb and RGB)

  /**
   * A colour of the player's own, stored beside the presets.
   *
   * It carries the colour itself rather than a name, so it needs no entry
   * anywhere and no migration when the preset list changes. The alpha byte is
   * forced opaque: a translucent accent has no contrast ratio of its own, and
   * the clamp would have nothing to measure.
   */
  @JvmInline
  value class Custom private constructor(
    override val argb: Int,
  ) : AccentChoice {
    override val id: String get() = CUSTOM_PREFIX + "%06X".format(argb and RGB)

    companion object {
      /**
       * [argb], made opaque.
       *
       * The constructor is private and this is the only way in, so that two
       * customs are equal when they are the same *colour* — `#38A8DC` picked
       * twice, once with an alpha byte and once without, is one choice and
       * must read back as one.
       */
      operator fun invoke(argb: Int): Custom = Custom(OPAQUE or (argb and RGB))
    }
  }

  companion object {
    /** What the design ships with, and what an unreadable setting falls back to. */
    val Default: AccentChoice get() = AccentColor.Default

    /** What a stored custom colour looks like: `custom:38A8DC`. */
    const val CUSTOM_PREFIX: String = "custom:"

    /**
     * The choice [id] names, or [Default] for anything this version cannot
     * read.
     *
     * Storage is a string, and strings from disk are not to be trusted: a
     * custom colour written by a later version, a half-written preference, a
     * preset that has since been retired and a file somebody edited by hand
     * all arrive here looking the same. A preset that has been retired is
     * mapped to its nearest survivor rather than dropped (`AccentColor`);
     * everything else falls back, because a wrong accent is a repaint and a
     * crash is not.
     */
    fun ofId(id: String?): AccentChoice =
      when {
        id == null -> Default
        id.startsWith(CUSTOM_PREFIX) -> custom(id.removePrefix(CUSTOM_PREFIX)) ?: Default
        else -> AccentColor.ofId(id)
      }

    private fun custom(digits: String): Custom? = digits.takeIf(SIX_HEX_DIGITS::matches)?.toInt(HEX)?.let { Custom(it) }

    /** Six hex digits and nothing else — no sign, no shorthand, no whitespace. */
    private val SIX_HEX_DIGITS = Regex("[0-9A-Fa-f]{6}")

    private const val HEX = 16
    private const val RGB = 0xFFFFFF
    private const val OPAQUE = 0xFF shl 24
  }
}
