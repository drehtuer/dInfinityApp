package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * A drawing made against the kite both trapezohedra used to share, carried
 * onto the one its die has now (`SharedKite`; `docs/architecture.md`,
 * decision 86).
 *
 * The promise is the tray's: a format-1 drawing lands on the die exactly
 * where it landed before the kites were split.
 */
class SharedKiteTest {
  @Test
  fun `a carried drawing lands in every cell where the shared kite's exporter put it`() {
    // Every face, because the move is worked out on the first and claimed to
    // be the same on all of them.
    KITES.forEach { shape ->
      val outline = FaceOutline.of(shape)
      SolidFaces.of(shape).forEach { face ->
        val old = FaceOnSolid.cellFitOf(face, SharedKite.CORNERS)
        val new = FaceOnSolid.cellFitOf(face, outline)
        val carried = SharedKite.carried(listOf(fill(PROBES)), shape).single().dots

        PROBES.zip(carried).forEach { (before, after) ->
          val was = old.of(before)
          val now = new.of(after)
          assertTrue(
            "${shape.id} face ${face.index} moved $before from $was to $now",
            hypot(was.x - now.x, was.y - now.y) < CLOSE,
          )
        }
      }
    }
  }

  @Test
  fun `the shared kite was bigger than either die's own, so a drawing grows`() {
    // What the exporter did to cover a face with a kite that was not its
    // shape: grew it. The d18's kite is narrower, so it grew more.
    val d10 = SharedKite.growthOn(DieShape.PentagonalTrapezohedron)
    val d18 = SharedKite.growthOn(DieShape.EnneagonalTrapezohedron)

    assertEquals(1.314, d10, FIGURE)
    assertEquals(1.329, d18, FIGURE)
  }

  @Test
  fun `a stroke's nib grows with it, and nothing else about a mark changes`() {
    val stroke = Stroke(dots = listOf(Dot(0.4f, 0.4f), Dot(0.6f, 0.6f)), colorArgb = INK, width = 0.02f, erases = true)
    val shape = DieShape.EnneagonalTrapezohedron

    val carried = SharedKite.carried(listOf(stroke), shape).single() as Stroke

    assertEquals(0.02 * SharedKite.growthOn(shape), carried.width.toDouble(), 1e-6)
    assertEquals(INK, carried.colorArgb)
    assertTrue("the eraser stopped erasing", carried.erases)
  }

  @Test
  fun `a whole-face fill drawn then still fills the whole face now`() {
    // The bucket's ordinary use is the canvas square, and the square carried
    // across still contains every corner of the die's own kite.
    KITES.forEach { shape ->
      val square = SharedKite.carried(listOf(fill(FaceFill.FACE)), shape).single().dots

      FaceShapes.corners(FaceOutline.of(shape)).forEach { corner ->
        assertTrue("${shape.id}'s $corner is outside $square", Polygon.contains(square, corner))
      }
    }
  }

  @Test
  fun `only the trapezohedra drew in the shared kite`() {
    val marks = listOf(fill(PROBES))

    DieShape.entries.filterNot { it in KITES }.forEach { shape ->
      assertFalse(shape.id, SharedKite.wasDrawnOn(shape))
      assertSame(shape.id, marks, SharedKite.carried(marks, shape))
    }
    KITES.forEach { assertTrue(it.id, SharedKite.wasDrawnOn(it)) }
  }

  @Test
  fun `the shared kite is the one format 1 drew`() {
    // Written down rather than derived, because nothing derives it any more:
    // it is the shape old drafts were drawn in, and it cannot change.
    assertEquals(
      listOf(Dot(0.5f, 0.04f), Dot(0.84f, 0.38f), Dot(0.5f, 0.96f), Dot(0.16f, 0.38f)),
      SharedKite.CORNERS,
    )
  }

  private fun fill(dots: List<Dot>) = Fill(dots = dots, colorArgb = INK)

  private companion object {
    val KITES = listOf(DieShape.PentagonalTrapezohedron, DieShape.EnneagonalTrapezohedron)

    /** Points across the shared kite, its corners and its middle among them. */
    val PROBES = SharedKite.CORNERS + listOf(Dot(0.5f, 0.5f), Dot(0.3f, 0.6f), Dot(0.7f, 0.2f))

    /** A float canvas's worth, in fractions of a cell. */
    const val CLOSE = 1e-5

    /** How near the growth figures in `docs/face-designer.md` are held. */
    const val FIGURE = 1e-3

    const val INK = 0xFF000000.toInt()
  }
}
