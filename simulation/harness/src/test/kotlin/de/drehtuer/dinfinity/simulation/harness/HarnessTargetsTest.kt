package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.CorrectionLadder
import de.drehtuer.dinfinity.simulation.api.SettleRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The comparison that decides whether a device run passed.
 *
 * Every boundary is pinned here rather than discovered on a phone: a target
 * whose behaviour exactly on the bar is unknown is a target two people will
 * read two ways.
 */
class HarnessTargetsTest {
  @Test
  fun `a run that meets every bar passes`() {
    val scorecard = HarnessTargets().score(perfect())

    assertTrue(scorecard.passed, scorecard.table())
    assertEquals(emptyList(), scorecard.failures)
    assertEquals(TARGET_COUNT, scorecard.rows.size)
  }

  @Test
  fun `one die left standing on another fails the run`() {
    assertMisses("dice at rest on another die", perfect().copy(stackedAtRest = 1))
  }

  @Test
  fun `one correction after rest fails the run, because it is a bug and not a statistic`() {
    assertMisses("corrections after rest", perfect().copy(postRestCorrections = 1))
  }

  @Test
  fun `the correction budget is the one CorrectionLadder already holds`() {
    val onTheBar = perfect().copy(dice = 1_000, corrections = (CorrectionLadder.CORRECTION_BUDGET * 1_000).toLong())
    assertTrue(HarnessTargets().score(onTheBar).passed, HarnessTargets().score(onTheBar).table())

    assertMisses("dice corrected", onTheBar.copy(corrections = onTheBar.corrections + 1))
  }

  @Test
  fun `the re-throw budget is too, and it is the tighter of the two`() {
    val onTheBar = perfect().copy(dice = 100_000, rethrows = (CorrectionLadder.RETHROW_BUDGET * 100_000).toLong())
    assertTrue(HarnessTargets().score(onTheBar).passed)

    assertMisses("dice re-thrown", onTheBar.copy(rethrows = onTheBar.rethrows + 1))
  }

  @Test
  fun `today's measured correction rate fails, and the harness says so rather than bending`() {
    // 45 % of dice corrected against a 0.5 % budget, which is where the Pixel
    // 10a is today (`docs/TODO.md`, Step 5.5). The check is not the thing that
    // is behind.
    val measured = perfect().copy(dice = 2_000, corrections = 900)

    assertMisses("dice corrected", measured)
  }

  @Test
  fun `a median settle exactly on two seconds passes, and a step past it does not`() {
    assertTrue(HarnessTargets().score(perfect().copy(settleSeconds = Distribution(2.0, 3.0, 3.0))).passed)
    assertMisses("median settle", perfect().copy(settleSeconds = Distribution(2.01, 3.0, 3.0)))
  }

  @Test
  fun `a p99 settle exactly on four seconds passes, and a step past it does not`() {
    assertTrue(HarnessTargets().score(perfect().copy(settleSeconds = Distribution(1.0, 4.0, 4.0))).passed)
    assertMisses("p99 settle", perfect().copy(settleSeconds = Distribution(1.0, 4.01, 4.01)))
  }

  @Test
  fun `a single roll that reached the cap fails, valve or not`() {
    assertMisses("rolls that hit the 12 s cap", perfect().copy(capsReached = 1))
  }

  @Test
  fun `a forced settle fails on its own, even in a run that finished inside the cap`() {
    assertMisses("forced settles", perfect().copy(forcedSettles = 1))
  }

  @Test
  fun `an overlap exactly at two tenths of a millimetre passes, and deeper does not`() {
    assertTrue(HarnessTargets().score(perfect().copy(deepestDiePenetrationMm = 0.2)).passed)
    assertMisses("deepest die-die overlap", perfect().copy(deepestDiePenetrationMm = 0.2001))
  }

  @Test
  fun `a step simulated in less time than it covers passes, and a slower one does not`() {
    val bar = SettleRule.TIMESTEP_SECONDS * HarnessTargets.MILLIS_PER_SECOND
    assertTrue(HarnessTargets().score(perfect().copy(stepWallMillis = Distribution(1.0, bar, bar))).passed)
    assertMisses("p99 step time", perfect().copy(stepWallMillis = Distribution(1.0, bar * 2, bar * 2)))
  }

  @Test
  fun `a bar can be moved deliberately, which is what makes them data rather than ifs`() {
    val relaxed = HarnessTargets(medianSettleSeconds = 5.0)

    assertTrue(relaxed.score(perfect().copy(settleSeconds = Distribution(4.0, 4.0, 4.0))).passed)
  }

  @Test
  fun `the table names every target, its bar, what was measured and the verdict`() {
    val table = HarnessTargets().score(perfect().copy(stackedAtRest = 2)).table()

    assertTrue(table.contains("target"), table)
    assertTrue(table.contains("dice at rest on another die"), table)
    assertTrue(table.contains("FAIL"), table)
    assertTrue(table.lines().last().startsWith(Scorecard.VERDICT), table)
    assertEquals("${Scorecard.VERDICT} FAIL (1 of $TARGET_COUNT)", table.lines().last())
  }

  @Test
  fun `a passing run's last line is the one word a script needs`() {
    assertEquals("${Scorecard.VERDICT} PASS", HarnessTargets().score(perfect()).verdict())
  }

  @Test
  fun `a long target name widens the column for every row under it`() {
    val scorecard =
      Scorecard(
        listOf(
          TargetResult("a", "1", "0", passed = true),
          TargetResult("a considerably longer target name", "1000", "0", passed = true),
        ),
      )
    val table = scorecard.table()
    // The heading and the rule are dropped, and so is the verdict: what is
    // left is two rows whose result cells are the same word, so any difference
    // in length is a column that did not line up.
    val rows = table.lines().drop(2).dropLast(1)

    assertEquals(1, rows.map(String::length).toSet().size, table)
  }

  @Test
  fun `a scorecard with no targets has missed none`() {
    assertTrue(Scorecard(emptyList()).passed)
    assertEquals("${Scorecard.VERDICT} PASS", Scorecard(emptyList()).verdict())
    assertTrue(Scorecard(emptyList()).table().isNotEmpty())
  }

  private fun assertMisses(
    target: String,
    summary: HarnessSummary,
  ) {
    val scorecard = HarnessTargets().score(summary)

    assertFalse(scorecard.passed, scorecard.table())
    assertEquals(listOf(target), scorecard.failures.map(TargetResult::name), scorecard.table())
  }

  /** A run that meets every bar, which each test then spoils in exactly one way. */
  private fun perfect(): HarnessSummary =
    HarnessSummary(
      rolls = 100,
      dice = 2_000,
      settleSeconds = Distribution(median = 1.2, p99 = 3.0, worst = 3.4),
      wallMillis = Distribution(median = 80.0, p99 = 200.0, worst = 240.0),
      stepWallMillis = Distribution(median = 0.5, p99 = 1.0, worst = 2.0),
      corrections = 0,
      postRestCorrections = 0,
      rethrows = 0,
      forcedSettles = 0,
      stackedAtRest = 0,
      capsReached = 0,
      deepestDiePenetrationMm = 0.01,
    )

  private companion object {
    /** How many bars Step 5 sets. A row that disappears is a target nobody is checking. */
    const val TARGET_COUNT = 10
  }
}
