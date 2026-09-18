package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * That the designer's glyphs are the **prototype's** glyphs
 * (`design/dInfinityPhone.dc.html`, the inline sprite of `<symbol>`s).
 *
 * The same bargain `ModernistTest` strikes with the stylesheet: the design is
 * half the specification, so a copy of it in Kotlin is either checked against
 * the original or it is a drawing that quietly drifts. Re-import the sprite
 * with a different pencil and this fails, rather than the app going on
 * drawing last year's one.
 *
 * The two glyphs that are **not** in the sprite are checked the other way
 * round — that they parse, and that nobody has quietly claimed the prototype
 * has them (`docs/design-handover.md`).
 */
@RunWith(RobolectricTestRunner::class)
// A real bitmap to draw into, for the reason `FaceInkTest` asks for one: the
// legacy canvas records calls and draws nothing, so a glyph that threw half
// way would look exactly like one that worked.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignerIconsTest {
  @Test
  fun `every glyph the app draws is the one the prototype draws`() {
    DesignerIcons.all.forEach { (symbol, path) ->
      assertEquals("#$symbol is not the prototype's", pathOf(symbol), path)
    }
  }

  @Test
  fun `the sprite is where it is looked for`() {
    // A missing file would make the test above pass over an empty map of
    // nothing, which is the one way a check like this fails silently.
    assertTrue("nothing was read out of the prototype", DesignerIcons.all.isNotEmpty())
    assertTrue("the sprite is not in the prototype any more", "id=\"ic-pencil\"" in sprite)
  }

  @Test
  fun `the two the prototype has not got are not claimed to be its`() {
    listOf(DesignerIcons.PASTE, DesignerIcons.MIRROR).forEach { path ->
      assertTrue("this one is in the sprite after all: $path", path !in sprite)
    }
  }

  @Test
  fun `every glyph is a path a renderer can follow`() {
    (DesignerIcons.all.values + DesignerIcons.PASTE + DesignerIcons.MIRROR).forEach { path ->
      // `parsePathString` throws on a `d` it cannot read, which is the whole
      // assertion: a glyph that does not parse draws nothing at all, and
      // nothing at all is a button with no face on it.
      assertTrue("an empty path is not a glyph", PathParser().parsePathString(path).toNodes().isNotEmpty())
    }
  }

  @Test
  fun `every glyph puts ink on a real canvas`() {
    // A path the parser accepted and the renderer then choked on would be a
    // button with no face on it, and a `Canvas` inside a composable that is
    // never laid out cannot say which happened (`FaceInkTest` takes the same
    // view of the same question).
    everyGlyph().forEach { (name, path) ->
      assertTrue("#$name drew nothing", inked(drawn(path, DesignerIcons.INK)))
    }
  }

  @Test
  fun `a pen's glyph is drawn at the width the pen draws`() {
    // The three pens are one glyph at three widths, so the widths have to
    // reach the canvas — a broad pen drawn at the fine one's stroke would be
    // three identical buttons.
    val fine = inkedPixels(drawn(DesignerIcons.PENCIL, FINE))
    val broad = inkedPixels(drawn(DesignerIcons.PENCIL, BROAD))

    assertTrue("the broad pen is no broader than the fine one: $fine, $broad", broad > fine)
  }

  /** [path] stroked at [ink], on a real bitmap. */
  private fun drawn(
    path: String,
    ink: Float,
  ): ImageBitmap {
    val target = ImageBitmap(SIDE, SIDE)
    val glyph = PathParser().parsePathString(path).toPath()
    CanvasDrawScope().draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(target),
      size = Size(SIDE.toFloat(), SIDE.toFloat()),
    ) { strokeGlyph(glyph, Color.Black, ink) }
    return target
  }

  private fun inked(drawn: ImageBitmap): Boolean = inkedPixels(drawn) > 0

  /** How much of the canvas the glyph put ink on: it starts transparent. */
  private fun inkedPixels(drawn: ImageBitmap): Int {
    val pixels = IntArray(SIDE * SIDE)
    drawn.readPixels(pixels)
    return pixels.count { it != 0 }
  }

  /** The sprite's glyphs and the two the sprite has not got. */
  private fun everyGlyph(): Map<String, String> =
    DesignerIcons.all + mapOf("paste" to DesignerIcons.PASTE, "mirror" to DesignerIcons.MIRROR)

  /** The `d` of one `<symbol>` of the sprite. */
  private fun pathOf(symbol: String): String {
    val start = sprite.indexOf("id=\"$symbol\"")
    assertTrue("#$symbol is not in the prototype", start >= 0)
    val end = sprite.indexOf("</symbol>", start)
    return DRAWN
      .find(sprite.substring(start, end))
      ?.groupValues
      ?.get(1)
      .orEmpty()
  }

  private companion object {
    /** Big enough that a 24-unit box scales up rather than down. */
    const val SIDE = 64

    /** The fine pen's stroke, and the broad pen's (`DesignerScreen`). */
    const val FINE = 1.1f
    const val BROAD = 3.4f

    /** `d="…"` — what an SVG path is drawn from. */
    val DRAWN = Regex("""\bd="([^"]*)"""")

    /**
     * The prototype, found by walking up to the repository root the way
     * `ModernistTest` finds the stylesheet: a test's working directory is its
     * module's, and `design/` is a sibling of every module.
     */
    val sprite: String by lazy {
      val root =
        generateSequence(File(".").absoluteFile) { it.parentFile }
          .first { File(it, "design/dInfinityPhone.dc.html").isFile }
      File(root, "design/dInfinityPhone.dc.html").readText()
    }
  }
}
