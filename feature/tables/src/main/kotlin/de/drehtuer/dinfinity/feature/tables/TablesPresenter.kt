package de.drehtuer.dinfinity.feature.tables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin

/**
 * One look a roll could happen on, and where it came from.
 *
 * The set is carried because **tables are global**: every look from every
 * installed package is offered, whatever dice set it shipped with
 * (`docs/tables.md`, "Selecting a table"). Two packages may each ship a
 * `green-felt`, so the name alone does not say which one this is.
 */
data class TableChoice(
  val pin: TablePin,
  val look: TableLook,
  /** The package's own name, for telling two `green-felt`s apart. */
  val setName: String,
)

/** What the table picker is showing. */
data class TablesState(
  val tables: List<TableChoice> = emptyList(),
  val chosen: TablePin? = null,
) {
  /**
   * True when nothing is installed that ships a table at all.
   *
   * It should not happen — the bundled package ships five — so it is drawn as
   * a state rather than assumed away: a package that stopped validating takes
   * its tables with it, and the bundled one is revalidated on every launch
   * like any other (`docs/dice-sets.md`).
   *
   * No `loaded` beside it, unlike the presenters that collect a flow. This one
   * is built from a list it is handed, in its own constructor, so there is no
   * moment where the screen is showing nothing because nothing has arrived —
   * and a flag that can never be false is a flag that tells a reader something
   * untrue.
   */
  val empty: Boolean get() = tables.isEmpty()

  /** Whether more than one package supplies a table, which is when the set is worth naming. */
  val manyPackages: Boolean get() = tables.map { it.pin.setId }.distinct().size > 1
}

/**
 * Which table the dice are thrown onto
 * (`design/dInfinity.dc.html`, option `1u`; `docs/tables.md`).
 *
 * **Every look from every installed package, in one list.** A dice set never
 * brings its own table along and never overrides the chosen one: a roll that
 * mixes two sets' dice happens on the one selected table, like reaching into
 * two bags over the same tray.
 *
 * What is *not* here is the pin precedence. A saved roll and a group can each
 * pin a table, and this screen chooses the app default they fall back to — the
 * bottom of that order and the only one of the three that is a setting
 * (`docs/tables.md`). Where the other two are chosen is the saved-roll editor,
 * which already has the field.
 *
 * @param sets the installed packages, usually the catalogue's. Handed in
 *   rather than reached for, because which packages are usable is the
 *   application's to know (`docs/architecture.md`, Modules).
 * @param onChosen what to remember. The choice is a setting, and settings
 *   belong to `:app`.
 */
class TablesPresenter(
  private val sets: () -> List<DiceSet>,
  chosen: TablePin?,
  private val onChosen: (TablePin) -> Unit,
) {
  /** What the screen draws. */
  var state: TablesState by mutableStateOf(TablesState())
    private set

  init {
    val tables =
      sets().flatMap { set ->
        set.tables.map { look -> TableChoice(pin = TablePin(set.id, look.id), look = look, setName = set.name) }
      }
    state =
      TablesState(
        tables = tables,
        // A choice pointing at a package that is no longer installed is not a
        // choice: it is shown as the one the roll screen would actually use,
        // which is the first look there is. The setting itself is left alone,
        // because the package may come back tomorrow — the same rule the
        // default dice set follows.
        chosen = chosen?.takeIf { pin -> tables.any { it.pin == pin } } ?: tables.firstOrNull()?.pin,
      )
  }

  /** A look was tapped. */
  fun choose(pin: TablePin) {
    if (state.tables.none { it.pin == pin }) return
    state = state.copy(chosen = pin)
    onChosen(pin)
  }
}
