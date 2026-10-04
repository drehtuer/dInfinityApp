package de.drehtuer.dinfinity.simulation.harness

import de.drehtuer.dinfinity.simulation.api.FrameMeter
import de.drehtuer.dinfinity.simulation.api.FrameRate

/**
 * What the frames of a **rendered** run cost, gathered as they are drawn
 * (`docs/TODO.md`, Step 5.7; `docs/build-setup.md`, "The physics harness").
 *
 * The headless harness can only time the simulation half of a frame
 * ([FrameTimes.drawn] is false there). The rendered harness — an instrumented
 * test in `render/filament` — throws the same dice through the shipping path:
 * the tray's own frame callback, `RollPace`'s watched pace, `LiveRoll`, and
 * `FilamentDiceRenderer` drawing onto a real surface. It hands each frame to
 * this, and this is all it does with them: every number below, and every
 * judgement about which of them count, is here, where a JVM test reaches it
 * (`docs/architecture.md`, decisions 53 and 80).
 *
 * Three figures, because a frame is three things and only two of them can be
 * timed from where the test stands:
 *
 * - **work** — the wall time of one frame's simulation *and* draw on the roll
 *   thread: `LiveRoll.advance`, which steps the world and hands the frame to
 *   the renderer, which places the dice and runs Filament's `beginFrame`,
 *   `render` and `endFrame`. This is the frame time Step 5.7's bar is scored
 *   on ([HarnessTargets.scoreFrames]).
 * - **GPU** — how long the GPU took over a frame, as Filament's own frame
 *   history reports it. Pipelined behind the roll thread rather than inside
 *   its frame, so it is scored as a row of its own: a frame the GPU cannot
 *   finish in a sixtieth of a second is a dropped frame whatever the CPU did.
 *   Absent when the driver does not report it, and then **not measured**.
 * - **rate** — the intervals between the starts of two frames in a row, which
 *   is the display's frame callback and so the frame rate a person holding the
 *   phone would see, summed up by the same [FrameMeter] the debug overlay uses
 *   (decision 72). Reported, not scored: on a 60 Hz panel every interval is
 *   about 16.7 ms by construction, which is the vsync and not the work.
 *
 * Not thread-safe: the roll thread fills it, and the test reads it only once
 * that thread has finished with the tray.
 */
class RenderedFrames {
  private val work = mutableListOf<Double>()
  private val intervals = mutableListOf<Long>()

  /** GPU time by Filament's frame id, because its history repeats a frame on every read until it ages out. */
  private val gpu = LinkedHashMap<Int, Double>()
  private var lastBegin: Long? = null
  private var droppedSteps = 0L

  /** Frames Filament declined to draw because the GPU was still behind. */
  var skippedDraws: Long = 0
    private set

  /**
   * One frame of a roll: it started at [beginNanos] on the monotonic clock and
   * its simulation and draw took [workNanos].
   *
   * Its interval from the frame before is counted only when there *was* a
   * frame before it in the same pass ([passEnded]). The gap between two passes
   * is the scene being built for the next throw, or the next throw being asked
   * for — a pause between throws, not a slow frame.
   */
  fun framed(
    beginNanos: Long,
    workNanos: Long,
  ) {
    require(workNanos >= 0) { "a frame cannot take $workNanos ns" }
    work += workNanos / NANOS_PER_MILLISECOND
    // A clock that did not move is the same frame twice, not a frame of no time.
    lastBegin?.let { previous -> if (beginNanos > previous) intervals += beginNanos - previous }
    lastBegin = beginNanos
  }

  /**
   * A pass came to an end, with [droppedSteps] steps its late frames never paid
   * for (`FrameClock.droppedSteps`). The next frame starts a new chain of
   * intervals.
   */
  fun passEnded(droppedSteps: Int) {
    require(droppedSteps >= 0) { "a pass cannot drop $droppedSteps steps" }
    this.droppedSteps += droppedSteps
    lastBegin = null
  }

  /** Filament said to skip a frame: nothing reached the surface for it. */
  fun skippedDraw() {
    skippedDraws++
  }

  /**
   * One entry of Filament's frame history: frame [frameId] kept the GPU busy
   * for [durationNanos].
   *
   * The history is a short window read after every draw, so the same frame
   * arrives several times and is kept once. A duration of nought or less is
   * Filament's `INVALID` (-1) or `PENDING` (-2) — a driver with no timer
   * queries, or a frame the GPU has not finished — and is not a measurement.
   * A pending frame is read again on the next draw, when it has one.
   */
  fun gpu(
    frameId: Int,
    durationNanos: Long,
  ) {
    if (durationNanos <= 0) return
    gpu.putIfAbsent(frameId, durationNanos / NANOS_PER_MILLISECOND)
  }

  /** The work figures as the headless harness's own type, drawn this time. */
  fun times(): FrameTimes = FrameTimes(millis = work.toList(), droppedSteps = droppedSteps, drawn = true)

  /**
   * Everything above added up, or null when not one frame of a roll was drawn
   * — which is a run that measured nothing, not a run that was fast
   * (`docs/architecture.md`, decision 57).
   */
  fun summary(): RenderedSummary? {
    val frames = times().summary() ?: return null
    return RenderedSummary(
      frames = frames,
      gpuMillis = if (gpu.isEmpty()) null else Distribution.of(gpu.values.toList()),
      gpuFrames = gpu.size,
      rate = rate(),
      skippedDraws = skippedDraws,
    )
  }

  /** The intervals through the overlay's own arithmetic, over all of them at once. */
  private fun rate(): FrameRate? {
    if (intervals.isEmpty()) return null
    val meter = FrameMeter(window = intervals.size)
    intervals.forEach(meter::record)
    return meter.reading()
  }

  private companion object {
    const val NANOS_PER_MILLISECOND = 1_000_000.0
  }
}

/**
 * A rendered run's frames, added up.
 *
 * @param frames the work of each frame — simulation and draw on the roll
 *   thread — with the steps late frames dropped. Always [FrameSummary.drawn].
 * @param gpuMillis the GPU's time per frame, or null when the driver reported
 *   none.
 * @param gpuFrames how many frames that is over.
 * @param rate the frame rate and p99 interval between frame starts, or null
 *   when no two frames of a pass came one after the other.
 * @param skippedDraws frames Filament declined to draw.
 */
data class RenderedSummary(
  val frames: FrameSummary,
  val gpuMillis: Distribution?,
  val gpuFrames: Int,
  val rate: FrameRate?,
  val skippedDraws: Long,
)
