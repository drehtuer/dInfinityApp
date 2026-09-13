package de.drehtuer.dinfinity.feature.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * What a formula is likely to come to, before or after it is thrown
 * (`design/dInfinity.dc.html`, options 1k and 7a).
 *
 * Stateless over [GraphPresenter], which is stateless over [GraphMachine]: the
 * distribution, the bars and every number beside them are settled before a
 * pixel is placed.
 *
 * The formula it is about arrives with the screen and is shown rather than
 * edited. Editing it here wants the same live-validated field with a squiggle
 * the roll screen has, and that field belongs somewhere both screens can reach
 * before it belongs to two of them (`docs/TODO.md`, Step 4.2).
 */
@Composable
fun GraphScreen(
  presenter: GraphPresenter,
  modifier: Modifier = Modifier,
  menu: @Composable () -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .testTag(GraphTestTags.SCREEN),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = presenter.text.ifBlank { stringResource(R.string.graph_no_formula) },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f).testTag(GraphTestTags.FORMULA),
      )
      menu()
    }

    when (val state = presenter.state) {
      GraphState.Empty ->
        Note(text = stringResource(R.string.graph_empty), tag = GraphTestTags.EMPTY)

      is GraphState.Invalid ->
        Note(
          text = state.error.message,
          tag = GraphTestTags.INVALID,
          colour = MaterialTheme.colorScheme.error,
        )

      // Legal, and past what can be worked out exactly. Saying so is the only
      // honest answer: an approximated curve presented as the odds would be a
      // number somebody bets on (`docs/probability.md`).
      is GraphState.TooLarge ->
        Note(text = state.reason, tag = GraphTestTags.TOO_LARGE)

      is GraphState.Graphed -> Graphed(state, presenter)
    }
  }
}

@Composable
private fun Graphed(
  state: GraphState.Graphed,
  presenter: GraphPresenter,
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
  val rolled = state.rolled
  if (rolled != null) {
    Text(
      text = stringResource(R.string.graph_your_roll, rolled.value, percent(rolled.exact), percent(rolled.atLeast)),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.testTag(GraphTestTags.ROLLED),
    )
  }

  val picked = state.picked
  Text(
    text =
      if (picked == null) {
        stringResource(R.string.graph_tap_a_bar)
      } else {
        stringResource(R.string.graph_picked, picked.value, percent(picked.exact), percent(picked.atLeast))
      },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.testTag(GraphTestTags.PICKED),
  )

  Numbers(state.stats)
}

/** `P(total = k)` or `P(total ≥ k)` — the question the bars answer. */
@Composable
private fun Question(
  chosen: GraphMode,
  onAsk: (GraphMode) -> Unit,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.testTag(GraphTestTags.MODE),
  ) {
    GraphMode.entries.forEach { mode ->
      FilterChip(
        selected = mode == chosen,
        onClick = { onAsk(mode) },
        label = { Text(stringResource(mode.label())) },
        modifier = Modifier.testTag(GraphTestTags.modeOf(mode)),
      )
    }
  }
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
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Number(MEAN, stringResource(R.string.graph_stat_mean), format(stats.mean))
    Number(DEVIATION, stringResource(R.string.graph_stat_deviation), format(stats.standardDeviation))
    Number(RANGE, stringResource(R.string.graph_stat_range), "${stats.lowest}–${stats.highest}")
    Number(LOWEST, stringResource(R.string.graph_stat_chance_of, stats.lowest), percent(stats.chanceOfLowest))
    Number(HIGHEST, stringResource(R.string.graph_stat_chance_of, stats.highest), percent(stats.chanceOfHighest))
    Number(DICE, stringResource(R.string.graph_stat_dice), stats.dice.toString())
  }
}

@Composable
private fun Number(
  tag: String,
  label: String,
  value: String,
) {
  Row(
    // Merged, so TalkBack reads "Mean, 7.0" as one thing rather than as a
    // label somewhere near a number.
    modifier =
      Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}
        .testTag(GraphTestTags.statOf(tag)),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Quiet(label)
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Composable
private fun Quiet(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun Note(
  text: String,
  tag: String,
  colour: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = colour,
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

private val CHART_HEIGHT = 200.dp
private const val PER_CENT = 100.0
private const val SMALLEST = 0.1

private const val MEAN = "mean"
private const val DEVIATION = "deviation"
private const val RANGE = "range"
private const val LOWEST = "lowest"
private const val HIGHEST = "highest"
private const val DICE = "dice"
