package de.drehtuer.dinfinity.simulation.jolt

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.CapacityVerdict
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The throws at the edges of what the table will take (`docs/TODO.md`, Step 5.3).
 *
 * Every one of these is a throw a player can actually ask for, and each is
 * chosen because it is the *worst* of its kind: the flattest shape and the
 * sharpest one, the smallest a die may be shrunk to carrying the largest a set
 * may declare, a tray filled exactly to the cap, and a hundred rolls in a row
 * to see whether any of it drifts.
 *
 * What every one of them asserts is the same two things, because they are the
 * two that matter: **every die is on the table** when it stops, and **no die is
 * standing on another**. A roll that cannot say both is not a roll a player can
 * read, whatever else it does.
 */
@RunWith(AndroidJUnit4::class)
class CornerCasesTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun exactlyAtTheCapIsThrownAndOneOverIsRefusedBeforeABodyExists() {
    val atTheCap = List(TableCapacity.MAX_DICE) { d6() }
    val oneOver = List(TableCapacity.MAX_DICE + 1) { d6() }

    // The refusal is arithmetic and happens before anything is created, which
    // is the whole point: a hundred and one bodies are never made, so there is
    // nothing to clean up and nothing to go wrong in the native layer.
    val refused = TableCapacity.check(oneOver, geometry)
    assertTrue("one over the cap was not refused", refused is CapacityVerdict.Refused)
    assertEquals(
      "the refusal did not offer the cap as what fits",
      TableCapacity.MAX_DICE,
      (refused as CapacityVerdict.Refused).largestThatFits,
    )

