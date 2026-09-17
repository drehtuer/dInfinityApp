package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome

/**
 * What a run of the device harness came to (`docs/TODO.md`, Step 5.1).
 *
 * **The document is a type, not a string.** The runner that produces it is an
 * instrumented test and can only be exercised on a phone or the emulator, so
 * nothing about the *shape* of the document, the arithmetic over it or the
 * comparison against the targets is allowed to live there: all of it is here,
 * in a plain Kotlin module a JVM test can round-trip and assert on
 * (`docs/architecture.md`, decision 40). The device's only job is to roll the
 * dice, time them, and hand the numbers over.
 *
 * @param facts what was run, and on what.
 * @param rolls one record per roll, in the order they were thrown. Kept whole
 *   rather than summarised away, because a run that fails is a run somebody
 *   will want to look through.
 * @param summary the same rolls added up ([HarnessSummary.of]).
 * @param scorecard the summary against the targets ([HarnessTargets.score]).
 *   Written into the document as well as printed, so a run's verdict survives
 *   the terminal it was printed in.
 */
data class HarnessReport(
  val facts: RunFacts,
  val rolls: List<RollRecord>,
  val summary: HarnessSummary,
  val scorecard: Scorecard,
) {
  companion object {
    /**
     * The schema version of the JSON document.
     *
     * Written into every file so that a run recorded by an older harness can
     * be recognised rather than misread. It is not a saved format the app
     * loads — nothing but this module ever reads it — so it may move freely;
     * what it may not do is move silently.
     */
    const val SCHEMA: Int = 2

    /**
     * A report over [records], summarised and scored in one go.
     *
     * [frames] is what a paced run measured at the frame clock and is
     * [FrameTimes.Nothing] for the headless run the harness makes by default —
     * which is how a frame-time figure comes to be absent rather than zero.
     */
    fun of(
      facts: RunFacts,
      records: List<RollRecord>,
      frames: FrameTimes = FrameTimes.Nothing,
      targets: HarnessTargets = HarnessTargets(),
    ): HarnessReport {
      val summary = HarnessSummary.of(facts.diceCount, records, frames)
      return HarnessReport(facts, records, summary, targets.score(summary))
    }
  }
}

/**
 * What was thrown, how often, and on what.
 *
 * @param label a name for the run, which is also the name of the file it is
 *   written to. `20d20` and `100d4` are two runs of the same harness and two
 *   different questions.
 * @param shapeId the catalogue shape every die in the throw was
 *   (`docs/dice-sets.md`). One shape per run: a mixed throw is its own run
 *   with its own label.
 * @param diceCount how many dice were in each throw.
 * @param dieScale how far down the capacity rule shrank them
 *   (`docs/tables.md`), recorded because the settle time of twenty dice at
 *   full size and twenty at 0.4 are not the same measurement.
 * @param length what was asked for: a number of throws, or a length of time
 *   (soak mode). Recorded beside [rolls] rather than instead of it, because
 *   "roll for five minutes" and "it managed 143 throws" are two different facts
 *   and a soak is only readable with both.
 * @param rolls how many throws were actually made. The same as the roll count
 *   for a counted run, and the answer to "how far did it get" for a soak.
 * @param framePaced whether each roll was stepped on a frame's cadence rather
 *   than run flat out. A paced run's wall times are the work a frame did, with
 *   the waiting between frames left out ([FrameTimes]).
 * @param seed the run's base seed; roll *n* is thrown with a seed derived from
 *   it, so a whole run replays from this one number.
 * @param device what it ran on. A settle time in milliseconds is a fact about
 *   a phone, and a figure with no phone attached to it says nothing.
 * @param startedAtEpochMs when the run began, for telling two runs apart.
 */
data class RunFacts(
  val label: String,
  val shapeId: String,
  val diceCount: Int,
  val dieScale: Double,
  val length: RunLength,
  val rolls: Int,
  val framePaced: Boolean,
  val seed: Long,
  val device: DeviceFacts,
  val startedAtEpochMs: Long,
)

