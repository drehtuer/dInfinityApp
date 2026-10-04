package de.drehtuer.dinfinity.feature.saved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How fast the list scrolls under a row held near its edge, without a finger
 * (`docs/dice-notation.md`, "Saved rolls").
 */
class SavedEdgeScrollTest {
  private fun speed(
    pointer: Float,
    origin: Float = MIDDLE,
    viewport: Float = VIEWPORT,
    zone: Float = ZONE,
  ) = SavedEdgeScroll.speed(pointer, origin, viewport, zone, TOP)

  @Test
  fun `a finger between the bands does not scroll the list`() {
    assertEquals(0f, speed(MIDDLE), EPSILON)
    assertEquals(0f, speed(ZONE), EPSILON)
    assertEquals(0f, speed(VIEWPORT - ZONE), EPSILON)
  }

  @Test
  fun `a finger in the bottom band scrolls down, faster the deeper it is`() {
    assertEquals(TOP / 4f, speed(VIEWPORT - ZONE * 3f / 4f), EPSILON)
    assertEquals(TOP / 2f, speed(VIEWPORT - ZONE / 2f), EPSILON)
    assertEquals(TOP, speed(VIEWPORT), EPSILON)
  }

  @Test
  fun `a finger in the top band scrolls up, faster the deeper it is`() {
    assertEquals(-TOP / 4f, speed(ZONE * 3f / 4f), EPSILON)
    assertEquals(-TOP / 2f, speed(ZONE / 2f), EPSILON)
    assertEquals(-TOP, speed(0f), EPSILON)
  }

  @Test
  fun `a finger past the edge scrolls at the top speed and no faster`() {
    assertEquals(TOP, speed(VIEWPORT + 500f), EPSILON)
    assertEquals(-TOP, speed(-500f), EPSILON)
  }

  @Test
  fun `a row picked up inside a band does not move the list until the finger moves towards the edge`() {
    val inBottom = VIEWPORT - ZONE / 2f
    assertEquals(0f, speed(inBottom, origin = inBottom), EPSILON)
    // Away from the edge is not towards it.
    assertEquals(0f, speed(inBottom - 5f, origin = inBottom), EPSILON)
    // Half-way from where it started to the edge is half the speed.
    assertEquals(TOP / 2f, speed(VIEWPORT - ZONE / 4f, origin = inBottom), EPSILON)

    val inTop = ZONE / 2f
    assertEquals(0f, speed(inTop, origin = inTop), EPSILON)
    assertEquals(0f, speed(inTop + 5f, origin = inTop), EPSILON)
    assertEquals(-TOP / 2f, speed(ZONE / 4f, origin = inTop), EPSILON)
  }

  @Test
  fun `a drag that started on the very edge scrolls at the top speed once past it`() {
    assertEquals(TOP, speed(VIEWPORT + 1f, origin = VIEWPORT), EPSILON)
    assertEquals(-TOP, speed(-1f, origin = 0f), EPSILON)
  }

  @Test
  fun `on a list shorter than two bands the bands split it and do not overlap`() {
    val short = ZONE
    assertEquals(0f, speed(short / 2f, origin = short / 2f, viewport = short), EPSILON)
    assertTrue(speed(short / 2f + 1f, origin = short / 2f, viewport = short) > 0f)
    assertTrue(speed(short / 2f - 1f, origin = short / 2f, viewport = short) < 0f)
  }

  @Test
  fun `a list with no height or no band scrolls nothing`() {
    assertEquals(0f, speed(0f, viewport = 0f), EPSILON)
    assertEquals(0f, speed(0f, zone = 0f), EPSILON)
  }

  @Test
  fun `a frame scrolls by the speed times its length`() {
    // 60 frames a second at 600 per second is 10 a frame.
    assertEquals(10f, SavedEdgeScroll.step(600f, 1_000_000_000L / 60), 0.01f)
    assertEquals(-10f, SavedEdgeScroll.step(-600f, 1_000_000_000L / 60), 0.01f)
  }

  @Test
  fun `a frame of no time or of time running backwards scrolls nothing`() {
    assertEquals(0f, SavedEdgeScroll.step(600f, 0L), EPSILON)
    assertEquals(0f, SavedEdgeScroll.step(600f, -5L), EPSILON)
  }

  @Test
  fun `a stalled frame scrolls no further than the longest frame does`() {
    val longest = SavedEdgeScroll.step(600f, SavedEdgeScroll.LONGEST_FRAME_NANOS)
    assertEquals(longest, SavedEdgeScroll.step(600f, 2_000_000_000L), EPSILON)
  }

  private companion object {
    const val VIEWPORT = 1000f
    const val ZONE = 100f
    const val MIDDLE = 500f
    const val TOP = 800f
    const val EPSILON = 0.0001f
  }
}
