package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettleRuleTest {
  @Test
  fun `the published thresholds are these`() {
    assertEquals(1.0 / 120.0, SettleRule.TIMESTEP_SECONDS)
    assertEquals(10.0, SettleRule.REST_SPEED_MM_PER_SECOND)
    assertEquals(0.05, SettleRule.REST_SPIN_RADIANS_PER_SECOND)
    assertEquals(0.250, SettleRule.REST_DURATION_SECONDS)
    assertEquals(12.0, SettleRule.HARD_CAP_SECONDS)
  }

  @Test
  fun `the step counts are the thresholds in steps, exactly`() {
    assertEquals(120, SettleRule.STEPS_PER_SECOND)
    assertEquals(30, SettleRule.REST_STEPS)
    assertEquals(1_440, SettleRule.HARD_CAP_STEPS)
    assertEquals((SettleRule.REST_DURATION_SECONDS * SettleRule.STEPS_PER_SECOND).toInt(), SettleRule.REST_STEPS)
    assertEquals((SettleRule.HARD_CAP_SECONDS * SettleRule.STEPS_PER_SECOND).toInt(), SettleRule.HARD_CAP_STEPS)
  }

  @Test
  fun `a die has to be both slow and barely turning to count as still`() {
    assertTrue(SettleRule.isStill(DieMotion(5.0, 0.01)))
    assertFalse(SettleRule.isStill(DieMotion(50.0, 0.01)), "still moving across the tray")
    assertFalse(SettleRule.isStill(DieMotion(5.0, 1.0)), "still spinning")
  }

  @Test
  fun `a die is watched while it is slowing, and not before or after`() {
    assertTrue(SettleRule.isSettling(DieMotion(50.0, 1.0)), "slowing but not stopped")
    assertFalse(SettleRule.isSettling(DieMotion(500.0, 20.0)), "still flying")
    assertFalse(SettleRule.isSettling(DieMotion.Stopped), "already stopped")
  }

  @Test
  fun `a die must keep still for a quarter of a second before it is read`() {
    val tracker = RestTracker(1)
    repeat(SettleRule.REST_STEPS - 1) { tracker.step(listOf(DieMotion.Stopped)) }
    assertFalse(tracker.isAtRest(0))
    tracker.step(listOf(DieMotion.Stopped))
    assertTrue(tracker.isAtRest(0))
  }

  @Test
  fun `a die thrown again forgets how still it was being`() {
    // Rung 3 puts a die back in the air. Without this it would still be
    // carrying the stillness of the die it used to be and the roll would stop
    // with it mid-flight (`docs/physics-and-rendering.md`).
    val tracker = RestTracker(2)
    repeat(SettleRule.REST_STEPS) { tracker.step(listOf(DieMotion.Stopped, DieMotion.Stopped)) }
    assertTrue(tracker.isAtRest(0))

    tracker.rethrown(0)

    assertFalse(tracker.isAtRest(0), "a die in the air is not at rest")
    assertTrue(tracker.isAtRest(1), "and the die beside it was not touched")
  }

  @Test
  fun `a die thrown again has to settle all over again`() {
    val tracker = RestTracker(1)
    repeat(SettleRule.REST_STEPS) { tracker.step(listOf(DieMotion.Stopped)) }
    tracker.rethrown(0)

    repeat(SettleRule.REST_STEPS - 1) { tracker.step(listOf(DieMotion.Stopped)) }
    assertFalse(tracker.isAtRest(0))
    tracker.step(listOf(DieMotion.Stopped))
    assertTrue(tracker.isAtRest(0), "the same quarter of a second as any other die")
  }

  @Test
  fun `a die that twitches starts counting again`() {
    val tracker = RestTracker(1)
    repeat(SettleRule.REST_STEPS - 1) { tracker.step(listOf(DieMotion.Stopped)) }
    tracker.step(listOf(DieMotion(100.0, 5.0)))
    repeat(SettleRule.REST_STEPS - 1) { tracker.step(listOf(DieMotion.Stopped)) }
    assertFalse(tracker.isAtRest(0), "a die that moved again is not at rest")
    tracker.step(listOf(DieMotion.Stopped))
    assertTrue(tracker.isAtRest(0))
  }

  @Test
  fun `a roll is over when every die is at rest, and not before`() {
    val tracker = RestTracker(2)
    repeat(SettleRule.REST_STEPS) { tracker.step(listOf(DieMotion.Stopped, DieMotion(200.0, 9.0))) }
    assertFalse(tracker.finished())
    repeat(SettleRule.REST_STEPS) { tracker.step(listOf(DieMotion.Stopped, DieMotion.Stopped)) }
    assertTrue(tracker.finished())
  }

  @Test
  fun `a roll that has run too long says so, and does not call itself finished`() {
    // It used to. Every die still moving was force-settled and read off the
    // face it was nearest, which is a made-up answer to a throw that never
    // ended. The roll is not over while a die is still going — what running
    // too long means now is that nobody watching it should keep waiting
    // (`SettleRule.HARD_CAP_SECONDS`).
    val tracker = RestTracker(2)
    repeat(SettleRule.HARD_CAP_STEPS) { tracker.step(listOf(DieMotion.Stopped, DieMotion(200.0, 9.0))) }
    assertTrue(tracker.outOfTime())
    assertFalse(tracker.finished(), "a roll with a die still moving called itself finished")
    assertEquals(listOf(1), tracker.stillMoving())
  }

  @Test
  fun `a roll that settles cleanly never runs out of time`() {
    val tracker = RestTracker(1)
    repeat(SettleRule.REST_STEPS) { tracker.step(listOf(DieMotion.Stopped)) }
    assertTrue(tracker.finished())
    assertFalse(tracker.outOfTime())
    assertEquals(SettleRule.REST_STEPS, tracker.stepsTaken)
  }

  @Test
  fun `the rest timer is readable, which is what the debug overlay draws`() {
    // Reading it cannot change it, which is the whole of why an overlay is
    // allowed to exist (`docs/physics-and-rendering.md`, "Debug tooling").
    val tracker = RestTracker(2)

    repeat(HALF_A_REST) { tracker.step(listOf(DieMotion.Stopped, DieMotion(500.0, 9.0))) }

    assertEquals(HALF_A_REST, tracker.stillSteps(0))
    assertEquals(0, tracker.stillSteps(1))
    assertFalse(tracker.isAtRest(0))
  }

  @Test
  fun `a die thrown again has its rest timer put back to nothing`() {
    val tracker = RestTracker(1)
    repeat(HALF_A_REST) { tracker.step(listOf(DieMotion.Stopped)) }

    tracker.rethrown(0)

    assertEquals(0, tracker.stillSteps(0))
  }

  @Test
  fun `a step for the wrong number of dice is a bug`() {
    assertFailsWith<IllegalArgumentException> { RestTracker(2).step(listOf(DieMotion.Stopped)) }
  }

  private companion object {
    /** Half of what a die needs to be at rest — a timer mid-fill. */
    const val HALF_A_REST = SettleRule.REST_STEPS / 2
  }
}
