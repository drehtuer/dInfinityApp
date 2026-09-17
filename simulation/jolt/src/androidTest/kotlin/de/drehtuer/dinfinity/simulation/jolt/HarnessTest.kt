package de.drehtuer.dinfinity.simulation.jolt

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.harness.DeviceFacts
import de.drehtuer.dinfinity.simulation.harness.FrameTimes
import de.drehtuer.dinfinity.simulation.harness.HarnessJson
import de.drehtuer.dinfinity.simulation.harness.HarnessPlan
import de.drehtuer.dinfinity.simulation.harness.HarnessReport
import de.drehtuer.dinfinity.simulation.harness.HarnessRequest
import de.drehtuer.dinfinity.simulation.harness.RollRecord
import de.drehtuer.dinfinity.simulation.harness.RunFacts
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.locks.LockSupport

/**
 * The Step 5 harness: N rolls, headless, and a JSON document of what they did
 * (`docs/TODO.md`, Step 5.1).
 *
 * **Almost nothing happens here.** The run is asked for in instrumentation
 * arguments, which `HarnessRequest` parses; the throw is worked out by
 * `HarnessRequest.plan`; the numbers are added up by `HarnessSummary`; the
 * comparison against the targets is `HarnessTargets.score`; and the document
 * is built by `HarnessJson`. All of that is plain Kotlin in
 * `:simulation:harness`, tested on the JVM, because a harness whose own
 * arithmetic can only be checked by running it on a phone is a harness nobody
 * can trust (`docs/architecture.md`, decision 40).
 *
 * What is left — and the only reason this file needs a device at all — is
 * rolling the dice, holding a stopwatch, and writing two files.
 *
 * ### Running it
 *
 * It does nothing without `harness.rolls` or `harness.soak`, so it can sit in
 * the ordinary device suite without adding minutes to it. `tools/harness.sh` is
 * the way in; by hand it is:
 *
 * ```sh
 * ./gradlew :simulation:jolt:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.harness.rolls=1000 \
 *   -Pandroid.testInstrumentationRunnerArguments.harness.dice=20 \
 *   -Pandroid.testInstrumentationRunnerArguments.harness.shape=d20
 * ```
 *
 * ### The three shapes a run comes in
 *
 * | | What the loop does |
 * | --- | --- |
 * | `harness.rolls` | throws that many times |
 * | `harness.soak` | goes on throwing until the time is up, and finishes the throw it is on |
 * | `harness.frames` | steps each throw the way the screen does — one `advance` per frame — and times the frames |
 *
 * The first two are the *same* loop asking `RunLength.keepGoing` a different
 * question, which is the whole of what soak mode is. The third changes how a
 * roll is stepped and nothing about what it comes to: the same seed comes to
 * the same faces paced or flat out, because `FrameClock` decides when steps are
 * taken and never how big they are (`docs/physics-and-rendering.md`, "The
 * simulation clock").
 *
 * ### What it asserts
 *
 * Every target in `HarnessTargets`, and it **fails** when one is missed. Two
 * of them are missed today and known to be missed — the correction rate and
 * `100d4` running out of the cap (`docs/TODO.md`, Steps 5.3 and 5.5). That is
 * the plan being behind the check, which is the right way round; the bound
 * that records where the engine has actually got to lives in `JoltBridgeTest`,
 * where it belongs.
 */
@RunWith(AndroidJUnit4::class)
class HarnessTest {
  @Test
  fun aRunOfRollsMeetsTheTargets() {
    val arguments = InstrumentationRegistry.getArguments()
    val asked = HarnessRequest.from { arguments.getString(it) }
    assumeTrue(
      "no run was asked for; pass -e ${HarnessRequest.ROLLS} <n> (tools/harness.sh)",
      asked != null,
    )
    val request = requireNotNull(asked)

    val plan = request.plan()
    val frames = Frames()
    val records = run(request, plan, frames)
    val report = HarnessReport.of(facts(request, plan, records.size), records, frames.measured(request))
    val table = report.scorecard.table()

    write(request.label, HarnessJson.encode(report), table)
    // Into logcat under System.out, so a run watched live says something
    // before the files are pulled. The files are the contract; this is the
    // courtesy.
    println(table)

    assertTrue(table, report.scorecard.passed)
  }

  /**
   * Every roll of the run, timed, until the run has had enough.
   *
   * One loop for both kinds of run: what a roll count and a soak differ in is
   * the answer to `RunLength.keepGoing`, which is asked after each throw and is
   * the only thing here that knows there are two kinds
   * (`docs/architecture.md`, decision 53). The throw under way when a soak's
   * time runs out is finished rather than cut short — a settle time that was
   * interrupted is the longest one in the sample and is not a fact about dice.
   *
   * `System.nanoTime` rather than the wall clock, because these are intervals
   * and the wall clock can step sideways; and around the whole roll rather
   * than around each step, because a stopwatch per step would be measuring
   * itself as much as the solver.
   */
  private fun run(
    request: HarnessRequest,
    plan: HarnessPlan,
    frames: Frames,
  ): List<RollRecord> {
    val simulator = JoltDiceSimulator()
    val records = mutableListOf<RollRecord>()
    val started = System.nanoTime()
    do {
      val spec = plan.specFor(records.size)
      records +=
        if (request.framePaced) {
          paced(simulator, spec, records.size, frames)
        } else {
          flatOut(simulator, spec, records.size)
        }
    } while (request.length.keepGoing(records.size, secondsSince(started)))
    return records
  }

