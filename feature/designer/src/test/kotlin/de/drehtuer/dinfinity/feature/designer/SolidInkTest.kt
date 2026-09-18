package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.drehtuer.dinfinity.designer.StageFace
import de.drehtuer.dinfinity.designer.StagePoint
import de.drehtuer.dinfinity.designer.StageShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Putting the die on the screen (`docs/face-designer.md`, "The solid, not just
 * the face").
 *
 * The same split — and the same harness — as `FaceInkTest`. Where a corner
 * landed is `SolidStageTest`'s; what is asked here is that every kind of thing
 * the stage hands over is actually *drawn*, on a real canvas, rather than
 * throwing on a shape nobody tried.
 */
@RunWith(RobolectricTestRunner::class)
// A real bitmap to draw into, for the reason `FaceInkTest` asks for one: the
// legacy canvas records calls and draws nothing, so a drawing that threw half
// way would look exactly like one that worked.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SolidInkTest {
  @Test
  fun `rings become a path across the stage they are fractions of`() {
    val path = pathOf(listOf(listOf(StagePoint(0f, 0f), StagePoint(1f, 0.5f), StagePoint(0f, 1f))), SIZE)

    val bounds = path.getBounds()
    assertEquals(0f, bounds.left, NEARLY)
    assertEquals(SIDE.toFloat(), bounds.right, NEARLY)
    assertEquals(SIDE.toFloat(), bounds.bottom, NEARLY)
  }

  @Test
  fun `a ring with nothing in it is skipped rather than started`() {
    // A face turned exactly edge-on projects to no polygon at all, and a path
    // with one point in it is not a shape anything can fill.
    assertTrue("an empty ring drew something", pathOf(listOf(emptyList()), SIZE).isEmpty)
  }

  @Test
  fun `a face is drawn, and so is what is on it`() {
    assertTrue("the face drew nothing", inked(onACanvas { drawFace(triangle(MARK), colours(), selected = false) }))
  }

  @Test
  fun `the face being drawn on is drawn differently from the others`() {
    // The tint and the 4 dp outline, which is what keeps the player's place
    // across the two tabs. What they look like is a phone's to say; that they
    // are not the same picture is this test's.
    val plain = onACanvas { drawFace(triangle(), colours(), selected = false) }
    val chosen = onACanvas { drawFace(triangle(), colours(), selected = true) }

    assertNotEquals("the selected face is drawn like every other", pixels(plain).toList(), pixels(chosen).toList())
  }

  @Test
  fun `the silhouette is drawn under everything else`() {
    assertTrue(
      "the die's own body drew nothing",
      inked(onACanvas { drawSolid(SQUARE, Color.Gray) }),
    )
  }

  @Test
  fun `a mark is drawn under the even-odd rule, so a hole stays open`() {
    // Two rings, one inside the other: a `0`. Filled either way the outer ring
    // is ink, and what says which rule was used is the middle of it.
    val zero =
      StageShape(
        rings =
          listOf(
            listOf(StagePoint(0.1f, 0.1f), StagePoint(0.9f, 0.1f), StagePoint(0.9f, 0.9f), StagePoint(0.1f, 0.9f)),
            listOf(StagePoint(0.3f, 0.3f), StagePoint(0.7f, 0.3f), StagePoint(0.7f, 0.7f), StagePoint(0.3f, 0.7f)),
          ),
        colorArgb = INK,
      )

    val drawn = onACanvas { drawShape(zero) }

    assertTrue("the ringed mark drew nothing", inked(drawn))
    assertEquals("the hole in a nought was painted in", 0, pixels(drawn)[SIDE / 2 * SIDE + SIDE / 2])
  }

  @Test
  fun `a face turned away from the lamp is drawn darker than one facing it`() {
    val colours = colours()

    assertNotEquals("the lamp makes no difference", colours.lit(1f), colours.lit(0f))
    assertEquals("a face full in the light is not its own paper", colours.paper, colours.lit(1f))
  }

  private fun triangle(vararg marks: StageShape) =
    StageFace(
      cell = 0,
      outline = listOf(StagePoint(0.5f, 0.1f), StagePoint(0.9f, 0.9f), StagePoint(0.1f, 0.9f)),
      marks = marks.toList(),
      light = 0.7f,
      depth = 1.0,
    )

  private fun colours() =
    SolidColours(
      paper = Color.White,
      shade = Color.Black,
      edge = Color.Gray,
      chosen = Color.Red,
      tint = Color.Red.copy(alpha = 0.16f),
    )

  /** Runs [drawing] against a real canvas, the way `FaceInkTest` does. */
  private fun onACanvas(drawing: DrawScope.() -> Unit): ImageBitmap {
    val target = ImageBitmap(SIDE, SIDE)
    CanvasDrawScope().draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(target),
      size = Size(SIDE.toFloat(), SIDE.toFloat()),
      block = drawing,
    )
    return target
  }

  private fun pixels(drawn: ImageBitmap): IntArray {
    val read = IntArray(SIDE * SIDE)
    drawn.readPixels(read)
    return read
  }

  /** True when anything at all was put down: the canvas starts transparent. */
  private fun inked(drawn: ImageBitmap): Boolean = pixels(drawn).any { it != 0 }

  private companion object {
    const val SIDE = 64
    val SIZE = Size(SIDE.toFloat(), SIDE.toFloat())
    const val NEARLY = 0.5f

    /** Opaque black, which is what a pen with nothing chosen draws in. */
    const val INK: Int = 0xFF000000.toInt()

    val SQUARE =
      listOf(
        StagePoint(0.1f, 0.1f),
        StagePoint(0.9f, 0.1f),
        StagePoint(0.9f, 0.9f),
        StagePoint(0.1f, 0.9f),
      )

    val MARK =
      StageShape(
        rings = listOf(listOf(StagePoint(0.4f, 0.4f), StagePoint(0.6f, 0.4f), StagePoint(0.5f, 0.7f))),
        colorArgb = INK,
      )
  }
}
