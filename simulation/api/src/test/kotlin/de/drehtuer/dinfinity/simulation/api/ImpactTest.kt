package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.TableSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ImpactTest {
  @Test
  fun `an impact reports when, which die, what it hit and how hard`() {
    val impact = impact(stepIndex = 12, dieIndex = 3, speed = 400.0)
    assertEquals(12, impact.stepIndex)
    assertEquals(3, impact.dieIndex)
    assertEquals(Struck.Floor, impact.struck)
    assertEquals(400.0, impact.speedChangeMmPerSecond, 0.0)
    assertEquals(16.0, impact.dieSizeMm, 0.0)
  }

  @Test
  fun `strength runs from nothing at the quietest to one at the loudest`() {
    assertEquals(0.0, impact(speed = ImpactRule.QUIETEST_MM_PER_SECOND).strength, 1e-9)
    assertEquals(1.0, impact(speed = ImpactRule.LOUDEST_MM_PER_SECOND).strength, 1e-9)
    val middle = (ImpactRule.QUIETEST_MM_PER_SECOND + ImpactRule.LOUDEST_MM_PER_SECOND) / 2
    assertEquals(0.5, impact(speed = middle).strength, 1e-9)
  }

  @Test
  fun `strength cannot leave nought to one, however hard the knock`() {
    assertEquals(1.0, impact(speed = 10_000.0).strength, 0.0)
    assertEquals(0.0, impact(speed = 0.0).strength, 0.0)
  }

  @Test
  fun `an impact refuses a step, a die, a hardness or a size that cannot be`() {
    assertFailsWith<IllegalArgumentException> { impact(stepIndex = -1) }
    assertFailsWith<IllegalArgumentException> { impact(dieIndex = -1) }
    assertFailsWith<IllegalArgumentException> { impact(speed = -1.0) }
    assertFailsWith<IllegalArgumentException> { impact(size = 0.0) }
  }

  @Test
  fun `the record is capped, and the cap is a number rather than a hope`() {
    assertTrue(Impact.MAX_RECORDED > SettleRule.HARD_CAP_STEPS)
  }

  @Test
  fun `gravity alone is never an impact, however hard down is pulling`() {
    // A die in free fall gains exactly g per second, so one step of it gains
    // g/120 — under any gravity, plain or shaken.
    listOf(9_806.65, 20_000.0, 49_806.65).forEach { gravity ->
      val gained = gravity * SettleRule.TIMESTEP_SECONDS
      assertFalse(
        ImpactRule.isImpact(ImpactRule.unexplained(0.0, gained, gravity)),
        "$gravity mm/s^2 of free fall read as an impact",
      )
    }
  }

  @Test
  fun `a die sliding at a steady speed is not an impact`() {
    assertFalse(ImpactRule.isImpact(ImpactRule.unexplained(300.0, 300.0, GRAVITY)))
  }

  @Test
  fun `a die at rest is not an impact`() {
    assertFalse(ImpactRule.isImpact(ImpactRule.unexplained(0.0, 0.0, GRAVITY)))
  }

  @Test
  fun `friction slowing a sliding die is not an impact either`() {
    // Friction can take at most the whole of gravity's worth out of a die in
    // one step, which is what the allowance covers from the other side.
    val lost = GRAVITY * SettleRule.TIMESTEP_SECONDS
    assertFalse(ImpactRule.isImpact(ImpactRule.unexplained(500.0, 500.0 - lost, GRAVITY)))
  }

  @Test
  fun `a die bouncing off the floor is an impact`() {
    // 1,200 mm/s down, restitution 0.3, so it leaves at 360: a loss of 840,
    // which no amount of gravity in one step explains.
    val change = ImpactRule.unexplained(1_200.0, 360.0, GRAVITY)
    assertTrue(ImpactRule.isImpact(change))
    // Solidly in the middle of the range, which is what a landing should be:
    // the top of it is reserved for a die fired into a wall by a shake.
    assertTrue(ImpactRule.strengthOf(change) > 0.3)
    assertTrue(ImpactRule.strengthOf(change) < 0.6)
  }

  @Test
  fun `a shake raises the bar rather than filling the record with itself`() {
    val shaken = 5 * GRAVITY
    val gentle = ImpactRule.unexplained(0.0, 400.0, shaken)
    assertFalse(ImpactRule.isImpact(gentle), "a gentle knock under a hard shake is not worth reporting")
    assertTrue(ImpactRule.isImpact(ImpactRule.unexplained(1_500.0, 0.0, shaken)), "a slam under a hard shake still is")
  }

  @Test
  fun `speeding up counts as much as slowing down`() {
    val up = ImpactRule.unexplained(0.0, 900.0, GRAVITY)
    val down = ImpactRule.unexplained(900.0, 0.0, GRAVITY)
    assertEquals(up, down, 1e-9)
  }

  @Test
  fun `what a die hit is read off the contacts it has`() {
    assertEquals(Struck.Floor, ImpactRule.struckBy(touchingFloor = true, touchingWall = false, supportedByDie = false))
    assertEquals(Struck.Wall, ImpactRule.struckBy(touchingFloor = false, touchingWall = true, supportedByDie = false))
    assertEquals(Struck.Die, ImpactRule.struckBy(touchingFloor = false, touchingWall = false, supportedByDie = true))
  }

  @Test
  fun `a die touching nothing the tray owns hit another die`() {
    assertEquals(Struck.Die, ImpactRule.struckBy(touchingFloor = false, touchingWall = false, supportedByDie = false))
  }

  @Test
  fun `a die on the floor and against a wall is a wall`() {
    assertEquals(Struck.Wall, ImpactRule.struckBy(touchingFloor = true, touchingWall = true, supportedByDie = false))
  }

  @Test
  fun `standing on another die beats every other contact`() {
    assertEquals(Struck.Die, ImpactRule.struckBy(touchingFloor = true, touchingWall = true, supportedByDie = true))
  }

  @Test
  fun `two impacts that differ anywhere are two impacts`() {
    assertNotEquals(impact(stepIndex = 1), impact(stepIndex = 2))
    assertEquals(impact(), impact())
  }

  @Test
  fun `the player that plays nothing takes everything and does nothing`() {
    Impacts.NONE.on(TableSound.Wood)
    Impacts.NONE.play(listOf(impact()), Impacts.REPLAY_SECONDS)
  }

  @Test
  fun `the replay is about a second, which is what the design asks for`() {
    assertEquals(1.0, Impacts.REPLAY_SECONDS, 0.0)
  }

  private fun impact(
    stepIndex: Int = 4,
    dieIndex: Int = 0,
    struck: Struck = Struck.Floor,
    speed: Double = 500.0,
    size: Double = 16.0,
  ): Impact =
    Impact(
      stepIndex = stepIndex,
      dieIndex = dieIndex,
      struck = struck,
      speedChangeMmPerSecond = speed,
      dieSizeMm = size,
    )

  private companion object {
    const val GRAVITY = 9_806.65
  }
}