    val landed = settle(atTheCap, seed = 41L)
    assertEquals("a throw exactly at the cap lost dice", TableCapacity.MAX_DICE, landed.states.size)
    assertOnTheTable(atTheCap, landed, "exactly at the cap")
  }

  @Test
  fun theSharpestShapeAtTheCapEndsOnTheTable() {
    // The d4 cannot rest flat on another one, so a heap of them has no stable
    // packing — it is the worst case in the catalogue by some way.
    val d4s = List(TableCapacity.MAX_DICE) { d4() }

    assertOnTheTable(d4s, settle(d4s, seed = 17L), "a hundred d4s")
  }

  @Test
  fun theFlattestShapeAtTheCapStaysOnTheTable() {
    // And the coin is the other end of it: two faces, almost no height, and
    // nothing easier to slide under a neighbour. Every one of them is on the
    // table when it stops, on every seed.
    val coins = List(TableCapacity.MAX_DICE) { coin() }

    (1L..COIN_SEEDS).forEach { seed ->
      val landed = settle(coins, seed)
      assertEquals("a hundred coins lost dice on seed $seed", coins.size, landed.states.size)
      assertInsideTheWalls(landed.states, "a hundred coins on seed $seed")
    }
  }

  @Test
  fun theFlattestShapeAtTheCapIsWhereStackingStillHappens() {
    // **The target is zero and this is not it** (`docs/TODO.md`, Step 5.5).
    // A hundred coins is the one throw in the catalogue that still comes to
    // rest with many dice standing on other dice, on every seed tried, which is
    // systematic rather than unlucky.
    //
    // It is the shape's own doing. A coin that lands on a coin is *stable*
    // there — a cube or an icosahedron on top of another rolls off, and that is
    // what makes prevention work everywhere else.
    //
    // **The rule that holds is that none of them is read.** A coin standing on
    // a coin is left lying there, unread, for the player's next shake to throw
    // again (decision 70).
    //
    // **And the heap one throw leaves is bounded again**, as what it now is:
    // the coins a single throw leaves on other coins, before any shake. That is
    // far more than the four to ten the old ladder left, because nothing
    // re-throws them by itself any more — 18 to 29 a seed on the Pixel 10a
    // (2026-10-04, `docs/TODO.md`, Step 5.3). The bound is the worst of those,
    // so a change to the throw or the solver either improves it or is noticed;
    // the figures are logged as well, so they are there to read.
    val coins = List(TableCapacity.MAX_DICE) { coin() }
    val landed = (1L..COIN_SEEDS).map { seed -> seed to settle(coins, seed) }

    landed.forEach { (seed, coinsDown) -> assertNoneStackedWasRead(coinsDown, "a hundred coins on seed $seed") }
    val stacked = landed.map { (seed, coinsDown) -> seed to coinsDown.states.count(DieState::supportedByDie) }
    Log.e("CoinStack", "coins left standing on another, by seed: $stacked")
    assertTrue(
      "a hundred coins stacked worse than they used to: $stacked",
      stacked.all { (_, count) -> count <= COINS_STACKED_ALLOWED },
    )
  }

  @Test
  fun theSmallestScaleCarriesTheLargestDieASetMayDeclare() {
    // Forty millimetres is the biggest a set file may ask for and 0.40 is as
    // far down as the capacity rule will shrink one, so this is the largest
    // nominal die a throw can ever be asked to shrink the most.
    val huge = Die.standard("d20", DieShape.Icosahedron, DieMaterial(sizeMm = LARGEST_DIE_MM))
    val many = List(TableCapacity.MAX_DICE) { huge }
    val verdict = TableCapacity.check(many, geometry)

    // It is refused or it fits; either is an answer, and what must not happen
    // is a throw that is made at a scale below the floor.
    when (verdict) {
      is CapacityVerdict.Refused ->
        assertTrue("the refusal offered more dice than the cap", verdict.largestThatFits <= TableCapacity.MAX_DICE)
      is CapacityVerdict.Fits -> {
        assertTrue("a throw was planned below the scale floor", verdict.scale >= TableCapacity.MIN_SCALE)
        assertOnTheTable(many, settle(many, seed = 29L), "the largest die at the smallest scale")
      }
    }
  }

  @Test
  fun mixedShapesInOneThrowAllEndOnTheTable() {
    // A formula mixes whatever a player writes, and the grid is laid out for
    // the biggest die in the throw rather than for each one's own size — so a
    // mixture is the case where that choice is load-bearing.
    val mixture =
      DieShape.entries.flatMap { shape ->
        List(MIXTURE_PER_SHAPE) { Die.standard(shape.id, shape) }
      }

    assertOnTheTable(mixture, settle(mixture, seed = 31L), "every shape at once")
  }

  @Test
  fun aHundredRollsInARowDoNotDrift() {
    // Thermal, as far as a headless run can ask it: the same throw a hundred
    // times, watching whether the *outcome* changes as the phone warms. It
    // cannot ask about frame times — there is no renderer here — but a solver
    // that started producing different answers as it heated would be a far
    // worse problem, and this is where it would show.
    val dice = List(FORTY) { d6() }
    val first = JoltDiceSimulator().run(specOf(dice, seed = 77L)).faces

    repeat(THERMAL_ROLLS) { round ->
      val again = JoltDiceSimulator().run(specOf(dice, seed = 77L)).faces
      assertEquals("roll $round came to different faces than the first", first, again)
    }
  }

  /** Where one throw's dice stopped, and what reading them came to — null if it gave up. */
  private class Landed(
    val states: List<DieState>,
    val outcome: SimulationOutcome?,
  )

  /** Throws [dice], once, and hands back where they stopped. */
  private fun settle(
    dice: List<Die>,
    seed: Long,
  ): Landed {
    val spec = specOf(dice, seed)
    val scale = spec.dieScale
    val layout = SpawnLayout(geometry, dice.maxOf { it.material.boundingRadiusMm } * scale, spec.seed)
    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = dice.size))

    return world.use {
      dice.forEachIndexed { index, die ->
        world.addDie(ShapeGeometry.hullOf(die, scale), die.material, layout.placementOf(index, dice.size))
      }
      world.finish()
      // A roll that gave up still left its dice somewhere, and where they are
      // is what these corners ask about.
      val outcome = RollLoop(spec, world, ShakeDriver(emptyList())).runOrGiveUp()
      Landed(world.readStates(), outcome)
    }
  }

  /** Every die inside the walls, and none standing on another read. */
  private fun assertOnTheTable(
    dice: List<Die>,
    landed: Landed,
    what: String,
  ) {
    assertEquals("$what lost dice", dice.size, landed.states.size)

    assertInsideTheWalls(landed.states, what)
    assertNoneStackedWasRead(landed, what)
  }

  /**
   * A die may come to rest on another; it may not be *read* there. It lies
   * where it is, unread, until the player's shake throws it again
   * (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice").
   */
  private fun assertNoneStackedWasRead(
    landed: Landed,
    what: String,
  ) {
    val read = landed.outcome?.faces.orEmpty()
    val stackedAndRead = landed.states.indices.filter { landed.states[it].supportedByDie && it in read }
    assertEquals("$what read dice standing on others: $stackedAndRead", emptyList<Int>(), stackedAndRead)
  }

  /** Every die's centre inside the tray's own walls. */
  private fun assertInsideTheWalls(
    landed: List<DieState>,
    what: String,
  ) {
    val escaped =
      landed.withIndex().filterNot { (_, state) ->
        abs(state.position.x) <= geometry.longSideMm / 2 + SLOP_MM &&
          abs(state.position.y) <= geometry.shortSideMm / 2 + SLOP_MM &&
          state.position.z >= -SLOP_MM
      }
    assertTrue("$what left dice off the table: ${escaped.map { it.index to it.value.position }}", escaped.isEmpty())
  }

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
      dieScale = (TableCapacity.check(dice, geometry) as CapacityVerdict.Fits).scale,
    )

  private fun d4(): Die = Die.standard("d4", DieShape.Tetrahedron)

  private fun d6(): Die = Die.standard("d6", DieShape.Cube)

  private fun coin(): Die = Die.standard("coin", DieShape.Coin)

  private companion object {
    const val FORTY = 40

    /** The biggest a set file may declare (`DieMaterial.SizeMmRange`). */
    const val LARGEST_DIE_MM = 40.0

    /** Enough of each shape that the mixture is a throw rather than a sample. */
    const val MIXTURE_PER_SHAPE = 8

    /** How many times the same throw is repeated to look for drift. */
    const val THERMAL_ROLLS = 100

    /** As `ContainmentTest`: a solver resolves overlaps rather than forbidding them. */
    const val SLOP_MM = 1.0

    /** How many seeds the coin — the worst shape here — is asked on. */
    const val COIN_SEEDS = 8L

    /**
     * How many of a hundred coins one throw may leave standing on another, on
     * any of the [COIN_SEEDS] seeds.
     *
     * **Not a target — a record of where prevention has got to.** The target
     * is zero (`docs/TODO.md`, Step 5.3); this is the worst seed measured on
     * the Pixel 10a under counting (decision 70), at two collision steps
     * (decision 77).
     */
    const val COINS_STACKED_ALLOWED = 29
  }
}