/**
 * The device a run happened on.
 *
 * @param model what the phone calls itself, or the emulator its AVD.
 * @param abi which of the two ABIs the physics was compiled for
 *   (`docs/build-setup.md`). The emulator is `x86_64` and the phone is
 *   `arm64-v8a`, and a divergence between them is a release blocker.
 * @param androidApi the API level it is running.
 * @param emulator true when this was the container's emulator rather than a
 *   phone. The emulator is software-rendered and shares a processor with
 *   everything else in the container, so its wall-clock figures are a
 *   regression signal and not a measurement of the reference device.
 */
data class DeviceFacts(
  val model: String,
  val abi: String,
  val androidApi: Int,
  val emulator: Boolean,
) {
  companion object {
    /**
     * The device, told apart from the emulator by the hardware it says it is.
     *
     * A rule rather than a guess made on the phone: `android.os.Build` is the
     * only Android thing the instrumented runner touches, and what its values
     * *mean* is a judgement, so it is made here where a test can check it
     * (`docs/architecture.md`, decision 40).
     */
    fun of(
      model: String,
      abi: String,
      androidApi: Int,
      hardware: String,
    ): DeviceFacts = DeviceFacts(model, abi, androidApi, hardware.trim().lowercase() in EMULATOR_HARDWARE)

    /**
     * The hardware names Android's own emulators report.
     *
     * `goldfish` is the old emulator kernel and `ranchu` the current one,
     * which is what the container's AVD runs (`docs/build-setup.md`); the
     * other two are Cuttlefish, which the same script would reach if it ever
     * pointed at one.
     */
    val EMULATOR_HARDWARE: Set<String> = setOf("goldfish", "ranchu", "gce_x86", "cutf_cvm")
  }
}

/**
 * One roll's numbers.
 *
 * Everything but [wallMillis] is simulated time or a count, so it is the same
 * on every device and every ABI (`docs/physics-and-rendering.md`, "Timestep
 * and determinism"). [wallMillis] is the one figure that belongs to the phone.
 *
 * @param index which roll of the run this was.
 * @param seed the seed it was thrown with, so one roll can be replayed alone.
 * @param steps how many fixed steps it took — simulated time, and the honest
 *   settle time.
 * @param wallMillis how long the device took to simulate it, start to finish.
 * @param corrections dice nudged while they were still moving (rung 2).
 * @param postRestCorrections dice touched after they had stopped. Always zero,
 *   and a single one is a bug rather than a statistic.
 * @param rethrows dice picked up and thrown again (rung 3).
 * @param forcedSettles dice the simulation had to finish for.
 * @param stackedAtRest dice that came to rest standing on another die.
 * @param deepestDiePenetrationMm the deepest die-into-die overlap the solver
 *   reported at any step of this roll.
 */
data class RollRecord(
  val index: Int,
  val seed: Long,
  val steps: Int,
  val wallMillis: Double,
  val corrections: Int,
  val postRestCorrections: Int,
  val rethrows: Int,
  val forcedSettles: Int,
  val stackedAtRest: Int,
  val deepestDiePenetrationMm: Double,
  /**
   * True when this roll gave up: it ran longer than a roll should and its dice
   * never stopped, so it has no faces to report.
   *
   * It used to be derived from the step count, because a roll that ran out of
   * time was force-settled and still produced an outcome. There is no such
   * outcome any more — a roll either reads every die or says it could not — so
   * this is recorded rather than inferred (`SettleRule.HARD_CAP_SECONDS`).
   */
  val gaveUp: Boolean = false,
) {
  /** How long the roll took in simulated seconds, which is what the targets are in. */
  val settleSeconds: Double get() = steps * SettleRule.TIMESTEP_SECONDS

  /** How long the device spent on one step of it, on average over the roll. */
  val stepWallMillis: Double get() = if (steps == 0) 0.0 else wallMillis / steps

  companion object {
    /**
     * A roll that gave up, with no faces to report.
     *
     * Everything a roll is measured by is about dice that stopped, so all of
     * it is nought here — and the run is scored on how *many* of these there
     * were, which is the one figure that matters about them.
     */
    fun gaveUp(
      index: Int,
      seed: Long,
      steps: Int,
      wallMillis: Double,
    ): RollRecord =
      RollRecord(
        index = index,
        seed = seed,
        steps = steps,
        wallMillis = wallMillis,
        corrections = 0,
        postRestCorrections = 0,
        rethrows = 0,
        forcedSettles = 0,
        stackedAtRest = 0,
        deepestDiePenetrationMm = 0.0,
        gaveUp = true,
      )

    /**
     * The record of one roll, from what the simulation reported and how long
     * the device took over it.
     *
     * The wall time is passed in rather than measured here: this module never
     * touches a clock, so a test of it is a test of arithmetic rather than of
     * how fast the machine running the test happens to be.
     */
    fun of(
      index: Int,
      seed: Long,
      outcome: SimulationOutcome,
      wallMillis: Double,
    ): RollRecord =
      RollRecord(
        index = index,
        seed = seed,
        steps = outcome.steps,
        wallMillis = wallMillis,
        corrections = outcome.corrections,
        postRestCorrections = outcome.postRestCorrections,
        rethrows = outcome.rethrows,
        forcedSettles = outcome.forcedSettles,
        stackedAtRest = outcome.stackedAtRest,
        deepestDiePenetrationMm = outcome.deepestDiePenetrationMm,
      )
  }
}

