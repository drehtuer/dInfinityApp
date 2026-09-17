package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [TOUCH_TARGET], against the figure it is supposed to be.
 *
 * The design tokens themselves are checked against the stylesheet by
 * [ModernistTest]; this one is not a design token at all, which is the point of
 * it living in its own file.
 *
 * Plain JVM assertions: none of this needs a screen.
 */
class TouchTargetTest {
  @Test
  fun `the smallest thing worth pressing is Android's own figure`() {
    // Not on the 4/8/12/16/24/32 scale, and deliberately so: 48 dp is WCAG
    // 2.2's 2.5.8 at level AA, which is a rule about fingers rather than about
    // this design system (`TouchTarget.kt`).
    assertEquals(48.dp, TOUCH_TARGET)
  }
}
