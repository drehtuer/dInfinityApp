package de.drehtuer.dinfinity.simulation.api

/**
 * Turns the time a frame actually took into whole simulation steps
 * (`docs/physics-and-rendering.md`, "The simulation clock").
 *
 * The physics runs at a fixed [SettleRule.TIMESTEP_SECONDS] and a display does
 * not run at anything in particular — 60 Hz, 120 Hz, whatever the panel and
 * the thermal state between them decide this second. Stepping the world by the
 * frame time would make the roll depend on the phone's mood, and a roll that
 * depends on the phone's mood is not the same roll twice
 * (`docs/architecture.md`, goal 1).
 *
 * So the frame time is never handed to the solver. It is accumulated here, cut
 * into whole steps of the one size there is, and the remainder is carried to
 * the next frame — where it becomes [interpolation], how far past the last
 * step this moment falls, which is what a renderer blends with
 * ([de.drehtuer.dinfinity.simulation.api.Quaternion.slerp]). The simulation
 * sees a fixed step or it sees nothing.
 *
 * Nothing here can change what a roll comes to. It decides *when* steps are
 * taken, never how big they are or in what order, so a roll stepped from a
 * clock and the same roll run flat out on a background thread take the same
 * steps and come to the same faces. That is what makes power-saving mode the
 * same roll rather than a second implementation (`docs/physics-and-rendering.md`,
 * "Power-saving mode").
 *
 * @param maxStepsPerFrame the catch-up limit; see [MAX_STEPS_PER_FRAME].
 */
class FrameClock(
  private val maxStepsPerFrame: Int = MAX_STEPS_PER_FRAME,
) {
  init {
    require(maxStepsPerFrame > 0) { "a frame that may take no steps never finishes a roll" }
  }

  /** Time left over from the last frame, less than one step of it. */
  private var carriedSeconds = 0.0

  /**
   * How far this moment sits past the last step taken, `0` to `1`.
   *
   * Handed to the render frame's own interpolation so that 120 Hz physics is
   * drawn smoothly at whatever rate the panel happens to run at.
   */
  var interpolation: Double = 0.0
    private set

  /**
   * Steps that were owed and never taken, because a frame ran long enough to
   * ask for more catch-up than [maxStepsPerFrame] allows.
   *
   * The roll is unaffected — it takes the same steps in the same order and
   * comes to the same faces, it simply gets there later in wall-clock terms.
   * What this counts is therefore not a correctness problem but a *smoothness*
   * one, and Step 5.7 is where a number here stops being acceptable
   * (`docs/TODO.md`).
   */
  var droppedSteps: Int = 0
    private set

  /**
   * How many steps to take for a frame that took [elapsedSeconds].
   *
   * Call once per frame, then take exactly that many steps, then read
   * [interpolation]. A frame shorter than one step takes none and moves the
   * interpolation on, which is the right answer for a 240 Hz panel watching a
   * 120 Hz simulation.
   */
  fun advance(elapsedSeconds: Double): Int {
    require(elapsedSeconds >= 0.0) { "$elapsedSeconds is not a length of time a frame took" }

    carriedSeconds += elapsedSeconds
    val owed = (carriedSeconds / SettleRule.TIMESTEP_SECONDS).toInt()
    val taken = owed.coerceAtMost(maxStepsPerFrame)
    droppedSteps += owed - taken
    // The time for the steps that were dropped is dropped with them. Keeping
    // it would mean the next frame owes even more, and the frame after that
    // more still — the spiral where a phone that fell behind once never
    // catches up and the roll runs slower and slower.
    carriedSeconds -= owed * SettleRule.TIMESTEP_SECONDS
    interpolation = (carriedSeconds / SettleRule.TIMESTEP_SECONDS).coerceIn(0.0, 1.0)
    return taken
  }

  /** Forgets the carried time. For the start of a roll, not for a pause. */
  fun reset() {
    carriedSeconds = 0.0
    interpolation = 0.0
    droppedSteps = 0
  }

  companion object {
    /**
     * The most steps one frame may take — the published figure
     * (`docs/physics-and-rendering.md`, "Timestep and determinism").
     *
     * A limit is needed because the alternative is unbounded: the app comes
     * back from the background, or the first frame after a roll starts arrives
     * a second late, and without a cap that one frame takes a hundred and
     * twenty steps of solver inside a frame callback — a freeze the player
     * watches happen, and on a slow device the start of a spiral where each
     * frame is late because the last one took too long.
     *
     * Four is twice what a 60 Hz frame needs: enough to absorb an ordinary
     * hitch within a frame or two, low enough that a bad one is paid off
     * gradually rather than all at once.
     */
    const val MAX_STEPS_PER_FRAME: Int = 4
  }
}
