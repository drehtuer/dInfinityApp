package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the debug overlay is handed, and what it can say about it
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * All of it is arithmetic over numbers a roll already keeps, so all of it is
 * tested here rather than by looking at a phone.
 */
class RollDiagnosticsTest {
  @Test
  fun `a tray with nothing on it reports nothing, and reports it as clean`() {
    val nothing = RollDiagnostics.NONE

    assertEquals(0, nothing.steps)
    assertEquals(0, nothing.diceCount)
    assertEquals(0, nothing.atRest)
    assertTrue(nothing.contacts.isEmpty())
    // Clean rather than unknown: no roll has gone wrong, which is the answer
    // the overlay wants before the first throw.
    assertTrue(nothing.clean)
    assertEquals(0.0, nothing.throughTheCap, EPSILON)
  }

  @Test
  fun `how many dice have stopped is counted rather than carried`() {
    val diagnostics = RollDiagnostics(dice = listOf(die(0, stillFor = 30), die(1, stillFor = 0)))

    assertEquals(2, diagnostics.diceCount)
    assertEquals(1, diagnostics.atRest)
  }

  @Test
  fun `a forced settle or a post-rest correction is not clean`() {
    assertFalse(RollDiagnostics(forcedSettles = 1).clean)
    assertFalse(RollDiagnostics(postRestCorrections = 1).clean)
    assertTrue(RollDiagnostics(corrections = 9, rethrows = 3).clean)
  }

  @Test
  fun `how far through the cap a roll is never leaves nought to one`() {
    assertEquals(0.5, RollDiagnostics(steps = SettleRule.HARD_CAP_STEPS / 2).throughTheCap, EPSILON)
    assertEquals(1.0, RollDiagnostics(steps = SettleRule.HARD_CAP_STEPS).throughTheCap, EPSILON)
    // A roll cannot run past the cap, but a snapshot taken of one that somehow
    // did should still draw a full bar rather than an overflowing one.
    assertEquals(1.0, RollDiagnostics(steps = SettleRule.HARD_CAP_STEPS * 2).throughTheCap, EPSILON)
  }

  @Test
  fun `a die's rest timer is a fraction of the quarter-second, and stops at one`() {
    assertEquals(0.0, die(0, stillFor = 0).restProgress, EPSILON)
    assertEquals(0.5, die(0, stillFor = SettleRule.REST_STEPS / 2).restProgress, EPSILON)
    assertEquals(1.0, die(0, stillFor = SettleRule.REST_STEPS).restProgress, EPSILON)
    assertEquals(1.0, die(0, stillFor = SettleRule.REST_STEPS * 3).restProgress, EPSILON)
  }

  @Test
  fun `a die standing on another is the trouble the overlay colours differently`() {
    assertTrue(die(0, stillFor = 0, supportedByDie = true).stacked)
    assertFalse(die(0, stillFor = 0).stacked)
  }

  @Test
  fun `a die that could not exist is refused rather than drawn`() {
    assertThrows { DieDiagnostic(index = -1, position = SOMEWHERE, acrossMm = 16.0, stillForSteps = 0, atRest = false) }
    assertThrows { DieDiagnostic(index = 0, position = SOMEWHERE, acrossMm = 0.0, stillForSteps = 0, atRest = false) }
    assertThrows { DieDiagnostic(index = 0, position = SOMEWHERE, acrossMm = 16.0, stillForSteps = -1, atRest = false) }
  }

  @Test
  fun `a contact that could not have happened is refused rather than drawn`() {
    assertThrows { contact(stepIndex = -1) }
    assertThrows { contact(dieIndex = -1) }
    assertThrows { contact(strength = 1.5) }
    // And the honest one is accepted.
    assertEquals(Struck.Die, contact().struck)
  }

