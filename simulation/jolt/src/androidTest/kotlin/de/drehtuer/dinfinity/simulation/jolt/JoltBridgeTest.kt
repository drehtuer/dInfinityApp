package de.drehtuer.dinfinity.simulation.jolt

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The bridge's first end-to-end run: dice that actually settle
 * (`docs/TODO.md`, Step 3).
 *
 * The spike proved Jolt configures, builds and links for this project's
 * toolchain. It could not prove that a scene settles, because an Android
 * binary needs an Android device — so that is what is here, and it runs on the
 * emulator in the devcontainer and on the Pixel 10a
 * (`docs/build-setup.md`).
 *
 * This is deliberately not the Step 5 harness. It asks whether the physics
 * runs at all: do dice fall, do they stop, do they stay in the tray, does the
 * same seed give the same roll. How *well* they do it — the fairness runs, the
 * stacking budget, the frame times — is Step 5, on a device, with a harness of
 * its own.
 */
@RunWith(AndroidJUnit4::class)
class JoltBridgeTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun theNativeLibraryIsInTheApk() {
    val world = JoltWorld.open(geometry, table, maxDice = 1)
    assertNotNull("libdinfinity_jolt.so did not load on this ABI", world)
    world?.close()
  }

  @Test
  fun oneDieFallsAndStops() {
    val outcome = JoltDiceSimulator().run(spec(listOf(d6()), seed = 1L))

    assertEquals(1, outcome.diceCount)
    assertTrue("a die that never stopped is not a roll", outcome.steps < CAP_STEPS)
    assertTrue(
      "the face read is not one of the die's",
      outcome.faces.getValue(0) in 0 until DieShape.Cube.faceCount,
    )
    assertTrue("the ladder had to rescue a single die on an empty table", outcome.clean)
  }

  @Test
  fun everyCatalogueShapeSettlesAndReadsAFace() {
    DieShape.entries.forEach { shape ->
      val die = Die.standard(shape.id, shape)
      val outcome = JoltDiceSimulator().run(spec(listOf(die), seed = 4L))

      assertTrue(
        "${shape.id} did not settle inside the cap",
        outcome.steps < CAP_STEPS,
      )
      assertTrue(
        "${shape.id} read face ${outcome.faces.getValue(0)} of ${shape.faceCount}",
        outcome.faces.getValue(0) in 0 until shape.faceCount,
      )
    }
  }

  @Test
  fun twentyDiceAllSettleAndStayInTheTray() {
    val dice = List(TWENTY) { d6() }
    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))
    val spec = spec(dice, seed = 12L)
    val layout = SpawnLayout(geometry, radiusOf(d6()), spec.seed)

    val outcome =
      world.use {
        dice.forEachIndexed { index, die ->
          world.addDie(
            hull = ShapeGeometry.hullOf(die),
            material = die.material,
            placement = layout.placementOf(index, dice.size),
          )
        }
        world.finish()
        RollLoop(spec, world, layout, ShakeDriver(emptyList())).run()
      }

    assertTrue("twenty dice did not settle inside the cap", outcome.steps < CAP_STEPS)
    assertEquals("a die was left touched after it had stopped", 0, outcome.postRestCorrections)
    assertEquals(TWENTY, outcome.faces.size)
  }

  @Test
  fun noDieEverLeavesTheTray() {
    val dice = List(TWENTY) { d6() }
    val spec = spec(dice, seed = 21L)
    val layout = SpawnLayout(geometry, radiusOf(d6()), spec.seed)
    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))

    world.use {
      dice.forEachIndexed { index, die ->
        world.addDie(ShapeGeometry.hullOf(die), die.material, layout.placementOf(index, dice.size))
      }
      world.finish()

      repeat(WATCHED_STEPS) {
        world.setGravity(ShakeDriver.DEFAULT_GRAVITY)
        world.step(SettleRule.TIMESTEP_SECONDS)
        world.readStates().forEachIndexed { index, state ->
          assertTrue(
            "die $index left the tray at ${state.position}",
            abs(state.position.x) <= geometry.longSideMm &&
              abs(state.position.y) <= geometry.shortSideMm &&
              state.position.z in -1.0..geometry.ceilingHeightMm,
          )
        }
      }
    }
  }

  @Test
  fun theSameSeedGivesTheSameRoll() {
    val dice = List(TEN) { d6() }
    val first = JoltDiceSimulator().run(spec(dice, seed = 99L))
    val again = JoltDiceSimulator().run(spec(dice, seed = 99L))

    assertEquals("a seeded roll that does not replay to itself is not seeded", first, again)
  }

  @Test
  fun anotherSeedGivesAnotherRoll() {
    // Not a fairness claim — that is Step 5's hundred thousand rolls. This only
    // says the seed reaches the physics at all, which a roll that ignored it
    // would fail.
    val dice = List(TEN) { d6() }
    val results = SEEDS.map { JoltDiceSimulator().run(spec(dice, seed = it)).faces }

    assertTrue("every seed gave the same faces", results.toSet().size > 1)
  }

  @Test
  fun aShakenTrayStillSettlesItsDice() {
    val dice = List(TEN) { d6() }
    val shake =
      List(SHAKE_STEPS) { step ->
        val swing = if ((step / SWING_STEPS) % 2 == 0) SHAKE_MM_PER_SECOND2 else -SHAKE_MM_PER_SECOND2
        ShakeSample(
          stepIndex = step,
          accelerationMmPerSecond2 = Vector3(swing, swing / 2, 0.0),
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }

    val outcome = JoltDiceSimulator().run(spec(dice, seed = 5L).copy(shake = shake))

    assertTrue("a shaken roll never settled", outcome.steps < CAP_STEPS)
    assertEquals(TEN, outcome.faces.size)
  }

  private fun d6(): Die = Die.standard("d6", DieShape.Cube)

  private fun radiusOf(die: Die): Double = die.material.boundingRadiusMm

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
    val CAP_STEPS = SettleRule.HARD_CAP_STEPS
    const val TEN = 10
    const val TWENTY = 20
    const val WATCHED_STEPS = 600
    const val SHAKE_STEPS = 120
    const val SWING_STEPS = 12
    const val SHAKE_MM_PER_SECOND2 = 18_000.0
    val SEEDS = listOf(1L, 2L, 3L, 4L, 5L, 6L)
  }
}
