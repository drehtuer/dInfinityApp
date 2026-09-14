package de.drehtuer.dinfinity.feature.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.ParseResult
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.core.probability.DistributionResult
import de.drehtuer.dinfinity.core.probability.OutcomeGraph
import de.drehtuer.dinfinity.core.probability.Pmf
import de.drehtuer.dinfinity.core.stats.RollComparison
import de.drehtuer.dinfinity.core.stats.RolledAgainstExpected
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One saved roll, and what it has done against what it should do. */
data class SavedRollStats(
  val roll: SavedRoll,
  val comparison: RollComparison = RollComparison(),
  /**
   * Why there is no expected distribution, when there is none.
   *
   * A saved roll's formula is stored as **text** and the set it names can be
   * uninstalled afterwards (`docs/dice-notation.md`), so a roll that graphed
   * last week may not today. The bars are still worth showing — they are what
   * the dice did — and the marks are what go missing.
   */
  val noExpectation: String? = null,
) {
  /** True when the chart has ink bars but no red marks to draw them against. */
  val observedOnly: Boolean get() = noExpectation != null
}

/** What the saved-roll statistics screen is showing. */
data class SavedStatsState(
  val rolls: List<SavedRoll> = emptyList(),
  val selected: SavedRollStats? = null,
  val loaded: Boolean = false,
) {
  /** True when the group has no saved rolls, rather than none having arrived. */
  val empty: Boolean get() = loaded && rolls.isEmpty()
}

/**
 * What a saved roll has actually rolled, against the exact distribution it was
 * rolling against (`design/dInfinity.dc.html`, options `8b` and `9e`;
 * `docs/statistics.md`).
 *
 * This is where the app's central claim can be checked. The result comes from
 * physics rather than a generator (`.claude/CLAUDE.md`), and the way to see
 * whether that is producing honest dice is to put a few hundred throws next to
 * the maths and look. Nothing here smooths or bins: the bars are the totals as
 * recorded, and the marks are `core/probability`'s exact answer.
 *
 * **The screen is about a group**, because a saved roll belongs to one and the
 * roll somebody wants is one they have been using (design `8b`).
 *
 * @param groupId which group's rolls to offer.
 * @param rounding how division rounds, which changes the distribution — a
 *   formula with a `/` in it has a different shape rounded down than rounded
 *   to nearest (`docs/dice-notation.md`).
 */
class SavedStatsPresenter(
  private val saved: SavedRollRepository,
  private val history: HistoryRepository,
  private val catalog: DiceCatalog,
  private val scope: CoroutineScope,
  private val groupId: String,
  private val rounding: Rounding = Rounding.Default,
) {
  /** What the screen draws. */
  var state: SavedStatsState by mutableStateOf(SavedStatsState())
    private set

  private var watching: Job? = null

  init {
    scope.launch {
      saved.inGroup(groupId).collect { rolls ->
        state = state.copy(rolls = rolls, loaded = true)
        // The open roll's own chart moves when a throw lands, and the roll
        // itself may have been renamed or edited under us.
        state.selected?.let { open -> select(open.roll.id) }
      }
    }
  }

  /** A saved roll was chosen. */
  fun select(rollId: String) {
    val roll = state.rolls.firstOrNull { it.id == rollId } ?: return
    watching?.cancel()
    watching =
      scope.launch {
        history.forSavedRoll(rollId).collect { entries ->
          state = state.copy(selected = statsOf(roll, entries))
        }
      }
  }

  /** Back to the list. */
  fun close() {
    watching?.cancel()
    watching = null
    state = state.copy(selected = null)
  }

  /**
   * The totals this roll has come to, against what it should come to.
   *
   * Only the throws made **through this saved roll** are counted. The same
   * formula typed by hand is a different question — it is not this roll's
   * record, and the history knows the difference because a roll started from a
   * saved roll carries its id (`docs/statistics.md`).
   */
  private fun statsOf(
    roll: SavedRoll,
    entries: List<HistoryEntry>,
  ): SavedRollStats {
    val totals = entries.map(HistoryEntry::total)
    return when (val expected = distributionOf(roll.formula)) {
      is Expectation.Exact ->
        SavedRollStats(roll = roll, comparison = RolledAgainstExpected.of(totals, expected.pmf))

      is Expectation.None ->
        SavedRollStats(
          roll = roll,
          comparison = RolledAgainstExpected.observed(totals),
          noExpectation = expected.why,
        )
    }
  }

  /**
   * The exact distribution of a formula, or **why** there is not one.
   *
   * Parse, plan, graph — the same three steps the outcome-graph screen takes,
   * because they are the same question. They are repeated here rather than
   * shared through `feature/graph`, which is a screen: a statistics screen
   * reaching into another screen for arithmetic would be the wrong way round
   * (`docs/architecture.md`).
   *
   * Each step's own message is carried out, rather than one message standing
   * for all three. They are different things — a formula that no longer parses,
   * a set that is not installed, and a throw too large to compute exactly — and
   * a screen that said "does not resolve" to all of them would be wrong about
   * two of them.
   */
  private fun distributionOf(formula: String): Expectation =
    when (val parsed = FormulaParser.parse(formula)) {
      is ParseResult.Failed -> Expectation.None(parsed.error.message)
      is ParseResult.Parsed ->
        when (val planned = RollPlanner.plan(parsed.formula, catalog)) {
          is PlanResult.Failed -> Expectation.None(planned.error.message)
          is PlanResult.Planned ->
            when (val graphed = OutcomeGraph.of(parsed.formula, planned.plan, rounding)) {
              is DistributionResult.TooLarge -> Expectation.None(graphed.reason)
              is DistributionResult.Computed -> Expectation.Exact(graphed.pmf)
            }
        }
    }

  /** Either there is an exact distribution to compare against, or there is a reason there is not. */
  private sealed interface Expectation {
    data class Exact(
      val pmf: Pmf,
    ) : Expectation

    data class None(
      val why: String,
    ) : Expectation
  }
}
