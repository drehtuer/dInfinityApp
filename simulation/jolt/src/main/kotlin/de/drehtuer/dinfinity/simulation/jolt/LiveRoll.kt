package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.FrameClock
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.ShakeSample
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
  override val running: Boolean get() = outcome == null && !loop.stalled

  /**
   * True while a hand is throwing these dice rather than a player watching
   * them ([RollLoop.driven]).
   *
   * Read by whoever converts real frames into simulated time, which is not
   * this: a roll is handed however much time it may spend and has no opinion
   * about where that came from
   * ([de.drehtuer.dinfinity.simulation.api.RollPace]).
   */
  override val driven: Boolean get() = running && loop.driven

  override val stalled: Boolean get() = loop.stalled

  override val unsettled: List<Int> get() = loop.unsettled

  /** How many fixed steps the roll has taken. Simulated time, never wall time. */
  override val stepsTaken: Int get() = loop.stepsTaken

  /**
   * Every moment of the shake that has reached this roll, in step order.
   *
   * The record of the throw, and it lives here rather than beside the result
   * because a throw's record is the throw: `spec.copy(shake = drivenBy)` is a
   * [ThrowSpec] that replays this roll exactly, and a shake kept anywhere else
   * would be a second half nobody joins back up. The dice are spawned when the
   * shake is confirmed, so the spec this roll was opened with is missing all of
   * it (`docs/physics-and-rendering.md`, "Shake input").
   *
   * It dies with the roll. A throw the player walked away from never reports
   * an [outcome], so nothing asks for this, and closing the roll takes the
   * samples with the world they drove.
   */
  override val drivenBy: List<ShakeSample> get() = loop.drivenBy

  /**
   * Everywhere the dice have hit something so far, in step order.
   *
   * Grows as the roll runs and is read by whoever is playing it — a watched
   * tray takes the ones it has not played yet on every frame, and a
   * power-saving roll takes the lot once the dice have stopped. It is the same
   * list either way; only the clock over it differs
   * (`docs/physics-and-rendering.md`, "Haptics and sound").
   */
  override val impacts: List<Impact> get() = loop.impacts

  /**
   * The roll as a developer sees it, right now
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * Built when it is asked for and not before, so a roll nobody is debugging
   * does none of the work — the overlay is off on every install and costs
   * nothing there. Reading it cannot change the roll, which is the same
   * promise [Renderer] makes and the reason both are allowed to exist
   * (`RollLoop.diagnostics`).
   */
  override val diagnostics: RollDiagnostics get() = loop.diagnostics()

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
   * Called once per displayed frame with however much simulated time that
   * frame earns the roll, which on a watched tray is a fraction of the time
   * the frame took ([de.drehtuer.dinfinity.simulation.api.RollPace], applied
   * by the caller). Either way the number never reaches the solver —
   * [FrameClock] cuts it into whole fixed steps first — so a stutter, a slow
   * frame, a 120 Hz panel or a pace change when the roll is drawn and never
   * what it comes to.
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
  fun runToEnd(giveUp: () -> Boolean = { outOfTime }): SimulationOutcome? {
    while (running) {
      if (giveUp()) return null
      step()
    }
    present()
    // Null for a roll that gave up, which is what the nullable return has
    // always been for: [running] goes false on a stall as well as on an
    // outcome, and requiring one here crashed power-saving mode on exactly
    // the throws that need the answer most ([stalled], [unsettled]).
    return outcome
  }

  /**
   * True once the roll has been going longer than a roll should
   * ([SettleRule.HARD_CAP_SECONDS]).
   *
   * **The backstop for the runs nobody is watching.** A roll on screen needs
   * none: it runs until its dice have stopped, and a player who is tired of
   * waiting leaves the screen, which gives it up. A headless run has no screen
   * to leave — the harness throws thousands of them — so a roll that never
   * settled would hang the run instead of failing it.
   *
   * What it does *not* do is end the roll. The dice are not read off whatever
   * face they were nearest and handed back as a result; there is no result, and
   * the caller is told so (`docs/build-setup.md`, "The physics harness").
   */
  val outOfTime: Boolean get() = loop.outOfTime

  /**
   * One more moment of the shake, for a roll that is still going.
   *
   * A sample for a roll that has already settled is dropped rather than
   * applied: nothing touches a die that has come to rest, and the hand is not
   * an exception (`.claude/CLAUDE.md`).
   */
  override val countedSoFar: Map<Int, Int> get() = loop.countedSoFar

  override fun shake(sample: ShakeSample) {
    if (running) loop.shake(sample)
  }

  /**
   * The dice as they are at this moment, ready to be drawn.
   *
   * **A die that has been lifted off the table is not in it** — and being
   * counted is not what lifts one. A die comes off only when the same pass is
   * about to throw something again, because the floor it was standing on is
   * then free for the re-thrown die to land on, which means a later die may
   * land exactly where it was. Drawing it there anyway would put two dice in
   * one place, which is a worse thing to watch than the stacking this
   * mechanism replaced (`docs/physics-and-rendering.md`).
   *
   * A roll that settles first time throws nothing again, so it lifts nothing:
   * every die stays where it landed and stays drawn there, which is what a
   * player expects to be looking at when the total appears.
   *
   * Both halves are filtered by the same list, so a frame still has the same
   * dice at both ends of the step it spans. A die lifted during that step
   * leaves at once rather than gliding away, which is what being lifted off
   * the table looks like.
   */
  fun frame(): RenderFrame {
    val gone = loop.liftedOut
    return RenderFrame(
      previous = previous.filterNot { gone.getOrElse(it.index) { false } },
      current = current.filterNot { gone.getOrElse(it.index) { false } },
      // A roll that is over is not between two states: it is at the second of
      // them, and stays there.
      interpolation = if (running) clock.interpolation else 1.0,
    )
  }

  /**
   * Gives the physics world back, whether or not the roll finished.
   *
   * It deliberately does **not** end the renderer. A roll that has landed is
   * still on screen and the player is still reading it; the picture outlives
   * the simulation that produced it, and tearing the scene down here meant a
   * settled roll vanished the moment anything took the surface away and gave
   * it back — the screen blanking was enough
   * (`docs/physics-and-rendering.md`, "The simulation clock").
   *
   * Whoever set the renderer up ends it, when there is nothing left to look
   * at.
   */
  override fun close() {
    world.close()
  }

  private fun step() {
    val before = loop.stepsTaken
    if (!loop.advance()) {
      // **The loop stops for two different reasons and only one of them has a
      // number in it.** A roll that finished has an outcome; a roll that gave
      // up has none at all, on purpose — its dice never stopped, so there is
      // nothing to read and making one up is the thing this app exists not to
      // do (`docs/physics-and-rendering.md`, "Settling and reading the
      // result"). Asking for it anyway threw `the roll has not finished yet`
      // off the roll thread, which is a crash rather than an exception
      // anybody could catch: a hundred d4 on a phone did it every time.
      if (!loop.stalled) outcome = loop.outcome()
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