  @Test
  fun `a snapshot carries every number the overlay draws`() {
    // Read one by one, because each is a row or a mark on the panel: a field
    // nothing reads is a field nothing draws.
    val diagnostics =
      RollDiagnostics(
        steps = 11,
        dice = listOf(die(0, stillFor = 3, supportedByDie = true)),
        corrections = 4,
        rethrows = 2,
        forcedSettles = 1,
        postRestCorrections = 1,
        contacts = listOf(contact(stepIndex = 9, dieIndex = 0, strength = 0.25)),
      )

    assertEquals(11, diagnostics.steps)
    assertEquals(4, diagnostics.corrections)
    assertEquals(2, diagnostics.rethrows)
    assertEquals(1, diagnostics.forcedSettles)
    assertEquals(1, diagnostics.postRestCorrections)
    assertFalse(diagnostics.clean)

    val die = diagnostics.dice.single()
    assertEquals(0, die.index)
    assertEquals(SOMEWHERE, die.position)
    assertEquals(16.0, die.acrossMm, EPSILON)
    assertEquals(3, die.stillForSteps)
    assertFalse(die.atRest)
    assertFalse(die.touchingFloor)
    assertFalse(die.touchingWall)
    assertTrue(die.supportedByDie)
    assertFalse(die.corrected)
    assertEquals(0, die.rethrows)

    val hit = diagnostics.contacts.single()
    assertEquals(9, hit.stepIndex)
    assertEquals(0, hit.dieIndex)
    assertEquals(SOMEWHERE, hit.position)
    assertEquals(Struck.Die, hit.struck)
    assertEquals(0.25, hit.strength, EPSILON)
  }

  @Test
  fun `a die that was helped says so, which is what the overlay counts`() {
    val helped =
      DieDiagnostic(
        index = 1,
        position = SOMEWHERE,
        acrossMm = 16.0,
        stillForSteps = 0,
        atRest = false,
        touchingFloor = true,
        touchingWall = true,
        corrected = true,
        rethrows = 2,
      )

    assertTrue(helped.corrected)
    assertTrue(helped.touchingFloor)
    assertTrue(helped.touchingWall)
    assertEquals(2, helped.rethrows)
    assertEquals(1, helped.index)
  }

  @Test
  fun `nothing is watching until something is, and nothing is built for it`() {
    // The gate the tray asks before it builds a snapshot. With it false a roll
    // walks no dice per frame, which is what makes the toggle free when off.
    assertFalse(DebugWatch.NONE.watching)
    // And it still takes a snapshot without complaint, because a caller that
    // ignored `watching` must not crash.
    DebugWatch.NONE.saw(RollDiagnostics(steps = 1))

    val seen = mutableListOf<RollDiagnostics>()
    val watch = DebugWatch { seen += it }
    assertTrue(watch.watching)
    watch.saw(RollDiagnostics(steps = 7))
    assertEquals(listOf(7), seen.map(RollDiagnostics::steps))
  }

  private fun die(
    index: Int,
    stillFor: Int,
    supportedByDie: Boolean = false,
  ): DieDiagnostic =
    DieDiagnostic(
      index = index,
      position = SOMEWHERE,
      acrossMm = 16.0,
      stillForSteps = stillFor,
      atRest = stillFor >= SettleRule.REST_STEPS,
      supportedByDie = supportedByDie,
    )

  private fun contact(
    stepIndex: Int = 0,
    dieIndex: Int = 0,
    strength: Double = 0.5,
  ): ContactPoint =
    ContactPoint(
      stepIndex = stepIndex,
      dieIndex = dieIndex,
      position = SOMEWHERE,
      struck = Struck.Die,
      strength = strength,
    )

  private fun assertThrows(block: () -> Unit) {
    runCatching(block).fold(
      onSuccess = { throw AssertionError("expected this to be refused") },
      onFailure = { assertTrue(it is IllegalArgumentException) },
    )
  }

  private companion object {
    val SOMEWHERE = Vector3(1.0, 2.0, 3.0)
    const val EPSILON = 1e-9
  }
}
