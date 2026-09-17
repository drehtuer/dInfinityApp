package de.drehtuer.dinfinity.feature.saved

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The tokens this module draws with are the ones the design system says
 * (`design/_ds/modernist-.../styles.css`).
 *
 * [Modernist] is a transcription, because the app's own copy of these values
 * lives in `:app` and `:app` depends on this module. A transcription that
 * nothing checks is a copy that drifts, so this reads the stylesheet itself:
 * re-import the design system with a different scale and this fails, rather
 * than a screen quietly going out of step with the prototype.
 */
class ModernistTest {
  @Test
  fun `the spacing scale is the stylesheet's`() {
    assertEquals(space(1).dp, Modernist.x1)
    assertEquals(space(2).dp, Modernist.x2)
    assertEquals(space(3).dp, Modernist.x3)
    assertEquals(space(4).dp, Modernist.x4)
    assertEquals(space(6).dp, Modernist.x6)
  }

  @Test
  fun `nothing in this system has a rounded corner`() {
    // `--radius-sm`, `--radius-md` and `--radius-lg` are all `0px`, and a
    // Material button is a pill until it is handed a shape that is not one.
    listOf("sm", "md", "lg").forEach { step ->
      assertEquals("--radius-$step", 0.0, value("--radius-$step"), 0.0)
    }
    assertEquals(0.dp, Modernist.radius)
    assertEquals(RectangleShape, Modernist.square)
  }

  @Test
  fun `a rule is 2 px and the line inside a list is 1`() {
    val hr = Regex("""\.hr\s*\{[^}]*height:\s*([\d.]+)px""").find(css)
    assertTrue("the stylesheet has no .hr", hr != null)
    assertEquals(hr!!.groupValues[1].toDouble(), Modernist.rule.value.toDouble(), 0.0)
    assertEquals(1.0, Modernist.hairline.value.toDouble(), 0.0)
  }

  private fun space(step: Int): Float = value("--space-$step").toFloat()

  private fun value(token: String): Double {
    val found =
      Regex(Regex.escape(token) + """:\s*([\d.]+)px""").find(css)
        ?: error("$token is not in the stylesheet")
    return found.groupValues[1].toDouble()
  }

  private companion object {
    /**
     * The stylesheet, found by walking up from wherever the test was run —
     * Gradle starts it in the module directory, a run from the IDE may not.
     */
    val css: String by lazy {
      generateSequence(File(".").absoluteFile) { it.parentFile }
        .mapNotNull { up ->
          File(up, "design/_ds")
            .listFiles()
            ?.firstOrNull { it.isDirectory && it.name.startsWith("modernist-") }
        }.first()
        .resolve("styles.css")
        .readText()
    }
  }
}
