package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3

/*
 * The trays and rolls the roll screen's tests are driven by.
 *
 * Out of [RollScreenTest] rather than nested in it: there is more than one
 * test class about that screen now — the two pull-ups along its bottom edge
 * have their own — and a fake copied into the second of them is a fake that
 * drifts from the first.
 *
 * None of them is a simulation. They stand exactly where the physics thread
 * would be and answer the four things a tray can say: dice counted, dice that
 * never stopped, dice that landed, or nothing at all.
 */

/**
 * A presenter over the built-in set, a fixed table and a seed that never
 * moves, wired to whatever tray and roll a test hands it.
 *
 * Everything about a roll that could vary is pinned, so the only thing a test
 * of the screen is asking about is the screen.
 */
internal fun rollPresenter(
  tray: Tray,
  rolls: Rolls,
  catalog: DiceCatalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
) = RollPresenter(
  machine =
    RollMachine(
      catalog = catalog,
      geometry = TableGeometry.referenceDevice(),
      look = { TableLook(id = "plain", name = "Plain") },
      outside = Outside(seeds = { 1L }, clock = { 0L }),
    ),
  driver = tray,
  rolls = rolls,
  toTheScreen = { it() },
)

/** A tray that throws the dice where it stands and says it draws nothing. */
internal class UndrawnTray : DirectTray() {
  override val draws: Boolean = false
}

internal open class DirectTray : Tray {
  val shaken = mutableListOf<ShakeSample>()

  /** How many times the screen has given this tray back. */
  var closes = 0
    private set

  /** How many throws this tray has been handed. */
  var throws = 0
    private set

  /** Every board this tray has been asked to show, in order. */
  val boards = mutableListOf<List<DieInstance>>()

  /** Every table this tray has been told about, in order. */
  val tabled = mutableListOf<Pair<TableGeometry, TableLook>>()

  /** Every view the player has asked for, in order. */
  val looked = mutableListOf<TrayView>()

  override fun surfaceAvailable(
    surface: Surface,
    width: Int,
    height: Int,
  ) = Unit

  override fun surfaceLost() = Unit

  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit,
    onStalled: (List<Int>) -> Unit,
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
  ) {
    throws++
    val live = start(HeadlessRenderer())
    while (live.running) live.advance(SettleRule.TIMESTEP_SECONDS)
    live.outcome?.let { onSettled(it, live.drivenBy) }
    live.close()
  }

  override fun waiting(spec: ThrowSpec) {
    boards += spec.dice
  }

  override fun shake(sample: ShakeSample) {
    shaken += sample
  }

  override fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    tabled += geometry to look
  }

  override fun look(view: TrayView) {
    looked += view
  }

  override fun clear() = Unit

  override fun close() {
    closes++
  }
}

/**
 * A tray that reports some dice counted and then leaves the roll in the air,
 * which is what the screen looks like halfway through one.
 */
internal class CountingTray(
  private val read: Map<Int, Int>,
) : Tray by PendingTray() {
  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit,
    onStalled: (List<Int>) -> Unit,
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
  ) {
    onCounted(read)
  }
}

/** A tray whose roll gives up: some dice never settle, and there is no total. */
internal class StallingTray(
  private val unsettled: List<Int>,
) : Tray by PendingTray() {
  var throws = 0
    private set

  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit,
    onStalled: (List<Int>) -> Unit,
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
  ) {
    throws++
    onStalled(unsettled)
  }
}

/** A tray that takes the throw and leaves the dice in the air. */
internal class PendingTray : Tray {
  val shaken = mutableListOf<ShakeSample>()

  /** Every table this tray has been told about, in order. */
  val tabled = mutableListOf<Pair<TableGeometry, TableLook>>()

  /** Every view the player has asked for, in order. */
  val looked = mutableListOf<TrayView>()

  override fun surfaceAvailable(
    surface: Surface,
    width: Int,
    height: Int,
  ) = Unit

  override fun surfaceLost() = Unit

  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit,
    onStalled: (List<Int>) -> Unit,
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
  ) {
    start(HeadlessRenderer())
  }

  override fun shake(sample: ShakeSample) {
    shaken += sample
  }

  override fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    tabled += geometry to look
  }

  override fun look(view: TrayView) {
    looked += view
  }

  override fun clear() = Unit

  override fun close() = Unit
}

/** A roll that lands on the given faces at the first frame. */
internal class LandingRolls(
  private val faces: Map<Int, Int>,
) : Rolls {
  override fun start(
    spec: ThrowSpec,
    watcher: Renderer,
  ): WatchedRoll =
    object : WatchedRoll {
      private var landed = false

      override val running: Boolean get() = !landed

      override val outcome: SimulationOutcome? get() = if (landed) SimulationOutcome(faces = faces) else null

      override val drivenBy: List<ShakeSample> = emptyList()

      override val impacts: List<Impact> = emptyList()

      override fun advance(elapsedSeconds: Double): RenderFrame {
        landed = true
        return RenderFrame.still(
          spec.dice.indices.map { BodyTransform(it, Vector3(0.0, 0.0, 8.0), Quaternion.Identity) },
        )
      }

      override fun shake(sample: ShakeSample) = Unit

      override fun close() = Unit
    }
}
