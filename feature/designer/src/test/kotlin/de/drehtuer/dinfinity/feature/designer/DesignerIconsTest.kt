package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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

  /** The `d` of one `<symbol>` of the sprite. */
  private fun pathOf(symbol: String): String {
    val start = sprite.indexOf("id=\"$symbol\"")
    assertTrue("#$symbol is not in the prototype", start >= 0)
    val end = sprite.indexOf("</symbol>", start)
    return DRAWN.find(sprite.substring(start, end))?.groupValues?.get(1).orEmpty()
  }

  private companion object {
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
