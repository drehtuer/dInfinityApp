package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    assertEquals(4, FaceShapes.corners(FaceOutline.Kite).size)
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
    val corners = FaceShapes.corners(FaceOutline.Kite)
    val waist = corners.filter { abs(it.x - 0.5f) > 0.1f }

    assertEquals(2, waist.size)
    waist.forEach { assertTrue("the waist is not above the middle: $it", it.y < 0.5f) }
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
}
