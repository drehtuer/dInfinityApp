package de.drehtuer.dinfinity.simulation.jolt

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * takes.
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
