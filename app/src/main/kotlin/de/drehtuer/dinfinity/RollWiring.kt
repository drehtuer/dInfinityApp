package de.drehtuer.dinfinity

import android.content.Context
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.feature.roll.RollMachine
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.render.filament.TrayDriver
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.jolt.JoltDiceSimulator

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
 */
class RollWiring(
  private val context: Context,
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
  private val catalog: DiceCatalog by lazy { DiceCatalog.of(listOf(BuiltinDiceSet.set)) }

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
   * Built per visit rather than held, because it owns a [TrayDriver] and a
   * driver owns a thread, a Filament engine and a physics world. Leaving the
   * screen gives all three back (`docs/architecture.md`, decision 49).
   */
  fun presenter(): RollPresenter =
    RollPresenter(
      machine = RollMachine(catalog = catalog, geometry = geometry, table = table, simulator = simulator),
      driver = TrayDriver(),
      rolls = Rolls(simulator::start),
    )

  private fun aspect(): Double {
    val metrics = context.resources.displayMetrics
    val short = minOf(metrics.widthPixels, metrics.heightPixels).toDouble()
    val long = maxOf(metrics.widthPixels, metrics.heightPixels).toDouble()
    return if (long > 0.0) short / long else TableGeometry.PIXEL_10A_ASPECT
  }
}
