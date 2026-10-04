package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.SolidFaces

/**
 * The one kite the canvas used to draw for both trapezohedra, and how a
 * drawing made against it is carried onto the kite its die has now
 * (`docs/face-designer.md`, "Export details"; `docs/architecture.md`,
 * decision 86).
 *
 * Up to draft format 1 the d10 and the d18 were masked into the same
 * hand-drawn kite, which was neither die's face. The exporter grew it until
 * it covered the real one — 1.20 times what fitted on a d10, 1.37 on a d18 —
 * so what the tray showed of such a drawing was its middle, larger than drawn
 * and clipped at the tip. Now each die is masked into its own face
 * ([FaceShapes.kiteOf]), and a drawing kept in canvas fractions would be
 * masked differently on the next load: a border along the old kite's edge
 * would fall outside the d18's narrower one.
 *
 * **So an old drawing is moved, once, to where the die showed it.** The
 * old exporter's fit for the shared kite, followed by the inverse of today's
 * fit for the die's own, is one similarity — a size and a shift, the same on
 * every face because every face of a trapezohedron is the same kite — and
 * putting a format-1 drawing through it leaves the tray showing exactly what
 * it showed before. Nothing is squashed: a numeral stays the shape it was
 * stamped, a fill stays a fill, and a stroke's nib grows with the rest of it.
 */
object SharedKite {
  /** The shared kite's corners, top, right, bottom, left — as the canvas drew them up to format 1. */
  val CORNERS: List<Dot> =
    listOf(Dot(0.5f, 0.04f), Dot(0.84f, 0.38f), Dot(0.5f, 0.96f), Dot(0.16f, 0.38f))

  /** True when a drawing on a [shape] die was masked into the shared kite in format 1. */
  fun wasDrawnOn(shape: DieShape): Boolean =
    shape == DieShape.PentagonalTrapezohedron || shape == DieShape.EnneagonalTrapezohedron

  /**
   * How much bigger a drawing on [shape] has to be on today's canvas to land
   * on the die where the shared kite's exporter put it — the old fit's size
   * against the new one's.
   */
  fun growthOn(shape: DieShape): Double = MOVES.getValue(shape).growth

  /**
   * [marks], drawn on a [shape] die against the shared kite, where they land
   * on that die's own kite.
   *
   * Marks on any other die are given back as they are: only the two
   * trapezohedra ever drew in the shared kite.
   */
  fun carried(
    marks: List<Mark>,
    shape: DieShape,
  ): List<Mark> {
    val move = MOVES[shape] ?: return marks
    return marks.map { mark ->
      val moved = mark.at(mark.dots.map(move::of))
      if (moved is Stroke) moved.copy(width = (moved.width * move.growth).toFloat()) else moved
    }
  }

  /** One die's move off the shared kite: the old fit into the cell, and today's back out of it. */
  private class Move(
    private val old: CellFit,
    private val new: CellFit,
  ) {
    val growth: Double = old.scale / new.scale

    fun of(dot: Dot): Dot = new.canvasOf(old.of(dot))
  }

  private val MOVES: Map<DieShape, Move> =
    DieShape.entries.filter(::wasDrawnOn).associateWith { shape ->
      // The first face stands for every one: a trapezohedron's faces are all
      // the same kite, so the move is the same on each (`SharedKiteTest`).
      val face = SolidFaces.of(shape).first()
      Move(old = FaceOnSolid.cellFitOf(face, CORNERS), new = FaceOnSolid.cellFitOf(face, FaceOutline.of(shape)))
    }
}
