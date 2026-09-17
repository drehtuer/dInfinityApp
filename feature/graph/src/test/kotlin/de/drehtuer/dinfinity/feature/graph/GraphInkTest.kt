package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.ui.common.Modernist
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the chart is drawn in, where the theme does not get to say
 * (`design/dInfinity.dc.html`, option 1k).
 *
 * Four of the chart's inks follow the theme and are the theme's business: the
 * full ink, the accent under a finger, the accent at the rolled total, the
 * surface behind the ±1σ band. One does not. A bar outside the band is
 * `--color-neutral-500`, a **named step** of the grey ramp, and the design
 * system remaps that step to itself on a dark ground
 * (`design/dInfinityPhone.dc.html`, `.dz-dark`) — the dark override reflects
 * the ramp about it, so it is the fixed point and the same grey on both
 * pages.
 *
 * It is asserted because it is one line that would drift in silence. It used
 * to be the ink at 45 %, which lands on neutral-500 on the light ground, on
 * **neutral-600** on the dark one, and on two further greys again where a bar
 * stands on the band and composites against the surface instead. Nothing
 * failed while it did that, which is the whole reason for this file.
 */
class GraphInkTest {
  @Test
  fun `a bar outside the deviation band is the ramp step itself`() {
    assertEquals(Modernist.Neutral.v500, BAR_GREY)
  }

  @Test
  fun `the bar grey is opaque, so it cannot have gone back to thinning the ink`() {
    // Every way of thinning the ink is translucent: the drifted colour came
    // from compositing the text colour over whatever happened to be behind
    // the bar, which is why it was three greys rather than one. An opaque
    // step is the same colour wherever it is drawn, and this is the assertion
    // that says so without needing a screenshot to prove it.
    assertEquals(1f, BAR_GREY.alpha, 0f)
  }
}
