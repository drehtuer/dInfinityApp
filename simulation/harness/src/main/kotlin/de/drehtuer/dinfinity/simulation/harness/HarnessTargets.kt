package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.CorrectionLadder
import de.drehtuer.dinfinity.simulation.api.SettleRule

/**
 * The bars Step 5 sets, written down as data so that a run can be scored
 * against them by a function rather than by somebody reading a table
 * (`docs/TODO.md`, Steps 5.3 to 5.7).
 *
 * **Nothing here is relaxed to match what the engine does today.** Two of
 * these targets are currently missed and known to be missed — the correction
 * rate sits near 45 % against a 0.5 % budget, and `100d4` runs out of the
 * twelve-second cap on some seeds — and the harness reports both as failures.
 * That is the point of having a harness: the plan is behind the check, and
 * moving the check to meet the engine would be moving the goal to meet the
 * shot (`.claude/CLAUDE.md`). Where today's worst case is worth *recording* so
 * it cannot quietly get worse, that belongs in a test's own bound, as
 * `JoltBridgeTest` does it — not here.
 *
 * The two share budgets are taken from [CorrectionLadder] rather than repeated,
 * because a budget with two homes is a budget that will eventually disagree
 * with itself.
 *
 * A bar is a **limit**, so a measurement exactly on it passes. That is the
 * sense `CorrectionLadder.withinBudget` already uses, and one comparison for
 * every row is worth more than arguing each one separately.
 *
 * @param stackedAtRest dice left standing on another die, over the whole run.
 *   Zero (Step 5.5).
 * @param postRestCorrections dice touched after they had come to rest. Zero,
 *   and one occurrence is a bug rather than a statistic (Step 5.5).
 * @param correctedShare the share of dice that may need any correction at all.
 * @param rethrownShare the share that may need the last resort.
 * @param medianSettleSeconds where the middle roll must settle by (Step 5.5,
 *   stated at 20 dice).
 * @param p99SettleSeconds and where the hundredth roll must.
 * @param capsReached rolls that ran out of their twelve seconds. Zero: the cap
 *   is a safety valve, and a valve that fires is a failure whatever the roll
 *   came to (Step 5.3).
 * @param forcedSettles dice the simulation had to finish for. Zero, for the
 *   same reason.
 * @param deepestDiePenetrationMm how far one die may ever be inside another
 *   (Step 5.4).
 * @param p99StepWallMillis how long the device may take over one simulated
 *   step. The bar is the step itself — 1/120 s — because a roll is only real
 *   time while the machine can simulate a step in less time than the step
 *   covers. It is **not** Step 5.7's 16.6 ms frame budget: that is a figure
 *   about drawing, and this harness is headless and draws nothing.
 */
