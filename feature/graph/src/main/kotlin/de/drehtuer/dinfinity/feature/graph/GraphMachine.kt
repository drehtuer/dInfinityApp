package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.NotationError
import de.drehtuer.dinfinity.core.notation.ParseResult
import de.drehtuer.dinfinity.core.notation.PlanResult
import de.drehtuer.dinfinity.core.notation.RollPlanner
import de.drehtuer.dinfinity.core.probability.DistributionResult
import de.drehtuer.dinfinity.core.probability.OutcomeGraph
import de.drehtuer.dinfinity.core.probability.Pmf

/**
 * What the outcome graph is showing (`docs/TODO.md`, Step 4.2).
 *
 * No Compose and no Android: a formula goes in and a distribution comes out,
 * along with everything the chart needs to draw it. The screen renders
 * [state] and calls the three things a player can do — retype the formula,
 * switch between `P(total = k)` and `P(total ≥ k)`, and tap a bar.
 *
 * **The graph is about the formula, not about the table.** `500d6` graphs
 * perfectly well and is refused only when it reaches the tray, so a throw too
 * big to roll still answers "what would it have been" — which is most of what
 * a graph is for (`docs/probability.md`).
 *
 * It is also exact. Every number here comes from `core/probability`'s
 * convolution of the same `RollPlan` a roll would use, so the bar heights and
 * the odds beside them are the same arithmetic, not a summary of it.
 */
class GraphMachine(
  private val catalog: DiceCatalog,
  private val rounding: Rounding = Rounding.Default,
) {
  /** What the screen draws. */
  var state: GraphState = GraphState.Empty
    private set

  /** The formula as typed, valid or not. */
  var text: String = ""
    private set

  private var graphed: Pmf? = null
  private var formula: Formula? = null
  private var mode: GraphMode = GraphMode.Exact
  private var picked: Int? = null
  private var rolled: RolledTotal? = null

  /**
   * The formula field changed.
   *
   * The pick is dropped with it: a bar at 14 on one distribution is not the
   * same bar at 14 on the next, and a number left over from the last formula
   * is the one thing a graph must not show.
   */
  fun type(typed: String) {
    text = typed
    picked = null
    graphed = null
    formula = null
    state =
      when (val parsed = FormulaParser.parse(typed)) {
        is ParseResult.Failed -> if (typed.isBlank()) GraphState.Empty else GraphState.Invalid(parsed.error)
        is ParseResult.Parsed -> computed(parsed.formula)
      }
  }

  /** `P(total = k)` or `P(total ≥ k)` — the question the bars answer. */
  fun show(mode: GraphMode) {
    this.mode = mode
    redraw()
  }

  /**
   * A bar was tapped: [value] is the total it stands for, or `null` to let go.
   *
   * A tap outside the distribution is a tap on nothing rather than an error.
   */
  fun pick(value: Int?) {
    picked = value
    redraw()
  }

  /**
   * The throw the player just made, to mark on the chart
   * (`design/dInfinity.dc.html`, option 7a).
   *
   * **Marked only while the graph is still about that roll.** The formula is
   * compared ignoring spacing, case and the label, because none of those
   * changes a distribution — but a d8 where a d6 was is a different chart, and
   * a line on it saying "your roll" would be pointing at somebody else's.
   */
  fun rolled(
    total: Int,
    formula: String,
  ) {
    rolled = RolledTotal(total = total, formula = formula)
    redraw()
  }

  private fun computed(parsed: Formula): GraphState =
    when (val planned = RollPlanner.plan(parsed, catalog)) {
      is PlanResult.Failed -> GraphState.Invalid(planned.error)
      is PlanResult.Planned ->
        when (val distribution = OutcomeGraph.of(parsed, planned.plan, rounding)) {
          is DistributionResult.TooLarge -> GraphState.TooLarge(distribution.reason)
          is DistributionResult.Computed -> {
            graphed = distribution.pmf
            formula = parsed
            drawn(distribution)
          }
        }
    }

  /** The same distribution again, after a tap or a change of question. */
  private fun redraw() {
    val pmf = graphed ?: return
    val parsed = formula ?: return
    state = drawn(DistributionResult.Computed(pmf = pmf, label = parsed.label))
  }

  private fun drawn(distribution: DistributionResult.Computed): GraphState.Graphed {
    val pmf = distribution.pmf
    return GraphState.Graphed(
      bars = GraphBars.of(pmf, mode),
      stats = GraphStats.of(pmf, dice = formula?.diceNodes?.sumOf { it.count } ?: 0),
      mode = mode,
      picked = picked?.takeIf { it in pmf.support }?.let { reading(pmf, it) },
      rolled = marked(pmf),
      label = distribution.label,
      truncatedMass = distribution.truncatedMass,
    )
  }

  /**
   * The rolled total, if this chart is still about the roll it came from.
   *
   * Whitespace, case and the label are all ignored: `3D6 + 4`, `3d6+4` and
   * `3d6 + 4 [Attack]` are one distribution, and a mark that vanished because
   * somebody tidied a space would look like a bug.
   */
  private fun marked(pmf: Pmf): Reading? {
    val throwOf = rolled ?: return null
    if (!same(throwOf.formula, text)) return null
    return if (throwOf.total in pmf.support) reading(pmf, throwOf.total) else null
  }

  private fun reading(
    pmf: Pmf,
    value: Int,
  ): Reading =
    Reading(
      value = value,
      exact = pmf.probabilityOf(value),
      atLeast = pmf.atLeast(value),
    )

  private class RolledTotal(
    val total: Int,
    val formula: String,
  )

  private companion object {
    private val SPACES = Regex("\\s+")

    /** Two formulas that mean the same distribution, however they were typed. */
    fun same(
      one: String,
      other: String,
    ): Boolean = bare(one) == bare(other)

    fun bare(formula: String): String = formula.substringBefore('[').replace(SPACES, "").lowercase()
  }
}

/** The question the bars answer (`design/dInfinity.dc.html`, option 1k). */
enum class GraphMode {
  /** `P(total = k)` — the shape of the distribution. */
  Exact,

  /** `P(total ≥ k)` — the question most game rules actually ask. */
  AtLeast,
}

/** One total and what the distribution says about it. */
data class Reading(
  val value: Int,
  val exact: Double,
  val atLeast: Double,
)

/** What the outcome graph can be showing, and nothing in between. */
sealed interface GraphState {
  /** Nothing typed. */
  data object Empty : GraphState

  /** The formula does not read, or names a die no installed set has. */
  data class Invalid(
    val error: NotationError,
  ) : GraphState

  /**
   * Legal, but past what the graph computes exactly
   * (`docs/probability.md`, limits). It may still be rollable.
   */
  data class TooLarge(
    val reason: String,
  ) : GraphState

  /**
   * The distribution, and everything the chart draws.
   *
   * @param picked the bar the player tapped, or null for none.
   * @param rolled the throw they just made, when this chart is still about it.
   * @param truncatedMass probability sitting in exploding chains that ran into
   *   the depth limit — under 1e-15 for a d6, and worth saying when it is not.
   */
  data class Graphed(
    val bars: List<GraphBar>,
    val stats: GraphStats,
    val mode: GraphMode,
    val picked: Reading? = null,
    val rolled: Reading? = null,
    val label: String? = null,
    val truncatedMass: Double = 0.0,
  ) : GraphState
}
