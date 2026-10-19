package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReleaseTest {
  @Test
  fun `no shake at all is a gentle throw in no direction of its own`() {
    assertEquals(Release(direction = null, speedMmPerSecond = Release.LEAST_MM_PER_SECOND), Release.of(emptyList()))
    assertEquals(Release(null, Release.LEAST_MM_PER_SECOND), Release.of(strokes(Vector3(1.0, 0.0, 0.0) to 1).take(1)))
  }

  @Test
  fun `a stroke along the tray throws the way the hand went, faster than the hand`() {
    // Pushed for a tenth of a second at 9 m/s² and stopped again: the hand
    // peaks at 900 mm/s, and the dice leave at half as much again.
    val release = Release.of(strokes(Vector3(PUSH, 0.0, 0.0) to HALF_STROKE))

    assertEquals(Vector3(1.0, 0.0, 0.0), release.direction)
    assertEquals(PEAK_MM_PER_SECOND * Release.GAIN, release.speedMmPerSecond, 1e-6)
  }

  @Test
  fun `every way along the floor is a way to throw`() {
    val across = Release.of(strokes(Vector3(0.0, -PUSH, 0.0) to HALF_STROKE))
    val corner = Release.of(strokes(Vector3(PUSH, PUSH, 0.0) to HALF_STROKE))

    assertEquals(Vector3(0.0, -1.0, 0.0), across.direction)
    val diagonal = assertNotNull(corner.direction)
    assertEquals(diagonal.x, diagonal.y, 1e-9)
    assertEquals(1.0, diagonal.length, 1e-9)
  }

  @Test
  fun `the last stroke is the throw, not the one before it going the other way`() {
    val shake =
      strokes(
        Vector3(-PUSH, 0.0, 0.0) to HALF_STROKE,
        Vector3(PUSH, 0.0, 0.0) to HALF_STROKE,
      )

    assertEquals(Vector3(1.0, 0.0, 0.0), Release.of(shake).direction)
  }

  @Test
  fun `a shake straight at the glass throws in no direction of its own`() {
    val release = Release.of(strokes(Vector3(0.0, 0.0, PUSH) to HALF_STROKE))

    assertNull(release.direction)
    assertEquals(Release.LEAST_MM_PER_SECOND, release.speedMmPerSecond, 0.0)
  }

  @Test
  fun `drift is taken out - a hand that never came back to rest has not thrown anything`() {
    // Pushed one way the whole time: as a velocity it only grows, but a phone
    // picked up still and put down still cannot have, so it is drift.
    val drifting =
      List(STEPS) { step ->
        ShakeSample(step, Vector3(GENTLE, 0.0, 0.0), DOWN)
      }

    assertNull(Release.of(drifting).direction)
  }

  @Test
  fun `a gentle stroke is still a throw across the tray, and a wild one is held to a hand's`() {
    val gentle = Release.of(strokes(Vector3(GENTLE, 0.0, 0.0) to HALF_STROKE))
    val wild = Release.of(strokes(Vector3(WILD, 0.0, 0.0) to HALF_STROKE))

    assertEquals(Release.LEAST_MM_PER_SECOND, gentle.speedMmPerSecond, 0.0)
    assertEquals(Release.MOST_MM_PER_SECOND, wild.speedMmPerSecond, 0.0)
  }

  @Test
  fun `moments that arrive out of order are read in order`() {
    val shake = strokes(Vector3(PUSH, 0.0, 0.0) to HALF_STROKE)

    assertEquals(Release.of(shake), Release.of(shake.reversed()))
  }

  @Test
  fun `moments fifty times a second on uneven steps read as the same stroke`() {
    // The sensor's own rate: a sample every two or three steps, each standing
    // for the time until the next.
    val sparse =
      strokes(
        Vector3(PUSH, 0.0, 0.0) to HALF_STROKE,
      ).filter { it.stepIndex % 5 == 0 || it.stepIndex % 5 == 2 }

    val release = Release.of(sparse)

    assertEquals(Vector3(1.0, 0.0, 0.0), release.direction)
    assertEquals(PEAK_MM_PER_SECOND * Release.GAIN, release.speedMmPerSecond, PEAK_MM_PER_SECOND * 0.1)
  }

  @Test
  fun `a direction that is not along the floor is refused`() {
    assertFailsWith<IllegalArgumentException> { Release(direction = Vector3(0.0, 0.0, 1.0), speedMmPerSecond = 1.0) }
  }

  @Test
  fun `a direction that is not a unit is refused`() {
    assertFailsWith<IllegalArgumentException> { Release(direction = Vector3(2.0, 0.0, 0.0), speedMmPerSecond = 1.0) }
  }

  @Test
  fun `a speed below nothing is refused`() {
    assertFailsWith<IllegalArgumentException> { Release(direction = null, speedMmPerSecond = -1.0) }
  }

  /**
   * Strokes one after another: each pushes the hand at its acceleration for
   * its number of steps and then brakes it as hard for as long, so the hand
   * ends every stroke at rest — as a shake does.
   */
  private fun strokes(vararg pushes: Pair<Vector3, Int>): List<ShakeSample> {
    val moments = mutableListOf<ShakeSample>()
    pushes.forEach { (push, steps) ->
      repeat(steps) { moments += ShakeSample(moments.size, push, DOWN) }
      repeat(steps) { moments += ShakeSample(moments.size, -push, DOWN) }
    }
    return moments
  }

  private companion object {
    const val PUSH = 9_000.0
    const val GENTLE = 2_000.0
    const val WILD = 40_000.0
    const val HALF_STROKE = 12
    const val STEPS = 60
    const val PEAK_MM_PER_SECOND = PUSH * HALF_STROKE / 120
    val DOWN = Vector3(0.0, 0.0, -1.0)
  }
}
