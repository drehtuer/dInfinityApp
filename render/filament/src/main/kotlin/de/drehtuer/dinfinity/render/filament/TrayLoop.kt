package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DebugWatch
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * What happens on the tray, frame by frame: which stage is being drawn to,
 * which roll is being watched, and how much time each frame is worth.
 *
 * Everything here is a decision and none of it is Android, so it is tested on
 * a JVM. [TrayDriver] is the thread and the surface it runs on, and is the
 * only part that needs a device (`docs/architecture.md`, decision 40).
 *
 * Two rules are worth naming because they are easy to get subtly wrong and
 * impossible to notice afterwards:
 *
 * - **A surface coming or going never touches the roll.** A new stage is given
 *   the scene it missed by [TrayRenderer]; the simulation is not asked for
 *   anything and certainly not restarted.
 * - **The first frame of a roll is worth no time at all.** There is no frame
 *   before it to measure against, and measuring from zero would hand the clock
 *   however long the device has been awake — spending the whole catch-up
 *   budget on frame one and starting the roll a fifth of a second in.
 *
 * Not thread-safe: one thread owns a roll, and [TrayDriver] is the thread that
 * does (`docs/architecture.md`, "Threading").
 *
 * The function-count suppression is [TrayDriver]'s, for the same reason: seven
 * of these are one per thing a screen can say to a tray, and folding two of
 * them together would hide which is which rather than shorten anything.
 */
