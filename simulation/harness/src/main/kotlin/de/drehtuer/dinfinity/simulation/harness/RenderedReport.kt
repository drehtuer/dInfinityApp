package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.core.model.DieShape
import java.util.Locale

/**
 * What a rendered run came to, as the text `tools/harness.sh --rendered`
 * prints (`docs/build-setup.md`, "The physics harness").
 *
 * The device writes [text] into a file and the script prints it unchanged, for
 * the reason the plain harness does it that way: one renderer, here, under a
 * JVM test, and no second copy of the comparison in a shell script to drift
 * from it (`docs/architecture.md`, decision 53). The scorecard is the last
 * thing in it, so its verdict line is the last line of the file.
 *
 * @param label what the run is called; its files are named after it.
 * @param shape the catalogue solid every die was.
 * @param diceCount dice per throw.
 * @param rolls how many throws were drawn.
 * @param width the surface's width in pixels.
 * @param height and its height.
 * @param device what it ran on.
 * @param summary the frames, or null when none was drawn.
 * @param targets the bars, which are the plain harness's own.
 */
data class RenderedReport(
  val label: String,
  val shape: DieShape,
  val diceCount: Int,
  val rolls: Int,
  val width: Int,
  val height: Int,
  val device: DeviceFacts,
  val summary: RenderedSummary?,
  val targets: HarnessTargets = HarnessTargets(),
) {
  /** The frame rows against their bars. */
  val scorecard: Scorecard get() = targets.scoreFrames(summary)

  /** The figures, a line each, then the scorecard and its verdict. */
  fun text(): String =
    buildList {
      add("Rendered: $rolls rolls of ${diceCount}d${shape.faceCount} on a ${width}x$height surface")
      val emulator = if (device.emulator) " (emulator)" else ""
      add("Device:   ${device.model}, API ${device.androidApi}, ${device.abi}$emulator")
      addAll(figures())
      add("")
      add(scorecard.table())
    }.joinToString("\n")

  private fun figures(): List<String> {
    val measured = summary ?: return listOf("Frames:   none drawn, so nothing about frames was measured")
    val work = measured.frames.millis
    val rate = measured.rate
    val gpu = measured.gpuMillis
    return listOf(
      "Frames:   ${measured.frames.frames} drawn, ${measured.skippedDraws} skipped by Filament, " +
        "${measured.frames.droppedSteps} steps dropped",
      "Work:     " + spread(work) + " (simulation and draw, on the roll thread)",
      "GPU:      " +
        if (gpu == null) "not reported by this driver" else spread(gpu) + " over ${measured.gpuFrames} frames",
      "Rate:     " +
        if (rate == null) {
          "no two frames in a row"
        } else {
          String.format(Locale.ROOT, "%.1f fps, p99 interval %.2f ms", rate.framesPerSecond, rate.p99Millis) +
            " over ${rate.frames} intervals"
        },
    )
  }

  /**
   * The middle, the p99 and the worst of [distribution], with [Locale.ROOT]'s
   * decimal point whatever the phone's language — the scorecard's own rule.
   */
  private fun spread(distribution: Distribution): String =
    String.format(
      Locale.ROOT,
      "p50 %.2f ms, p99 %.2f ms, worst %.2f ms",
      distribution.median,
      distribution.p99,
      distribution.worst,
    )

  companion object {
    /**
     * What a rendered run's files are called, given the label its request
     * came to.
     *
     * Prefixed, so a rendered `20d20` never overwrites the plain harness's
     * `20d20` in `build/harness` — two measurements of different things under
     * one file name.
     */
    fun labelOf(requestLabel: String): String = "$PREFIX$requestLabel"

    /** What every rendered run's label starts with. */
    const val PREFIX: String = "rendered-"
  }
}
