package de.drehtuer.dinfinity.core.model

/**
 * The six accents Settings offers as swatches, of which the first is what a
 * new install is painted with.
 *
 * They are **presets and not the whole palette**: the player may also hand the
 * app a colour of its own ([AccentChoice.Custom]), and both go through the
 * same clamp before anything is painted with them ([AccentRamp]). So what
 * these six are is a shortcut to a good answer rather than a fence around a
 * bad one — the fence is the clamp, and it holds for every colour rather than
 * for six (`docs/architecture.md`, "Settings").
 *
 * Not one of them is safe on both grounds as it stands: [LightBlue] is 2.41:1
 * on paper and [Magenta] and [Cobalt] are under 3:1 on a dark page. That is
 * not a fault in the list — it is what the clamp is for, and it is why the six
 * are the design's colours rather than six colours chosen for passing a test.
 *
 * The mark in the app icon does not follow this setting — it is the identity,
 * and it stays the blue of the infinity in the mark (`design/Logo.dc.html`).
 *
 * @param id the stable key written to storage, which is **not** the name: two
 *   of these carry an id from the palette they replaced, because the colour a
 *   player chose is the same colour and re-labelling it must not move it.
 * @param argb the colour on the swatch, before the clamp.
 */
enum class AccentColor(
  override val id: String,
  override val argb: Int,
) : AccentChoice {
  /** The default, and the one the prototype's Settings opens on. */
  LightBlue("light-blue", 0xFF38A8DC.toInt()),

  /**
   * The design system's own `--color-accent`, under the name the system gives
   * it. Its id is the old palette's `vermilion` because it is the same
   * `#EC3013`: everybody who picked it keeps it, and only the label changed.
   */
  ModernistRed("vermilion", 0xFFEC3013.toInt()),

  Magenta("magenta", 0xFFC2186F.toInt()),

  Cobalt("cobalt", 0xFF1D5FD4.toInt()),

  Pine("pine", 0xFF0F7A50.toInt()),

  /** Deeper than the old `amber`, and keeping its id for the same reason. */
  Amber("amber", 0xFFC07000.toInt()),
  ;

  companion object {
    /** What the design ships with, and what an unreadable setting falls back to. */
    val Default: AccentColor = LightBlue

    /**
     * The four ids the 2026-09-17 palette dropped, each mapped to the survivor
     * nearest it.
     *
     * Falling back to [Default] instead — which is what an unknown id does,
     * and what these would have done — would silently repaint every phone that
     * had chosen one of them, in the *same* release that changed the default.
     * Somebody who asked for a green app would have opened a blue one and had
     * no way of knowing why. The mapping is applied once, on read; the next
     * write stores the survivor's id, so it costs one lookup per install and
     * then nothing.
     *
     * Nearest is measured rather than eyeballed: the smallest CIE Lab distance
     * to any surviving preset, which is unambiguous in all four cases —
     * `coral` (`#E15B47`) is 28 from Modernist red and 35 from Amber, `sky`
     * (`#1F92CC`) is 9 from Light blue and 46 from Cobalt, `moss` (`#3F8F29`)
     * is 31 from Pine and 71 from Amber, and `violet` (`#7A5AF8`) is 30 from
     * Cobalt and 73 from Magenta.
     */
    private val RETIRED: Map<String, AccentColor> =
      mapOf(
        "coral" to ModernistRed,
        "sky" to LightBlue,
        "moss" to Pine,
        "violet" to Cobalt,
      )

    /** Storage is a string, and strings from disk are not to be trusted. */
    fun ofId(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: RETIRED[id] ?: Default

    /** Which preset a retired id becomes, or null if it was never one. */
    fun retired(id: String): AccentColor? = RETIRED[id]
  }
}
