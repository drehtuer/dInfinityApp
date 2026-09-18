package de.drehtuer.dinfinity.feature.saved

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.ParseResult
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.core.probability.DistributionResult
import de.drehtuer.dinfinity.core.probability.OutcomeGraph
import de.drehtuer.dinfinity.data.SavedRollLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Writing down a saved roll (`design/dInfinity.dc.html`, options 1r and 7b).
 *
 * The formula is validated on every keystroke like the tray's, and when it
 * reads, the editor says what it is *worth* — the mean and the range, exactly,
 * from `core/probability`. That is the number a player is actually choosing
 * between when they write `2d6 + 3` or `1d12 + 2`, and it is free to compute
 * because nothing has to be thrown to know it (`docs/probability.md`).
 *
 * Saving is refused while the formula does not read. A saved roll that cannot
 * be thrown is a button that fails when it is pressed, and the failure would
 * arrive weeks later in the middle of somebody's game.
 *
 * A **new** roll opened from nothing starts on the last formula that was
 * thrown rather than on a blank field, because "add a roll" is what somebody
 * presses just after throwing the thing they want to keep (`lastRolled`). It
 * is a starting point and not a decision: it is typed over like anything else,
 * and it goes through the same validation, so a formula whose dice set has
 * since gone says so instead of being saved.
 *
 * **Seven parameters**, and four of them are seams rather than state: the
 * library it reads, the scope its watching lives on, the id generator a test
 * pins and the history read the wiring supplies. A parameter object would put
 * them all behind one name and hide the one a caller actually has to think
 * about, which is [Editing].
 */
@Suppress("LongParameterList")
class EditorPresenter(
  private val library: SavedRollLibrary,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
  private val ids: () -> String = {
    java.util.UUID
      .randomUUID()
      .toString()
  },
  opening: Editing = Editing.New(),
  defaultGroupId: String = SavedRollGroup.UNFILED_ID,
  /**
   * The formula of the last roll that was actually thrown, for a new roll that
   * arrived with nothing to start from.
   *
   * "Add a roll" is pressed straight after throwing something worth keeping,
   * and an empty field there asks somebody to type again what the app watched
   * them type a moment ago. It is *the last throw* rather than a remembered
   * draft because that is the thing the player means by "that one".
   *
   * Suspending and read on the editor's own scope, next to the groups, so no
   * frame waits on a database: the field draws empty and fills in with the
   * rest of the screen's state. A player who beat it to the first keystroke
   * keeps what they typed.
   *
   * Empty when nothing has ever been thrown, which is a blank field — the
   * right answer for a fresh install and for anybody who has cleared the
   * history (`docs/statistics.md`, "Export and reset").
   */
  private val lastRolled: suspend () -> String = { "" },
) {
  /** What the screen draws. */
  var state: EditorState by mutableStateOf(
    EditorState(formula = opening.startingFormula, groupId = defaultGroupId, tables = tableChoices()),
  )
    private set

  private var id: String? = opening.existingId

  init {
    scope.launch {
      val known = library.groups.all.first()
      val roll = opening.existingId?.let { library.rolls.byId(it) }
      // Only asked for when there is a gap to fill: an existing roll has its
      // own formula, and one the graph handed over was chosen deliberately.
      // Neither is a reason to go to the database.
      val instead = if (roll == null && state.formula.isEmpty()) lastRolled() else ""
      state =
        state.copy(
          name = roll?.name ?: state.name,
          formula = roll?.formula ?: state.formula.ifEmpty { instead },
          icon = roll?.icon ?: state.icon,
          colourArgb = roll?.colorArgb,
          groupId = roll?.groupId ?: state.groupId,
          sortOrder = roll?.sortOrder ?: 0,
          tablePin = roll?.tablePin,
          groups = known,
          existing = roll != null,
          loaded = true,
        )
      revalidate()
    }
    // Kept watching rather than read once: a group made from the sheet on this
    // screen has to appear in the chooser that asked for it.
    scope.launch {
      library.groups.all.collect { known -> state = state.copy(groups = known) }
    }
  }

  fun name(typed: String) {
    state = state.copy(name = typed)
    revalidate()
  }

  fun formula(typed: String) {
    state = state.copy(formula = typed)
    revalidate()
  }

  /**
   * Everything that is a choice rather than a keystroke: the icon, the colour
   * tag, the group and the table pin.
   *
   * One function rather than four because none of them needs re-validating —
   * a formula does, and it has its own. Five identical one-line setters would
   * be five places for the next one to be written slightly differently.
   */
  fun choose(change: EditorState.() -> EditorState) {
    state = state.change()
  }

  /**
   * Writes it down.
   *
   * The id is kept when one is being edited and made when one is not, so
   * saving twice edits the same roll rather than making two.
   */
  fun save() {
    if (!state.savable) return
    val saved = asRoll()
    id = saved.id
    scope.launch {
      library.rolls.save(saved)
      state = state.copy(existing = true, saved = true)
    }
  }

  /** Takes it away, if it was ever written down. */
  fun delete() {
    val existing = id ?: return
    scope.launch {
      library.rolls.delete(existing)
      state = state.copy(gone = true)
    }
  }

  /** The roll as it stands, for a caller that wants to throw it without saving. */
  fun asRoll(): SavedRoll =
    SavedRoll(
      id = id ?: ids(),
      groupId = state.groupId,
      name = state.name.ifBlank { state.formula },
      formula = state.formula,
      icon = state.icon,
      colorArgb = state.colourArgb,
      // Carried rather than recomputed: the editor is not where the list's
      // order is decided, and a roll saved from here has to come back where
      // the player dragged it (`docs/dice-notation.md`, "Saved rolls").
      sortOrder = state.sortOrder,
      tablePin = state.tablePin,
    )

  /**
   * What the formula reads as, and what it is worth.
   *
   * Both come from the same plan: there is no path here that says a formula is
   * fine and then cannot work out what it does.
   */
  private fun revalidate() {
    val parsed = FormulaParser.parse(state.formula)
    if (state.formula.isBlank()) {
      state = state.copy(error = null, odds = null)
      return
    }
    if (parsed is ParseResult.Failed) {
      state = state.copy(error = parsed.error, odds = null)
      return
    }
    val formula = (parsed as ParseResult.Parsed).formula
    when (val planned = RollPlanner.plan(formula, catalog)) {
      is PlanResult.Failed -> state = state.copy(error = planned.error, odds = null)
      is PlanResult.Planned -> {
        val distribution = OutcomeGraph.of(formula, planned.plan)
        state =
          state.copy(
            error = null,
            // A formula too big to graph exactly is still a formula worth
            // saving; it simply has no numbers to show beside it.
            odds = (distribution as? DistributionResult.Computed)?.pmf?.let { Odds(it.mean, it.min, it.max) },
          )
      }
    }
  }

  private fun tableChoices(): List<TableChoice> = tableChoicesOf(catalog)
}

