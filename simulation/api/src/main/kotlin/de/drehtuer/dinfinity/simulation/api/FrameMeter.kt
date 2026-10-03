package de.drehtuer.dinfinity.simulation.api

import kotlin.math.ceil

/**
 * How fast the tray is really being drawn: the last [window] frame intervals,
 * and the frame rate and p99 frame time they come to
 * (`docs/physics-and-rendering.md`, "Debug tooling"; `docs/architecture.md`,
 * decision 72).
 *
 * The arithmetic behind the frame-rate line on the debug overlay, kept here
 * in plain Kotlin so it is a JVM test rather than something only a phone can
 * answer. The device side measures nothing but the time between two frame
 * callbacks in a row and hands it to [record].
 *
 * **It reads frames and never decides them.** Nothing here reaches the
 * [FrameClock], the solver or the pacing, so a tray with the meter running
 * takes the same steps as one without it.
 *
 * A fixed number of frames rather than a fixed stretch of time: at 120 Hz the
 * window is one second of frames, at 60 Hz two, and the p99 is taken over the
 * same number of samples either way — which is what makes it comparable from
 * one panel rate to another. Not thread-safe; one thread owns a roll, and
 * this is fed from that thread.
 *
 * @param window how many frames the figures are over.
 */
class FrameMeter(
  private val window: Int = WINDOW,
) {
  init {
    require(window > 0) { "a window of $window frames holds no frame" }
  }

  private val intervals = LongArray(window)
  private var next = 0
  private var held = 0

  /** Every frame ever recorded, for deciding when a reading is worth posting. */
  var recorded: Long = 0
    private set

  /**
   * One frame came [intervalNanos] after the one before it.
   *
   * A frame of no time or less — a clock that went backwards, or the same
   * vsync twice — is not a frame anybody saw, and is left out rather than
   * counted as an infinitely fast one.
   */
  fun record(intervalNanos: Long) {
    if (intervalNanos <= 0) return
    intervals[next] = intervalNanos
    next = (next + 1) % window
    if (held < window) held++
    recorded++
  }

  /** Forgets every frame, as if none had been drawn. */
  fun reset() {
    next = 0
    held = 0
    recorded = 0
  }

  /**
   * The frame rate and the p99 frame time over the window, or null before the
   * first frame — a readout with nothing measured says so rather than "0 fps".
   */
  fun reading(): FrameRate? {
    if (held == 0) return null
    val sorted = intervals.copyOf(held).also { it.sort() }
    val total = sorted.sum()
    // The nearest-rank p99: the smallest interval at least 99 % of the
    // frames are no longer than. Over 120 frames that is the second-worst,
    // which is exactly the frame a person holding the phone sees stutter.
    val rank = ceil(P99 * held).toInt().coerceIn(1, held)
    return FrameRate(
      framesPerSecond = held * NANOS_PER_SECOND / total,
      p99Millis = sorted[rank - 1] / NANOS_PER_MILLI,
      frames = held,
    )
  }

  companion object {
    /**
     * How many frames a reading is over: one second at 120 Hz, two at 60.
     *
     * Long enough that one hitch does not swing the frame rate by a third,
     * short enough that the readout follows a roll starting and ending.
     */
    const val WINDOW: Int = 120

    private const val P99 = 0.99
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val NANOS_PER_MILLI = 1_000_000.0
  }
}

/**
 * What the frame-rate readout says.
 *
 * @param framesPerSecond frames over the time they took, averaged over the
 *   window.
 * @param p99Millis the frame time 99 % of the window's frames came in under —
 *   the figure Step 5.7's "p99 frame under 16.6 ms" is stated in.
 * @param frames how many frames this is over, up to [FrameMeter.WINDOW].
 */
data class FrameRate(
  val framesPerSecond: Double,
  val p99Millis: Double,
  val frames: Int,
)
