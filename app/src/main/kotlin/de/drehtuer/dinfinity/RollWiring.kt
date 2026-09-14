package de.drehtuer.dinfinity

import android.content.Context
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.RollRecording
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.roll.RollMachine
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.roll.ThrowRecorder
import de.drehtuer.dinfinity.render.filament.PowerSavingTray
import de.drehtuer.dinfinity.render.filament.RollThread
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayDriver
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.jolt.JoltDiceSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything the roll screen needs, put together in the one place that is
 * allowed to know what is underneath.
 *
 * This is where "the physics engine is a choice that can change" stops being a
 * claim: [JoltDiceSimulator] is named here and nowhere above. The screen has a
 * [Rolls] and a `DiceSimulator`, both interfaces, and could be handed a
 * different engine — or a fake — without a line of it changing
 * (`docs/architecture.md`, decision 40).
 *
 * @param context used for the screen's shape and nothing else. Held by the
 *   application, so it is the application context.
 * @param recording what a finished throw is written down with. Given here
 *   rather than reached for from the screen, which is what keeps `feature/roll`
 *   from being able to see a database at all.
 */
class RollWiring(
  private val context: Context,
  private val recording: RollRecording? = null,
) {
  private val simulator = JoltDiceSimulator()

  /**
   * The installed sets a formula resolves against.
   *
   * The bundled set only, for now: the installed-set registry arrives with the
   * dice-set screen (`docs/TODO.md`, Step 4.4). It goes through the same
   * validator as a package from a stranger, on every launch
   * (`docs/dice-sets.md`).
   */
  val catalog: DiceCatalog by lazy { DiceCatalog.of(listOf(BuiltinDiceSet.set)) }

  /**
   * The tray, shaped to this phone.
   *
   * The table is a fixed 240 mm long and as wide as the screen's proportions
   * allow, so a roll on a tall phone and a roll on a squat one are the same
   * roll on differently shaped tables rather than differently sized dice
   * (`docs/tables.md`).
   */
  private val geometry: TableGeometry by lazy { TableGeometry.forAspect(aspect()) }

  /** The default look. Choosing another is the table picker's job (Step 4.5). */
  private val table: TableLook by lazy {
    BuiltinDiceSet.set.tables.firstOrNull() ?: TableLook(id = "default", name = "Default")
  }

  /**
   * A presenter for one visit to the roll screen.
   *
   * Built per visit rather than held, because it owns a [Tray] and a tray owns
   * a roll: in the drawing case a scene and a physics world. Leaving the screen
   * gives both back (`docs/architecture.md`, decision 49). The thread and the
   * engine underneath do not go with them — those are [rollThread]'s, and one
   * of those serves every visit (decision 50).
   *
   * @param powerSaving whether to throw the dice without drawing them. It is
   *   read once, when the screen opens, rather than watched: a renderer
   *   appearing or vanishing under a roll in progress is not a setting taking
   *   effect, it is a bug. Turning it on takes effect the next time the screen
   *   is opened (`design/dInfinity.dc.html`, option 1z).
   */
  fun presenter(
    powerSaving: Boolean = false,
    rounding: Rounding = Rounding.Default,
    scope: CoroutineScope,
  ): RollPresenter =
    RollPresenter(
      machine =
        RollMachine(
          catalog = catalog,
          geometry = geometry,
          table = table,
          simulator = simulator,
          defaultRounding = rounding,
        ),
      driver = tray(powerSaving),
      rolls = Rolls(simulator::start),
      recorder = recorder(scope),
    )

  /**
   * Writing a throw down, off the thread the result arrived on.
   *
   * A roll is finished when the dice stop, not when a database says so, so the
   * write is launched and not waited for. A failure to record is a statistic
   * that is missing, which is a great deal better than a roll that appears to
   * hang while SQLite thinks about it.
   */
  private fun recorder(scope: CoroutineScope): ThrowRecorder =
    recording?.let { recording ->
      ThrowRecorder { thrown ->
        scope.launch { recording.record(result = thrown.result, plan = thrown.plan, seed = thrown.seed) }
      }
    } ?: ThrowRecorder.NONE

  /**
   * The roll thread and the engine on it, shared by every visit.
   *
   * Lazy, so power-saving mode still starts no thread and opens no engine at
   * all. Made once and never given back: compiling the dice material happens on
   * the device and costs long enough to watch, so paying for it on every visit
   * to the screen is the black tray somebody sees on the way back from the menu
   * (`RollThread`).
   */
  private val rollThread: RollThread by lazy { RollThread() }

  /**
   * The tray this visit gets.
   *
   * This is where "power-saving mode creates no graphics engine" is decided,
   * and it is one branch in one place: nothing below it knows there is a mode
   * at all, and the roll it opens is the same roll either way
   * (`docs/architecture.md`, decision 38).
   *
   * A driver per visit still, because a driver owns a roll — but handed the
   * thread and the engine rather than making its own.
   */
  private fun tray(powerSaving: Boolean): Tray = if (powerSaving) PowerSavingTray() else TrayDriver(shared = rollThread)

  /**
   * The outcome graph's state, for one visit to that screen.
   *
   * It shares the catalogue with the roll screen and nothing else: the graph
   * has no simulator, no tray and no thread, because it is about the formula
   * rather than about a throw (`docs/probability.md`).
   */
  fun graph(): GraphMachine = GraphMachine(catalog)

  private fun aspect(): Double {
    val metrics = context.resources.displayMetrics
    val short = minOf(metrics.widthPixels, metrics.heightPixels).toDouble()
    val long = maxOf(metrics.widthPixels, metrics.heightPixels).toDouble()
    return if (long > 0.0) short / long else TableGeometry.PIXEL_10A_ASPECT
  }
}
