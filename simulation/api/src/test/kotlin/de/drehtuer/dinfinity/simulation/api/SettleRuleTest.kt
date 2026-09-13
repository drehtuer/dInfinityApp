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
  fun `the cap fires at twelve seconds, and says which dice were still going`() {
    val tracker = RestTracker(2)
    repeat(SettleRule.HARD_CAP_STEPS) { tracker.step(listOf(DieMotion.Stopped, DieMotion(200.0, 9.0))) }
    assertTrue(tracker.capReached())
    assertTrue(tracker.finished())
    assertEquals(listOf(1), tracker.stillMoving())
  }

  @Test
  fun `a roll that settles cleanly never reaches the cap`() {
    val tracker = RestTracker(1)
    repeat(SettleRule.REST_STEPS) { tracker.step(listOf(DieMotion.Stopped)) }
    assertTrue(tracker.finished())
    assertFalse(tracker.capReached())
    assertEquals(SettleRule.REST_STEPS, tracker.stepsTaken)
  }

  @Test
  fun `a step for the wrong number of dice is a bug`() {
    assertFailsWith<IllegalArgumentException> { RestTracker(2).step(listOf(DieMotion.Stopped)) }
  }
}
