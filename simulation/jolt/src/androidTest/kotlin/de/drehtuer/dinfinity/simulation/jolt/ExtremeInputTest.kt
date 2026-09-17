package de.drehtuer.dinfinity.simulation.jolt

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.CapacityVerdict
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What a hand can do to a roll that nobody sensible would (`docs/TODO.md`,
 * Step 5.3).
 *
 * A shake is the one thing that reaches a roll from outside, so it is the one
 * input a player controls directly — and every one of these is a real thing a
 * real phone reports: a sensor pinned at its maximum, an arm that does not stop
 * for half a minute, a phone turned over and over while the dice are in the
 * air, and a shake that ends in a drop.
 *
 * What each asserts is that the roll is still *a roll*: it finishes, every die
 * is on the table, and the answer is the same one twice. A throw that a hand
 * can break is a throw whose answer nobody should trust.
 */
@RunWith(AndroidJUnit4::class)
class ExtremeInputTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun aSensorPinnedAtItsMaximumIsStillARoll() {
    // Every axis at the cap, every step, never changing. Not a shake anybody
    // could perform — it is what a broken accelerometer reports — and the
    // driver's own clamp is what stands between it and dice fired at the walls
    // faster than any thickness of wall survives.
    val pinned =
      List(SettleRule.HARD_CAP_STEPS) { step ->
        ShakeSample(
          stepIndex = step,
          accelerationMmPerSecond2 =
            Vector3(
              Double.MAX_VALUE / 2,
              Double.MAX_VALUE / 2,
              Double.MAX_VALUE / 2,
            ),
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }

    assertARoll(shaken(pinned), "a sensor pinned at its maximum")
  }

  @Test
  fun thirtySecondsOfShakingIsBoundedByTheRollItCanDrive() {
    // A hand goes on for as long as it likes; a roll is force-settled at twelve
    // seconds. The samples past that name steps that will never be taken, and
    // the record refuses them rather than growing for as long as the arm does.
    val halfAMinute = SECONDS_OF_SHAKING * SettleRule.STEPS_PER_SECOND
    val forAges =
      List(halfAMinute) { step ->
        ShakeSample(
          stepIndex = step,
          accelerationMmPerSecond2 = swing(step, Vector3(1.0, 0.3, 0.0)),
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }

    val driver = ShakeDriver(forAges)
    assertEquals(
      "a thirty-second shake kept more moments than a roll has steps",
      SettleRule.HARD_CAP_STEPS,
      driver.recorded().size,
    )
    assertTrue(
      "a moment was kept for a step no roll will ever take",
      driver.recorded().all { it.stepIndex < SettleRule.HARD_CAP_STEPS },
    )

    assertARoll(shaken(forAges), "thirty seconds of shaking")
  }

  @Test
  fun aPhoneTurnedThroughEveryAxisMidRollIsStillARoll() {
    // The gravity a sample carries is recorded and does not turn the world —
    // the table is horizontal whatever the phone is doing — but the *hand's*
    // force is in the phone's frame and does turn. This is that frame spinning
    // through all three axes while the dice are in the air.
    val tumbling =
      List(SettleRule.HARD_CAP_STEPS) { step ->
        val turn = step * TURN_PER_STEP
        ShakeSample(
          stepIndex = step,
          accelerationMmPerSecond2 =
            Vector3(
              x = HARD * kotlin.math.cos(turn),
              y = HARD * kotlin.math.sin(turn),
              z = HARD * kotlin.math.cos(turn * 2),
            ),
          // Down, tumbling with it: what a gyroscope reports for a phone being
          // turned over and over.
          gravity =
            Vector3(
              x = kotlin.math.sin(turn),
              y = kotlin.math.cos(turn * 2),
              z = -kotlin.math.cos(turn),
            ),
        )
      }

    assertARoll(shaken(tumbling), "a phone turned through every axis")
  }

  @Test
  fun aShakeThatEndsInADropIsStillARoll() {
    // The hand shakes hard and then lets go entirely — free fall, which is what
    // a dropped phone reports, and then nothing. The dice must still come down
    // and stop rather than being left coasting.
    val shakeThenDrop =
      List(DROP_AT + DROP_STEPS) { step ->
        ShakeSample(
          stepIndex = step,
          accelerationMmPerSecond2 =
            if (step < DROP_AT) {
              swing(step, Vector3(1.0, -0.6, 0.2))
            } else {
              // Free fall: the phone's own acceleration is gravity, so the
              // linear-acceleration sensor reports it and the tray goes light.
              Vector3(0.0, 0.0, ShakeDriver.GRAVITY_MM_PER_SECOND2)
            },
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }

    assertARoll(shaken(shakeThenDrop), "a shake that ended in a drop")
  }

  /** A hand swinging, scaled onto [direction]. */
  private fun swing(
    step: Int,
    direction: Vector3,
  ): Vector3 {
    val sign = if ((step / SWING_STEPS) % 2 == 0) 1.0 else -1.0
    return direction * (sign * HARD)
  }

  private fun shaken(shake: List<ShakeSample>): Pair<List<DieState>, Map<Int, Int>> {
    val dice = List(TWENTY) { Die.standard("d6", DieShape.Cube) }
    val spec = specOf(dice).copy(shake = shake)
    // Null when the roll gave up. Input this violent is exactly where a throw
    // may fail to settle, and a roll that cannot finish says so rather than
    // reading every die off the face it was nearest.
    val outcome = runCatching { JoltDiceSimulator().run(spec) }.getOrNull()

    // And again, to say the same hand gives the same answer — including the
    // answer "this one did not settle". A roll driven by input this violent is
    // exactly where a dependence on anything but the seed would show.
    val again = runCatching { JoltDiceSimulator().run(spec) }.getOrNull()
    assertEquals("the same extreme shake gave two different rolls", outcome?.faces, again?.faces)

    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))
    val layout = SpawnLayout(geometry, dice.first().material.boundingRadiusMm * spec.dieScale, spec.seed)
    val states =
      world.use {
        dice.forEachIndexed { index, die ->
          world.addDie(ShapeGeometry.hullOf(die, spec.dieScale), die.material, layout.placementOf(index, dice.size))
        }
        world.finish()
        RollLoop(spec, world, layout, ShakeDriver(shake)).runOrGiveUp()
        world.readStates()
      }
    return states to outcome?.faces.orEmpty()
  }

