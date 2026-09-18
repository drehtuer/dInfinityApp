package de.drehtuer.dinfinity

import android.content.Context
import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.RollRecording
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.roll.DebugRelay
import de.drehtuer.dinfinity.feature.roll.RollMachine
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.roll.ThrowRecorder
import de.drehtuer.dinfinity.feedback.AndroidFeedback
import de.drehtuer.dinfinity.feedback.ImpactFeedback
import de.drehtuer.dinfinity.render.filament.PowerSavingTray
import de.drehtuer.dinfinity.render.filament.RollThread
import de.drehtuer.dinfinity.render.filament.ThumbnailPlan
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayDriver
import de.drehtuer.dinfinity.render.filament.TrayThumbnails
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.simulation.api.DebugWatch
import de.drehtuer.dinfinity.simulation.api.DeveloperLog
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
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
  private val catalogue: () -> DiceCatalog,
  /**
   * Which table look the player chose, or null for the bundled package's
   * first (`docs/tables.md`).
   *
   * A function for the reason [catalogue] is one: it is a preference, and one
   * read when this object was built would be the table the app started with
   * rather than the one on the picker.
   */
  private val chosenTable: () -> TablePin? = { null },
  /**
   * What the developer toggle remembers, or [DeveloperLog.NONE] when it is off
   * — which it is on every install (`docs/physics-and-rendering.md`, "Debug
   * tooling").
   *
   * Held by the application rather than by a visit, because an anomaly is a
   * bug report and the last throw is what replays it: both are wanted after
   * the player has left the tray.
   */
  private val developer: DeveloperLog = DeveloperLog.NONE,
  /**
   * Where a die's artwork comes from, keyed by package and path
   * (`de.drehtuer.dinfinity.render.filament.AtlasKey`).
   *
   * Handed to the roll thread and therefore to the engine, because a decoded
   * atlas belongs to a *package* and outlives every visit to the screen
   * (`docs/dice-sets.md`, "Textures"). The default draws nothing, which is
   * what the dice looked like before anything filled this in.
   */
  private val artwork: (String) -> AtlasImage? = { null },
) {
  private val simulator = JoltDiceSimulator()

  /**
   * The installed sets a formula resolves against.
   *
   * Asked for rather than held, and asked once per visit to a screen. The
   * catalogue is fixed for the life of a roll screen by design — choosing a
   * different set is a change to what is *installed*, and that happens
   * somewhere else (`RollMachine`) — so the moment to read it is the moment
   * the screen opens, and a set installed while the player was on another
   * screen is there when they come back.
   *
   * `SetLibrary` is what builds it, from the packages on disk that are on and
   * still validate (`docs/dice-sets.md`).
   */
  val catalog: DiceCatalog get() = catalogue()

  /**
   * The tray, shaped to this phone.
   *
   * The table is a fixed 240 mm long and as wide as the screen's proportions
   * allow, so a roll on a tall phone and a roll on a squat one are the same
   * roll on differently shaped tables rather than differently sized dice
   * (`docs/tables.md`).
   */
  private val geometry: TableGeometry by lazy { TableGeometry.forAspect(aspect()) }

  /**
   * The look the dice are thrown onto (`docs/tables.md`, "Selecting a table").
   *
   * [pinned] is the throw's own table — a saved roll's pin, or the pin of the
   * group it lives in, whichever the precedence rule already settled — and
   * `null` for a throw that pins nothing, which is every throw somebody typed.
   * Then the one chosen in the table picker, and the bundled package's first
   * look when nothing has been chosen — which is where a new install starts.
   *
   * A pin naming a package that is no longer installed falls back the same
   * way, rather than leaving the tray with no look at all. The *setting* is
   * left as it was: the package may be re-installed tomorrow, which is the
   * rule the default dice set already follows.
   *
   * Read per visit rather than captured, because it is a preference and a
   * preference changes while the app is running — choosing a table and going
   * back to the tray should land on it.
   */
  private fun table(pinned: TablePin?): TableLook {
    val wanted = pinned ?: chosenTable()
    val fromPin = wanted?.let { catalog.set(it.setId)?.tables?.firstOrNull { table -> table.id == it.tableId } }
    return fromPin
      ?: catalog.set(DiceSet.BUILTIN_ID)?.tables?.firstOrNull()
      ?: TableLook(id = "default", name = context.getString(R.string.table_look_fallback))
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
   * @param tableView how far the camera leans over the table. Read here for
   *   the same reason and with the same effect: the tray is made when the
   *   screen opens, and a camera that moved under a roll in progress is not a
   *   setting taking effect (`docs/physics-and-rendering.md`, "Rendering
   *   (normal mode)").
   * @param haptics whether a die landing is felt, and [sound] whether it is
   *   heard. Read here for the same reason and with the same effect: both the
   *   thing that listens — the roll — and the thing that plays are made when
   *   the screen opens, and a roll that started buzzing half way through is
   *   not a setting taking effect (`docs/physics-and-rendering.md`, "Haptics
   *   and sound").
   */
  @Suppress("LongParameterList")
  fun presenter(
    powerSaving: Boolean = false,
    rounding: Rounding = Rounding.Default,
    tableView: TableView = TableView.Default,
    haptics: Boolean = true,
    sound: Boolean = true,
    developerTools: Boolean = false,
    scope: CoroutineScope,
  ): RollPresenter {
    // One relay per visit, and none at all when the toggle is off: with no
    // relay the tray is given `DebugWatch.NONE`, which is asked before a
    // snapshot is built, so a roll nobody is debugging walks no dice for it.
    val relay = if (developerTools) DebugRelay() else null
    return RollPresenter(
      machine =
        RollMachine(
          catalog = catalog,
          geometry = geometry,
          look = ::table,
          defaultRounding = rounding,
        ),
      driver = tray(powerSaving, feedback(haptics, sound), relay ?: DebugWatch.NONE, tableView),
      // A roll records where the dice hit something only when something is
      // going to play it. Both settings off is the one thing those two
      // switches actually save: nothing is measured, rather than measured and
      // then muted (`docs/physics-and-rendering.md`, "Impacts").
      //
      // The overlay is the third listener: its contact dots are the same
      // impacts, so a roll being debugged records them whatever the haptics
      // and the sound say.
      rolls =
        Rolls { spec, watcher ->
          simulator.start(spec, watcher, listening = haptics || sound || developerTools)
        },
      recorder = recorder(scope),
      debug = relay,
      developer = developer,
    )
  }

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
        scope.launch {
          recording.record(
            result = thrown.result,
            plan = thrown.plan,
            seed = thrown.seed,
            savedRollId = thrown.savedRollId,
            groupId = thrown.groupId,
          )
        }
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
  private val rollThread: RollThread by lazy { RollThread(artwork) }

  /** Made once, because the pictures it has already drawn are worth keeping. */
  private var pictures: TrayThumbnails? = null

  /**
   * Pictures of tables for the table picker, or null where none can be drawn
   * (`docs/architecture.md`, decision 60).
   *
   * Drawn on [rollThread] with the same engine the tray uses, because Filament
   * takes calls only from the thread that made the engine and because
   * compiling the dice material a second time is the cost decision 50 exists
   * to avoid. The table picker is not the roll screen, so this is the first
   * thing to want the renderer somewhere that is not the tray.
   *
   * **Null in power-saving mode.** That mode's promise is that no Filament
   * engine is created at all, and a thumbnail would create one on the way to a
   * screen that is not even about rolling. The picker falls back to its
   * swatch, which is exactly what it falls back to on a device with no working
   * engine (`docs/physics-and-rendering.md`, "Power-saving mode").
   *
   * Held rather than rebuilt per visit, for the reason the thread is: the
   * pictures it has drawn are of looks that cannot change, and drawing them
   * again on the way back from the menu is work with nothing to show for it.
   *
   * @param widthPx how big a picture the screen wants. Asked for rather than
   *   worked out here: how much room a row gives a thumbnail is the table
   *   picker's to say, and this module draws what it is asked for.
   */
  fun thumbnails(
    powerSaving: Boolean,
    widthPx: Int,
    heightPx: Int,
  ): TrayThumbnails? {
    if (powerSaving) return null
    return pictures ?: TrayThumbnails.on(rollThread, ThumbnailPlan.of(widthPx, heightPx)).also { pictures = it }
  }

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
  private fun tray(
    powerSaving: Boolean,
    impacts: Impacts,
    debug: DebugWatch,
    tableView: TableView,
  ): Tray =
    if (powerSaving) {
      // No overlay in power-saving mode, because there is no tray to draw it
      // over: the dice are thrown and never drawn, and a panel floating on a
      // blank screen would be describing something nobody can see.
      PowerSavingTray(impacts = impacts)
    } else {
      TrayDriver(shared = rollThread, impacts = impacts, debug = debug, tableView = tableView)
    }

  /**
   * What plays this visit's impacts.
   *
   * Held by the application rather than made per visit, for the reason
   * [rollThread] is: it owns an actuator, a handful of audio buffers and a
   * thread, and making those again every time somebody comes back from the
   * menu is a cost with nothing to show for it. It is rebuilt only when the two
   * settings behind it actually change, which is what "takes effect the next
   * time the roll screen opens" means here.
   */
  private fun feedback(
    haptics: Boolean,
    sound: Boolean,
  ): ImpactFeedback {
    val wanted = haptics to sound
    playing?.takeIf { playingFor == wanted }?.let { return it }
    playing?.close()
    playingFor = wanted
    return AndroidFeedback.create(context, haptics = haptics, sound = sound).also { playing = it }
  }

  private var playing: ImpactFeedback? = null
  private var playingFor: Pair<Boolean, Boolean>? = null

  /**
   * Throws a spec again and reports what the dice came to — the developer
   * toggle's replay (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * The same simulator every roll uses, run headlessly: no tray, no thread of
   * its own and nothing drawn. There is no second path to a number here either
   * — a replay is the same `DiceSimulator.run` a power-saving roll takes
   * (`docs/architecture.md`, goal 1).
   *
   * Blocking, and called from a background dispatcher by whoever wants it
   * ([ScreenWiring]).
   */
  fun replay(spec: ThrowSpec): SimulationOutcome = simulator.run(spec)

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
