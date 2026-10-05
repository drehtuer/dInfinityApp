package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieMaterial

/**
 * How round the solver makes a die's edges, said once for the solver and for
 * the picture of it (`docs/physics-and-rendering.md`, "Dice bodies" and
 * "Rounded edges"; `docs/architecture.md`, decision 91).
 *
 * The solver does not collide the sharp solid. Jolt's convex hull takes a
 * *convex radius*: it pulls every face plane in by that much and grows the
 * smaller solid back out by a ball of the same radius, so what touches the
 * felt is the solid with its edges and corners rounded off — which is what
 * lets a d4 tumble instead of catching on a corner. The renderer draws that
 * same rounded solid, and it can only do so if the two ask for the same
 * radius, so the number lives here rather than with either of them.
 *
 * What the solver *gets* is less than it asks for where the corners are
 * sharp: Jolt shrinks the radius until no corner of the rounded solid stands
 * more than [MAX_ERROR_MM] inside the sharp one, and until it fits twice
 * across the thinnest part of the solid. That arithmetic is over the solid's
 * faces and is the renderer's to repeat (`render/filament`'s `RoundedEdges`);
 * the two limits it repeats it with are here.
 */
object HullMargin {
  /**
   * The radius asked for, as a share of a die's nominal size: 0.48 mm on a
   * 16 mm die.
   */
  const val SHARE: Double = 0.03

  /**
   * The furthest a rounded corner may stand inside the sharp one, in
   * millimetres: Jolt's `ConvexHullShapeSettings::mMaxErrorConvexRadius`,
   * 0.05 of its units, which the solver leaves at its default — and a unit is
   * a centimetre here, so half a millimetre.
   */
  const val MAX_ERROR_MM: Double = 0.5

  /**
   * The radius [material] asks for, in millimetres, before the solver shrinks
   * it for a sharp corner.
   *
   * Of the nominal size, clamped to the set file's limits as the solver
   * clamps it, and **not** of the size the capacity rule shrank the die to: a
   * die drawn at half size asks for the same radius as a full-size one. That
   * is what the solver has always been handed, and the picture repeats it
   * rather than correcting it.
   */
  fun requestedMm(material: DieMaterial): Double = material.clampedToLimits().sizeMm * SHARE
}
