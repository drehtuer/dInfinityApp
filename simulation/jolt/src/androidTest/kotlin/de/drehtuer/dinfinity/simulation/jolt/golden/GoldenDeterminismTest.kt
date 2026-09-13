package de.drehtuer.dinfinity.simulation.jolt.golden

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.fixtures.Fixtures
import de.drehtuer.dinfinity.fixtures.GoldenCase
import de.drehtuer.dinfinity.fixtures.GoldenOutcome
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.jolt.JoltDiceSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The golden determinism suite where the dice actually roll
 * (`docs/physics-and-rendering.md`, "Timestep and determinism").
 *
 * The same cases as `GoldenCasesTest`, run through the engine on whatever ABI
 * this device is. The claim is exact and deliberately unforgiving: the same
 * seed, formula and shake give the same faces, in the same number of steps,
 * with the same corrections and re-throws, on the emulator (`x86_64`), on the
 * Pixel 10a (`arm64-v8a`) and on whatever comes next. Any diff is a bug —
 * either one somebody meant to make, in which case the fixture is re-recorded
 * and the diff reviewed, or one they did not, in which case every roll the app
 * makes has quietly moved.
 *
 * This is not the Step 5 harness. It runs ten rolls and compares them to a
 * record; Step 5 runs ten thousand and asks whether they are *good*.
 *
 * Every run logs its results as the lines `cases.tsv` carries, matched or not,
 * so re-recording after a deliberate change is one run and a copy rather than
 * ten failures read one at a time (`docs/build-setup.md`, "Re-recording the
 * golden cases").
 */
@RunWith(AndroidJUnit4::class)
class GoldenDeterminismTest {
  private val cases = Fixtures.goldenCases()

  @Test
  fun everyGoldenCaseRollsExactlyAsItWasRecorded() {
    val results = cases.map { case -> case to roll(case) }
    results.forEach { (case, outcome) -> Log.i(TAG, Fixtures.goldenLine(case, outcome)) }

    val unrecorded = results.filter { (case, _) -> case.expected == null }
    assertTrue(
      "no outcome is recorded for ${unrecorded.joinToString { "seed ${it.first.seed}" }}. " +
        "This run produced:\n" +
        unrecorded.joinToString("\n") { (case, outcome) -> Fixtures.goldenLine(case, outcome) },
      unrecorded.isEmpty(),
    )

    results.forEach { (case, outcome) -> assertMatches(case, case.expected ?: return@forEach, outcome) }
  }

  @Test
  fun aGoldenCaseGivesTheSameRollTwiceOnThisDevice() {
    // Determinism across devices is what the fixture is for; this is the
    // cheaper half of the same claim, and it fails first and more clearly when
    // something in a roll has started depending on the clock, on an address or
    // on a hash that is not seeded.
    cases.forEach { case ->
      assertEquals(
        "seed ${case.seed} '${case.formula}' did not replay to itself on this device",
        roll(case).faces,
        roll(case).faces,
      )
    }
  }

  private fun assertMatches(
    case: GoldenCase,
    expected: GoldenOutcome,
    actual: GoldenOutcome,
  ) {
    val where = "seed ${case.seed} '${case.formula}' (${case.input.id})"
    assertEquals("$where came to rest on different faces", expected.faces, actual.faces)
    assertEquals("$where took a different number of steps", expected.steps, actual.steps)
    assertEquals("$where needed a different number of corrections", expected.corrections, actual.corrections)
    assertEquals("$where needed a different number of re-throws", expected.rethrows, actual.rethrows)
    assertEquals("$where was thrown at a different scale", expected.scale, actual.scale, SCALE_TOLERANCE)
    assertEquals(
      "$where hands the engine a different throw than the JVM does — " +
        "the device and the JVM no longer agree about what this case even is",
      expected.spawn,
      actual.spawn,
    )
  }

  private fun roll(case: GoldenCase): GoldenOutcome {
    val spec = GoldenThrow.specOf(case)
    val outcome = JoltDiceSimulator().run(spec)
    assertClean(case, outcome)
    return GoldenOutcome(
      scale = spec.dieScale,
      spawn = GoldenThrow.spawnDigest(spec),
      faces = spec.dice.indices.map { outcome.faces.getValue(it) },
      steps = outcome.steps,
      corrections = outcome.corrections,
      rethrows = outcome.rethrows,
    )
  }

  /**
   * A golden case that had to be finished for the dice, or that touched a die
   * after it stopped, is not a case to record — it is the bug Step 5.5 exists
   * to keep at zero, and recording it would turn it into an expectation.
   */
  private fun assertClean(
    case: GoldenCase,
    outcome: SimulationOutcome,
  ) {
    assertEquals(
      "seed ${case.seed} '${case.formula}' touched ${outcome.postRestCorrections} dice after they came to rest",
      0,
      outcome.postRestCorrections,
    )
    assertEquals(
      "seed ${case.seed} '${case.formula}' had to be finished for ${outcome.forcedSettles} dice",
      0,
      outcome.forcedSettles,
    )
  }

  private companion object {
    const val TAG = "dinfinity.golden"

    /** The fixture carries the scale to three places, so compare it to three. */
    const val SCALE_TOLERANCE = 5e-4
  }
}
