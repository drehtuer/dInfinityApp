package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic between "what the dice did" and "what the run came to".
 *
 * All of it on the JVM, because none of it is physics: the device rolls and
 * times, and every number derived from that is derived here
 * (`docs/architecture.md`, decision 40).
 */
class HarnessReportTest {
  @Test
  fun `a roll's record is its outcome plus the one figure the outcome cannot hold`() {
    val outcome =
      SimulationOutcome(
        faces = mapOf(0 to 3, 1 to 11),
        steps = 240,
        corrections = 2,
        rethrows = 1,
        forcedSettles = 0,
        postRestCorrections = 0,
        stackedAtRest = 1,
        deepestDiePenetrationMm = 0.07,
      )

    val record = RollRecord.of(index = 4, seed = 99L, outcome = outcome, wallMillis = 120.0)

    assertEquals(4, record.index)
    assertEquals(99L, record.seed)
    assertEquals(240, record.steps)
    assertEquals(120.0, record.wallMillis)
    assertEquals(2, record.corrections)
    assertEquals(1, record.rethrows)
    assertEquals(1, record.stackedAtRest)
    assertEquals(0.07, record.deepestDiePenetrationMm)
  }

  @Test
  fun `a roll's settle time is its steps, because steps are the same on every device`() {
    assertEquals(2.0, record(steps = 240).settleSeconds)
    assertEquals(SettleRule.HARD_CAP_SECONDS, record(steps = SettleRule.HARD_CAP_STEPS).settleSeconds)
  }

  @Test
  fun `a roll that gave up says so, and a long one that finished does not`() {
    // It used to be read off the step count, because a roll that ran out of
    // time was force-settled and still produced an outcome. A roll that takes
    // its full twelve seconds and *finishes* is a slow roll, not a failed one,
    // and only the roll with no faces to report is the second
    // (`SettleRule.HARD_CAP_SECONDS`).
    assertFalse(record(steps = SettleRule.HARD_CAP_STEPS).gaveUp)
    assertTrue(RollRecord.gaveUp(index = 0, seed = 1L, steps = SettleRule.HARD_CAP_STEPS, wallMillis = 1.0).gaveUp)
  }

  @Test
  fun `the per-step time of a roll of no steps is zero rather than a division by it`() {
    assertEquals(0.0, record(steps = 0, wallMillis = 5.0).stepWallMillis)
  }

  @Test
  fun `the per-step time is the roll spread over the steps it took`() {
    assertEquals(0.5, record(steps = 240, wallMillis = 120.0).stepWallMillis)
  }

  @Test
  fun `a run of no rolls has no distributions and no dice`() {
    val summary = HarnessSummary.of(diceCount = 20, records = emptyList())

    assertEquals(0, summary.rolls)
    assertEquals(0L, summary.dice)
    assertEquals(Distribution.Nothing, summary.settleSeconds)
    assertEquals(0.0, summary.correctedShare)
    assertEquals(0.0, summary.rethrownShare)
    assertEquals(0.0, summary.deepestDiePenetrationMm)
  }

  @Test
  fun `a run counts dice rather than rolls, because that is how the budgets are stated`() {
    val records =
      listOf(
        record(index = 0, steps = 120, corrections = 4, rethrows = 1),
        record(index = 1, steps = 360, corrections = 2, rethrows = 0),
      )

    val summary = HarnessSummary.of(diceCount = 20, records = records)

    assertEquals(2, summary.rolls)
    assertEquals(40L, summary.dice)
    assertEquals(6L, summary.corrections)
    assertEquals(1L, summary.rethrows)
    assertEquals(6.0 / 40.0, summary.correctedShare)
    assertEquals(1.0 / 40.0, summary.rethrownShare)
  }

  @Test
  fun `a run keeps the worst overlap anywhere in it, not the last one`() {
    val records =
      listOf(
        record(index = 0, penetrationMm = 0.31),
        record(index = 1, penetrationMm = 0.02),
      )

    assertEquals(0.31, HarnessSummary.of(diceCount = 1, records = records).deepestDiePenetrationMm)
  }

