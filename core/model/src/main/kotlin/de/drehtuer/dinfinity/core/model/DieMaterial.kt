package de.drehtuer.dinfinity.core.model

/**
 * How a die looks and how it behaves when it hits something — the `defaults`
 * table of a set file, with any per-die overrides already applied
 * (`docs/dice-sets.md`).
 *
 * Every field is bounded. The ranges live here rather than in the validator
 * because two places need them and they must agree: `:dicesets:format` clamps
 * when a set is installed, and the simulation clamps again when it is loaded,
 * so a folder edited on disk after installation still cannot hand the physics
 * a frictionless die (`docs/dice-sets.md`, "Runtime isolation").
 *
 * @param colorArgb the body colour.
 * @param numberColorArgb the colour labels are printed in.
 * @param roughness PBR roughness, `0` mirror to `1` matte.
 * @param metallic PBR metalness.
 * @param sizeMm the die's nominal size across, before the table's capacity rule
 *   shrinks it (`docs/tables.md`).
 * @param density grams per cm³; ~1.2 is acrylic.
 * @param restitution bounciness.
 * @param friction surface friction. Never zero: dice-on-dice friction is what
 *   stops a pile from behaving like ball bearings
 *   (`docs/physics-and-rendering.md`).
 */
data class DieMaterial(
  val colorArgb: Int = DEFAULT_COLOR_ARGB,
  val numberColorArgb: Int = DEFAULT_NUMBER_COLOR_ARGB,
  val roughness: Double = 0.35,
  val metallic: Double = 0.0,
  val sizeMm: Double = 16.0,
  val density: Double = 1.2,
  val restitution: Double = 0.3,
  val friction: Double = 0.5,
) {
  /**
   * The radius of the sphere that contains this die at scale 1, in
   * millimetres. It is what the table's capacity rule sums
   * (`docs/tables.md`), so it is derived from [sizeMm] once, here.
   */
  val boundingRadiusMm: Double get() = sizeMm / 2.0

  /**
   * The same material with every value forced inside its range.
   *
   * The validator already clamps when a set is installed, so this is the
   * second of the two clamps `docs/dice-sets.md` asks for: it runs at load
   * time, on data that has been sitting in a folder on disk since. A
   * non-finite value cannot be clamped into anything meaningful, so it falls
   * back to the default rather than propagating a `NaN` into the solver.
   */
  fun clampedToLimits(): DieMaterial =
    DieMaterial(
      colorArgb = colorArgb,
      numberColorArgb = numberColorArgb,
      roughness = clamp(roughness, UnitRange, DieMaterial().roughness),
      metallic = clamp(metallic, UnitRange, DieMaterial().metallic),
      sizeMm = clamp(sizeMm, SizeMmRange, DieMaterial().sizeMm),
      density = clamp(density, DensityRange, DieMaterial().density),
      restitution = clamp(restitution, RestitutionRange, DieMaterial().restitution),
      friction = clamp(friction, FrictionRange, DieMaterial().friction),
    )

  companion object {
    /** Bone-coloured resin, the built-in set's body colour. */
    const val DEFAULT_COLOR_ARGB: Int = 0xFFE8DCC0.toInt()

    /** The built-in set's numerals. */
    const val DEFAULT_NUMBER_COLOR_ARGB: Int = 0xFF2B2B2B.toInt()

    /** `roughness` and `metallic`, which are plain PBR unit ranges. */
    val UnitRange: ClosedFloatingPointRange<Double> = 0.0..1.0

    /** A die smaller than 8 mm is unreadable; one over 40 mm is not a die. */
    val SizeMmRange: ClosedFloatingPointRange<Double> = 8.0..40.0

    /** Balsa to brass, roughly. */
    val DensityRange: ClosedFloatingPointRange<Double> = 0.5..8.0

    /** Above 0.8 a die never settles inside a 12-second cap. */
    val RestitutionRange: ClosedFloatingPointRange<Double> = 0.0..0.8

    /** Never 0: frictionless dice slide forever and pile like marbles. */
    val FrictionRange: ClosedFloatingPointRange<Double> = 0.1..1.0

    /**
     * [value] inside [range], or [fallback] when it is not a number at all.
     * `coerceIn` would happily return `NaN` for a `NaN` input, which is the
     * one case the physics must never see (`docs/dice-sets.md`, "Validation").
     */
    fun clamp(
      value: Double,
      range: ClosedFloatingPointRange<Double>,
      fallback: Double,
    ): Double = if (value.isFinite()) value.coerceIn(range) else fallback
  }
}
