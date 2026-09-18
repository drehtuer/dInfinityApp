package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.CorrectionLadder
import de.drehtuer.dinfinity.simulation.api.SettleRule
import java.util.Locale

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
 * **[leastTurnsAfterLanding] is the one bar that is a floor**, and it is here
 * because every other row can be met by a die that never rolls. Dice that drop
 * dead on the felt settle fast, never stack, never overlap and never run out
 * the cap — a perfect scorecard for a throw that reads as a number being
 * placed rather than thrown. It is still a limit in the sense above; it is the
 * direction that differs, and it is named so that nobody reading the table
 * takes a small number for a good one.
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
 * @param p99FrameMillis Step 5.7's frame budget, which *is* about drawing:
 *   60 fps sustained at twenty dice. A run that drew nothing is scored
 *   [TargetOutcome.NotMeasured] against it rather than passing it — the one
 *   rule that keeps a headless run from quietly claiming a frame rate
 *   ([FrameTimes]).
 * @param leastTurnsAfterLanding how far the middle die must turn after it
 *   first touches the table, in whole turns — **a floor, not a ceiling**. One
 *   whole turn is the bar because that is about what it takes to see a die
 *   topple from the face it landed on onto the one it is read from; below it a
 *   die is arriving and stopping rather than rolling (`Tumble`).
 * @param droppedSteps steps a late frame never paid for, over a paced run.
 *   Zero: the roll comes to the same faces either way, so what this catches is
 *   a frame that could not keep up, which is what Step 5.7 is about
 *   ([de.drehtuer.dinfinity.simulation.api.FrameClock.droppedSteps]).
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
  val p99FrameMillis: Double = P99_FRAME_MILLIS,
  val leastTurnsAfterLanding: Double = LEAST_TURNS_AFTER_LANDING,
  val droppedSteps: Long = 0,
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
        atMost("rolls that gave up", summary.capsReached.toLong(), capsReached.toLong()),
        // Nought by construction rather than by measurement: a forced settle
        // was a die read off a face it never landed on, and there is no code
        // left that can produce one. Kept as a row so that a change which
        // brought one back would show up here rather than pass unnoticed.
        atMost("forced settles", summary.forcedSettles, forcedSettles),
        millimetres("deepest die-die overlap", summary.deepestDiePenetrationMm, deepestDiePenetrationMm),
        atLeast("turns after landing", summary.turnsAfterLanding.median, leastTurnsAfterLanding),
        millis("p99 step time", summary.stepWallMillis.p99, p99StepWallMillis),
        frameTime(summary.frames),
        dropped(summary.frames),
      ),
    )

  /**
   * Step 5.7's frame budget, scored only against frames somebody drew.
   *
   * Three outcomes and not two, and the third is the important one. A headless
   * run has no frames at all, and a paced run in this harness has frames whose
   * cost is the simulation half of one — neither is the figure this bar is
   * about, and neither may be reported as meeting it. What is printed instead
   * is what there is: nothing, or the half that was measured, said to be a
   * half (`docs/build-setup.md`, "The physics harness").
   */
  private fun frameTime(frames: FrameSummary?): TargetResult =
    when {
      frames == null ->
        TargetResult(FRAME_TIME, written("%.2f ms", p99FrameMillis), NOT_MEASURED, TargetOutcome.NotMeasured)
      !frames.drawn ->
        TargetResult(
          FRAME_TIME,
          written("%.2f ms", p99FrameMillis),
          written("%.2f ms", frames.millis.p99) + " (simulation only)",
          TargetOutcome.NotMeasured,
        )
      else -> millis(FRAME_TIME, frames.millis.p99, p99FrameMillis)
    }

  /**
   * Steps a late frame dropped, which a paced run measures whether or not it
   * drew anything: keeping up with the clock is the simulation's half of a
   * frame, and that half is exactly what this harness runs.
   */
  private fun dropped(frames: FrameSummary?): TargetResult =
    if (frames == null) {
      TargetResult(DROPPED_STEPS, droppedSteps.toString(), NOT_MEASURED, TargetOutcome.NotMeasured)
    } else {
      atMost(DROPPED_STEPS, frames.droppedSteps, droppedSteps)
    }

  private fun atMost(
    name: String,
    measured: Long,
    bar: Long,
  ): TargetResult = TargetResult(name, bar.toString(), measured.toString(), TargetOutcome.of(measured <= bar))

  /**
   * The one row scored the other way up, with its bar written so it reads as
   * one: `>= 1.00` rather than `1.00`, because a column of ceilings with a
   * single floor hidden in it is a table that lies to whoever skims it.
   */
  private fun atLeast(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult =
    TargetResult(
      name,
      written(">= %.2f", bar),
      written("%.2f", measured),
      TargetOutcome.of(measured >= bar),
    )

  private fun share(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult =
    TargetResult(
      name,
      written("%.3f %%", bar * PERCENT),
      written("%.3f %%", measured * PERCENT),
      TargetOutcome.of(measured <= bar),
    )

  private fun seconds(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult =
    TargetResult(name, written("%.2f s", bar), written("%.2f s", measured), TargetOutcome.of(measured <= bar))

  private fun millis(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult =
    TargetResult(name, written("%.2f ms", bar), written("%.2f ms", measured), TargetOutcome.of(measured <= bar))

  private fun millimetres(
    name: String,
    measured: Double,
    bar: Double,
  ): TargetResult =
    TargetResult(name, written("%.3f mm", bar), written("%.3f mm", measured), TargetOutcome.of(measured <= bar))

  /**
   * A number written the way the report writes every number.
   *
   * [Locale.ROOT] rather than the device's own, and this is not a nicety: the
   * phone this is run against is set to German, so the first table it printed
   * read `0,500 %` against `43,550 %`. A run is a *measurement*, compared with
   * the run before it, pasted into a pull request and read by whoever is not
   * holding the phone — and a decimal point that depends on whose phone it was
   * is a measurement that cannot be compared with anything
   * (`docs/build-setup.md`).
   */
  private fun written(
    how: String,
    value: Double,
  ): String = String.format(Locale.ROOT, how, value)

  companion object {
    /** Where the middle roll of twenty dice has to have settled by (Step 5.5). */
    const val MEDIAN_SETTLE_SECONDS: Double = 2.0

    /** And the hundredth roll. */
    const val P99_SETTLE_SECONDS: Double = 4.0

    /** How far one die may ever be inside another (Step 5.4). */
    const val DEEPEST_PENETRATION_MM: Double = 0.2

    /** A sixtieth of a second, which is Step 5.7's frame budget at twenty dice. */
    const val P99_FRAME_MILLIS: Double = 16.6

    /**
     * How far the middle die must turn once it is down, in whole turns.
     *
     * One turn, because that is roughly what it takes to watch a die topple
     * off the face it landed on onto the one it is read from. It is a
     * judgement about what a thrown die looks like rather than a measurement
     * of what this engine does, which is the same footing every other bar
     * here is on.
     */
    const val LEAST_TURNS_AFTER_LANDING: Double = 1.0

    /** What the frame rows are called, in one place so the table and its tests agree. */
    const val FRAME_TIME: String = "p99 frame time"

    /** And the steps a frame that ran long never paid for. */
    const val DROPPED_STEPS: String = "steps a late frame dropped"

    /** What a row says when there was nothing to measure it from. */
    const val NOT_MEASURED: String = "-"

    /** Shares are read as percentages, because that is how the targets are written. */
    const val PERCENT: Double = 100.0

    const val MILLIS_PER_SECOND: Double = 1000.0
  }
}

/**
 * How a run came out against one target.
 *
 * **Three, not two.** A target nothing was measured for is neither met nor
 * missed, and calling it either is a lie in one direction or the other: a
 * headless run that reported "pass" on a frame-rate target would be a harness
 * claiming a frame rate it never saw, and one that reported "FAIL" would stop
 * every run that is not about frames (`docs/TODO.md`, Step 5.1).
 */
enum class TargetOutcome(
  /** The word the table prints in the result column. */
  val word: String,
  /** And the token the JSON document carries, which is read back by name. */
  val token: String,
) {
  Pass("pass", "pass"),
  Fail("FAIL", "fail"),
  NotMeasured("not measured", "not-measured"),
  ;

  companion object {
    /** [met] as an outcome, for the bars that are a plain comparison. */
    fun of(met: Boolean): TargetOutcome = if (met) Pass else Fail

    /**
     * The outcome [token] names.
     *
     * Refused rather than guessed, for the same reason every other field of
     * the document is: a verdict that could not be read is worth saying so
     * about ([HarnessJson]).
     */
    fun ofToken(token: String): TargetOutcome =
      entries.firstOrNull { it.token == token }
        ?: throw IllegalArgumentException(
          "the harness document's \"result\" is $token, which is none of " + entries.joinToString { it.token },
        )
  }
}

/**
 * One target, and how the run came out against it.
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
  val outcome: TargetOutcome,
) {
  /** True only when the target was met. A target nothing was measured for was not. */
  val passed: Boolean get() = outcome == TargetOutcome.Pass
}

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
  /**
   * True when nothing was missed.
   *
   * A row nothing was measured for does not fail the run — a headless run is
   * not a broken one — but it does not pass silently either: [verdict] says how
   * many there were, so a green line with a gap in it reads as a green line
   * with a gap in it. An empty scorecard has met nothing and no bar, so it
   * passes.
   */
  val passed: Boolean get() = failures.isEmpty()

  /** The targets that were missed, which is what a failing run is about. */
  val failures: List<TargetResult> get() = rows.filter { it.outcome == TargetOutcome.Fail }

  /** And the ones this run could not answer for at all. */
  val notMeasured: List<TargetResult> get() = rows.filter { it.outcome == TargetOutcome.NotMeasured }

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
        rows.forEach { add(row(widths, it.name, it.bar, it.measured, it.outcome.word)) }
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
  fun verdict(): String {
    val gap = if (notMeasured.isEmpty()) "" else ", ${notMeasured.size} not measured"
    return if (passed) {
      "$VERDICT PASS$gap"
    } else {
      "$VERDICT FAIL (${failures.size} of ${rows.size}$gap)"
    }
  }

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
  ): String =
    String.format(
      Locale.ROOT,
      "%-${widths.name}s  %${widths.bar}s  %${widths.measured}s  %s",
      name,
      bar,
      measured,
      result,
    )

  companion object {
    /** The prefix the script greps for. */
    const val VERDICT: String = "HARNESS VERDICT:"

    private const val TARGET_HEADING = "target"
    private const val BAR_HEADING = "bar"
    private const val MEASURED_HEADING = "measured"
    private const val RESULT_HEADING = "result"
  }
}
