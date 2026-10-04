package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieMaterial
import kotlin.math.abs

/**
 * What a die is made of and how round it is, as the face designer sets it
 * (`docs/face-designer.md`, "Material and edges"; `docs/architecture.md`,
 * decision 94).
 *
 * Four fields of a [DieMaterial] and no others. The colour is not here: a
 * drawing is meant to work on a black die and on a white one, so a drawn die
 * takes the body colour of the package it is in. Weight, translucency's
 * stepper and size are the details screen's, for every die of the set at
 * once; this is one die's.
 *
 * [translucency] is here although the details screen also sets it, because a
 * glass die *is* its translucency — a "Glass" that left it at nought would be
 * a shiny plastic die. A die with a finish of its own keeps its own; the
 * details screen's stepper moves every other die, and says what each one is
 * (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
 * them").
 *
 * Every value is held inside the set file's limits, so nothing this writes is
 * a value the validator would have to bring back.
 */
data class DieFinish(
  val roughness: Double,
  val metallic: Double,
  val translucency: Double,
  val edgeRounding: Double,
) {
  /** [material] with this finish in place of its own, clamped to the format's limits. */
  fun on(material: DieMaterial): DieMaterial =
    material
      .copy(
        roughness = roughness,
        metallic = metallic,
        translucency = translucency,
        edgeRounding = edgeRounding,
      ).clampedToLimits()

  /** The same finish made of [preset], with its rounding kept. */
  fun madeOf(preset: MaterialPreset): DieFinish =
    copy(roughness = preset.roughness, metallic = preset.metallic, translucency = preset.translucency)

  /** The same finish rounded by [roundness], with what it is made of kept. */
  fun rounded(roundness: Roundness): DieFinish = copy(edgeRounding = roundness.share)

  companion object {
    /** What [material] is made of and how round it is, inside the format's limits. */
    fun of(material: DieMaterial): DieFinish {
      val clamped = material.clampedToLimits()
      return DieFinish(
        roughness = clamped.roughness,
        metallic = clamped.metallic,
        translucency = clamped.translucency,
        edgeRounding = clamped.edgeRounding,
      )
    }

    /** The finish a die has when nobody has said anything: plastic, rounded as every die always was. */
    val STANDARD: DieFinish = of(DieMaterial())
  }
}

/**
 * What the face designer's **Material** menu offers (`docs/face-designer.md`,
 * "Material and edges").
 *
 * A short list of names rather than three sliders, because "glass" is a thing
 * somebody wants and "translucency 1.0, roughness 0.05" is how it is made.
 * Each sets the three fields a material is and leaves the colour and the
 * rounding alone. The values are tuned against how the renderer draws them
 * (`docs/physics-and-rendering.md`, "A die you can see into"): the blur of what
 * is seen through a die is the larger of its roughness and
 * `0.6 × (1 − translucency)`, so a quarter translucent is milky, three fifths
 * is clear enough to see the felt bent through it, and wholly translucent with
 * a polished body is glass.
 *
 * A die whose fields match none of them — one copied from a set somebody else
 * wrote — is shown as **Custom** ([of] answers null), and is kept as it is
 * until a name is chosen.
 */
enum class MaterialPreset(
  val roughness: Double,
  val metallic: Double,
  val translucency: Double,
) {
  /** What every die is when nobody says otherwise: [DieMaterial]'s own defaults. */
  Plastic(roughness = 0.35, metallic = 0.0, translucency = 0.0),

  /** Milky resin: a quarter of the light through, blurred to nothing in particular. */
  Pearl(roughness = 0.35, metallic = 0.0, translucency = 0.25),

  /** Clear-ish resin: the felt shows through, bent and a little soft. */
  Resin(roughness = 0.15, metallic = 0.0, translucency = 0.6),

  /** Everything through, polished: the felt seen as through a lens. */
  Glass(roughness = 0.05, metallic = 0.0, translucency = 1.0),

  /** A metal die: wholly metallic, brushed rather than mirror. */
  Metal(roughness = 0.3, metallic = 1.0, translucency = 0.0),

  /** Matte and solid, like a die cut from stone. */
  Stone(roughness = 0.8, metallic = 0.0, translucency = 0.0),
  ;

  /** True when [finish] is made of exactly this, whatever its rounding. */
  fun matches(finish: DieFinish): Boolean =
    near(finish.roughness, roughness) && near(finish.metallic, metallic) && near(finish.translucency, translucency)

  companion object {
    /** The preset [finish] is made of, or null for **Custom**. */
    fun of(finish: DieFinish): MaterialPreset? = entries.firstOrNull { it.matches(finish) }
  }
}

/**
 * How round the face designer makes a die's edges (`docs/face-designer.md`,
 * "Material and edges"; `docs/architecture.md`, decision 94).
 *
 * **Four steps rather than a slider.** The rounding is a physical property —
 * the solver's convex radius, which the renderer draws — so every value on
 * offer is a die that rolls differently, and four named dice are four that
 * can be thrown a hundred thousand times each and checked for fairness. A
 * slider would offer a continuum nobody has thrown. The steps double: the
 * least a set may ask for, the default every die has always had, twice it and
 * the most a set may ask for ([DieMaterial.EdgeRoundingRange]).
 */
enum class Roundness(
  /** The share of the die's size its edges are rounded by — `edge_rounding`. */
  val share: Double,
) {
  Sharp(share = 0.015),
  Standard(share = DieMaterial.DEFAULT_EDGE_ROUNDING),
  Rounded(share = 0.06),
  VeryRounded(share = 0.12),
  ;

  companion object {
    /** The step [share] is, or null when it is none of them (a set somebody else wrote). */
    fun of(share: Double): Roundness? = entries.firstOrNull { near(it.share, share) }
  }
}

/** Two values a set file can only have written as the same number, give or take its parsing. */
private fun near(
  one: Double,
  other: Double,
): Boolean = abs(one - other) < SAME

private const val SAME = 1e-9
