package de.drehtuer.dinfinity.feature.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.ui.common.FormulaField
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.RuleWeight
import de.drehtuer.dinfinity.ui.common.SegmentedControl

/**
 * What a formula is likely to come to, before or after it is thrown
 * (`design/dInfinity.dc.html`, options 1k and 7a).
 *
 * Stateless over [GraphPresenter], which is stateless over [GraphMachine]: the
 * distribution, the bars and every number beside them are settled before a
 * pixel is placed.
 *
 * The formula it is about arrives with the screen and can be edited there, in
 * the same live-validated field the tray has — `ui/common`'s, so the two
 * cannot come to disagree about whether a formula is valid.
 */
@Composable
fun GraphScreen(
  presenter: GraphPresenter,
  modifier: Modifier = Modifier,
  onRoll: (String) -> Unit = {},
  onSave: (String) -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag(GraphTestTags.SCREEN),
  ) {
    // The screen's name over the 2 dp rule every screen in the prototype hangs
    // from. `titleLarge` is the heading face at 800 — the system's own heading
    // weight.
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Modernist.x4, vertical = Modernist.x2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.graph_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
      )
      menu()
    }
    Rule()

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(Modernist.x4),
      verticalArrangement = Arrangement.spacedBy(Modernist.x3),
    ) {
      // The same live-validated field the tray has, squiggle and all. It is
      // `ui/common`'s, so the two screens cannot come to disagree about
      // whether a formula is valid (`docs/architecture.md`, Modules).
      FormulaField(
        text = presenter.text,
        onChange = presenter::type,
        label = stringResource(R.string.graph_formula_label),
        hint = stringResource(R.string.graph_formula_hint),
        error = (presenter.state as? GraphState.Invalid)?.error,
      )

      when (val state = presenter.state) {
        GraphState.Empty ->
          Note(text = stringResource(R.string.graph_empty), tag = GraphTestTags.EMPTY)

        // The field says what is wrong, under the part that is wrong. Saying
        // it twice on one screen is saying it once too often.
        is GraphState.Invalid -> Unit

        // Legal, and past what can be worked out exactly. Saying so is the
        // only honest answer: an approximated curve presented as the odds
        // would be a number somebody bets on (`docs/probability.md`).
        is GraphState.TooLarge ->
          Note(text = state.reason, tag = GraphTestTags.TOO_LARGE)

        is GraphState.Graphed -> Graphed(state, presenter, onRoll, onSave)
      }
    }
  }
}

@Composable
private fun Graphed(
  state: GraphState.Graphed,
  presenter: GraphPresenter,
  onRoll: (String) -> Unit,
  onSave: (String) -> Unit,
) {
  Question(chosen = state.mode, onAsk = presenter::show)

  GraphChart(
    bars = state.bars,
    stats = state.stats,
    picked = state.picked,
    rolled = state.rolled,
    onPick = presenter::pick,
    modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
  )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Quiet(state.stats.lowest.toString())
    Quiet(stringResource(R.string.graph_mean_is, format(state.stats.mean)))
    Quiet(state.stats.highest.toString())
  }

  // The roll that opened this, when the chart is still about it.
  state.rolled?.let { rolled -> YourRoll(rolled) }
  Picked(state.picked)

  Numbers(state.stats)

  // Under the chart, because they are what somebody does *after* reading it
  // (`design/dInfinity.dc.html`, option 7a). Only here, inside `Graphed`: a
  // formula that does not parse is not one to roll or to keep, and a button
  // that refuses is worse than one that is not there.
  Doing(formula = presenter.text, onRoll = onRoll, onSave = onSave)
}

/**
 * The roll that opened the graph, in words as well as in ink.
 *
 * A band with the accent down its left edge, which is how the prototype says
 * "this line on the chart is yours" (`design/dInfinityPhone.dc.html`:
 * `border-left:3px solid var(--color-accent)` over a tint of it).
 */
@Composable
private fun YourRoll(rolled: Reading) {
  Row(
    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier
          .width(MARK)
          .height(BAND_HEIGHT)
          .background(MaterialTheme.colorScheme.primary),
    )
    Text(
      text = stringResource(R.string.graph_your_roll, rolled.value, percent(rolled.exact), percent(rolled.atLeast)),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
      modifier =
        Modifier
          .padding(horizontal = Modernist.x3, vertical = Modernist.x2)
          .testTag(GraphTestTags.ROLLED),
    )
  }
}

/** What the bar under the finger is worth, or an invitation to tap one. */
@Composable
private fun Picked(picked: Reading?) {
  Text(
    text =
      if (picked == null) {
        stringResource(R.string.graph_tap_a_bar)
      } else {
        stringResource(R.string.graph_picked, picked.value, percent(picked.exact), percent(picked.atLeast))
      },
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.testTag(GraphTestTags.PICKED),
  )
}

/**
 * The two things to do with a formula you have just read the odds of.
 *
 * "Roll this" hands it to the tray; "Save as roll" opens the editor with it
 * already typed. Neither does the thing itself — where those go is the
 * navigation graph's business, and that belongs to `:app`
 * (`docs/architecture.md`, Modules).
 */
