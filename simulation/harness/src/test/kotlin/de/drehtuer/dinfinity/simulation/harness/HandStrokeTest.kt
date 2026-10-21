package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.Release
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HandStrokeTest {
  @Test
  fun `the stroke a run asks for is the toss the hand makes`() {
    for (speed in listOf(Release.LEAST_MM_PER_SECOND, 1_200.0, Release.MOST_MM_PER_SECOND)) {
      val release = Release.of(HandStroke.of(speed, seed = 5L))
      assertNotNull(release.direction)
      // Whole steps: a stroke cannot be cut finer than one, at the push.
      val step = HandStroke.PUSH_MM_PER_SECOND2 / 120 * Release.GAIN
      assertEquals(speed, release.speedMmPerSecond, step)
    }
  }

  @Test
  fun `each roll's hand goes its own way, and the same seed the same way`() {
    val one = Release.of(HandStroke.of(1_200.0, seed = 1L)).direction
    val other = Release.of(HandStroke.of(1_200.0, seed = 2L)).direction

    assertNotEquals(one, other)
    assertEquals(HandStroke.of(1_200.0, seed = 1L), HandStroke.of(1_200.0, seed = 1L))
  }

  @Test
  fun `the shake ends quiet, long enough for the hand to let go`() {
    val moments = HandStroke.of(1_200.0, seed = 3L)
    val quiet = moments.takeLastWhile { it.accelerationMmPerSecond2.length < Release.ACTIVE_MM_PER_SECOND2 }

    assertEquals(HandStroke.QUIET_STEPS, quiet.size)
    assertTrue(moments.zipWithNext().all { (a, b) -> b.stepIndex == a.stepIndex + 1 })
  }
}
