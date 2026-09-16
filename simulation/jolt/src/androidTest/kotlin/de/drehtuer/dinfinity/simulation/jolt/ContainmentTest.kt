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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The dice stay in the box — on **every step**, not only at the end
 * (`docs/TODO.md`, Step 5.4).
 *
 * `JoltBridgeTest` has had a containment check since the bridge opened, and it
 * was too kind twice over. It allowed a die's centre to be a whole side length
 * from the middle, which is twice as far out as the wall, and it watched twenty
 * dice under plain gravity — the easy case. A die that left the tray mid-roll
 * and came back would have passed it, which is exactly the failure the plan
 * names.
 *
 * So the bounds here are the tray's own, and the questions are the ones a
 * solver actually gets wrong: a full tray, a hand shaking as hard as the cap
 * allows, a corner driven into at speed, and a pile left alone.
 *
 * All of it is on a device because it is the bridge: `JoltWorld` is JNI over a
 * native solver, and a JVM cannot answer any of it.
 */
@RunWith(AndroidJUnit4::class)
class ContainmentTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun aFullTrayKeepsEveryDieInsideTheWallsOnEveryStep() {
    val dice = List(TableCapacity.MAX_DICE) { d6() }

    watch(dice, seed = 5L, steps = WATCHED_STEPS) { step, index, state ->
      assertTrue(
        "die $index was outside the tray at step $step, at ${state.position}",
        inside(state.position),
      )
    }
  }

  @Test
  fun noDieTunnelsOutAtTheHardestShakeTheCapAllows() {
    // The cap is about four gravities, and it is there because harder than that
    // is a sensor fault rather than a hand. This drives every step at it, in a
    // direction that keeps changing, which is the worst thing a swept collision
    // can be asked: a die crossing more than its own width between two steps is
    // how a body ends up on the far side of a wall.
    val dice = List(TWENTY) { d6() }
    val hardest =
      List(WATCHED_STEPS) { step ->
        val swing = if ((step / SWING_STEPS) % 2 == 0) 1.0 else -1.0
        ShakeSample(
          stepIndex = step,
          accelerationMmPerSecond2 =
            Vector3(
              x = swing * ShakeDriver.MAX_SHAKE_MM_PER_SECOND2,
              y = -swing * ShakeDriver.MAX_SHAKE_MM_PER_SECOND2,
              z = swing * ShakeDriver.MAX_SHAKE_MM_PER_SECOND2,
            ),
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }

    watch(dice, seed = 13L, steps = WATCHED_STEPS, shake = hardest) { step, index, state ->
      assertTrue(
        "die $index tunnelled out at step $step, at ${state.position}",
        inside(state.position),
      )
    }
  }

  @Test
  fun diceDrivenIntoACornerNeitherWedgeNorJitter() {
    // The corners are rounded for exactly this (`TableGeometry.cornerRadiusMm`).
    // A wedged die is one that never stops; a jittering one stops and then does
    // not stay stopped. Both are asked here by driving everything at one corner
    // and then letting go.
    val dice = List(TEN) { d6() }
    val intoTheCorner =
      List(DRIVING_STEPS) { step ->
        ShakeSample(
          stepIndex = step,
          // One direction, held: the hand does not swing back, so the dice pile
          // into the corner and stay pressed there.
          accelerationMmPerSecond2 = Vector3(-CORNER_DRIVE, -CORNER_DRIVE, 0.0),
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }

    val settled =
      rollOut(dice, seed = 3L, steps = SettleRule.HARD_CAP_STEPS, shake = intoTheCorner) { step, index, state ->
        assertTrue("die $index left the tray at step $step, at ${state.position}", inside(state.position))
      }

    assertTrue(
      "dice driven into a corner never stopped: ${settled.count { it.motion.speedMmPerSecond > STILL }} still moving",
      settled.all { it.motion.speedMmPerSecond <= STILL },
    )
  }

  @Test
  fun aSettledPileStaysWhereItStopped() {
    // No creep, no vibration, no slow slide. The dice are rolled until they
    // stop, and then the world is stepped on for another two seconds with
    // nothing driving it: whatever moves after that is the solver moving it.
    val dice = List(TWENTY) { d6() }
    val scale = scaleFor(dice)
    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))
    val layout = SpawnLayout(geometry, radiusOf(d6()) * scale, seed = 8L)

    world.use {
      dice.forEachIndexed { index, die ->
        world.addDie(ShapeGeometry.hullOf(die, scale), die.material, layout.placementOf(index, dice.size))
      }
      world.finish()
      repeat(SettleRule.HARD_CAP_STEPS) {
        world.setGravity(ShakeDriver.DEFAULT_GRAVITY)
        world.step(SettleRule.TIMESTEP_SECONDS)
      }

      val stopped = world.readStates().map { it.position }
      repeat(WATCHING_A_PILE) {
        world.setGravity(ShakeDriver.DEFAULT_GRAVITY)
        world.step(SettleRule.TIMESTEP_SECONDS)
      }

      world.readStates().forEachIndexed { index, state ->
        val moved = (state.position - stopped[index]).length
        assertTrue(
          "die $index crept $moved mm in two seconds of being left alone",
          moved <= CREEP_MM,
        )
      }
    }
  }

  /** True when a die's centre is inside the tray's own walls, not somewhere near them. */
  private fun inside(position: Vector3): Boolean =
    abs(position.x) <= geometry.longSideMm / 2 + SLOP_MM &&
      abs(position.y) <= geometry.shortSideMm / 2 + SLOP_MM &&
      position.z >= -SLOP_MM &&
      position.z <= geometry.ceilingHeightMm + SLOP_MM

  /** Steps a throw and asks [check] of every die on every step. */
  private fun watch(
    dice: List<Die>,
    seed: Long,
    steps: Int,
    shake: List<ShakeSample> = emptyList(),
    check: (Int, Int, DieState) -> Unit,
  ) {
    rollOut(dice, seed, steps, shake, check)
  }

  private fun rollOut(
    dice: List<Die>,
    seed: Long,
    steps: Int,
    shake: List<ShakeSample>,
    check: (Int, Int, DieState) -> Unit,
  ): List<DieState> {
    val spec = specOf(dice, seed).copy(shake = shake)
    val scale = spec.dieScale
    val layout = SpawnLayout(geometry, radiusOf(dice.first()) * scale, spec.seed)
    val driver = ShakeDriver(shake)
    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))

    return world.use {
      dice.forEachIndexed { index, die ->
        world.addDie(ShapeGeometry.hullOf(die, scale), die.material, layout.placementOf(index, dice.size))
      }
      world.finish()
      repeat(steps) { step ->
        driver.advance(step)
        world.setGravity(driver.gravity)
        world.step(SettleRule.TIMESTEP_SECONDS)
        world.readStates().forEachIndexed { index, state -> check(step, index, state) }
      }
      world.readStates()
    }
  }

  private fun d6(): Die = Die.standard("d6", DieShape.Cube)

  private fun radiusOf(die: Die): Double = die.material.boundingRadiusMm

  /** The throw, shrunk by the capacity rule exactly as a real one would be. */
  private fun specOf(
    dice: List<Die>,
    seed: Long,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = table,
      seed = seed,
      dieScale = scaleFor(dice),
    )

  private fun scaleFor(dice: List<Die>): Double = (TableCapacity.check(dice, geometry) as CapacityVerdict.Fits).scale

  private companion object {
    const val TEN = 10
    const val TWENTY = 20

    /** Five seconds of steps, which is longer than almost every roll takes. */
    const val WATCHED_STEPS = 600

    /** How long a shake holds one direction before it swings back. */
    const val SWING_STEPS = 12

    /** And how long a hand drives at one corner without letting go. */
    const val DRIVING_STEPS = 240

    /** Two thirds of the cap: hard, and held rather than swung. */
    const val CORNER_DRIVE = 26_000.0

    /** Two seconds of a world nobody is touching. */
    const val WATCHING_A_PILE = 240

    /**
     * How far outside the walls a die's centre may be before it has left.
     *
     * Not nought: a solver resolves overlaps rather than forbidding them, so a
     * die pressed into a wall is briefly a fraction of a millimetre inside it.
     * A millimetre is well under a die and well over the solver's slop.
     */
    const val SLOP_MM = 1.0

    /** Slower than this is stopped, for a die that should have stopped. */
    const val STILL = 5.0

    /**
     * How far a settled die may move in two undriven seconds.
     *
     * **Measured at 3.4 × 10⁻⁵ mm on the Pixel 10a**, which is a thousandth of
     * a hair and is what "no creep" turns out to mean here. The bound is a
     * hundredth of a millimetre — three hundred times the measurement, so it
     * will not flake, and still four hundred times smaller than anything an eye
     * could follow on a sixteen-millimetre die.
     *
     * Not nought, because a solver keeps resolving contacts for as long as it
     * is stepped. Nought would be asserting that it does not.
     */
    const val CREEP_MM = 0.01
  }
}
