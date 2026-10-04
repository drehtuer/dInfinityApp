package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.glyphs.LabelRoom
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Where an outline's corners are, and where a guide number sits
 * (`docs/face-designer.md`; design `8d`).
 *
 * The arithmetic lives outside the `Canvas` draw lambda for the reason
 * `ChartShapes` does: a draw lambda is the one place a test cannot reach, so
 * everything that can be wrong is kept out of it.
 */
class FaceShapesTest {
  @Test
  fun `a polygon has as many corners as its name says`() {
    assertEquals(3, FaceShapes.corners(FaceOutline.Triangle).size)
    assertEquals(4, FaceShapes.corners(FaceOutline.Square).size)
    assertEquals(5, FaceShapes.corners(FaceOutline.Pentagon).size)
    assertEquals(4, FaceShapes.corners(FaceOutline.PentagonalKite).size)
    assertEquals(4, FaceShapes.corners(FaceOutline.EnneagonalKite).size)
  }

  @Test
  fun `a circle has no corners rather than many`() {
    // The one outline that is not a polygon. An approximation would be corners
    // nobody would draw with.
    assertTrue(FaceShapes.corners(FaceOutline.Circle).isEmpty())
  }

  @Test
  fun `every corner is on the canvas`() {
    FaceOutline.entries.forEach { outline ->
      FaceShapes.corners(outline).forEach { dot ->
        assertTrue("$outline at $dot", dot.x in 0f..1f && dot.y in 0f..1f)
      }
    }
  }

  @Test
  fun `a triangle points upwards, which is what a face mask looks like`() {
    val corners = FaceShapes.corners(FaceOutline.Triangle)

    val apex = corners.minBy { it.y }
    assertEquals("the apex is not centred", 0.5f, apex.x, 1e-3f)
    assertEquals("two corners should share the bottom edge", 2, corners.count { it.y > 0.5f })
  }

  @Test
  fun `a square sits on a flat edge, because a square on its corner is a diamond`() {
    // Found by looking at it on a phone: the d6's face was a diamond. A test
    // that only counted corners had nothing to say about it.
    val corners = FaceShapes.corners(FaceOutline.Square)

    val top = corners.sortedBy { it.y }.take(2)
    assertEquals("the two top corners are not level", top[0].y, top[1].y, 1e-3f)
    assertTrue("the square has a corner at the top", corners.none { abs(it.x - 0.5f) < 1e-3f })
  }

  @Test
  fun `a triangle and a pentagon do point upwards, which is how they are moulded`() {
    listOf(FaceOutline.Triangle, FaceOutline.Pentagon).forEach { outline ->
      val apex = FaceShapes.corners(outline).minBy { it.y }
      assertEquals("$outline has no corner at the top", 0.5f, apex.x, 1e-3f)
    }
  }

  @Test
  fun `a kite is not a diamond - its waist sits above the middle`() {
    // What makes a d10 face read as a kite: two short edges at the top, two
    // long ones down to the point.
    KITES.forEach { outline ->
      val corners = FaceShapes.corners(outline)
      val waist = corners.filter { abs(it.x - 0.5f) > 0.1f }

      assertEquals("$outline", 2, waist.size)
      waist.forEach { assertTrue("$outline's waist is not above the middle: $it", it.y < 0.5f) }
    }
  }

  @Test
  fun `a d10's kite and a d18's are different shapes`() {
    // The fault this replaced: one kite for both, which fitted neither. The
    // d18's is the longer and narrower of the two.
    val d10 = FaceShapes.corners(FaceOutline.PentagonalKite)
    val d18 = FaceShapes.corners(FaceOutline.EnneagonalKite)

    assertTrue("the d18's kite is not narrower: $d10 against $d18", widthOf(d18) < widthOf(d10) - 0.05f)
  }

  @Test
  fun `each kite is its own die's face, corner for corner`() {
    // Similar to the polygon the solid has, which is what lets a turn and a
    // size carry one exactly onto the other. Every distance between two
    // corners in the same proportion to the face's own, on every face — the
    // faces of a trapezohedron are all one kite, and this says so too.
    mapOf(
      FaceOutline.PentagonalKite to DieShape.PentagonalTrapezohedron,
      FaceOutline.EnneagonalKite to DieShape.EnneagonalTrapezohedron,
    ).forEach { (outline, shape) ->
      val drawn = distances(FaceShapes.corners(outline).map { it.x.toDouble() to it.y.toDouble() })
      SolidFaces.of(shape).forEach { face ->
        val real = distances(face.corners.map(face::flatOf))
        val ratio = real.max() / drawn.max()
        assertEquals(
          "${shape.id} face ${face.index}: drawn $drawn against ${real.map { it / ratio }}",
          drawn,
          real.map { it / ratio },
          SIMILAR,
        )
      }
    }
  }

