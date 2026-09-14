package de.drehtuer.dinfinity.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.stats.RollComparison
import de.drehtuer.dinfinity.core.stats.TotalBar

/**
 * What a saved roll has actually rolled, against what it should
 * (`design/dInfinity.dc.html`, options `8b` and `9e`).
 *
 * Observed totals as ink bars; the exact distribution as marks across them.
 * The comparison is `core/stats`' and the distribution is
 * `core/probability`'s — what is here is the drawing, which is the one part a
 * test cannot read.
 *
 * The screen refuses to draw a conclusion. A drift is shown with the number of
 * throws behind it, and is called "worth a look" only once it is past two
 * standard errors — dice are not accused on the strength of forty throws
 * (`docs/statistics.md`).
 */
@Composable
fun SavedStatsScreen(
  presenter: SavedStatsPresenter,
  modifier: Modifier = Modifier,
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(SavedStatsTestTags.SCREEN),
  ) {
    Header(state, presenter, menu)
    when {
      state.selected != null -> Detail(state.selected)
      state.empty -> EmptyNote()
      else -> Rolls(state.rolls, presenter)
    }
  }
}

@Composable
private fun Header(
  state: SavedStatsState,
  presenter: SavedStatsPresenter,
  menu: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = state.selected?.roll?.name ?: stringResource(R.string.savedstats_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f),
    )
    if (state.selected != null) {
      TextButton(onClick = { presenter.close() }, modifier = Modifier.testTag(SavedStatsTestTags.BACK)) {
        Text(stringResource(R.string.savedstats_back))
      }
    }
    menu()
  }
}

@Composable
private fun EmptyNote() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(SavedStatsTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = stringResource(R.string.savedstats_empty_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(R.string.savedstats_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun Rolls(
  rolls: List<SavedRoll>,
  presenter: SavedStatsPresenter,
) {
  Text(
    text = stringResource(R.string.savedstats_order),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
  )
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(SavedStatsTestTags.LIST)) {
    items(rolls, key = SavedRoll::id) { roll ->
      HorizontalDivider()
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { presenter.select(roll.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(SavedStatsTestTags.rollOf(roll.id)),
      ) {
        Text(text = roll.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
          text = roll.formula,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun Detail(stats: SavedRollStats) {
  val comparison = stats.comparison
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .testTag(SavedStatsTestTags.DETAIL),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = stats.roll.formula,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (comparison.throws == 0L) {
      Text(text = stringResource(R.string.savedstats_never), modifier = Modifier.testTag(SavedStatsTestTags.NEVER))
      return@Column
    }
    Numbers(comparison, stats.observedOnly)
    stats.noExpectation?.let { why ->
      Text(
        text = why,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(SavedStatsTestTags.NO_EXPECTATION),
      )
    }
    Chart(comparison.bars)
    Verdict(comparison)
  }
}

/** Throws, the two means, and the range against the possible range. */
@Composable
private fun Numbers(
  comparison: RollComparison,
  observedOnly: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = pluralStringResource(R.plurals.savedstats_throws, comparison.throws.toInt(), comparison.throws.toInt()),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.testTag(SavedStatsTestTags.THROWS),
    )
    Text(
      text = stringResource(R.string.savedstats_rolled, format(comparison.mean)),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.testTag(SavedStatsTestTags.MEAN),
    )
    if (!observedOnly) {
      Text(
        text = stringResource(R.string.savedstats_expected, format(comparison.expectedMean)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(SavedStatsTestTags.EXPECTED),
      )
    }
    Range(comparison, observedOnly)
  }
}

@Composable
private fun Range(
  comparison: RollComparison,
  observedOnly: Boolean,
) {
  val lowest = comparison.lowest ?: return
  val highest = comparison.highest ?: return
  Text(
    text =
      if (observedOnly || comparison.possible.isEmpty()) {
        stringResource(R.string.savedstats_range_observed, lowest, highest)
      } else {
        stringResource(R.string.savedstats_range, lowest, highest, comparison.possible.first, comparison.possible.last)
      },
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.testTag(SavedStatsTestTags.RANGE),
  )
}

/**
 * Whether the average is worth remarking on, and never more than that.
 *
 * Below [RollComparison.ENOUGH_TO_JUDGE] throws it says so rather than showing
 * a number that invites a conclusion the data cannot support.
 */
@Composable
private fun Verdict(comparison: RollComparison) {
  val text =
    when {
      comparison.driftInErrors == null -> stringResource(R.string.savedstats_too_few)
      comparison.worthALook -> stringResource(R.string.savedstats_worth_a_look)
      else -> return
    }
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = if (comparison.worthALook) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.testTag(SavedStatsTestTags.VERDICT),
  )
}

/**
 * Observed totals as ink bars, the exact distribution as marks across them
 * (design `8b`).
 *
 * Both are scaled to the same tallest share, so a mark that sits above its bar
 * means exactly what it looks like.
 */
@Composable
private fun Chart(bars: List<TotalBar>) {
  if (bars.isEmpty()) return
  val ink = MaterialTheme.colorScheme.onSurface
  val mark = MaterialTheme.colorScheme.error
  val tallest = bars.maxOf { maxOf(it.observed, it.expected) }.coerceAtLeast(MINIMUM_SCALE)
  Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT).testTag(SavedStatsTestTags.CHART)) {
    val step = size.width / bars.size
    bars.forEachIndexed { index, bar ->
      val left = index * step
      val width = step * BAR_SHARE
      val barHeight = (bar.observed / tallest * size.height).toFloat()
      drawRect(
        color = ink,
        topLeft = Offset(left + (step - width) / 2, size.height - barHeight),
        size = Size(width, barHeight),
      )
      if (bar.expected <= 0.0) return@forEachIndexed
      val markY = size.height - (bar.expected / tallest * size.height).toFloat()
      drawRect(
        color = mark,
        topLeft = Offset(left + (step - width) / 2, markY),
        size = Size(width, MARK_THICKNESS),
      )
    }
  }
}

@Composable
private fun format(value: Double?): String = value?.let { "%.2f".format(it) } ?: "—"

private val CHART_HEIGHT = 140.dp
private const val BAR_SHARE = 0.7f
private const val MARK_THICKNESS = 2f

/** A chart of nothing but zeroes still needs a scale to divide by. */
private const val MINIMUM_SCALE = 1e-9

/** What the tests reach for. */
object SavedStatsTestTags {
  const val SCREEN: String = "savedstats:screen"
  const val LIST: String = "savedstats:list"
  const val EMPTY: String = "savedstats:empty"
  const val DETAIL: String = "savedstats:detail"
  const val BACK: String = "savedstats:back"
  const val CHART: String = "savedstats:chart"
  const val THROWS: String = "savedstats:throws"
  const val MEAN: String = "savedstats:mean"
  const val EXPECTED: String = "savedstats:expected"
  const val RANGE: String = "savedstats:range"
  const val VERDICT: String = "savedstats:verdict"
  const val NEVER: String = "savedstats:never"
  const val NO_EXPECTATION: String = "savedstats:noexpectation"

  fun rollOf(id: String): String = "savedstats:roll:$id"
}