@Suppress("TooManyFunctions")
class TrayLoop(
  /**
   * What plays the roll's impacts, if anything does.
   *
   * A watcher like the renderer and with the same promise: it is handed what
   * happened and returns nothing, so hearing a roll cannot change it. On a
   * watched tray the clock is the frame callback, so each frame's impacts are
   * played as they happen (`docs/physics-and-rendering.md`, "Haptics and
   * sound").
   */
  private val impacts: Impacts = Impacts.NONE,
  /**
   * What watches the roll's diagnostics, if anything does — the debug overlay
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * The third watcher, after the renderer and the player of impacts, and it
   * makes the same promise as both: handed a snapshot, asked for nothing back.
   * [DebugWatch.watching] is asked before a snapshot is built, so a tray with
   * the developer toggle off — which is every install — walks no dice and
   * allocates nothing per frame for it.
   */
  private val debug: DebugWatch = DebugWatch.NONE,
) : AutoCloseable {
  private val renderer = TrayRenderer()

  private var stage: Stage? = null
  private var roll: WatchedRoll? = null
  private var settling: ((SimulationOutcome, List<ShakeSample>) -> Unit)? = null

  /** Who wants to know as the dice are counted, and what they were last told. */
  private var counting: ((Map<Int, Int>) -> Unit)? = null
  private var lastCounted: Map<Int, Int> = emptyMap()
  private var lastFrameNanos: Long? = null
  private var owed = false

  /**
   * How many of the roll's impacts have been handed on already.
   *
   * The roll keeps the whole list and this keeps the place in it, rather than
   * the roll handing out a batch and forgetting it: a watcher that could empty
   * the roll's record would be a watcher changing it.
   */
  private var played = 0

  /**
   * True while another frame is worth asking for.
   *
   * **A roll always wants one, with or without somewhere to draw.** The frame
   * callback is what steps the simulation, so a roll that stops being asked is
   * a roll that stops — and one that stops half way is never read, never
   * reported and never over. The screen sits on "Rolling…" for good and the
   * dice are frozen where the last frame left them, which is what losing the
   * surface mid-throw used to do: the app backgrounded, the screen blanked, or
   * the view resized, and the throw was stranded.
   *
   * Drawing is the part that needs a surface, and [TrayRenderer] already has
   * nothing to say without one. The physics does not, and must not wait for a
   * player to be looking (`docs/physics-and-rendering.md`).
   *
   * A still picture is the other half, and it does need somewhere to draw: an
   * empty table is worth one frame, but Filament may decline the one it is
   * offered, so the asking goes on until a frame actually lands.
   */
  val wantsFrames: Boolean get() = roll != null || (stage != null && owed)

  /** True while a roll is in progress, watched or not. */
  val rolling: Boolean get() = roll != null

  /**
   * Draw onto this from now on. Any stage already here is closed first.
   *
   * Called again with a new stage when the view is resized or the phone is
   * turned: Filament fixes its swap chain and viewport when a stage is made,
   * so a new size means a new stage. The roll does not notice.
   */
  fun stage(stage: Stage) {
    surfaceLost()
    this.stage = stage
    renderer.stage(stage)
    // A new surface has never been drawn to. Whatever is being shown — a table
    // with nothing on it, or a roll that has already come to rest — owes it a
    // frame, because neither of those will produce one on its own.
    owed = true
  }

  /**
   * There is a table, and nothing has been thrown onto it yet.
   *
   * Told when the screen opens, before any roll, so that what a player sees on
   * arrival is a table waiting rather than a black rectangle
   * (`docs/TODO.md`, Step 4.1). Remembered, so a surface that arrives
   * afterwards gets it too.
   */
  fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    renderer.table(geometry, look)
    // Which table's impacts these will be. A package names one of five sound
    // sets rather than shipping audio (`docs/tables.md`).
    impacts.on(look.sound)
    owed = true
  }

  /**
   * Puts the dice that are waiting to be thrown on the table.
   *
   * A still picture like the empty table is, and owed a frame for the same
   * reason: nothing else here would produce one, so without it the dice would
   * not appear until something else happened to draw.
   *
   * A roll already in the air is left alone. The board is what a player
   * arranges *between* throws, and a formula edited while the dice are still
   * moving is for the throw after this one.
   */
  fun waiting(spec: ThrowSpec) {
    if (roll != null) return
    renderer.waiting(spec)
    owed = true
  }

  /**
   * The player is looking somewhere else, or closer.
   *
   * Only the camera moves, and nothing about the roll does. Owed a frame like
   * any other still picture: between throws nothing else would produce one, so
   * a pinch would otherwise not appear until something else happened to draw.
   */
  fun look(view: TrayView) {
    renderer.look(view)
    owed = true
  }

  /**
   * The surface is gone. The roll, if there is one, carries on unwatched.
   *
   * Also how a surface is let go of on the way to a new one, and on the way
   * out: there is only one way to stop drawing to a surface, and this is it.
   */
  fun surfaceLost() {
    renderer.stage(null)
    stage?.close()
    stage = null
  }

  /**
   * Throws the dice.
   *
   * [start] is handed the renderer to watch with and returns the roll it
   * opened, so the physics world is created on whichever thread is going to
   * step it. A roll already in progress is ended first — a second throw
   * replaces the first rather than landing on top of it.
   *
   * [onSettled] is called once, on this thread, with what the dice came to and
   * the shake that drove it — and only for a roll that actually finished. A
   * roll abandoned because the player left the screen reports nothing, because
   * nothing landed, and the samples it had collected go with it.
   */
  fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit = {},
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit = { _, _ -> },
  ) {
    endRoll()
    roll = start(renderer)
    settling = onSettled
    counting = onCounted
    lastFrameNanos = null
    played = 0
  }

  /** One more moment of the shake, if there is a roll for it to drive. */
  fun shake(sample: ShakeSample) {
    roll?.shake(sample)
  }

  /**
   * Takes whatever is on the tray off it.
   *
   * The table stays. What is cleared is the throw, and what is left is the
   * empty table it was thrown onto — which has to be drawn, because nothing
   * else is going to.
   */
  fun clear() {
    endRoll()
    renderer.end()
    owed = true
  }

  /**
   * One displayed frame, at [nanos] on the frame clock's own timeline.
   *
   * Returns whether another frame is worth asking for. A roll that has
   * finished is left on screen exactly as it finished: the dice have stopped
   * and nothing may touch them, so there is nothing left to draw
   * (`.claude/CLAUDE.md`).
   */
  fun frame(nanos: Long): Boolean {
    val live = roll
    if (live == null) {
      // Nothing is moving, but something may not have been drawn yet: the
      // table before the first throw, or a landed roll on a surface that has
      // just arrived. One frame settles it — and only a frame that actually
      // landed does, because a still picture has nothing coming after it to
      // cover for a skip.
      if (owed && renderer.redraw()) owed = false
      return wantsFrames
    }

    val previous = lastFrameNanos
    lastFrameNanos = nanos

    // A frame clock that jumped backwards — a different clock source, or a
    // counter that wrapped — is worth no time rather than a negative amount,
    // which the frame clock would refuse outright.
    val elapsed = if (previous == null) 0.0 else ((nanos - previous).coerceAtLeast(0)) / NANOS_PER_SECOND
    live.advance(elapsed)
    hear(live)
    watch(live)
    count(live)

    // A roll draws every frame of its own accord, so nothing is owed while one
    // is running.
    owed = false

    if (!live.running) {
      // Read before closing: a roll that has been given up holds nothing —
      // neither what the dice came to nor the shake that got them there.
      val reached = live.outcome
      val drove = live.drivenBy
      val report = settling
      endRoll()
      // The last frame of a roll is the picture that stays on screen, and the
      // one frame with nothing after it to cover for a skip. Owed until it
      // lands, like any other still picture.
      owed = true
      reached?.let { report?.invoke(it, drove) }
    }
    return wantsFrames
  }

  override fun close() {
    endRoll()
    // The picture ends here rather than with the roll: a landed roll stays on
    // screen for as long as there is a screen, and only giving the tray up
    // takes it away.
    renderer.end()
    surfaceLost()
  }

  /**
   * Plays whatever the dice have hit since the last frame.
   *
   * Zero seconds, because a frame's worth of impacts *is* now: at most four
   * steps of them, and the player thins them down to one tick anyway. The
   * other clock — the whole roll over about a second — is power-saving mode,
   * where there are no frames to pace anything
   * (`docs/physics-and-rendering.md`, "Power-saving mode").
   */

  private fun hear(live: WatchedRoll) {
    val heard = live.impacts
    if (heard.size <= played) return
    // Copied rather than handed as a view: the roll's list goes on growing,
    // and a window onto it would change under whoever was playing it.
    impacts.play(heard.subList(played, heard.size).toList(), NOW)
    played = heard.size
  }

  /**
   * Passes on the faces read so far, when they have changed.
   *
   * Only when: this runs every frame, and a roll whose dice are all still in
   * the air would otherwise post the same empty map a hundred and twenty times
   * a second at the thread the screen is drawn on.
   */
  private fun count(live: WatchedRoll) {
    val read = live.countedSoFar
    if (read == lastCounted) return
    lastCounted = read
    counting?.invoke(read)
  }

  /**
   * Shows the overlay what the roll is doing, if anything is looking.
   *
   * The question comes first and the snapshot second: building one means
   * walking every die, and a tray with nobody debugging it should not do that
   * sixty times a second to hand the result to something that drops it.
   */
  private fun watch(live: WatchedRoll) {
    if (debug.watching) debug.saw(live.diagnostics)
  }

  private fun endRoll() {
    roll?.close()
    roll = null
    settling = null
    counting = null
    lastCounted = emptyMap()
    lastFrameNanos = null
  }

  private companion object {
    const val NANOS_PER_SECOND = 1_000_000_000.0

    /** Impacts on a watched tray have already happened; there is nothing to spread. */
    const val NOW = 0.0
  }
}
