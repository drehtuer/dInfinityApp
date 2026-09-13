package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.DieMotion
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * The correction ladder, tested where it is decided rather than where it is
 * carried out (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked
 * dice").
 *
 * Every rule here is one a device run can only sample. A phone can show that
 * ten thousand rolls contained no post-rest correction; it cannot show that
 * none is possible. These can, because the gate is in Kotlin and the JVM can
 * hand the loop a die that has stopped dead in the worst state a die can be in
 * and watch nothing happen to it.
 */
class RollLoopTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  /** A cube turned 45° about x: no face within 15° of up, so it reads cocked. */
  private val cocked = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 4)

  @Test
  fun `a throw of dice that simply settle is read off their faces`() {
    val world = FakeWorld(2) { _, _, _ -> FakeWorld.settled() }
    val outcome = loop(listOf(StandardDice.d6, StandardDice.d20), world).run()

    assertEquals(2, outcome.faces.size)
    assertEquals(0, outcome.corrections)
    assertEquals(0, outcome.rethrows)
    assertTrue("a roll that just settled has nothing to report", outcome.clean)
    // A die is at rest once it has kept still for the documented time, not the
    // moment it stops moving.
    assertEquals(SettleRule.REST_STEPS.toLong(), outcome.steps.toLong())
  }

  @Test
  fun `nothing touches a die that has come to rest, however wrong it looks`() {
    // Stopped dead and standing on another die: as much trouble as a die can
    // be in, and out of reach for exactly that reason.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertTrue("a settled die was biased", world.biases.isEmpty())
    assertEquals(0, outcome.corrections)
    assertEquals(0, outcome.postRestCorrections)
  }

  @Test
  fun `a die settling onto another is biased once, while it still has speed`() {
    val world = FakeWorld(1, inTroubleFor(TROUBLE_STEPS))
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals("one die needed correcting, so the count is one", 1, outcome.corrections)
    assertEquals("and it was one nudge, not a hand on the die", 1, world.biases.size)
    assertEquals("a bias that worked needs no re-throw", 0, outcome.rethrows)
    assertEquals(0, outcome.postRestCorrections)
  }

  @Test
  fun `a die merely tumbling through an awkward angle is left alone`() {
    // In trouble, but only for a moment — which is what a tumbling die looks
    // like from one step to the next, and is not what rung 2 is for.
    val world = FakeWorld(1, inTroubleFor(RollLoop.TROUBLE_STEPS_BEFORE_BIAS - 1))
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertTrue("a passing angle is not trouble", world.biases.isEmpty())
    assertEquals(0, outcome.corrections)
  }

  @Test
  fun `a die in trouble but already too slow to touch is left alone`() {
    // Below the resting speed but not yet counted as at rest. The watching
    // window has closed even though the tracker is still counting, so this die
    // is past helping and can only be thrown again.
    val crawling =
      FakeWorld.settled(supportedByDie = true).copy(
        motion = DieMotion(speedMmPerSecond = 1.0, spinRadiansPerSecond = 0.001),
      )
    val world = FakeWorld(1) { _, _, _ -> crawling }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertTrue(world.biases.isEmpty())
    assertTrue("what a bias may not fix, a re-throw must", outcome.rethrows > 0)
  }

  @Test
  fun `a die that stops cocked is thrown again, not nudged onto a face`() {
    val world =
      FakeWorld(1) { _, _, rethrows ->
        if (rethrows == 0) FakeWorld.settled(cocked) else FakeWorld.settled()
      }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(1, outcome.rethrows)
    assertEquals(listOf(0), world.respawns.map { it.second })
    assertTrue("a settled die is never biased, cocked or not", world.biases.isEmpty())
    assertEquals("the re-thrown die reads its new face", 0, outcome.faces.getValue(0))
    assertTrue(outcome.clean)
  }

  @Test
  fun `a die resting on another is thrown again even though it reads fine`() {
    val world = FakeWorld(1) { _, _, rethrows -> FakeWorld.settled(supportedByDie = rethrows == 0) }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(
      "a stacked die has no table under it, whatever face is up",
      1,
      outcome.rethrows,
    )
  }

  @Test
  fun `a die that will not come good is given up on rather than thrown for ever`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(cocked) }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(RollLoop.MAX_RETHROWS, outcome.rethrows)
    assertEquals("giving up is an anomaly, and is counted as one", 1, outcome.forcedSettles)
    assertFalse(outcome.clean)
    assertTrue("a roll still has to answer", outcome.faces.containsKey(0))
  }

  @Test
  fun `a roll that never settles is stopped by the cap and says so`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.tumbling() }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(SettleRule.HARD_CAP_STEPS, outcome.steps)
    assertEquals(1, outcome.forcedSettles)
    assertFalse(outcome.clean)
  }

  @Test
  fun `the same seed biases the same die the same way, and another seed does not`() {
    val trouble = inTroubleFor(TROUBLE_STEPS)

    val first = FakeWorld(1, trouble).also { loop(listOf(StandardDice.d6), it, seed = 7L).run() }
    val again = FakeWorld(1, trouble).also { loop(listOf(StandardDice.d6), it, seed = 7L).run() }
    val other = FakeWorld(1, trouble).also { loop(listOf(StandardDice.d6), it, seed = 8L).run() }

    assertEquals("a roll has to replay to itself", first.biasVelocities, again.biasVelocities)
    assertNotEquals(
      "two seeds that correct identically are one seed",
      first.biasVelocities,
      other.biasVelocities,
    )
  }

  @Test
  fun `the shake drives every step, and pushes the dice the way the phone did not`() {
    val shake =
      List(SettleRule.REST_STEPS) {
        ShakeSample(
          stepIndex = it,
          accelerationMmPerSecond2 = Vector3(2_000.0, 0.0, 0.0),
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    loop(listOf(StandardDice.d6), world, shake = shake).run()

    assertEquals("gravity is set once per step, always", world.steps, world.gravities.size)
    assertTrue(
      "a phone pulled one way has to load the dice the other",
      world.gravities.first().x < 0.0,
    )
  }

  @Test
  fun `a throw with no dice in it is not a roll`() {
    val world = FakeWorld(0) { _, _, _ -> FakeWorld.settled() }
    val outcome = loop(emptyList(), world).run()

    assertEquals(0, outcome.diceCount)
    assertEquals(0, world.steps)
  }

  /** A die stuck on another for [steps] steps, then down and clean. */
  private fun inTroubleFor(steps: Int): FakeWorld.States =
    FakeWorld.States { step, _, rethrows ->
      if (rethrows == 0 && step < steps) {
        FakeWorld.settling(supportedByDie = true)
      } else {
        FakeWorld.settled()
      }
    }

  @Test
  fun `a roll does not end while the phone is still being shaken`() {
    // Dice that look still while the hand is still going are not a roll that is
    // over — they are a roll caught at the top of a swing. Before this, the
    // shake was the signal to tumble and nothing more: the dice settled under
    // it and every later moment of the shake was dropped.
    val shaking = List(SHAKE_STEPS) { ShakeSample(it, Vector3(6_000.0, 0.0, 0.0), DOWN) }
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }

    val outcome = loop(listOf(StandardDice.d6), world, shake = shaking).run()

    assertTrue(
      "the roll ended after ${outcome.steps} steps, with the hand still shaking at $SHAKE_STEPS",
      outcome.steps > SHAKE_STEPS,
    )
  }

  @Test
  fun `a tapped roll still ends the moment its dice are at rest`() {
    // The rule above must not cost a throw that nobody is shaking a single
    // step: there is no hand to wait for.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }

    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(SettleRule.REST_STEPS.toLong(), outcome.steps.toLong())
  }

  private fun loop(
    dice: List<Die>,
    world: FakeWorld,
    shake: List<ShakeSample> = emptyList(),
    seed: Long = 42L,
  ): RollLoop {
    val spec = spec(dice, seed).copy(shake = shake)
    return RollLoop(
      spec = spec,
      world = world,
      layout = SpawnLayout(geometry, RADIUS_MM, spec.seed),
      shake = ShakeDriver(spec.shake),
    )
  }

  private fun spec(
    dice: List<Die>,
    seed: Long,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(
            index = index,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = die,
          )
        },
      geometry = geometry,
      table = table,
      seed = seed,
    )

  private companion object {
    const val RADIUS_MM = 8.0

    /** A shake that outlasts the settle rule several times over. */
    const val SHAKE_STEPS = 200

    /** Straight down, as the gyroscope reports it: a direction, not a magnitude. */
    val DOWN = Vector3(0.0, 0.0, -1.0)
    const val TROUBLE_STEPS = 12
  }
}
