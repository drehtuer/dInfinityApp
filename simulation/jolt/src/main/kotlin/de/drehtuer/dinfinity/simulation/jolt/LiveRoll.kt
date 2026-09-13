package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.FrameClock
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * A roll in progress: the world, the loop over it, the clock that decides when
 * it steps, and the renderer watching (`docs/architecture.md`, "Data flow of a
 * roll").
 *
 * This is the whole of the difference between a roll on screen and a roll in
 * power-saving mode, and the difference is *who calls [advance]* — a frame
 * callback sixty times a second, or [runToEnd] on a background thread as fast
 * as the processor allows. Underneath it is one [RollLoop] over one
 * [PhysicsWorld] taking the same fixed steps in the same order, so the same
 * seed comes to the same faces either way. There is no second path to a
 * number and there is no mode flag in the physics (`docs/architecture.md`,
 * goal 1).
 *
 * The renderer is handed frames and nothing else. It cannot step the world,
 * cannot reach a die and cannot ask for another go: [Renderer] has no method
 * that returns anything, which is that promise made into a type rather than a
 * convention (`docs/physics-and-rendering.md`).
 *
 * Not thread-safe, and deliberately so: a roll belongs to one thread — the
 * simulation thread in normal mode, a worker in power-saving mode — and
 * sharing one between two would be sharing a physics world between two
 * (`docs/architecture.md`, "Threading").
 */
class LiveRoll internal constructor(
  spec: ThrowSpec,
  private val world: PhysicsWorld,
  private val loop: RollLoop,
  private val renderer: Renderer = HeadlessRenderer(),
  private val clock: FrameClock = FrameClock(),
) : WatchedRoll,
  AutoCloseable {
  private var previous: List<BodyTransform> = transforms()
  private var current: List<BodyTransform> = previous
  private var settledShown = false

  /** What the throw came to, or null while it is still going. */
  override var outcome: SimulationOutcome? = null
    private set

  /** True until the last die has come to rest. */
  override val running: Boolean get() = outcome == null

  /** How many fixed steps the roll has taken. Simulated time, never wall time. */
  val stepsTaken: Int get() = loop.stepsTaken

  /**
   * Steps that a frame was too late to pay for, in total
   * ([FrameClock.droppedSteps]). Always zero for a roll run with [runToEnd].
   */
  val droppedSteps: Int get() = clock.droppedSteps

  init {
    renderer.begin(spec, spec.geometry, spec.table)
  }

  /**
   * Moves the roll on by however much [elapsedSeconds] is worth, shows the
   * renderer where the dice are, and hands back the same frame.
   *
   * Called once per displayed frame with the time since the last one. The
   * frame time never reaches the solver — [FrameClock] cuts it into whole
   * fixed steps first — so a stutter, a slow frame or a 120 Hz panel change
   * when the roll is drawn and never what it comes to.
   */
  override fun advance(elapsedSeconds: Double): RenderFrame {
    var steps = clock.advance(elapsedSeconds)
    while (steps > 0 && running) {
      step()
      steps--
    }
    return present()
  }

  /**
   * Runs the throw straight through with no clock at all and reports what the
   * dice did — power-saving mode, and the way a headless test rolls.
   *
   * No frames are shown: there is nobody watching, and a renderer that draws
   * nothing still costs a call per step for the whole twelve seconds. The
   * settled dice are handed over once at the end, because even power-saving
   * mode has a last position and something may want it.
   */
  fun runToEnd(): SimulationOutcome {
    while (running) step()
    present()
    return requireNotNull(outcome)
  }

  /** The dice as they are at this moment, ready to be drawn. */
  fun frame(): RenderFrame =
    RenderFrame(
      previous = previous,
      current = current,
      // A roll that is over is not between two states: it is at the second of
      // them, and stays there.
      interpolation = if (running) clock.interpolation else 1.0,
    )

  /** Ends the roll, whether or not it finished, and closes the world. */
  override fun close() {
    renderer.end()
    world.close()
  }

  private fun step() {
    val before = loop.stepsTaken
    if (!loop.advance()) {
      outcome = loop.outcome()
      return
    }

    val states = transforms()
    // A re-throw takes no simulated time: the die is picked up and put back at
    // the spawn point between one step and the next. Blending across that
    // would draw it gliding smoothly back through the air, which is the one
    // thing rung 3 must not look like — it is meant to read as a die being
    // thrown again (`docs/physics-and-rendering.md`, rung 3).
    previous = if (loop.stepsTaken > before) current else states
    current = states
  }

  private fun present(): RenderFrame {
    val frame = frame()
    when {
      running -> renderer.show(frame)
      !settledShown -> {
        renderer.settled(frame)
        settledShown = true
      }
    }
    return frame
  }

  private fun transforms(): List<BodyTransform> =
    loop.dice.mapIndexed { index, state ->
      BodyTransform(index = index, position = state.position, orientation = state.orientation)
    }
}
