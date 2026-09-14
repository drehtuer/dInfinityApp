package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead

/**
 * Where a number sits on the canvas while a face is being drawn.
 *
 * Not pixels: the canvas is square and the outline is masked into it, so a
 * position is a place on the *shape* rather than a coordinate. What turns
 * these into pixels is the screen, which knows how big it is.
 */
enum class GuideSpot {
  /** The middle of the face, which is where a single number goes. */
  Middle,

  /** The three corners of a triangular cell, in the order its corners are numbered. */
  FirstCorner,
  SecondCorner,
  ThirdCorner,
}

/**
 * One number shown faintly under the drawing, as something to trace or ignore.
 *
 * @param value what the die scores. Shown rather than the label, because a
 *   label may be four characters and a guide is a target for a finger.
 * @param spot where on the face it sits.
 */
data class GuideMark(
  val value: Int,
  val spot: GuideSpot,
)

/**
 * What the guide shows on one cell of a die being drawn
 * (`docs/face-designer.md`, "Flow"; `docs/dice-sets.md`, "The d4").
 *
 * For every shape but one this is a single number in the middle of the face:
 * the value that face scores, drawn faintly so somebody can trace it or turn
 * it off.
 *
 * **The d4 is the exception, and it is the reason this file exists.** A
 * tetrahedron resting on a face has no face pointing up, so its numbers belong
 * to *corners* and its cells are painted on faces. Cell `i` is the triangle
 * opposite corner `i`, so it carries the values of the other three corners,
 * one at each of its own corners. That is what a moulded d4 does: when a
 * corner points up, all three faces you can see carry that corner's number at
 * their apex.
 *
 * It follows that two cells sharing an edge must agree along it — both draw
 * the same value at each end — because the value belongs to the corner rather
 * than to either cell. `docs/TODO.md` asks for that to be "hard to do by
 * accident, not a warning afterwards", so **it is derived rather than
 * checked**: the guide for a cell is computed from the corner values, and
 * there is no way to express a d4 whose cells disagree. A wrong one cannot be
 * drawn because it cannot be said.
 */
object FaceGuide {
  /**
   * The marks for cell [cell] of [die].
   *
   * @return one mark for most shapes; three for a tetrahedron read from its
   *   vertices. Empty when [cell] is not a cell of this die.
   */
  fun of(
    die: Die,
    cell: Int,
  ): List<GuideMark> {
    if (cell !in die.faces.indices) return emptyList()
    return if (isCornerRead(die)) corners(die, cell) else listOf(GuideMark(die.faces[cell].value, GuideSpot.Middle))
  }

  /**
   * True when this die's numbers belong to corners rather than faces.
   *
   * Both halves are asked. The shape says a tetrahedron *can* be read from a
   * vertex; the die's own `read` says whether it is — a set may paint a
   * tetrahedron to be read face-up instead, and then its cells carry one
   * number each like any other die (`docs/dice-sets.md`).
   */
  fun isCornerRead(die: Die): Boolean = die.shape == DieShape.Tetrahedron && die.read == FaceRead.VertexUp

  /**
   * The three numbers cell [cell] carries, in corner order.
   *
   * Cell `i` is the triangle *opposite* corner `i`, so its corners are the
   * three that are not `i`, and each carries that corner's own value —
   * `faces[c].value`, because a vertex-read die indexes its faces by vertex.
   *
   * Two cells sharing an edge share two corners, and both read those corners'
   * values from the same place. There is no second copy to disagree with.
   */
  private fun corners(
    die: Die,
    cell: Int,
  ): List<GuideMark> =
    die.faces.indices
      .filter { it != cell }
      .mapIndexed { position, corner -> GuideMark(die.faces[corner].value, SPOTS[position]) }

  private val SPOTS = listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner)
}
