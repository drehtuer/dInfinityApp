package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieMaterial

/**
 * How round the solver makes a die's edges, said once for the solver and for
 * the picture of it (`docs/physics-and-rendering.md`, "Dice bodies" and
 * "Rounded edges"; `docs/architecture.md`, decisions 91 and 94).
 *
 * The solver does not collide the sharp solid. Jolt's convex hull takes a
 * *convex radius*: it pulls every face plane in by that much and grows the
 * smaller solid back out by a ball of the same radius, so what touches the
 * felt is the solid with its edges and corners rounded off — which is what
 * lets a d4 tumble instead of catching on a corner. The renderer draws that
 * same rounded solid, and it can only do so if the two ask for the same
 * radius, so the number lives here rather than with either of them.
 *
 * **The radius is each die's own**: a share of its size, which its set may
 * choose (`edge_rounding`, [DieMaterial.edgeRounding]) and which is 3 % when
 * it does not.
 *
 * What the solver *gets* is less than it asks for where the corners are
 * sharp: Jolt shrinks the radius until no corner of the rounded solid stands
 * more than [maxErrorMm] inside the sharp one, and until it fits twice across
 * the thinnest part of the solid. That arithmetic is over the solid's faces
 * and is the renderer's to repeat (`render/filament`'s `RoundedEdges`); the
 * two limits it repeats it with are here.
 */
object HullMargin {
  /**
   * The radius a die asks for when its set does not say, as a share of its
   * nominal size: 0.48 mm on a 16 mm die.
   */
  const val SHARE: Double = DieMaterial.DEFAULT_EDGE_ROUNDING

  /**
   * The furthest a rounded corner may stand inside the sharp one at the
   * default rounding, in millimetres: Jolt's own default for
   * `ConvexHullShapeSettings::mMaxErrorConvexRadius`, 0.05 of its units —
   * and a unit is a centimetre here, so half a millimetre.
   */
  const val MAX_ERROR_MM: Double = 0.5

  /**
   * The radius [material] asks for, in millimetres, before the solver shrinks
   * it for a sharp corner: its [DieMaterial.edgeRounding] of its size.
   *
   * Of the nominal size, clamped to the set file's limits as the solver
   * clamps it, and **not** of the size the capacity rule shrank the die to: a
   * die drawn at half size asks for the same radius as a full-size one. That
   * is what the solver has always been handed, and the picture repeats it
   * rather than correcting it.
   */
  fun requestedMm(material: DieMaterial): Double {
    val clamped = material.clampedToLimits()
    return clamped.sizeMm * clamped.edgeRounding
  }

  /**
   * How far a rounded corner of a die of [material] may stand inside the
   * sharp one, in millimetres: the `mMaxErrorConvexRadius` its hull is built
   * with.
   *
   * **It grows with the rounding asked for**, in proportion: [MAX_ERROR_MM]
   * at the default share, twice that at twice the share. Held at half a
   * millimetre, a rounder die would get what it asked for only where its
   * corners are blunt — a d6's corner stands `0.73 r` inside its sharp one,
   * so anything over 0.68 mm would be cut back, and a d4 would not get
   * rounder at all. In proportion, every shape is rounded in proportion to
   * what it asks for: a d4 still gets half (its spike stands `2 r` in), as
   * it always has, and the rest get all of it. At the default this is
   * exactly the half millimetre Jolt has always used, so nothing a set does
   * not ask for moves.
   */
  fun maxErrorMm(material: DieMaterial): Double =
    MAX_ERROR_MM * (material.clampedToLimits().edgeRounding / DieMaterial.DEFAULT_EDGE_ROUNDING)
}
