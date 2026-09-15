package de.drehtuer.dinfinity.designer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How a copied face is turned and flipped before it lands
 * (`docs/face-designer.md`, "Copy and paste").
 *
 * Arithmetic over the stored dots and nothing else: a mark is a list of points
 * in fractions of the canvas, so turning a drawing is turning its points about
 * the middle of the canvas and mirroring it is `x → 1 - x`. That is why this
 * lives here rather than in the screen — there is nothing to draw to find out
 * whether it is right.
 *
 * **The turn is the cell's own**, a whole step of its symmetry: a third of a
 * turn on a triangle, a quarter on a square, a fifth on a pentagon. A turn
 * that did not carry the cell onto itself would carry the drawing off the face
 * and under the mask, which is not a turn anybody asked for. A kite has no
 * such turn at all — its only symmetry is the mirror — and so it offers none.
 *
 * **There is one mirror, the vertical one**, left for right. It is the only
 * axis every cell outline here shares: a triangle standing on its base and a
 * pentagon with its point up are both unchanged by it, and neither survives
 * being flipped top for bottom.
 *
 * @param turns how many steps of that turn, clockwise as the face is looked at
 *   — the canvas counts `y` downwards, so that is what turning the drawing the
 *   positive way round comes to. Any whole number: it is taken modulo the
 *   cell's steps, so a screen can count up for as long as a finger presses.
 * @param mirrored whether it is flipped left for right. The mirror is applied
 *   **first** and then the turn, which is the order that makes "mirror, then
 *   turn twice" mean what it says.
 */
data class FaceTransform(
  val turns: Int = 0,
  val mirrored: Boolean = false,
) {
  /** True when this changes nothing, whatever cell it is applied to. */
  val identity: Boolean get() = turns == 0 && !mirrored

  /**
   * [marks] as they land on a cell shaped like [outline].
   *
   * Dots are left where the arithmetic puts them even when that is off the
   * canvas: a drawing copied from a square onto a triangle has corners the
   * triangle does not, and they are **clipped by the mask** like any other
   * ink. Squashing them back inside would change the shape that was copied,
   * which is the one thing a copy must not do.
   */
  fun applyTo(
    marks: List<Mark>,
    outline: FaceOutline,
  ): List<Mark> {
    val steps = stepsOf(outline)
    val turn = turns.mod(steps)
    if (turn == 0 && !mirrored) return marks
    val angle = TURN * turn / steps
    return marks.map { mark -> mark.at(mark.dots.map { dot -> moved(dot, angle) }) }
  }

  /** One dot, mirrored if it is to be and then turned by [angle] about the middle. */
  private fun moved(
    dot: Dot,
    angle: Double,
  ): Dot {
    val x = (if (mirrored) 1f - dot.x else dot.x) - MIDDLE
    val y = dot.y - MIDDLE
    return Dot(
      x = (MIDDLE + x * cos(angle) - y * sin(angle)).toFloat(),
      y = (MIDDLE + x * sin(angle) + y * cos(angle)).toFloat(),
    )
  }

  companion object {
    /**
     * How many turns of its own a cell shaped like [outline] has.
     *
     * One means the only thing on offer is the mirror. A disc has every turn
     * there is; it is given quarters, because a control has to count in
     * something and a quarter is what a finger expects of a picture.
     */
    fun stepsOf(outline: FaceOutline): Int =
      when (outline) {
        FaceOutline.Triangle -> THIRDS
        FaceOutline.Square -> QUARTERS
        FaceOutline.Pentagon -> FIFTHS
        FaceOutline.Circle -> QUARTERS
        FaceOutline.Kite -> NO_TURN
      }

    private const val THIRDS = 3
    private const val QUARTERS = 4
    private const val FIFTHS = 5

    /** A kite's, which is to say none: its only symmetry is the mirror. */
    private const val NO_TURN = 1

    private const val MIDDLE = 0.5f
    private const val TURN = 2 * PI
  }
}
