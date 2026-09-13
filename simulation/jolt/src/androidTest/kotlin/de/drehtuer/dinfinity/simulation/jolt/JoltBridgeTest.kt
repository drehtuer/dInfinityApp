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

  @Test
  fun aFullTrayLeavesEveryDieOnTheTableAndNoneInTheAir() {
    // The throw from the phone: 2d4 + 3d6 + 95d20, which is the engine's cap.
    // Two things were seen there that no roll may contain — a die at rest on
    // the *wall*, outside the floor entirely, and dice stopped in mid-air with
    // their shadows well below them (`docs/TODO.md`, Step 5.4).
    //
    // The older containment check allows a die a whole half-tray outside the
    // wall before it complains, which is why neither showed up in a suite.
    // This one asks what the screenshot asks: is every die on the table.
    val dice = List(2) { d4() } + List(3) { d6() } + List(95) { d20() }
    val verdict = TableCapacity.check(dice, geometry)
    val scale = (verdict as CapacityVerdict.Fits).scale
    val spec = spec(dice, seed = 7L).copy(dieScale = scale)

    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))
    val layout = SpawnLayout(geometry, radiusOf(d20()) * scale, spec.seed)

    val states =
      world.use {
        dice.forEachIndexed { index, die ->
          world.addDie(
            hull = ShapeGeometry.hullOf(die, scale),
            material = die.material,
            placement = layout.placementOf(index, dice.size),
          )
        }
        world.finish()
        RollLoop(spec, world, layout, ShakeDriver(emptyList())).run()
        world.readStates()
      }

    val halfLong = geometry.longSideMm / 2
    val halfShort = geometry.shortSideMm / 2
    val escaped =
      states.withIndex().filter { (_, state) ->
        abs(state.position.x) > halfLong || abs(state.position.y) > halfShort
      }
    assertTrue(
      "dice came to rest outside the tray: ${escaped.map { it.index to it.value.position }}",
      escaped.isEmpty(),
    )

    // A die resting on the floor sits about its own radius above it. One much
    // higher than that is either on a pile or in the air, and the difference is
    // whether anything is under it.
    val radius = radiusOf(d20()) * scale
    val floating =
      states.withIndex().filter { (index, state) ->
        state.position.z > radius * AIRBORNE &&
          states.withIndex().none { (other, below) ->
            other != index &&
              below.position.z < state.position.z &&
              abs(below.position.x - state.position.x) < radius * 2 &&
              abs(below.position.y - state.position.y) < radius * 2
          }
      }
    val highest = states.withIndex().sortedByDescending { it.value.position.z }.take(5)
    assertTrue(
      "dice came to rest in mid-air with nothing under them: " +
        "${floating.map { it.index to it.value.position }}; " +
        "tray ${geometry.longSideMm}x${geometry.shortSideMm} wall ${geometry.wallHeightMm} " +
        "ceiling ${geometry.ceilingHeightMm}; scale $scale radius $radius; " +
        "highest ${highest.map { it.index to it.value.position.z }}",
      floating.isEmpty(),
    )
  }

  @Test
  fun aHundredD4sFinishAndEveryOneIsOnTheTable() {
    // `100d4` is the worst case the engine will accept: the sharpest solid,
    // the one that cannot rest flat on another, at the capacity cap. On the
    // phone this throw never finished — the screen said "Rolling…" for good
    // (`docs/TODO.md`, Step 5.3).
    val dice = List(100) { d4() }
    val verdict = TableCapacity.check(dice, geometry)
    val scale = (verdict as CapacityVerdict.Fits).scale

    val report =
      (1L..8L).map { seed ->
        val spec = spec(dice, seed).copy(dieScale = scale)
        val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))
        val layout = SpawnLayout(geometry, radiusOf(d4()) * scale, spec.seed)
        val outcome =
          world.use {
            dice.forEachIndexed { index, die ->
              world.addDie(ShapeGeometry.hullOf(die, scale), die.material, layout.placementOf(index, dice.size))
            }
            world.finish()
            RollLoop(spec, world, layout, ShakeDriver(emptyList())).run()
          }
        assertEquals("a die went unread at seed $seed", dice.size, outcome.faces.size)
        seed to outcome
      }

    val stuck = report.filter { (_, outcome) -> outcome.steps >= CAP_STEPS }
    assertTrue(
      "a hundred d4s ran out of time: " +
        report.joinToString { (seed, o) -> "$seed:${o.steps}/${o.forcedSettles}f/${o.rethrows}r" },
      stuck.isEmpty(),
    )
    assertTrue(
      "a hundred d4s had to be forced to settle: " +
        report.joinToString { (seed, o) -> "$seed:${o.forcedSettles}" },
      report.all { (_, outcome) -> outcome.forcedSettles == 0 },
    )
  }

  @Test
  fun twentyD6sEndSpreadOutAndNoneStandingOnAnother() {
    // `20d6` on the phone came to rest as a neat column of cubes stacked
    // against one wall (`docs/TODO.md`, Step 5.5: **zero** dice at rest
    // supported by another die). This asks the same question of eight seeds.
    val dice = List(TWENTY) { d6() }
    val worst =
      (1L..8L).map { seed ->
        val spec = spec(dice, seed)
        val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))
        val layout = SpawnLayout(geometry, radiusOf(d6()), spec.seed)
        val states =
          world.use {
            dice.forEachIndexed { index, die ->
              world.addDie(ShapeGeometry.hullOf(die), die.material, layout.placementOf(index, dice.size))
            }
            world.finish()
            RollLoop(spec, world, layout, ShakeDriver(emptyList())).run()
            world.readStates()
          }
        seed to states
      }

    val stacked = worst.map { (seed, states) -> seed to states.count { it.supportedByDie } }
    assertTrue(
      "dice came to rest standing on other dice: ${stacked.filter { it.second > 0 }}",
      stacked.all { it.second == 0 },
    )

    // And spread out rather than swept into one corner: a roll that pours into
    // a heap against one wall is not a roll anybody can read.
    val heaped =
      worst.filter { (_, states) ->
        val spreadX = states.maxOf { it.position.x } - states.minOf { it.position.x }
        spreadX < geometry.longSideMm / 4
      }
    assertTrue("twenty dice ended in a heap: ${heaped.map { it.first }}", heaped.isEmpty())
  }

  private fun d4(): Die = Die.standard("d4", DieShape.Tetrahedron)

  private fun d20(): Die = Die.standard("d20", DieShape.Icosahedron)

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

    /** Higher above the floor than this, in die radii, and something should be under it. */
    const val AIRBORNE = 2.5
    val SEEDS = listOf(1L, 2L, 3L, 4L, 5L, 6L)
  }
}