data class HarnessTargets(
  val stackedAtRest: Long = 0,
  val postRestCorrections: Long = 0,
  val correctedShare: Double = CorrectionLadder.CORRECTION_BUDGET,
  val rethrownShare: Double = CorrectionLadder.RETHROW_BUDGET,
  val medianSettleSeconds: Double = MEDIAN_SETTLE_SECONDS,
  val p99SettleSeconds: Double = P99_SETTLE_SECONDS,
  val capsReached: Int = 0,
  val forcedSettles: Long = 0,
  val deepestDiePenetrationMm: Double = DEEPEST_PENETRATION_MM,
  val p99StepWallMillis: Double = SettleRule.TIMESTEP_SECONDS * MILLIS_PER_SECOND,
) {
  /**
   * [summary] against every bar above, in the order Step 5 states them.
   *
   * Pure, and the reason the whole module exists: the comparison that decides
   * whether a device run passed is arithmetic, and arithmetic belongs where a
   * JVM test can reach every branch of it — including the ones a phone would
   * have to misbehave to produce (`docs/architecture.md`, decision 40).
   */
  fun score(summary: HarnessSummary): Scorecard =
    Scorecard(
      listOf(
        atMost("dice at rest on another die", summary.stackedAtRest, stackedAtRest),
        atMost("corrections after rest", summary.postRestCorrections, postRestCorrections),
        share("dice corrected", summary.correctedShare, correctedShare),
        share("dice re-thrown", summary.rethrownShare, rethrownShare),
        seconds("median settle", summary.settleSeconds.median, medianSettleSeconds),
        seconds("p99 settle", summary.settleSeconds.p99, p99SettleSeconds),
        atMost("rolls that hit the 12 s cap", summary.capsReached.toLong(), capsReached.toLong()),
        atMost("forced settles", summary.forcedSettles, forcedSettles),
        millimetres("deepest die-die overlap", summary.deepestDiePenetrationMm, deepestDiePenetrationMm),
        millis("p99 step time", summary.stepWallMillis.p99, p99StepWallMillis),
      ),
    )

  private fun atMost(
    name: String,
    measured: Long,
    bar: Long,
  ): TargetResult = TargetResult(name, bar.toString(), measured.toString(), measured <= bar)

  private fun share(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult =
    TargetResult(
      name,
      "%.3f %%".format(bar * PERCENT),
      "%.3f %%".format(measured * PERCENT),
      measured <= bar,
    )

  private fun seconds(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult = TargetResult(name, "%.2f s".format(bar), "%.2f s".format(measured), measured <= bar)

  private fun millis(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult = TargetResult(name, "%.2f ms".format(bar), "%.2f ms".format(measured), measured <= bar)

  private fun millimetres(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult = TargetResult(name, "%.3f mm".format(bar), "%.3f mm".format(measured), measured <= bar)

  companion object {
    /** Where the middle roll of twenty dice has to have settled by (Step 5.5). */
    const val MEDIAN_SETTLE_SECONDS: Double = 2.0

    /** And the hundredth roll. */
    const val P99_SETTLE_SECONDS: Double = 4.0

    /** How far one die may ever be inside another (Step 5.4). */
    const val DEEPEST_PENETRATION_MM: Double = 0.2

    /** Shares are read as percentages, because that is how the targets are written. */
    const val PERCENT: Double = 100.0

    const val MILLIS_PER_SECOND: Double = 1000.0
  }
}

/**
 * One target, and whether the run met it.
 *
 * The bar and the measurement are strings rather than numbers because they are
 * read rather than computed with — a share in percent, a time in seconds, a
 * depth in millimetres — and a table of numbers with no units is a table
 * nobody can check. The numbers themselves are in [HarnessSummary], which is in
 * the same document.
 */
data class TargetResult(
  val name: String,
  val bar: String,
  val measured: String,
  val passed: Boolean,
)

/**
 * A whole run against every target.
 *
 * It is what the devcontainer script prints and what the instrumented test
 * asserts on, and both get it from here rather than each writing their own
 * (`tools/harness.sh`, `docs/build-setup.md`).
 */
data class Scorecard(
  val rows: List<TargetResult>,
) {
  /** True when every target was met. An empty scorecard has met nothing and no bar, so it passes. */
  val passed: Boolean get() = rows.all(TargetResult::passed)

  /** The targets that were missed, which is what a failing run is about. */
  val failures: List<TargetResult> get() = rows.filterNot(TargetResult::passed)

  /**
   * The scorecard as a plain-text table, ending in the verdict line.
   *
   * Written here rather than in the shell script that shows it: the script
   * pulls this text off the device and prints it unchanged, so there is one
   * renderer and no second copy of the comparison to drift
   * (`tools/harness.sh`).
   */
  fun table(): String {
    val widths =
      Widths(
        name = widest(TARGET_HEADING) { it.name },
        bar = widest(BAR_HEADING) { it.bar },
        measured = widest(MEASURED_HEADING) { it.measured },
      )
    val lines =
      buildList {
        add(row(widths, TARGET_HEADING, BAR_HEADING, MEASURED_HEADING, RESULT_HEADING))
        add(row(widths, rule(widths.name), rule(widths.bar), rule(widths.measured), rule(RESULT_HEADING.length)))
        rows.forEach { add(row(widths, it.name, it.bar, it.measured, if (it.passed) PASSED else FAILED)) }
        add(verdict())
      }
    return lines.joinToString("\n")
  }

  /**
   * The one line a script has to read.
   *
   * Machine-readable on purpose, and deliberately the last line of [table]:
   * `tools/harness.sh` takes its exit code from it, so a run that fails on the
   * phone fails in the terminal too rather than printing a red row that
   * scrolls past.
   */
  fun verdict(): String = if (passed) "$VERDICT PASS" else "$VERDICT FAIL (${failures.size} of ${rows.size})"

  /** How wide each column has to be to hold its heading and every cell under it. */
  private data class Widths(
    val name: Int,
    val bar: Int,
    val measured: Int,
  )

  private fun widest(
    heading: String,
    cell: (TargetResult) -> String,
  ): Int = maxOf(heading.length, rows.maxOfOrNull { cell(it).length } ?: 0)

  private fun rule(width: Int): String = "-".repeat(width)

  private fun row(
    widths: Widths,
    name: String,
    bar: String,
    measured: String,
    result: String,
  ): String = "%-${widths.name}s  %${widths.bar}s  %${widths.measured}s  %s".format(name, bar, measured, result)

  companion object {
    /** The prefix the script greps for. */
    const val VERDICT: String = "HARNESS VERDICT:"

    private const val TARGET_HEADING = "target"
    private const val BAR_HEADING = "bar"
    private const val MEASURED_HEADING = "measured"
    private const val RESULT_HEADING = "result"
    private const val PASSED = "pass"
    private const val FAILED = "FAIL"
  }
}
