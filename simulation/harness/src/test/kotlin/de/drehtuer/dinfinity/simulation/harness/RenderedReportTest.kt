package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.FrameRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a rendered run prints, and how its frame rows are scored
 * (`docs/build-setup.md`, "The physics harness", `--rendered`).
 */
class RenderedReportTest {
  @Test
  fun `a rendered run is scored on its frame rows and nothing else`() {
    val scorecard = HarnessTargets().scoreFrames(summary(work = 9.0, gpu = 7.0))

    assertEquals(
      listOf(HarnessTargets.FRAME_TIME, HarnessTargets.GPU_FRAME_TIME, HarnessTargets.DROPPED_STEPS),
      scorecard.rows.map(TargetResult::name),
    )
    assertTrue(scorecard.passed, scorecard.table())
    assertEquals(emptyList(), scorecard.notMeasured)
  }

  @Test
  fun `drawn frames are scored on the bar, exactly on it passing`() {
    val onTheBar = HarnessTargets().scoreFrames(summary(work = HarnessTargets.P99_FRAME_MILLIS, gpu = 1.0))
    val over = HarnessTargets().scoreFrames(summary(work = 16.7, gpu = 1.0))

    assertEquals(TargetOutcome.Pass, onTheBar.row(HarnessTargets.FRAME_TIME).outcome)
    assertEquals(TargetOutcome.Fail, over.row(HarnessTargets.FRAME_TIME).outcome)
    assertEquals("16.70 ms", over.row(HarnessTargets.FRAME_TIME).measured)
  }

  @Test
  fun `a GPU that cannot keep up fails the run even when the roll thread can`() {
    val scorecard = HarnessTargets().scoreFrames(summary(work = 3.0, gpu = 20.0))

    assertFalse(scorecard.passed)
    assertEquals(listOf(HarnessTargets.GPU_FRAME_TIME), scorecard.failures.map(TargetResult::name))
  }

  @Test
  fun `a driver that keeps no GPU timings leaves that row not measured, not passed`() {
    val scorecard = HarnessTargets().scoreFrames(summary(work = 3.0, gpu = null))
    val row = scorecard.row(HarnessTargets.GPU_FRAME_TIME)

    assertEquals(TargetOutcome.NotMeasured, row.outcome)
    assertEquals(HarnessTargets.NOT_MEASURED, row.measured)
    assertTrue(scorecard.passed)
    assertTrue(scorecard.verdict().endsWith("1 not measured"), scorecard.verdict())
  }

  @Test
  fun `dropped steps fail the run the moment there is one`() {
    val scorecard = HarnessTargets().scoreFrames(summary(work = 3.0, gpu = 3.0, dropped = 1))

    assertEquals(TargetOutcome.Fail, scorecard.row(HarnessTargets.DROPPED_STEPS).outcome)
  }

  @Test
  fun `a run that drew nothing measured nothing`() {
    val scorecard = HarnessTargets().scoreFrames(null)

    assertEquals(3, scorecard.notMeasured.size)
    assertTrue(scorecard.passed)
  }

  @Test
  fun `the text names the run, gives every figure and ends in the verdict`() {
    val text = report(summary(work = 4.0, gpu = 6.0, rate = FrameRate(120.0, 8.5, 240))).text()
    val lines = text.lines()

    assertEquals("Rendered: 5 rolls of 20d20 on a 1080x2424 surface", lines.first())
    assertTrue(lines.any { it == "Device:   Pixel 10a, API 37, arm64-v8a" }, text)
    assertTrue(lines.any { it.startsWith("Frames:   10 drawn, 1 skipped by Filament, 0 steps dropped") }, text)
    assertTrue(lines.any { it.startsWith("Work:     p50 4.00 ms, p99 4.00 ms") }, text)
    assertTrue(lines.any { it == "GPU:      p50 6.00 ms, p99 6.00 ms, worst 6.00 ms over 10 frames" }, text)
    assertTrue(lines.any { it == "Rate:     120.0 fps, p99 interval 8.50 ms over 240 intervals" }, text)
    assertTrue(lines.any { it.startsWith(HarnessTargets.FRAME_TIME) }, text)
    assertEquals("${Scorecard.VERDICT} PASS", lines.last())
  }

  @Test
  fun `the text says what was not measured rather than printing a zero`() {
    val text = report(summary(work = 4.0, gpu = null, rate = null)).text()

    assertTrue(text.contains("GPU:      not reported by this driver"), text)
    assertTrue(text.contains("Rate:     no two frames in a row"), text)
  }

  @Test
  fun `an emulator says so, and a run with no frames says it measured none`() {
    val text =
      report(null)
        .copy(device = DeviceFacts.of("sdk_gphone64_x86_64", "x86_64", 36, "ranchu"))
        .text()

    assertTrue(text.contains("Device:   sdk_gphone64_x86_64, API 36, x86_64 (emulator)"), text)
    assertTrue(text.contains("Frames:   none drawn"), text)
    assertTrue(text.lines().last().startsWith("${Scorecard.VERDICT} PASS"), text)
  }

  @Test
  fun `figures are written with a point whatever the phone's language`() {
    val before = java.util.Locale.getDefault()
    try {
      java.util.Locale.setDefault(java.util.Locale.GERMANY)
      val text = report(summary(work = 4.5, gpu = 6.25, rate = FrameRate(59.9, 16.7, 10))).text()
      assertTrue(text.contains("p50 4.50 ms"), text)
      assertTrue(text.contains("59.9 fps"), text)
    } finally {
      java.util.Locale.setDefault(before)
    }
  }

  @Test
  fun `a report keeps what it was told about the run`() {
    val summary = summary(work = 4.0, gpu = 5.0)
    val report = report(summary)

    assertEquals("rendered-20d20", report.label)
    assertEquals(DieShape.Icosahedron, report.shape)
    assertEquals(20, report.diceCount)
    assertEquals(5, report.rolls)
    assertEquals(1080 to 2424, report.width to report.height)
    assertEquals("Pixel 10a", report.device.model)
    assertEquals(summary, report.summary)
    assertEquals(HarnessTargets(), report.targets)
    // Scored against the bars it was given, not against the defaults.
    val strict = report.copy(targets = HarnessTargets(p99FrameMillis = 1.0))
    assertFalse(strict.scorecard.passed)
  }

  @Test
  fun `a rendered run's files never take the plain harness's name`() {
    assertEquals("rendered-20d20", RenderedReport.labelOf("20d20"))
    assertTrue(RenderedReport.labelOf("x").startsWith(RenderedReport.PREFIX))
  }

  private fun Scorecard.row(name: String): TargetResult = rows.single { it.name == name }

  private fun summary(
    work: Double,
    gpu: Double?,
    dropped: Long = 0,
    rate: FrameRate? = FrameRate(60.0, 16.7, 9),
  ): RenderedSummary =
    RenderedSummary(
      frames =
        requireNotNull(
          FrameTimes(millis = List(FRAMES) { work }, droppedSteps = dropped, drawn = true).summary(),
        ),
      gpuMillis = gpu?.let { Distribution(it, it, it) },
      gpuFrames = if (gpu == null) 0 else FRAMES,
      rate = rate,
      skippedDraws = 1,
    )

  private fun report(summary: RenderedSummary?): RenderedReport =
    RenderedReport(
      label = "rendered-20d20",
      shape = DieShape.Icosahedron,
      diceCount = 20,
      rolls = 5,
      width = 1080,
      height = 2424,
      device = DeviceFacts.of("Pixel 10a", "arm64-v8a", 37, "tensor"),
      summary = summary,
    )

  private companion object {
    const val FRAMES = 10
  }
}