/**
 * Every roll of a run, added up.
 *
 * The counts are totals over the whole run and the distributions are over its
 * rolls. The two shares are per *die* rather than per roll, because that is
 * how `CorrectionLadder` states its budgets and how Step 5.5 states its
 * targets: "fewer than 0.5 % of **dice** need any correction".
 *
 * @param rolls how many throws the run made.
 * @param dice how many dice were thrown in total, across every roll.
 * @param settleSeconds how long a roll took, in simulated seconds.
 * @param wallMillis how long a roll took on the device.
 * @param stepWallMillis how long one step took on the device.
 * @param corrections dice nudged while still moving, over the whole run.
 * @param postRestCorrections dice touched after they had stopped. Zero.
 * @param rethrows dice thrown again.
 * @param forcedSettles dice the simulation finished for.
 * @param stackedAtRest dice left standing on another die.
 * @param capsReached rolls that gave up: they ran longer than a roll should
 *   and their dice never stopped, so they have no faces to report. They used
 *   to be force-settled and counted as rolls that happened.
 * @param deepestDiePenetrationMm the deepest overlap seen anywhere in the run.
 * @param frames what the run's frames cost, or **null** when it had none.
 *   Null rather than an empty distribution: a headless run did not measure a
 *   frame time of zero, it measured no frame times, and the scorecard says
 *   "not measured" rather than "pass" ([FrameTimes]).
 */
data class HarnessSummary(
  val rolls: Int,
  val dice: Long,
  val settleSeconds: Distribution,
  val wallMillis: Distribution,
  val stepWallMillis: Distribution,
  val corrections: Long,
  val postRestCorrections: Long,
  val rethrows: Long,
  val forcedSettles: Long,
  val stackedAtRest: Long,
  val capsReached: Int,
  val deepestDiePenetrationMm: Double,
  val frames: FrameSummary? = null,
) {
  /** The share of dice that needed any correction at all. */
  val correctedShare: Double get() = share(corrections)

  /** And the share that needed the last resort. */
  val rethrownShare: Double get() = share(rethrows)

  private fun share(count: Long): Double = if (dice == 0L) 0.0 else count.toDouble() / dice

  companion object {
    /**
     * [records] added up, for a run that threw [diceCount] dice each time,
     * with whatever [frames] the run measured.
     */
    fun of(
      diceCount: Int,
      records: List<RollRecord>,
      frames: FrameTimes = FrameTimes.Nothing,
    ): HarnessSummary =
      HarnessSummary(
        rolls = records.size,
        dice = diceCount.toLong() * records.size,
        settleSeconds = Distribution.of(records.map(RollRecord::settleSeconds)),
        wallMillis = Distribution.of(records.map(RollRecord::wallMillis)),
        stepWallMillis = Distribution.of(records.map(RollRecord::stepWallMillis)),
        corrections = records.sumOf { it.corrections.toLong() },
        postRestCorrections = records.sumOf { it.postRestCorrections.toLong() },
        rethrows = records.sumOf { it.rethrows.toLong() },
        forcedSettles = records.sumOf { it.forcedSettles.toLong() },
        stackedAtRest = records.sumOf { it.stackedAtRest.toLong() },
        capsReached = records.count(RollRecord::gaveUp),
        deepestDiePenetrationMm = records.maxOfOrNull { it.deepestDiePenetrationMm } ?: 0.0,
        frames = frames.summary(),
      )
  }
}
