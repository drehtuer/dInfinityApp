package de.drehtuer.dinfinity.core.model

/**
 * One figure as a set can show it: a low end and a high end
 * (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
 * them").
 *
 * A set is a bag of dice and its dice need not agree. They *always* disagree
 * about weight, in fact, because a d4 and a d20 of one size and one density
 * are not the same amount of material — the d4 is the thinner solid inside the
 * same sphere (`docs/dice-sets.md`, "Size"). So the screen says what is true
 * of every die in the set rather than picking one and quoting it as if it
 * spoke for the rest.
 */
data class Span(
  val low: Double,
  val high: Double,
) {
  /** True when every die in the set gives the same answer, to the printed digit. */
  fun isOne(step: Double): Boolean = Math.round(low / step) == Math.round(high / step)

  companion object {
    /** The span of [values], which must not be empty. */
    fun of(values: List<Double>): Span = Span(low = values.min(), high = values.max())
  }
}

/**
 * The three things a dice shop says about a die, worked out from the three a
 * set file writes (`docs/dice-sets.md`, "Weight, translucency and size, as a
 * person sets them").
 *
 * An author writes `size_mm`, `density` and `translucency`; a player thinks in
 * grams, per cent and "bigger than average". Both ends of that translation are
 * here rather than in the screen, because they are arithmetic about dice and
 * because the steppers on "My dice" have to run it backwards — a tap adds a
 * tenth of a gram and what is stored is a density.
 */
object DiePhysical {
  /**
   * The die every size is a percentage of: the 16 mm the built-in set uses
   * ([DieMaterial.sizeMm]'s own default).
   *
   * "Average" is a claim about dice rather than about this app, and this is
   * the number the design of 2026-09-17 makes it: 100 % is the built-in set.
   */
  const val AVERAGE_SIZE_MM: Double = 16.0

  /** What one tap of the weight stepper adds or takes away, in grams. */
  const val WEIGHT_STEP_G: Double = 0.1

  /** What one tap of the translucency stepper is, in per cent. */
  const val TRANSLUCENCY_STEP_PERCENT: Double = 5.0

  /** What one tap of the size stepper is, in per cent of the average die. */
  const val SIZE_STEP_PERCENT: Double = 5.0

  /**
   * How big a die may be said to be, as a percentage of the average.
   *
   * Far tighter than the 8–40 mm the file format clamps `size_mm` to
   * ([DieMaterial.SizeMmRange]), and deliberately: this is a stepper somebody
   * holds a finger on, not a number an author thought about. 50–150 % of
   * 16 mm is 8–24 mm, so everything this can reach is a size the format
   * already accepts and nothing it writes has to be clamped on the way out.
   */
  val SizePercentRange: ClosedFloatingPointRange<Double> = 50.0..150.0

  /** Per cent, nought solid to a hundred glass — the scale a set file writes. */
  val TranslucencyPercentRange: ClosedFloatingPointRange<Double> = 0.0..100.0

  /**
   * What one die of this material weighs, in grams.
   *
   * `density` is grams per cubic centimetre and [DieVolume] is the solid, so
   * this is the mass the solver gives the body — the same number, not an
   * estimate of it.
   */
  fun gramsOf(
    shape: DieShape,
    material: DieMaterial,
  ): Double = material.density * DieVolume.cm3(shape, material.sizeMm)

  /** What one [die] weighs, in grams. */
  fun gramsOf(die: Die): Double = gramsOf(die.shape, die.material)

  /** [sizeMm] as a percentage of the average die. */
  fun sizePercentOf(sizeMm: Double): Double = PERCENT * sizeMm / AVERAGE_SIZE_MM

  /** The size in millimetres a [percent] of the average die is. */
  fun sizeMmOf(percent: Double): Double = AVERAGE_SIZE_MM * percent / PERCENT

  /** A material's translucency as the per cent a set file writes it in. */
  fun translucencyPercentOf(translucency: Double): Double = PERCENT * translucency

  /**
   * [material] made heavier or lighter by [steps] taps of a tenth of a gram.
   *
   * **What moves is the density**, because that is what a die *has* and what
   * the file keeps. A gram is a fact about one die of one shape, so a step of
   * 0.1 g only means something against a reference solid, and the reference is
   * a **d6 of this set's size** — the die the design's own figures quote and
   * the one die nearly every set defines. A set of nothing but d20s therefore
   * moves by a little more than a tenth of a gram per tap, and the screen says
   * what it actually weighs rather than what the tap was called.
   *
   * The density stays inside [DieMaterial.DensityRange], so a run of taps
   * stops at balsa and at brass rather than running off either end.
   */
  fun weighted(
    material: DieMaterial,
    steps: Int,
  ): DieMaterial {
    val perStep = WEIGHT_STEP_G / DieVolume.cm3(DieShape.Cube, material.sizeMm)
    val density = material.density + steps * perStep
    return material.copy(density = density.coerceIn(DieMaterial.DensityRange))
  }

  /** [material] made more or less see-through by [steps] taps of five per cent. */
  fun seenThrough(
    material: DieMaterial,
    steps: Int,
  ): DieMaterial {
    val percent = translucencyPercentOf(material.translucency) + steps * TRANSLUCENCY_STEP_PERCENT
    return material.copy(translucency = percent.coerceIn(TranslucencyPercentRange) / PERCENT)
  }

  /**
   * [material] made bigger or smaller by [steps] taps of five per cent of the
   * average die.
   *
   * Clamped to [SizePercentRange] rather than to the format's millimetres: the
   * tighter bound is the stepper's, and it sits inside what the file allows.
   */
  fun sized(
    material: DieMaterial,
    steps: Int,
  ): DieMaterial {
    val percent = sizePercentOf(material.sizeMm) + steps * SIZE_STEP_PERCENT
    return material.copy(sizeMm = sizeMmOf(percent.coerceIn(SizePercentRange)))
  }

  private const val PERCENT = 100.0
}

/**
 * What the **Physical** block on a set's detail screen says
 * (`design/dInfinityPhone.dc.html`, the Dice set details screen).
 *
 * Three figures over all the dice of one set, each as a [Span] because a set's
 * dice need not agree with one another. A set with no dice has none of this,
 * which is why [of] can answer null: a package that is only tables, or one
 * that stopped validating, has nothing to weigh.
 */
data class DicePhysical(
  /** Grams per die. */
  val weightG: Span,
  /** Per cent, nought solid to a hundred glass. */
  val translucencyPercent: Span,
  /** Per cent of the average die, which is the built-in set's 16 mm. */
  val sizePercent: Span,
) {
  companion object {
    /** What [dice] weigh, let through and measure, or null when there are none. */
    fun of(dice: List<Die>): DicePhysical? {
      if (dice.isEmpty()) return null
      return DicePhysical(
        weightG = Span.of(dice.map(DiePhysical::gramsOf)),
        translucencyPercent = Span.of(dice.map { DiePhysical.translucencyPercentOf(it.material.translucency) }),
        sizePercent = Span.of(dice.map { DiePhysical.sizePercentOf(it.material.sizeMm) }),
      )
    }
  }
}
