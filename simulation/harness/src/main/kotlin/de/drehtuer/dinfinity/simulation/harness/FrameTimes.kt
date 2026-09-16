package de.drehtuer.dinfinity.simulation.harness

/**
 * What the frames of a run cost, as the device measured them at the
 * [de.drehtuer.dinfinity.simulation.api.FrameClock] seam.
 *
 * A harness run is headless by default: it calls `runToEnd`, there is no clock,
 * there are no frames and there is nothing here to fill in. A *paced* run
 * steps the same roll the way the screen does — one `LiveRoll.advance` per
 * display frame — and times each of those calls, which is the only figure
 * about frames a run with no surface is entitled to.
 *
 * **[drawn] is the whole honesty of this file.** Step 5.7's bar — p99 frame
 * time under 16.6 ms at twenty dice — is about *drawing*, and drawing needs a
 * renderer and a surface to draw to. A paced run in the physics harness has
 * neither: it advances the simulation on a frame's cadence and hands its frames
 * to a renderer that draws nothing. The numbers below are therefore the
 * simulation half of a frame, and the report says so rather than presenting
 * them as the frame time Step 5.7 asks for
 * (`docs/physics-and-rendering.md`, "The simulation clock").
 *
 * @param millis how long each frame's work took, one entry per frame, over
 *   every roll of the run. Kept as the whole sample rather than as a
 *   per-roll percentile, because a p99 of a list of p99s is a number about
 *   arithmetic rather than about frames.
 * @param droppedSteps steps that were owed and never taken, summed over the
 *   run ([de.drehtuer.dinfinity.simulation.api.FrameClock.droppedSteps]). The
 *   roll is unaffected and comes to the same faces; what it counts is a frame
 *   that ran long enough for the clock to give up catching it up.
 * @param drawn whether a renderer drew those frames onto a surface. False for
 *   every run this harness can make today.
 */
data class FrameTimes(
  val millis: List<Double>,
  val droppedSteps: Long,
  val drawn: Boolean,
) {
  /**
   * The three figures of [millis] with the counts beside them, or null when no
   * frames were measured at all.
   *
   * Null rather than zero, and that is the point: a headless run did not
   * measure a frame time of nothing, it measured nothing. Zero would score as
   * the fastest run ever made (`HarnessTargets.score`).
   */
  fun summary(): FrameSummary? =
    if (millis.isEmpty()) {
      null
    } else {
      FrameSummary(
        frames = millis.size.toLong(),
        droppedSteps = droppedSteps,
        millis = Distribution.of(millis),
        drawn = drawn,
      )
    }

  companion object {
    /** A run that measured no frames, which is every headless run. */
    val Nothing: FrameTimes = FrameTimes(millis = emptyList(), droppedSteps = 0, drawn = false)

    /**
     * The display rate a paced run pretends to be shown at.
     *
     * Sixty rather than the panel's own, because the harness cannot ask a
     * surface it does not have what rate it runs at, and sixty is the rate
     * Step 5.7 states its bar in ("60 fps sustained at 20 dice"). A phone that
     * can service a frame in a sixtieth of a second can service one on a 120 Hz
     * panel or it cannot, and that is a question for the tray rather than for
     * this.
     */
    const val FRAMES_PER_SECOND: Double = 60.0

    /** How long one of those frames is. */
    const val FRAME_SECONDS: Double = 1.0 / FRAMES_PER_SECOND

    /** And in nanoseconds, which is what a paced loop waits in. */
    const val FRAME_NANOS: Long = 16_666_667L

    /**
     * How long a paced loop should wait after doing [workNanos] of work before
     * the next frame is due.
     *
     * Never negative: a frame that overran has nothing left to wait for and the
     * next one starts at once, late. Here rather than on the device because it
     * is the one line of the loop that is a decision rather than a measurement,
     * and a decision belongs where a JVM test can reach it
     * (`docs/architecture.md`, decision 53).
     */
    fun waitNanos(workNanos: Long): Long = (FRAME_NANOS - workNanos).coerceAtLeast(0L)
  }
}

/**
 * The frames of a run, added up.
 *
 * @param frames how many frames the run advanced through.
 * @param droppedSteps steps a late frame never paid for, over the whole run.
 * @param millis how long a frame's work took: the middle, the p99 and the worst.
 * @param drawn whether anything was actually drawn. When it is false the p99 is
 *   the simulation half of a frame and not the frame, and the scorecard scores
 *   it as not measured rather than as a pass.
 */
data class FrameSummary(
  val frames: Long,
  val droppedSteps: Long,
  val millis: Distribution,
  val drawn: Boolean,
)