  /** One roll stepped as fast as the processor allows — power-saving mode, and the default. */
  private fun flatOut(
    simulator: JoltDiceSimulator,
    spec: ThrowSpec,
    index: Int,
  ): RollRecord {
    val before = System.nanoTime()
    val outcome = simulator.run(spec)
    val elapsed = System.nanoTime() - before
    return RollRecord.of(index, spec.seed, outcome, elapsed.toDouble() / NANOS_PER_MILLISECOND)
  }

  /**
   * One roll stepped the way the screen steps one: an `advance` per frame, at
   * a frame's cadence, with each call timed.
   *
   * **The wall time recorded is the work, not the wait.** A paced roll takes as
   * long as the dice really take, and counting the sleeping between frames as
   * time the device spent simulating would put every paced run exactly on the
   * step-time bar whatever the phone was doing. What is timed is therefore the
   * inside of `advance`, which is the frame's own half of the simulation
   * (`FrameTimes`).
   *
   * Nothing is drawn: the renderer is the one that draws nothing, because this
   * module has no surface and no Filament. That is why the frame figures are
   * marked as not drawn and why the scorecard scores Step 5.7's bar as not
   * measured rather than as a pass.
   */
  private fun paced(
    simulator: JoltDiceSimulator,
    spec: ThrowSpec,
    index: Int,
    frames: Frames,
  ): RollRecord =
    simulator.start(spec, listening = false).use { roll ->
      // A frame's worth behind, so the first frame advances the roll rather
      // than measuring the moment the loop started.
      var last = System.nanoTime() - FrameTimes.FRAME_NANOS
      var workNanos = 0L
      while (roll.running) {
        val begin = System.nanoTime()
        roll.advance((begin - last).toDouble() / NANOS_PER_SECOND)
        last = begin
        val work = System.nanoTime() - begin
        workNanos += work
        frames.millis += work.toDouble() / NANOS_PER_MILLISECOND
        LockSupport.parkNanos(FrameTimes.waitNanos(work))
      }
      frames.droppedSteps += roll.droppedSteps
      val millis = workNanos.toDouble() / NANOS_PER_MILLISECOND
      // A roll that gave up is a row like any other, and the one row that
      // matters most: it ran longer than a roll should and its dice never
      // stopped, so it has no faces. It used to be force-settled into an
      // outcome and counted as a roll that happened.
      roll.outcome?.let { RollRecord.of(index, spec.seed, it, millis) }
        ?: RollRecord.gaveUp(index, spec.seed, roll.stepsTaken, millis)
    }

  private fun secondsSince(nanos: Long): Double = (System.nanoTime() - nanos).toDouble() / NANOS_PER_SECOND

  /**
   * What the run's frames cost, gathered as they happen.
   *
   * A mutable heap of numbers and nothing else: every judgement about them —
   * the percentiles, whether they are worth scoring, what a run with none of
   * them reports — is `FrameTimes` in `:simulation:harness`, on the JVM.
   */
  private class Frames {
    val millis = mutableListOf<Double>()
    var droppedSteps = 0L

    /** The measurement, or [FrameTimes.Nothing] for a run that never paced a frame. */
    fun measured(request: HarnessRequest): FrameTimes =
      if (!request.framePaced) {
        FrameTimes.Nothing
      } else {
        // Never drawn: see `paced`. A phone that measured its own drawing
        // would need a surface, which is Step 5.7's job and not this one's.
        FrameTimes(millis = millis.toList(), droppedSteps = droppedSteps, drawn = false)
      }
  }

  private fun facts(
    request: HarnessRequest,
    plan: HarnessPlan,
    rolls: Int,
  ): RunFacts =
    RunFacts(
      label = request.label,
      shapeId = request.shape.id,
      diceCount = request.diceCount,
      dieScale = plan.dieScale,
      length = request.length,
      rolls = rolls,
      framePaced = request.framePaced,
      seed = request.seed,
      device =
        DeviceFacts.of(
          model = Build.MODEL,
          // The ABI the physics was actually compiled for on this device, and
          // the first thing to look at when two tiers disagree about a seed
          // (`docs/TODO.md`, Step 5.2).
          abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
          androidApi = Build.VERSION.SDK_INT,
          hardware = Build.HARDWARE,
        ),
      startedAtEpochMs = System.currentTimeMillis(),
    )

  /**
   * The document and the table, side by side in the app's external files
   * directory.
   *
   * Two files rather than one because they are for two readers: the JSON is
   * for whoever goes looking through a run that went wrong, and the table is
   * what `tools/harness.sh` prints. The table is rendered here rather than in
   * the script so that there is one renderer and no second copy of the
   * comparison to drift.
   */
  private fun write(
    label: String,
    json: String,
    table: String,
  ) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory =
      requireNotNull(context.getExternalFilesDir(null)) {
        "this device has no external files directory, so a run has nowhere to leave its numbers"
      }
    File(directory, "harness-$label.json").writeText(json)
    File(directory, "harness-$label.txt").writeText(table + "\n")
  }

  private companion object {
    const val NANOS_PER_MILLISECOND = 1_000_000.0
    const val NANOS_PER_SECOND = 1_000_000_000.0
  }
}
