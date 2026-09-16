package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.FaceOutline
import de.drehtuer.dinfinity.designer.Fill
import de.drehtuer.dinfinity.designer.GuideMark
import de.drehtuer.dinfinity.designer.GuideSpot
import de.drehtuer.dinfinity.designer.Stamp
import de.drehtuer.dinfinity.designer.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Putting the canvas on the screen (`docs/face-designer.md`).
 *
 * Split in two, which is the split the file itself has. The geometry — where a
 * finger landed, what path an outline makes — is arithmetic and is asserted
 * directly. The drawing is a `DrawScope`, which a test cannot read back, so
 * what is asserted there is that every kind of mark is actually *drawn*: the
 * lambda runs to the end for each of them, on a real Canvas, rather than
 * throwing on a shape nobody tried.
 *
 * That second half is worth having even though it reads nothing. Each mark kind
 * takes a different path through the file — the stamp winds its rings under
 * even-odd, the eraser paints white, the fill closes — and a kind that threw
 * would take the designer's canvas down with it.
 */
@RunWith(RobolectricTestRunner::class)
// A real bitmap to draw into, for the same reason `BitmapAtlasTest` asks for one:
// the legacy canvas records calls and draws nothing, so a drawing that threw
// half way would look exactly like one that worked.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaceInkTest {
  @Test
  fun `a touch becomes the fraction of the canvas it landed on`() {
    assertEquals(Dot(0.5f, 0.25f), Offset(50f, 25f).asDot(width = 100f, height = 100f))
  }

  @Test
  fun `a touch outside the canvas is brought back to its edge`() {
    // A finger that slides off the side draws to the edge rather than storing a
    // mark nothing can rasterise.
    assertEquals(Dot(0f, 1f), Offset(-20f, 300f).asDot(width = 100f, height = 100f))
    assertEquals(Dot(1f, 0f), Offset(140f, -7f).asDot(width = 100f, height = 100f))
  }

  @Test
  fun `a polygon outline traces its corners and closes`() {
    val path = Path().apply { follow(FaceOutline.Triangle, width = 100f, height = 100f) }

    assertFalse("a triangle drew nothing", path.isEmpty)
    // Three corners on a hundred-square canvas cannot reach outside it.
    val bounds = path.getBounds()
    assertTrue(bounds.left >= -1f && bounds.top >= -1f)
    assertTrue(bounds.right <= 101f && bounds.bottom <= 101f)
  }

  @Test
  fun `the coin is the one outline that is a circle rather than corners`() {
    val path = Path().apply { follow(FaceOutline.Circle, width = 80f, height = 80f) }

    assertFalse(path.isEmpty)
    assertEquals(80f, path.getBounds().width, 0.5f)
    assertEquals(80f, path.getBounds().height, 0.5f)
  }

  @Test
  fun `dots are traced across the canvas they are fractions of`() {
    val path = Path().apply { trace(listOf(Dot(0f, 0f), Dot(1f, 0.5f)), width = 200f, height = 100f) }

    val bounds = path.getBounds()
    assertEquals(0f, bounds.left, 0.5f)
    assertEquals(200f, bounds.right, 0.5f)
    assertEquals(50f, bounds.bottom, 0.5f)
  }

  @Test
  fun `a path from no dots at all is empty rather than a mistake`() {
    // An undo that leaves a mark with nothing in it must not take the canvas
    // down with it.
    assertTrue(Path().apply { trace(emptyList(), width = 10f, height = 10f) }.isEmpty)
  }

  @Test
  fun `every kind of mark draws`() {
    val marks =
      listOf(
        Stroke(dots = listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.9f)), width = 0.02f, colorArgb = INK),
        Stroke(dots = listOf(Dot(0.2f, 0.2f)), width = 0.05f, colorArgb = INK, erases = true),
        Fill(dots = listOf(Dot(0f, 0f), Dot(1f, 0f), Dot(1f, 1f)), colorArgb = INK),
        Stamp(rings = listOf(listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.1f), Dot(0.5f, 0.9f))), colorArgb = INK),
      )

    assertTrue("nothing was drawn", inked(onACanvas { marks.forEach { mark -> drawMark(mark) } }))
  }

  @Test
  fun `each kind of mark draws on its own, so none of them is carried by another`() {
    // Together they would hide one that drew nothing. Each kind takes a
    // different route through the file — the stamp winds its rings under
    // even-odd, the eraser paints the canvas's own white, the fill closes.
    val kinds =
      mapOf(
        "a pen stroke" to Stroke(listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.9f)), width = 0.05f, colorArgb = INK),
        "a filled region" to Fill(listOf(Dot(0f, 0f), Dot(1f, 0f), Dot(1f, 1f)), colorArgb = INK),
        "a stamped glyph" to Stamp(listOf(listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.1f), Dot(0.5f, 0.9f))), INK),
      )

    kinds.forEach { (what, mark) ->
      assertTrue("$what drew nothing", inked(onACanvas { drawMark(mark) }))
    }
  }

  @Test
  fun `the eraser paints the canvas rather than cutting a hole in it`() {
    // A hole would be a fourth kind of thing to store, to undo and to
    // rasterise. So an erasing stroke is a white one, and it leaves pixels
    // behind like any other.
    val rubbed = Stroke(listOf(Dot(0.1f, 0.5f), Dot(0.9f, 0.5f)), width = 0.2f, colorArgb = INK, erases = true)

    assertTrue("the eraser left the canvas untouched", inked(onACanvas { drawMark(rubbed) }))
  }

  @Test
  fun `a stamp with a hole in it draws both rings in one shape`() {
    // Even-odd is what leaves the counter of a `0` open. Drawn as two shapes it
    // would be a blob; this asserts the ringed path runs at all, which is the
    // part a Canvas can be asked.
    val zero =
      Stamp(
        rings =
          listOf(
            listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.1f), Dot(0.9f, 0.9f), Dot(0.1f, 0.9f)),
            listOf(Dot(0.3f, 0.3f), Dot(0.7f, 0.3f), Dot(0.7f, 0.7f), Dot(0.3f, 0.7f)),
          ),
        colorArgb = INK,
      )

    assertTrue("the ringed glyph drew nothing", inked(onACanvas { drawStamp(zero) }))
  }

  @Test
  fun `the guide draws the numeral it was given`() {
    val guide =
      GuideMark(
        value = 6,
        spot = GuideSpot.Middle,
        rings = listOf(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.2f), Dot(0.5f, 0.8f))),
      )

    assertTrue("the guide drew nothing", inked(onACanvas { drawGuide(guide, Color.Gray) }))
  }

  @Test
  fun `a guide cannot be made with nothing to trace`() {
    // The type forbids it rather than the drawing coping with it: a guide with
    // no rings is a guide that was turned off, and that is an absence rather
    // than an empty mark.
    assertThrows(IllegalArgumentException::class.java) {
      GuideMark(value = 6, spot = GuideSpot.Middle, rings = emptyList())
    }
  }

  /**
   * Runs [drawing] against a real canvas.
   *
   * A `DrawScope` driven straight rather than a `Canvas` composable inside a
   * Compose rule, for the reason this whole half exists: what is being asked is
   * whether the drawing *runs*, and a composable that is never laid out and
   * never drawn answers nothing while looking as though it did.
   */
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

  /** True when anything at all was put down: the canvas starts transparent. */
  private fun inked(drawn: ImageBitmap): Boolean {
    val pixels = IntArray(SIDE * SIDE)
    drawn.readPixels(pixels)
    return pixels.any { it != 0 }
  }

  private companion object {
    const val SIDE = 64

    /** Opaque black, which is what a pen with nothing chosen draws in. */
    const val INK: Int = 0xFF000000.toInt()
  }
}
