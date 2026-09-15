package de.drehtuer.dinfinity.simulation.jolt

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.drehtuer.dinfinity.simulation.harness.DeviceFacts
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
 * It does nothing without `harness.rolls`, so it can sit in the ordinary device
 * suite without adding minutes to it. `tools/harness.sh` is the way in; by
 * hand it is:
 *
 * ```sh
 * ./gradlew :simulation:jolt:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.harness.rolls=1000 \
 *   -Pandroid.testInstrumentationRunnerArguments.harness.dice=20 \
 *   -Pandroid.testInstrumentationRunnerArguments.harness.shape=d20
 * ```
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
    val report = HarnessReport.of(facts(request, plan), run(request, plan))
    val table = report.scorecard.table()

    write(request.label, HarnessJson.encode(report), table)
    // Into logcat under System.out, so a run watched live says something
    // before the files are pulled. The files are the contract; this is the
    // courtesy.
    println(table)

    assertTrue(table, report.scorecard.passed)
  }

  /**
   * Every roll of the run, timed.
   *
   * `System.nanoTime` rather than the wall clock, because this is an interval
   * and the wall clock can step sideways; and around the whole roll rather
   * than around each step, because a stopwatch per step would be measuring
   * itself as much as the solver.
   */
  private fun run(
    request: HarnessRequest,
    plan: HarnessPlan,
  ): List<RollRecord> {
    val simulator = JoltDiceSimulator()
    return (0 until request.rolls).map { index ->
      val spec = plan.specFor(index)
      val before = System.nanoTime()
      val outcome = simulator.run(spec)
      val elapsed = System.nanoTime() - before
      RollRecord.of(
        index = index,
        seed = spec.seed,
        outcome = outcome,
        wallMillis = elapsed.toDouble() / NANOS_PER_MILLISECOND,
      )
    }
  }

  private fun facts(
    request: HarnessRequest,
    plan: HarnessPlan,
  ): RunFacts =
    RunFacts(
      label = request.label,
      shapeId = request.shape.id,
      diceCount = request.diceCount,
      dieScale = plan.dieScale,
      rolls = request.rolls,
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
  }
}
