package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pips instead of numbers, on a d6 and only on a d6
 * (`docs/face-designer.md`, "Fill all with eyes").
 *
 * A pipped die is the one die everybody has held, and it is not something
 * anybody wants to draw thirty-six circles for. One tap lays the standard
 * patterns on all six faces in the ink in the pen; `Clear eyes` takes them off
 * again, which is the undo for somebody who pressed it to see.
 *
 * **Only a d6.** A pip pattern is a way of writing one to six and nothing
 * else: there is no pip pattern for a 7, none for a `−` and none for a d20's
 * 17, so the offer is made for a cube carrying exactly 1–6 and withheld for
 * everything else — including a Fudge die, which is a cube and is not a d6
 * ([canBePipped]).
 *
 * **Pips and numerals are mutually exclusive.** A face carrying both is not a
 * die anybody makes, and the two are solved against the same face centre and
 * would land on top of each other — so filling one takes the other off, in one
 * step ([FaceDrawing.swap]).
 *
 * Everything here is arithmetic over the stored dots, so all of it is tested
 * without a canvas: what a pip *is* is a ring like any other mark's, which is
 * what makes pips appear in the flat canvas, in the strip and in the exported
 * atlas without any of them being told about them.
 */
object FaceEyes {
  /**
   * The design's authored face, in its own units
   * (`design/dInfinityPhone.dc.html`, 2026-09-17).
   *
   * The numbers below are quoted from it at that size — `96 / 160 / 224` on a
   * 320-unit face, each pip `24` across — and turned into fractions of the
   * canvas here, once, rather than written out as decimals somebody would have
   * to check against the design by eye.
   */
  const val FACE: Double = 320.0

  /** The near column and row of the 3 × 3 grid, as a fraction of the canvas. */
  const val NEAR: Double = 96.0 / FACE

  /** Its middle, which is the middle of the face. */
  const val MIDDLE: Double = 160.0 / FACE

  /** Its far column and row. */
  const val FAR: Double = 224.0 / FACE

  /** How big a pip is, as a fraction of the canvas. */
  const val RADIUS: Double = 24.0 / FACE

  /** The most pips a face of a d6 carries, which is what makes it a d6. */
  const val MOST: Int = 6

  // The values a moulded d6 carries. Written out because the patterns are a
  // list of cases rather than a formula, and a `when` over bare digits is the
  // one place detekt is right to ask what they mean.
  private const val ONE = 1
  private const val TWO = 2
  private const val THREE = 3
  private const val FOUR = 4
  private const val FIVE = 5

  /**
   * Whether [die] can be pipped: a cube carrying exactly one to six.
   *
   * The shape alone is not enough. A Fudge die is a cube and there is no pip
   * pattern for a minus, and a set may label a cube anything at all — so what
   * is asked is what the die *scores*, which is the thing a pip pattern
   * writes down.
   */
  fun canBePipped(die: Die): Boolean =
    die.shape == DieShape.Cube && die.faces.map { it.value }.sorted() == (1..MOST).toList()

  /**
   * Where the pips of a face worth [value] sit, in fractions of the canvas.
   *
   * The patterns a moulded die carries: one in the middle, two on a diagonal,
   * three on that diagonal through the middle, four in the corners, five those
   * four and the middle, six in two columns of three. Empty for a value no
   * pattern writes, which is every value a d6 does not have.
   */
  fun spots(value: Int): List<Pair<Double, Double>> {
    val corners = listOf(NEAR to NEAR, FAR to FAR, FAR to NEAR, NEAR to FAR)
    return when (value) {
      ONE -> listOf(MIDDLE to MIDDLE)
      TWO -> corners.take(TWO)
      THREE -> corners.take(TWO) + listOf(MIDDLE to MIDDLE)
      FOUR -> corners
      FIVE -> corners + listOf(MIDDLE to MIDDLE)
      MOST -> corners + listOf(NEAR to MIDDLE, FAR to MIDDLE)
      else -> emptyList()
    }
  }

  /**
   * The pips of a face worth [value], as one mark, or null when no pattern
   * writes that value.
   *
   * A circle is stored as a ring like every other mark, so a pip is turned,
   * mirrored, clipped to the face outline and rasterised into the atlas by the
   * arithmetic that was already there — and appears in the canvas, in the
   * strip and in the export without any of the three being taught what a pip
   * is.
   */
  fun of(
    value: Int,
    colorArgb: Int,
  ): Eyes? {
    val spots = spots(value)
    return if (spots.isEmpty()) null else Eyes(rings = spots.map(::ring), colorArgb = colorArgb)
  }

  /**
   * [draft] with the standard pips on every face, in [colorArgb].
   *
   * **It leaves a pipped face alone**, so pressing it twice changes nothing,
   * and it takes any stamped numeral off the faces it does fill — the two
   * cannot share a face. It is one undoable step per face rather than one for
   * the die, for the reason "fill all with numbers" is
   * (`FaceStamp.fill`): undo belongs to the face the step was made on.
   *
   * A die that cannot be pipped is returned untouched. Nothing offers the
   * button for one, and a model that quietly obliged would be a d20 with four
   * dots on a face.
   */
  fun fill(
    draft: Draft,
    colorArgb: Int,
  ): Draft {
    if (!canBePipped(draft.die)) return draft
    return draft.die.faces.indices.fold(draft) { so, cell ->
      val pips = of(draft.die.faces[cell].value, colorArgb)
      if (pips == null || so.face(cell).marks.any { it is Eyes }) {
        so
      } else {
        so.onFace(cell) { it.swap({ mark -> mark is Stamp }, listOf(pips)) }
      }
    }
  }

  /**
   * [draft] with the pips taken off every face, and the drawing round them
   * kept.
   *
   * One step per face, like [fill], and nothing at all on a face that has no
   * pips — so pressing it on an unpipped die is not a row of empty steps in
   * the undo stack.
   */
  fun clear(draft: Draft): Draft =
    draft.die.faces.indices.fold(draft) { so, cell ->
      if (so.face(cell).marks.none { it is Eyes }) {
        so
      } else {
        so.onFace(cell) { it.swap({ mark -> mark is Eyes }) }
      }
    }

  /** True when any face of [draft] is carrying pips, which is what `Clear eyes` is for. */
  fun pipped(draft: Draft): Boolean =
    draft.die.faces.indices
      .any { cell -> draft.face(cell).marks.any { it is Eyes } }

  /** One pip: a circle drawn as a ring, round enough that nothing reads as a polygon. */
  private fun ring(spot: Pair<Double, Double>): List<Dot> =
    (0 until PIP_SIDES).map { side ->
      val angle = TURN * side / PIP_SIDES
      Dot(
        x = (spot.first + RADIUS * cos(angle)).toFloat(),
        y = (spot.second + RADIUS * sin(angle)).toFloat(),
      )
    }

  /**
   * How many sides a pip is drawn with.
   *
   * A pip is 24 units of 320, which is 19 pixels across in the exported atlas
   * (`docs/face-designer.md`, "Export details"). Twenty-four sides is under a
   * pixel of chord at that size and still under one at the size the canvas
   * draws, which is as round as a circle needs to be when nothing ever sees
   * its edge.
   */
  private const val PIP_SIDES = 24

  private const val TURN = 2 * PI
}
