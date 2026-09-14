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
import de.drehtuer.dinfinity.data.SavedRollRepository
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
 */
class EditorPresenter(
  private val repository: SavedRollRepository,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
  private val ids: () -> String = {
    java.util.UUID
      .randomUUID()
      .toString()
  },
  editing: String? = null,
  defaultGroupId: String = SavedRollGroup.UNFILED_ID,
) {
  /** What the screen draws. */
  var state: EditorState by mutableStateOf(EditorState(groupId = defaultGroupId, tables = tableChoices()))
    private set

  private var id: String? = editing

  init {
    scope.launch {
      val groups = repository.groups.first()
      val roll = editing?.let { repository.byId(it) }
      state =
        state.copy(
          name = roll?.name ?: state.name,
          formula = roll?.formula ?: state.formula,
          icon = roll?.icon ?: state.icon,
          colourArgb = roll?.colorArgb,
          groupId = roll?.groupId ?: state.groupId,
          favourite = roll?.favourite ?: false,
          tablePin = roll?.tablePin,
          groups = groups,
          existing = roll != null,
          loaded = true,
        )
      revalidate()
    }
    // Kept watching rather than read once: a group made from the sheet on this
    // screen has to appear in the chooser that asked for it.
    scope.launch {
      repository.groups.collect { groups -> state = state.copy(groups = groups) }
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
   * tag, the group, the favourite flag and the table pin.
   *
   * One function rather than five because none of them needs re-validating —
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
      repository.save(saved)
      state = state.copy(existing = true, saved = true)
    }
  }

  /** Takes it away, if it was ever written down. */
  fun delete() {
    val existing = id ?: return
    scope.launch {
      repository.delete(existing)
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
      favourite = state.favourite,
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

  /** Every table any installed set offers, plus following whatever is pinned above. */
  private fun tableChoices(): List<TableChoice> =
    listOf(TableChoice(pin = null, name = null)) +
      catalog.installed.flatMap { set ->
        set.tables.map { table -> TableChoice(pin = TablePin(set.id, table.id), name = table.name) }
      }
}

/** A table a roll can be pinned to, or following the group's (`pin` null). */
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

/** What the saved-roll editor is showing. */
data class EditorState(
  val name: String = "",
  val formula: String = "",
  val icon: String = "",
  val colourArgb: Int? = null,
  val groupId: String = SavedRollGroup.UNFILED_ID,
  val favourite: Boolean = false,
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
