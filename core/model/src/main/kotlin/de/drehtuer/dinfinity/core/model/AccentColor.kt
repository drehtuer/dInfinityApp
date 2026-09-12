package de.drehtuer.dinfinity.core.model

/**
 * The accent the interface is painted with, chosen in Settings.
 *
 * It is a fixed palette rather than a free colour picker, for two reasons. The
 * Modernist system spends its colour in one place and relies on that one
 * colour carrying meaning (`design/README.md`); a slider that lets someone
 * pick a pale yellow would produce an app whose most important control is
 * invisible. And every entry here is checked against both grounds by
 * `AccentColorTest`, which a free picker could not be.
 *
 * The mark in the app icon does not follow this setting — it is the identity,
 * and it stays [Sky] (`design/Logo.dc.html`).
 *
 * @param id the stable key written to storage. Never rename one: an unknown id
 *   read back falls to [Default], so a rename silently resets everybody.
 * @param argb the accent itself, `--color-accent`.
 * @param pressedOnLightArgb the deeper step used for pressed states and for
 *   body copy on a light ground, where the accent alone reaches only 3:1.
 * @param pressedOnDarkArgb the same role on a dark ground.
 *
 * On a dark ground [Vermilion] and [Coral] deepen, because that is what their
 * ramp step in the design system is, while the derived four lighten, because
 * that is what mixing towards the text colour does there. Both read as
 * "pressed" and both stay above 3:1; the requirement is legibility, not that
 * every accent move in the same direction.
 */
enum class AccentColor(
  val id: String,
  val argb: Int,
  val pressedOnLightArgb: Int,
  val pressedOnDarkArgb: Int,
) {
  /** `--color-accent`, with the CSS ramp's own 700 and 600 steps. */
  Vermilion("vermilion", 0xFFEC3013.toInt(), 0xFFAE1800.toInt(), 0xFFDD2B0F.toInt()),

  /** `--color-accent-2`, likewise straight from the ramp. */
  Coral("coral", 0xFFE15B47.toInt(), 0xFF9E3526.toInt(), 0xFFC94B39.toInt()),

  // The system defines exact ramps only for the two accents above. The rest
  // are derived the way the design derives an ad-hoc accent
  // (`design/Logo.dc.html`): `color-mix(in srgb, accent 58%, text)`.
  // `AccentColorTest` re-computes them, so they cannot drift by hand.

  /** The identity's blue — the colour of the infinity in the mark. */
  Sky("sky", 0xFF1F92CC.toInt(), 0xFF1F6183.toInt(), 0xFF78BADC.toInt()),

  Moss("moss", 0xFF3F8F29.toInt(), 0xFF326024.toInt(), 0xFF8BB97D.toInt()),

  Amber("amber", 0xFFB26A00.toInt(), 0xFF754A0C.toInt(), 0xFFCDA366.toInt()),

  Violet("violet", 0xFF7A5AF8.toInt(), 0xFF54419C.toInt(), 0xFFAD9AF5.toInt()),
  ;

  companion object {
    /** What the design ships with, and what an unreadable setting falls back to. */
    val Default: AccentColor = Vermilion

    /** Storage is a string, and strings from disk are not to be trusted. */
    fun ofId(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: Default
  }
}
