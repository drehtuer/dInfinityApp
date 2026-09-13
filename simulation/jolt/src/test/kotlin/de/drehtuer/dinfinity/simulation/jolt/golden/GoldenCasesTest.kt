package de.drehtuer.dinfinity.simulation.jolt.golden

import de.drehtuer.dinfinity.fixtures.Fixtures
import de.drehtuer.dinfinity.fixtures.GoldenCase
import de.drehtuer.dinfinity.fixtures.GoldenInput
import de.drehtuer.dinfinity.fixtures.GoldenOutcome
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of the golden determinism suite that runs on every CI build.
 *
 * The engine is native and cannot be stepped on the JVM, so the faces are the
 * device tier's job (`GoldenDeterminismTest`). Everything *before* the first
 * step can be checked here and is: which dice a formula resolves to, how far
 * the capacity rule shrank them, where each one starts, how hard it is thrown,
 * the hull that will be collided, and the gravity of every step of the shake.
 *
 * That is the part of a roll that is judgement rather than physics, and it is
 * also the part most likely to be changed by accident — a constant tuned, a
 * random draw added, a solid's closed form rearranged. Any of those would
 * silently change every roll the app has ever made, and this is what notices
 * (`docs/architecture.md`, decision 40).
 */
class GoldenCasesTest {
  private val cases = Fixtures.goldenCases()

  @Test
  fun `there are golden cases, and they cover the notation`() {
    assertTrue("the golden suite is empty", cases.size >= MINIMUM_CASES)
    assertEquals("two cases with one seed are one case", cases.size, cases.map(GoldenCase::seed).toSet().size)
    assertTrue(
      "a suite with no shake in it does not test the shake",
      cases.any { it.input == GoldenInput.Shake },
    )
  }

  @Test
  fun `every golden case has been recorded`() {
    cases.forEach { case ->
      assertNotNull(
        "seed ${case.seed} '${case.formula}' has no recorded outcome. " +
          "Record it on a device — `docs/build-setup.md`, 'Re-recording the golden cases'",
        case.expected,
      )
    }
  }

  @Test
  fun `the capacity rule shrinks each case's dice exactly as far as it did`() {
    recorded { case, expected ->
      assertEquals(
        "'${case.formula}' is thrown at a different scale than it was recorded at",
        expected.scale,
        GoldenThrow.specOf(case).dieScale,
        SCALE_TOLERANCE,
      )
    }
  }

  @Test
  fun `every die still starts where it was recorded as starting`() {
    recorded { case, expected ->
      assertEquals(
        "the throw handed to the engine for seed ${case.seed} '${case.formula}' has changed. " +
          "If that was deliberate, re-record on a device; if it was not, it has changed every roll",
        expected.spawn,
        GoldenThrow.spawnDigest(GoldenThrow.specOf(case)),
      )
    }
  }

  @Test
  fun `a golden case is a roll the table would actually take`() {
    recorded { case, _ ->
      val spec = GoldenThrow.specOf(case)
      assertTrue("'${case.formula}' resolved to no dice", spec.dice.isNotEmpty())
      assertTrue(
        "'${case.formula}' is past the engine's cap and could never have been recorded",
        spec.dice.size <= TableCapacity.MAX_DICE,
      )
    }
  }

  @Test
  fun `the recorded outcome has a face for every die and nothing forced`() {
    recorded { case, expected ->
      val spec = GoldenThrow.specOf(case)
      assertEquals(
        "'${case.formula}' throws ${spec.dice.size} dice but ${expected.faces.size} faces were recorded",
        spec.dice.size,
        expected.faces.size,
      )
      spec.dice.forEachIndexed { index, instance ->
        val face = expected.faces[index]
        assertTrue(
          "die $index of '${case.formula}' recorded face $face, which ${instance.die.id} does not have",
          face in instance.die.faces.indices,
        )
      }
      assertTrue("a roll recorded past the cap is a recorded bug", expected.steps in 1..SettleRule.HARD_CAP_STEPS)
      // A die may be corrected once per throw, and a re-thrown die is a fresh
      // throw that gets a fresh chance to be helped (`RollLoop`, rung 3). So
      // the bound is one per die plus one per re-throw — not one per die,
      // which held only while no recorded case had both.
      assertTrue(
        "'${case.formula}' recorded ${expected.corrections} corrections for ${spec.dice.size} dice " +
          "and ${expected.rethrows} re-throws, which is more nudges than there were chances to nudge",
        expected.corrections <= spec.dice.size + expected.rethrows,
      )
    }
  }

  /** Runs [check] over the cases that have an outcome to check against. */
  private fun recorded(check: (GoldenCase, GoldenOutcome) -> Unit) {
    cases.forEach { case -> case.expected?.let { check(case, it) } }
  }

  private companion object {
    const val MINIMUM_CASES = 10

    /** The fixture carries the scale to three places, so compare it to three. */
    const val SCALE_TOLERANCE = 5e-4
  }
}
