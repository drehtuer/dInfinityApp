package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The arithmetic behind the frame-rate line on the debug overlay
 * (`docs/physics-and-rendering.md`, "Debug tooling"; decision 72).
 *
 * The device measures the time between two frame callbacks and nothing else;
 * what that comes to is decided here, where a JVM can check it.
 */
class FrameMeterTest {
  @Test
  fun `a meter that has seen no frame has nothing to say`() {
    assertNull(FrameMeter().reading())
  }

  @Test
  fun `a steady sixtieth of a second reads as sixty frames a second`() {
    val meter = FrameMeter()
    repeat(FrameMeter.WINDOW) { meter.record(SIXTIETH) }

    val rate = requireNotNull(meter.reading())

    assertEquals(60.0, rate.framesPerSecond, 0.01)
    assertEquals(16.667, rate.p99Millis, 0.001)
    assertEquals(FrameMeter.WINDOW, rate.frames)
  }

  @Test
  fun `one hitch in a hundred is not the p99, two are`() {
    // Over 120 frames the nearest-rank p99 is the second-worst frame, so a
    // single 50 ms stall shows in the frame rate and not in the p99 — and a
    // second one does.
    val once = FrameMeter()
    repeat(119) { once.record(SIXTIETH) }
    once.record(FIFTY_MS)
    assertEquals(16.667, requireNotNull(once.reading()).p99Millis, 0.001)

    val twice = FrameMeter()
    repeat(118) { twice.record(SIXTIETH) }
    repeat(2) { twice.record(FIFTY_MS) }
    assertEquals(50.0, requireNotNull(twice.reading()).p99Millis, 0.001)
  }

  @Test
  fun `the window forgets the oldest frames first`() {
    val meter = FrameMeter(window = 2)
    meter.record(FIFTY_MS)
    meter.record(SIXTIETH)
    meter.record(SIXTIETH)

    val rate = requireNotNull(meter.reading())

    assertEquals(2, rate.frames)
    assertEquals(60.0, rate.framesPerSecond, 0.01)
    assertEquals(3L, meter.recorded)
  }

  @Test
  fun `a frame of no time is not a frame and is left out`() {
    val meter = FrameMeter()
    meter.record(0)
    meter.record(-5)

    assertNull(meter.reading())
    assertEquals(0L, meter.recorded)
  }

  @Test
  fun `a reset forgets every frame`() {
    val meter = FrameMeter()
    meter.record(SIXTIETH)
    meter.reset()

    assertNull(meter.reading())
    assertEquals(0L, meter.recorded)
  }

  @Test
  fun `a short run reads over the frames it has`() {
    val meter = FrameMeter()
    meter.record(SIXTIETH)
    meter.record(SIXTIETH * 2)

    val rate = requireNotNull(meter.reading())

    assertEquals(2, rate.frames)
    assertEquals(40.0, rate.framesPerSecond, 0.01)
    assertEquals(33.333, rate.p99Millis, 0.001)
  }

  @Test
  fun `a window of no frames is refused`() {
    assertFailsWith<IllegalArgumentException> { FrameMeter(window = 0) }
  }

  private companion object {
    const val SIXTIETH = 16_666_667L
    const val FIFTY_MS = 50_000_000L
  }
}