  @Test
  fun `a kite stands on its own axis in the middle of the canvas`() {
    // Upright and centred, so the mirror — the one symmetry a kite has —
    // carries it onto itself, and it spans what a regular outline spans.
    KITES.forEach { outline ->
      val corners = FaceShapes.corners(outline)
      val (top, right, bottom) = corners
      val left = corners[3]

      assertEquals("$outline's tip", 0.5f, top.x, 1e-5f)
      assertEquals("$outline's point", 0.5f, bottom.x, 1e-5f)
      assertEquals("$outline's waist is not level", left.y, right.y, 1e-5f)
      assertEquals("$outline's waist is not even", 0.5f - left.x, right.x - 0.5f, 1e-5f)
      assertEquals("$outline's length", 0.96f, bottom.y - top.y, 1e-5f)
    }
  }

  @Test
  fun `the tip that is up is the one between the short edges`() {
    KITES.forEach { outline ->
      val (top, right, bottom) = FaceShapes.corners(outline)

      assertTrue("$outline's short edges are not at the top", away(top, right) < away(right, bottom))
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `a face that is not a kite is refused`() {
    FaceShapes.kiteOf(SolidFaces.of(DieShape.Icosahedron).first())
  }

  private fun widthOf(corners: List<Dot>): Float = corners.maxOf(Dot::x) - corners.minOf(Dot::x)

  private fun away(
    a: Dot,
    b: Dot,
  ): Float = hypot(a.x - b.x, a.y - b.y)

  /** Every distance between two of [corners], in a fixed order. */
  private fun distances(corners: List<Pair<Double, Double>>): List<Double> =
    corners.indices
      .flatMap { a ->
        (a + 1 until corners.size).map { b ->
          hypot(corners[a].first - corners[b].first, corners[a].second - corners[b].second)
        }
      }.sorted()

  private fun assertEquals(
    message: String,
    expected: List<Double>,
    actual: List<Double>,
    delta: Double,
  ) {
    assertEquals(message, expected.size, actual.size)
    expected.zip(actual).forEach { (e, a) -> assertEquals(message, e, a, delta) }
  }

  @Test
  fun `a single number sits in the middle`() {
    assertEquals(0.5f, FaceShapes.spot(FaceOutline.Square, GuideSpot.Middle).x, 1e-3f)
    assertEquals(0.5f, FaceShapes.spot(FaceOutline.Square, GuideSpot.Middle).y, 1e-3f)
  }

  @Test
  fun `a d4's three numbers sit at three different corners, inside the shape`() {
    // Inside rather than on the edge, which is where a moulded d4 has them.
    val spots =
      listOf(GuideSpot.FirstCorner, GuideSpot.SecondCorner, GuideSpot.ThirdCorner)
        .map { FaceShapes.spot(FaceOutline.Triangle, it) }

    assertEquals("two numbers landed in the same place", 3, spots.distinct().size)
    val corners = FaceShapes.corners(FaceOutline.Triangle)
    spots.forEachIndexed { index, spot ->
      val corner = corners[index]
      assertTrue(
        "the number is not between its corner and the centre: $spot",
        abs(spot.x - 0.5f) < abs(corner.x - 0.5f) + 1e-3f && abs(spot.y - 0.5f) < abs(corner.y - 0.5f) + 1e-3f,
      )
    }
  }

  @Test
  fun `a corner spot on a shape with no corners falls back to the middle`() {
    // It cannot happen from a real die — only a tetrahedron reads from its
    // corners and a tetrahedron's cells are triangles — but a guide in the
    // middle is better than one off the canvas.
    val spot = FaceShapes.spot(FaceOutline.Circle, GuideSpot.FirstCorner)

    assertEquals(0.5f, spot.x, 1e-3f)
    assertEquals(0.5f, spot.y, 1e-3f)
  }

  @Test
  fun `a guide sits exactly where a stamp of the same number lands`() {
    // One answer to "how far in from the corner", in `core/glyphs`, because
    // the tray prints a d4's numbers there too (`FaceStamp.numbers`).
    val corner = FaceShapes.corner(FaceOutline.Triangle, GuideSpot.SecondCorner)
    val (x, y) = LabelRoom.inside(corner.x.toDouble() to corner.y.toDouble())

    val spot = FaceShapes.spot(FaceOutline.Triangle, GuideSpot.SecondCorner)
    assertEquals(x.toFloat(), spot.x, 1e-6f)
    assertEquals(y.toFloat(), spot.y, 1e-6f)
  }

  @Test
  fun `the middle is a corner nothing pulls on`() {
    val middle = FaceShapes.corner(FaceOutline.Square, GuideSpot.Middle)

    assertEquals(0.5f, middle.x, 1e-6f)
    assertEquals(0.5f, middle.y, 1e-6f)
  }
}

private val KITES = listOf(FaceOutline.PentagonalKite, FaceOutline.EnneagonalKite)

/** Corners are floats on the canvas, so the proportions agree to a float's worth. */
private const val SIMILAR = 1e-5
