package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TumbleTest {
  @Test
  fun `a die that never touches anything has not tumbled, however it spins`() {
    val tumble = Tumble(1)
    repeat(TURNS_TO_TRY) { step ->
      tumble.step(0, about(step * QUARTER), touching = false)
    }
    assertEquals(0.0, tumble.turnsOf(0), TOLERANCE)
  }

  @Test
  fun `turning is counted only once the die is on the table`() {
    val tumble = Tumble(1)
    tumble.step(0, about(0.0), touching = false)
    tumble.step(0, about(QUARTER), touching = false)
    assertEquals(0.0, tumble.turnsOf(0), TOLERANCE)

    tumble.step(0, about(QUARTER), touching = true)
    tumble.step(0, about(2 * QUARTER), touching = true)
    assertEquals(0.25, tumble.turnsOf(0), TOLERANCE)
  }

  @Test
  fun `a whole turn on the table is one turn`() {
    val tumble = Tumble(1)
    tumble.step(0, about(0.0), touching = true)
    repeat(QUARTERS_IN_A_TURN) { quarter ->
      tumble.step(0, about((quarter + 1) * QUARTER), touching = true)
    }
    assertEquals(1.0, tumble.turnsOf(0), TOLERANCE)
  }

  @Test
  fun `a die that bounces back into the air is still rolling`() {
    val tumble = Tumble(1)
    tumble.step(0, about(0.0), touching = true)
    tumble.step(0, about(QUARTER), touching = false)
    assertEquals(0.25, tumble.turnsOf(0), TOLERANCE)
  }

  @Test
  fun `a die that lands flat and stays put has tumbled not at all`() {
    val tumble = Tumble(1)
    val still = about(0.0)
    repeat(TURNS_TO_TRY) { tumble.step(0, still, touching = true) }
    assertEquals(0.0, tumble.turnsOf(0), TOLERANCE)
  }

  @Test
  fun `a counted die stops counting, so a slow neighbour cannot dilute it`() {
    val tumble = Tumble(1)
    tumble.step(0, about(0.0), touching = true)
    tumble.step(0, about(QUARTER), touching = true)
    tumble.settled(0)
    repeat(TURNS_TO_TRY) { tumble.step(0, about(2 * QUARTER), touching = true) }
    assertEquals(0.25, tumble.turnsOf(0), TOLERANCE)
  }

  @Test
  fun `a die thrown again starts again`() {
    val tumble = Tumble(1)
    tumble.step(0, about(0.0), touching = true)
    tumble.step(0, about(2 * QUARTER), touching = true)
    tumble.rethrown(0)
    tumble.step(0, about(0.0), touching = true)
    tumble.step(0, about(QUARTER), touching = true)
    assertEquals(0.25, tumble.turnsOf(0), TOLERANCE)
  }

  @Test
  fun `the median is the middle die, not the one that skittered`() {
    val tumble = Tumble(THREE_DICE)
    // A die dropped dead, a die that toppled a quarter, and a die that rolled
    // the length of the tray. The middle one is what the throw was like.
    turn(tumble, index = 0, quarters = 0)
    turn(tumble, index = 1, quarters = 1)
    turn(tumble, index = 2, quarters = QUARTERS_IN_A_TURN * 2)
    assertEquals(0.25, tumble.medianTurns, TOLERANCE)
  }

  @Test
  fun `an even number of dice takes the middle of the two`() {
    val tumble = Tumble(2)
    turn(tumble, index = 0, quarters = 0)
    turn(tumble, index = 1, quarters = 2)
    assertEquals(0.25, tumble.medianTurns, TOLERANCE)
  }

  /**
   * Turns one die through [quarters] quarter-turns, a quarter at a time.
   *
   * A step at a time, because [Tumble.angleBetween] takes the short way round
   * and so can never see more than half a turn between two readings — which is
   * right for 120 Hz physics and a trap for a test that tries to take a
   * shortcut.
   */
  private fun turn(
    tumble: Tumble,
    index: Int,
    quarters: Int,
  ) {
    tumble.step(index, about(0.0), touching = true)
    repeat(quarters) { quarter -> tumble.step(index, about((quarter + 1) * QUARTER), touching = true) }
  }

  @Test
  fun `no dice have turned nothing rather than dividing by none`() {
    assertEquals(0.0, Tumble(0).medianTurns, TOLERANCE)
  }

  @Test
  fun `a die index nobody threw is ignored rather than thrown at`() {
    val tumble = Tumble(1)
    tumble.step(OFF_THE_END, about(QUARTER), touching = true)
    tumble.settled(OFF_THE_END)
    tumble.rethrown(OFF_THE_END)
    assertEquals(0.0, tumble.turnsOf(OFF_THE_END), TOLERANCE)
  }

  @Test
  fun `the same rotation written the other way round is no turn at all`() {
    val turn = about(QUARTER)
    val theOtherWayRound = Quaternion(-turn.w, -turn.x, -turn.y, -turn.z)
    assertEquals(0.0, Tumble.angleBetween(turn, theOtherWayRound), TOLERANCE)
  }

  @Test
  fun `a half turn is half a turn, whichever way it is written`() {
    assertTrue(Tumble.angleBetween(about(0.0), about(PI)) > PI - TOLERANCE)
  }

  private fun about(radians: Double): Quaternion = Quaternion.about(Vector3(0.0, 0.0, 1.0), radians)

  private companion object {
    const val TOLERANCE: Double = 1e-9
    const val QUARTER: Double = PI / 2.0
    const val QUARTERS_IN_A_TURN: Int = 4
    const val TURNS_TO_TRY: Int = 8
    const val THREE_DICE: Int = 3
    const val OFF_THE_END: Int = 7
  }
}