/**
 * Every table any installed set offers, plus following whatever is pinned
 * above (`docs/tables.md`, "Selecting a table").
 *
 * Shared by the roll editor and the group sheet, because the two offer the
 * same list and the `null` at the front means the same thing in both: *follow
 * the pin above this one*. For a roll that is its group's table and then the
 * app's; for a group it is the app's.
 */
internal fun tableChoicesOf(catalog: DiceCatalog): List<TableChoice> =
  listOf(TableChoice(pin = null, name = null)) +
    catalog.installed.flatMap { set ->
      set.tables.map { table -> TableChoice(pin = TablePin(set.id, table.id), name = table.name) }
    }

/** A table a roll or a group can be pinned to, or following the pin above (`pin` null). */
data class TableChoice(
  val pin: TablePin?,
  val name: String?,
)

/** The mean and the range of a formula, exactly (`docs/probability.md`). */
data class Odds(
  val mean: Double,
  val lowest: Int,
  val highest: Int,
)

/**
 * What the editor opens on (`design/dInfinity.dc.html`, options `1o` and `7a`).
 *
 * Two cases rather than an id and a formula side by side, because side by side
 * they need a rule about which wins: an existing roll has a formula already,
 * and one arriving in a link would silently edit somebody's saved roll by
 * being followed. As two cases there is no rule to remember and none to get
 * wrong — an [Existing] roll has nothing to start from, and a [New] one has no
 * id.
 */
sealed interface Editing {
  /** A roll that already exists. Its own formula is the one it has. */
  data class Existing(
    val id: String,
  ) : Editing

  /**
   * A roll that does not exist yet.
   *
   * @param formula what it starts from — what the outcome graph's "Save as
   *   roll" carries, so somebody who has been reading a formula's odds does
   *   not have to type it again. Empty for a roll started from nothing, which
   *   is the "+" on the tray's strip and "New" on the saved list; those fall
   *   back to the last formula thrown ([EditorPresenter]'s `lastRolled`).
   */
  data class New(
    val formula: String = "",
  ) : Editing
}

/** The id being edited, or null for a roll that does not exist yet. */
internal val Editing.existingId: String?
  get() = (this as? Editing.Existing)?.id

/** What the formula field starts with, which only a new roll has. */
internal val Editing.startingFormula: String
  get() = (this as? Editing.New)?.formula.orEmpty()

/** What the saved-roll editor is showing. */
data class EditorState(
  val name: String = "",
  val formula: String = "",
  val icon: String = "",
  val colourArgb: Int? = null,
  val groupId: String = SavedRollGroup.UNFILED_ID,
  /** Where it sits in its group's list; the editor keeps it rather than sets it. */
  val sortOrder: Int = 0,
  val tablePin: TablePin? = null,
  val groups: List<SavedRollGroup> = emptyList(),
  val tables: List<TableChoice> = emptyList(),
  val error: NotationError? = null,
  val odds: Odds? = null,
  /** True when this is a roll that already exists rather than a new one. */
  val existing: Boolean = false,
  val loaded: Boolean = false,
  /** True once it has been written down, which is when the screen can leave. */
  val saved: Boolean = false,
  /** True once it has been taken away, likewise. */
  val gone: Boolean = false,
) {
  /**
   * True when there is something worth writing down.
   *
   * A formula that does not read is refused: a saved roll that cannot be
   * thrown is a button that fails when it is pressed, and the failure would
   * arrive weeks later in the middle of somebody's game.
   */
  val savable: Boolean get() = formula.isNotBlank() && error == null
}