@Composable
private fun Doing(
  formula: String,
  onRoll: (String) -> Unit,
  onSave: (String) -> Unit,
) {
  // `.btn-primary` beside `.btn-secondary`, both hugging their labels: the
  // system's buttons are sized by what is written on them, and neither of
  // these is the screen.
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    ModernistButton(
      text = stringResource(R.string.graph_roll_this),
      onClick = { onRoll(formula) },
      kind = ModernistButtonKind.Primary,
      modifier = Modifier.testTag(GraphTestTags.ROLL_THIS),
    )
    ModernistButton(
      text = stringResource(R.string.graph_save_as_roll),
      onClick = { onSave(formula) },
      modifier = Modifier.testTag(GraphTestTags.SAVE_AS_ROLL),
    )
  }
}

/** `P(total = k)` or `P(total ≥ k)` — the question the bars answer. */
@Composable
private fun Question(
  chosen: GraphMode,
  onAsk: (GraphMode) -> Unit,
) {
  SegmentedControl(
    options = GraphMode.entries,
    selected = chosen,
    label = { mode -> stringResource(mode.label()) },
    onSelect = onAsk,
    tagOf = GraphTestTags::modeOf,
    modifier = Modifier.testTag(GraphTestTags.MODE),
  )
}

private fun GraphMode.label(): Int =
  when (this) {
    GraphMode.Exact -> R.string.graph_mode_exact
    GraphMode.AtLeast -> R.string.graph_mode_at_least
  }

/**
 * The six numbers a picture cannot say.
 *
 * The two extreme probabilities are there because they are the ones people
 * argue about: how often `4d6dl1` gives an 18, how often `1d20` gives a 1.
 */
@Composable
private fun Numbers(stats: GraphStats) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Rule()
    Row(modifier = Modifier.fillMaxWidth()) {
      Number(MEAN, stringResource(R.string.graph_stat_mean), format(stats.mean))
      Number(DEVIATION, stringResource(R.string.graph_stat_deviation), format(stats.standardDeviation))
      Number(RANGE, stringResource(R.string.graph_stat_range), "${stats.lowest}–${stats.highest}")
    }
    Rule(weight = RuleWeight.Hairline)
    Row(modifier = Modifier.fillMaxWidth()) {
      Number(LOWEST, stringResource(R.string.graph_stat_chance_of, stats.lowest), percent(stats.chanceOfLowest))
      Number(HIGHEST, stringResource(R.string.graph_stat_chance_of, stats.highest), percent(stats.chanceOfHighest))
      Number(DICE, stringResource(R.string.graph_stat_dice), stats.dice.toString())
    }
    Rule(weight = RuleWeight.Hairline)
  }
}

/**
 * One cell of the grid: a kicker over a number in the heading face.
 *
 * Not a label with the value beside it. The prototype sets these as six cells
 * of one ruled grid, which is what lets the eye go down a column of numbers
 * instead of along six lines of prose.
 */
@Composable
private fun RowScope.Number(
  tag: String,
  label: String,
  value: String,
) {
  Column(
    // Merged, so TalkBack reads "Mean, 7.0" as one thing rather than as a
    // label somewhere near a number.
    modifier =
      Modifier
        .weight(1f)
        .semantics(mergeDescendants = true) {}
        .padding(end = Modernist.x2, top = Modernist.x3, bottom = Modernist.x3)
        .testTag(GraphTestTags.statOf(tag)),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      letterSpacing = Modernist.headingTracking,
      color = Ink.muted,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

/** The prototype's `font-size:11px;opacity:.65` — a caption under the chart. */
@Composable
private fun Quiet(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = Ink.muted,
  )
}

@Composable
private fun Note(
  text: String,
  tag: String,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = Ink.muted,
    modifier = Modifier.testTag(tag),
  )
}

/**
 * A probability as a percentage, to one decimal — and never as `0.0 %` when it
 * is possible.
 *
 * A 1-in-10,000 outcome printed as zero is the app telling a player something
 * cannot happen when it can, which on a screen about probability is the one
 * thing it must not do.
 */
internal fun percent(probability: Double): String {
  val shown = probability * PER_CENT
  return when {
    probability <= 0.0 -> "0 %"
    shown < SMALLEST -> "< $SMALLEST %"
    else -> "%.1f %%".format(shown)
  }
}

/** A mean or a deviation, to one decimal. */
internal fun format(value: Double): String = "%.1f".format(value)

/** `height:220px` — the chart the prototype draws. */
private val CHART_HEIGHT: Dp = 220.dp

/** The accent edge beside the roll's own line, as tall as the line it names. */
private val BAND_HEIGHT: Dp = 40.dp
private const val PER_CENT = 100.0
private const val SMALLEST = 0.1

private const val MEAN = "mean"
private const val DEVIATION = "deviation"
private const val RANGE = "range"
private const val LOWEST = "lowest"
private const val HIGHEST = "highest"
private const val DICE = "dice"
