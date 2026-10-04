package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.simulation.api.HullMargin

/**
 * The two numbers a die's hull is rounded with, in the solver's units, as
 * [JoltWorld] hands them across the bridge (`docs/physics-and-rendering.md`,
 * "Dice bodies"; `docs/architecture.md`, decisions 91 and 94).
 *
 * Apart from [JoltWorld] because that class cannot run on the JVM, and this is
 * the part of it that decides something: which radius a die asks for, and how
 * far Jolt may cut its corners back. Both come from `simulation/api`'s
 * [HullMargin], which the renderer repeats, so what is drawn is what collides.
 *
 * @param convexRadius `ConvexHullShapeSettings::mMaxConvexRadius`: the
 *   rounding asked for, in simulation units.
 * @param maxError `ConvexHullShapeSettings::mMaxErrorConvexRadius`: how far a
 *   rounded corner may stand inside the sharp one before Jolt asks for less.
 *   At the default rounding it is exactly Jolt's own default, `0.05f`, so a
 *   die whose set says nothing is the body it always was.
 */
internal data class HullRounding(
  val convexRadius: Float,
  val maxError: Float,
) {
  companion object {
    /** What a die of [material] is rounded with, clamped as the solver clamps it. */
    fun of(material: DieMaterial): HullRounding =
      HullRounding(
        convexRadius = Units.mmToUnits(HullMargin.requestedMm(material)),
        maxError = Units.mmToUnits(HullMargin.maxErrorMm(material)),
      )
  }
}
