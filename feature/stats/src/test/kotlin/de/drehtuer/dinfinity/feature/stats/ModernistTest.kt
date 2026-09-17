package de.drehtuer.dinfinity.feature.stats

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The module's copy of the Modernist tokens, against the CSS it was copied from
 * (`design/_ds/modernist-f7022762-4cb9-409e-a6ce-7116795bae5b/styles.css`).
 *
 * A transcription nobody checks is a transcription that drifts. These are the
 * numbers the design system states, written out again here so that changing one
 * of them in [Modernist] without changing it in the design system fails.
 *
 * Plain JVM assertions: none of this needs a screen.
 */
class ModernistTest {
  @Test
  fun `the spacing scale is the design system's`() {
    // --space-1 … --space-8. There is no --space-5 and no --space-7, which is
    // the point of a scale: 18 px is not a space this system has.
    assertEquals(4f, Modernist.x1.value, 0f)
    assertEquals(8f, Modernist.x2.value, 0f)
    assertEquals(12f, Modernist.x3.value, 0f)
    assertEquals(16f, Modernist.x4.value, 0f)
    assertEquals(24f, Modernist.x6.value, 0f)
    assertEquals(32f, Modernist.x8.value, 0f)
  }

  @Test
  fun `every step of the scale is a multiple of the smallest`() {
    val scale = listOf(Modernist.x1, Modernist.x2, Modernist.x3, Modernist.x4, Modernist.x6, Modernist.x8)
    scale.forEach { step ->
      assertEquals("$step is off the 4 dp grid", 0f, step.value % Modernist.x1.value, 0f)
    }
    assertEquals("the scale must be in order", scale.sortedBy { it.value }, scale)
  }

  @Test
  fun `nothing in this system has a rounded corner`() {
    // --radius-sm, --radius-md and --radius-lg are all 0px.
    assertEquals(0f, Modernist.radius.value, 0f)
    // And a Material button is a pill unless it is handed a shape, because
    // `ButtonDefaults.shape` reads `CornerFull` rather than `MaterialTheme.shapes`.
    assertSame(RectangleShape, Modernist.square)
  }

  @Test
  fun `a rule is 2 dp and the line inside a list is 1`() {
    assertEquals(2f, Modernist.rule.value, 0f)
    assertEquals(1f, Modernist.hairline.value, 0f)
    assertTrue("a rule is heavier than the line between two rows", Modernist.rule > Modernist.hairline)
  }

  @Test
  fun `quiet copy is the ink at the prototype's opacity`() {
    assertEquals(0.65f, Modernist.MUTED, 0f)
  }

  @Test
  fun `a kicker is tracked out by a tenth of an em`() {
    assertEquals(0.1.em, Modernist.kickerTracking)
  }

  @Test
  fun `the smallest thing worth pressing is Android's own figure`() {
    // Not on the 4/8/12/16/24/32 scale, and deliberately so: 48 dp is WCAG
    // 2.2's 2.5.8 at level AA, which is a rule about fingers rather than about
    // this design system (`TouchTarget.kt`).
    assertEquals(48.dp, TOUCH_TARGET)
  }
}
