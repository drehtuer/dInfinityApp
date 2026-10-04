package de.drehtuer.dinfinity.simulation.jolt

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.harness.HarnessRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three slowest rolls of a 10,000-roll `60d20` harness run, replayed
 * exactly (`docs/TODO.md`, Step 5.5).
 *
 * Two of them ran the whole twelve seconds and gave up, and the third took
 * 1,153 steps — against a median of 106 — all the same way: one die leaning
 * on the round post that used to stand in each corner of the tray, and on a
 * neighbour, rocking or trembling on a single contact on a curve and never
 * still for a quarter of a second, while every other die had stopped dead
 * (`docs/physics-and-rendering.md`, "Why a die could rock for ever").
 *
 * The corners are flat-faced fillets now, standing where they are drawn, and
 * the post is gone. This keeps the rolls that found it: each is thrown the way
 * the harness throws it — the same plan, the same derived seed, the same
 * scripted passes — and has to come to rest well inside the time a slow roll
 * takes. So does the one coin `FairnessTest` lost to the same post.
 */
@RunWith(AndroidJUnit4::class)
class StuckRollTest {
  @Test
  fun theRollsThatLeanedOnACornerPostNowSettle() {
    val plan =
      requireNotNull(
        HarnessRequest.from(
          mapOf(
            HarnessRequest.ROLLS to "$RUN_ROLLS",
            HarnessRequest.DICE to "$DICE",
            HarnessRequest.SHAPE to "d20",
          )::get,
        ),
      ).plan()
    val simulator = JoltDiceSimulator()

    val steps =
      STUCK.map { (index, seed) ->
        val spec = plan.specFor(index)
        // Checked rather than trusted: a change to how the harness derives a
        // roll's seed would otherwise quietly replay some other roll.
        assertEquals("roll $index of the run is not the roll that was stuck", seed, spec.seed)
        val outcome = simulator.runOrGiveUp(spec)
        assertNotNull("roll $index (seed $seed) gave up: its dice never came to rest", outcome)
        index to requireNotNull(outcome).steps
      }

    // Into logcat, so the bound below can be checked against what was measured.
    Log.i("StuckRoll", "steps by roll: $steps")
    assertTrue(
      "a roll that once ran out the cap now takes longer than a slow roll should: $steps",
      steps.all { (_, taken) -> taken <= MOST_STEPS },
    )
  }

  /**
   * The one coin in 100,000 `FairnessTest` throws that ran out the cap
   * (`docs/TODO.md`, Step 5.2) — the same post, found by a lone die.
   *
   * Replayed against the tray as it was, the coin hit the far short wall,
   * dropped and leaned at 43° with its rim on the floor and its upper rim on
   * the post in the +x, +y corner, and swung about the line through those two
   * points for eleven seconds: ±0.15 mm, 1.7 rad/s at the bottom of each swing,
   * a swing every 0.3 s and never smaller (`docs/physics-and-rendering.md`,
   * "Why a die could rock for ever"). With the fillets it lands flat and is
   * read in 89 steps.
   *
   * Thrown exactly as `FairnessTest` throws throw 14,476 of its run — one
   * standard coin on the reference tray, the seed SplitMix64 stirs from that
   * number.
   */
  @Test
  fun theCoinThatLeanedOnACornerPostNowSettles() {
    val coin = Die.standard(DieShape.Coin.id, DieShape.Coin)
    val spec =
      ThrowSpec(
        dice = listOf(DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = coin)),
        geometry = TableGeometry.referenceDevice(),
        table = TableLook(id = "plain", name = "Plain"),
        seed = STUCK_COIN,
      )

    val outcome = JoltDiceSimulator().runOrGiveUp(spec)

    assertNotNull("the coin (seed $STUCK_COIN) gave up: it never came to rest", outcome)
    val steps = requireNotNull(outcome).steps
    Log.i("StuckRoll", "the coin took $steps steps")
    assertTrue("the coin that once ran out the cap took $steps steps", steps <= MOST_STEPS)
  }

  private companion object {
    /** The run they came from: `tools/harness.sh -n 10000 -c 60 -s d20`, seed 1. */
    const val RUN_ROLLS = 10_000
    const val DICE = 60

    /** Roll index in that run, and the seed it derived — which is what was logged. */
    val STUCK =
      listOf(
        7196 to 8_028_586_073_466_521_667L,
        8091 to 8_761_657_533_792_694_254L,
        8926 to -4_967_573_788_364_099_082L,
      )

    /**
     * The coin's seed: what `FairnessTest` printed, and what its SplitMix64
     * makes of throw 14,476.
     */
    const val STUCK_COIN = 5_897_839_758_308_530_927L

    /**
     * Three seconds, every pass included.
     *
     * Measured on the Pixel 10a with the fillets: 210 steps for 7196 (one die
     * left cocked, thrown again in a pass of its own), 106 for 8091 and 101
     * for 8926. The same seed is the same roll on every ABI the golden suite
     * runs on, so this is not a bound against noise; it is room for the next
     * deliberate change to the throw, and still far under the 1,153 steps the
     * quickest of the three took against a post.
     */
    const val MOST_STEPS = 360
  }
}