  @Test
  fun `a run counts the rolls that gave up, and they report nothing else`() {
    // A roll that gave up has no faces, so it has no corrections, no re-throws
    // and no forced settles either — the one thing worth counting about it is
    // that it happened (`SettleRule.HARD_CAP_SECONDS`).
    val records =
      listOf(
        RollRecord.gaveUp(index = 0, seed = 1L, steps = SettleRule.HARD_CAP_STEPS, wallMillis = 1.0),
        record(index = 1, steps = 200),
        RollRecord.gaveUp(index = 2, seed = 3L, steps = SettleRule.HARD_CAP_STEPS, wallMillis = 1.0),
      )

    val summary = HarnessSummary.of(diceCount = 20, records = records)

    assertEquals(2, summary.capsReached)
    assertEquals(0L, summary.forcedSettles, "a roll with no faces reported a forced settle")
  }

  @Test
  fun `a run adds up the two numbers that must be zero`() {
    val records = listOf(record(index = 0, postRest = 1, stacked = 2), record(index = 1, stacked = 1))
    val summary = HarnessSummary.of(diceCount = 20, records = records)

    assertEquals(1L, summary.postRestCorrections)
    assertEquals(3L, summary.stackedAtRest)
  }

  @Test
  fun `a report summarises and scores in one go, so the file and the verdict cannot disagree`() {
    val report = HarnessReport.of(facts(), listOf(record(index = 0, steps = 120)))

    assertEquals(1, report.summary.rolls)
    assertEquals(HarnessTargets().score(report.summary), report.scorecard)
    assertEquals(1, report.rolls.size)
  }

  @Test
  fun `a run with no frames has none, rather than having them at zero`() {
    val summary = HarnessSummary.of(diceCount = 20, records = listOf(record()))

    assertNull(summary.frames)
  }

  @Test
  fun `a paced run's frames are summarised over every frame of the run`() {
    val frames = FrameTimes(millis = listOf(4.0, 2.0, 30.0, 6.0), droppedSteps = 5, drawn = false)

    val summary = HarnessSummary.of(diceCount = 20, records = listOf(record()), frames = frames)
    val measured = requireNotNull(summary.frames)

    assertEquals(4L, measured.frames)
    assertEquals(5L, measured.droppedSteps)
    assertEquals(30.0, measured.millis.worst)
    assertFalse(measured.drawn)
  }

  @Test
  fun `a report carries whatever the run measured about its frames into its scorecard`() {
    val frames = FrameTimes(millis = listOf(8.0), droppedSteps = 0, drawn = true)

    val report = HarnessReport.of(facts(), listOf(record()), frames)

    assertEquals(HarnessTargets().score(report.summary), report.scorecard)
    assertEquals(1L, requireNotNull(report.summary.frames).frames)
  }

  @Test
  fun `a device is told from an emulator by the hardware it says it is`() {
    assertTrue(DeviceFacts.of("sdk_gphone64_x86_64", "x86_64", 36, "ranchu").emulator)
    assertTrue(DeviceFacts.of("Android SDK", "x86_64", 36, " Goldfish ").emulator)
    assertFalse(DeviceFacts.of("Pixel 10a", "arm64-v8a", 37, "tegu").emulator)
  }

  private fun facts(): RunFacts =
    RunFacts(
      label = "20d20",
      shapeId = "d20",
      diceCount = 20,
      dieScale = 1.0,
      length = RunLength.Rolls(1),
      rolls = 1,
      framePaced = false,
      seed = 7L,
      device = DeviceFacts(model = "Pixel 10a", abi = "arm64-v8a", androidApi = 37, emulator = false),
      startedAtEpochMs = 1_700_000_000_000L,
    )

  @Suppress("LongParameterList")
  private fun record(
    index: Int = 0,
    steps: Int = 120,
    wallMillis: Double = 10.0,
    corrections: Int = 0,
    rethrows: Int = 0,
    forcedSettles: Int = 0,
    postRest: Int = 0,
    stacked: Int = 0,
    penetrationMm: Double = 0.0,
  ): RollRecord =
    RollRecord(
      index = index,
      seed = index.toLong(),
      steps = steps,
      wallMillis = wallMillis,
      corrections = corrections,
      postRestCorrections = postRest,
      rethrows = rethrows,
      forcedSettles = forcedSettles,
      stackedAtRest = stacked,
      deepestDiePenetrationMm = penetrationMm,
    )
}
