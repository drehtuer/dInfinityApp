package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * What the debug overlay reads off a roll in progress, and — the point of the
 * file — that reading it changes nothing
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * The overlay is a view, like the renderer, and `docs/architecture.md`'s
 * decision 38 says a view cannot act on the simulation it is watching. That is
 * a claim a device can only sample and this can settle: the same seed, stepped
 * the same way, with a snapshot taken on every single step and with none taken
 * at all.
 */
class RollDiagnosticsTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  /** A cube turned 45° about x: no face within 15° of up, so it reads cocked. */
  private val cocked = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 4)

  @Test
  fun `the same seed comes to the same roll with the overlay watching and without`() {
    // Every step, on the watched run: the worst case for anything that could
    // have been recorded, allocated or consumed as a side effect of looking.
    val watched = run(watching = true)
    val unwatched = run(watching = false)

    assertEquals(unwatched.faces, watched.faces)
    assertEquals(unwatched.steps, watched.steps)
    assertEquals(unwatched.corrections, watched.corrections)
    assertEquals(unwatched.rethrows, watched.rethrows)
    assertEquals(unwatched.forcedSettles, watched.forcedSettles)
    assertEquals(unwatched.postRestCorrections, watched.postRestCorrections)
  }

  @Test
  fun `the overlay changes nothing the world was asked to do either`() {
    // Not only the same answer but the same working: the same biases, in the
    // same order, on the same steps. An overlay that drew a different roll
    // identically would pass the test above and fail this one.
    val watching = FakeWorld(2) { step, index, _ -> troubled(step, index) }
    val not = FakeWorld(2) { step, index, _ -> troubled(step, index) }

    loop(watching).let { roll ->
      while (roll.advance()) {
        roll.diagnostics()
      }
    }
    loop(not).let { roll ->
      @Suppress("ControlFlowWithEmptyBody")
      while (roll.advance()) {
        // Nothing looks at this one, which is the whole point.
      }
    }

    assertEquals(not.biases, watching.biases)
    assertEquals(not.biasVelocities, watching.biasVelocities)
    assertEquals(not.respawns, watching.respawns)
    assertEquals(not.steps, watching.steps)
  }

  @Test
  fun `a snapshot reports every die, where it is and how long it has been still`() {
    val world = FakeWorld(2) { _, _, _ -> FakeWorld.settled() }
    val roll = loop(world)

    repeat(SETTLING_STEPS) { roll.advance() }
    val seen = roll.diagnostics()

    assertEquals(2, seen.diceCount)
    assertEquals(SETTLING_STEPS, seen.steps)
    assertEquals(listOf(0, 1), seen.dice.map { it.index })
    // The rest timer is what the overlay fills the footprint with: ten steps of
    // stillness out of the thirty a die needs.
    assertEquals(SETTLING_STEPS, seen.dice.first().stillForSteps)
    assertFalse("ten steps is not a quarter of a second", seen.dice.first().atRest)
    // The die's size at the scale it was thrown, which is the footprint drawn.
    assertEquals(StandardDice.d6.material.sizeMm, seen.dice.first().acrossMm, EPSILON)
    assertTrue(seen.clean)
  }

  @Test
  fun `a snapshot reports what each die is touching, which is why the bridge carries it`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }
    val roll = loop(world, dice = listOf(StandardDice.d6))

    repeat(SETTLING_STEPS) { roll.advance() }
    val die = roll.diagnostics().dice.single()

    assertTrue("a stacked die is the one the overlay has to show", die.stacked)
    assertTrue(die.touchingFloor)
    assertFalse(die.touchingWall)
  }

  @Test
  fun `a snapshot counts the corrections and the re-throws as they happen`() {
    val world = FakeWorld(1) { step, index, rethrows -> settlingThenStuck(step, index, rethrows) }
    val roll = loop(world, dice = listOf(StandardDice.d6))

    @Suppress("ControlFlowWithEmptyBody")
    while (roll.advance()) {
      // Run it out; the snapshot is taken at the end.
    }
    val seen = roll.diagnostics()

    assertEquals(roll.outcome().corrections, seen.corrections)
    assertEquals(roll.outcome().rethrows, seen.rethrows)
    assertEquals(0, seen.postRestCorrections)
    // Per die as well as in total, so the overlay can point at the one that
    // needed help.
    assertTrue(seen.dice.single().rethrows <= RollLoop.MAX_RETHROWS)
  }

  @Test
  fun `a roll with no dice in it reports a snapshot rather than refusing one`() {
    val world = FakeWorld(0) { _, _, _ -> FakeWorld.settled() }
    val roll = loop(world, dice = emptyList())

    @Suppress("ControlFlowWithEmptyBody")
    while (roll.advance()) {
      // A roll with no dice has nothing to step, and ends at once.
    }

    assertEquals(NO_DICE, roll.diagnostics().dice.size)
  }

  /**
   * A die that settles into trouble and stays there: it is nudged, and then
   * thrown again when the nudge does not help.
   */
  private fun settlingThenStuck(
    step: Int,
    @Suppress("UNUSED_PARAMETER") index: Int,
    rethrows: Int,
  ) = if (step < TROUBLE_STEPS || rethrows >= RollLoop.MAX_RETHROWS) {
    FakeWorld.settling(cocked)
  } else {
    FakeWorld.settled(cocked)
  }

  /** Two dice, one of which settles cocked and has to be helped. */
  private fun troubled(
    step: Int,
    index: Int,
  ) = if (index == 0) FakeWorld.settled() else settlingThenStuck(step, index, rethrows = 0)

  private fun run(watching: Boolean): SimulationOutcome {
    val world = FakeWorld(2) { step, index, _ -> troubled(step, index) }
    val roll = loop(world)
    while (roll.advance()) {
      if (watching) roll.diagnostics()
    }
    return roll.outcome()
  }

  private fun loop(
    world: FakeWorld,
    dice: List<Die> = listOf(StandardDice.d6, StandardDice.d20),
  ): RollLoop {
    val spec = spec(dice)
    return RollLoop(
      spec = spec,
      world = world,
      layout = SpawnLayout(geometry, RADIUS_MM, spec.seed),
      shake = ShakeDriver(spec.shake),
    )
  }

  private fun spec(dice: List<Die>): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = table,
      seed = 42L,
    )

  private companion object {
    const val RADIUS_MM = 8.0
    const val EPSILON = 1e-9

    /** Fewer steps than a die needs to be at rest, so the timer is mid-fill. */
    const val SETTLING_STEPS = 10

    /** Long enough for `TROUBLE_STEPS_BEFORE_BIAS` to have passed. */
    const val TROUBLE_STEPS = 12

    /** A roll with no dice has no dice in its snapshot either. */
    const val NO_DICE = 0
  }
}