  /** Every die on the table, and a face read for each of them. */
  private fun assertARoll(
    result: Pair<List<DieState>, Map<Int, Int>>,
    what: String,
  ) {
    val (states, faces) = result
    // Either every die was read, or the roll gave up and read none of them.
    // What it may not do is answer for some and invent the rest: a die read off
    // whatever face it was nearest is a number nobody rolled
    // (`docs/physics-and-rendering.md`).
    assertTrue(
      "$what read $faces of $TWENTY dice, which is neither a roll nor a roll given up",
      faces.isEmpty() || faces.size == TWENTY,
    )

    val escaped =
      states.withIndex().filterNot { (_, state) ->
        abs(state.position.x) <= geometry.longSideMm / 2 + SLOP_MM &&
          abs(state.position.y) <= geometry.shortSideMm / 2 + SLOP_MM &&
          state.position.z >= -SLOP_MM
      }
    assertTrue("$what left dice off the table: ${escaped.map { it.index to it.value.position }}", escaped.isEmpty())

    assertTrue(
      "$what produced a position that is not a number",
      states.all { it.position.x.isFinite() && it.position.y.isFinite() && it.position.z.isFinite() },
    )
  }

  private fun specOf(dice: List<Die>): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = table,
      seed = 19L,
      dieScale = (TableCapacity.check(dice, geometry) as CapacityVerdict.Fits).scale,
    )

  private companion object {
    const val TWENTY = 20
    const val SWING_STEPS = 12
    const val HARD = 20_000.0
    const val SECONDS_OF_SHAKING = 30
    const val TURN_PER_STEP = 0.07
    const val DROP_AT = 120
    const val DROP_STEPS = 240
    const val SLOP_MM = 1.0
  }
}
