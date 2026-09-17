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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.stats.RollComparison
import de.drehtuer.dinfinity.core.stats.TotalBar
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist

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
    modifier = Modifier.fillMaxWidth().padding(horizontal = Modernist.x4, vertical = Modernist.x2),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Text(
      text = state.selected?.roll?.name ?: stringResource(R.string.savedstats_title),
      // The heading weight is the style's own 800; `Bold` is 700.
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    if (state.selected != null) {
      TextButton(
        onClick = { presenter.close() },
        shape = Modernist.square,
        modifier = Modifier.testTag(SavedStatsTestTags.BACK),
      ) {
        Text(stringResource(R.string.savedstats_back))
      }
    }
    menu()
  }
  // The rule every screen in the prototype hangs from.
  HorizontalDivider(
    thickness = Modernist.rule,
    color = Ink.divider,
    modifier = Modifier.testTag(SavedStatsTestTags.HEADER_RULE),
  )
}

@Composable
private fun EmptyNote() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(Modernist.x4).testTag(SavedStatsTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    Text(
      text = stringResource(R.string.savedstats_empty_title),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.savedstats_empty_body),
      style = MaterialTheme.typography.bodyLarge,
      color = Ink.muted,
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
    color = Ink.muted,
    modifier = Modifier.padding(horizontal = Modernist.x4, vertical = Modernist.x1),
  )
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(SavedStatsTestTags.LIST)) {
    items(rolls, key = SavedRoll::id) { roll ->
      HorizontalDivider(thickness = Modernist.hairline, color = Ink.divider)
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { presenter.select(roll.id) }
            .padding(horizontal = Modernist.x4, vertical = Modernist.x3)
            .testTag(SavedStatsTestTags.rollOf(roll.id)),
      ) {
        Text(
          text = roll.name,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = roll.formula,
          style = MaterialTheme.typography.labelSmall,
          color = Ink.muted,
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
        .padding(horizontal = Modernist.x4)
        .testTag(SavedStatsTestTags.DETAIL),
    verticalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Text(
      text = stats.roll.formula,
      style = MaterialTheme.typography.bodyLarge,
      color = Ink.muted,
    )
    if (comparison.throws == 0L) {
      Text(text = stringResource(R.string.savedstats_never), modifier = Modifier.testTag(SavedStatsTestTags.NEVER))
      return@Column
    }
    Numbers(comparison, stats.observedOnly)
    stats.noExpectation?.let { why ->
      Text(
        text = why,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
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
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    Text(
      text = pluralStringResource(R.plurals.savedstats_throws, comparison.throws.toInt(), comparison.throws.toInt()),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.testTag(SavedStatsTestTags.THROWS),
    )
    Text(
      text = stringResource(R.string.savedstats_rolled, format(comparison.mean)),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.testTag(SavedStatsTestTags.MEAN),
    )
    if (!observedOnly) {
      Text(
        text = stringResource(R.string.savedstats_expected, format(comparison.expectedMean)),
        style = MaterialTheme.typography.bodyLarge,
        color = Ink.muted,
        modifier = Modifier.testTag(SavedStatsTestTags.EXPECTED),
      )
    }
    val lowest = comparison.lowest
    val highest = comparison.highest
    if (lowest == null || highest == null) return@Column
    Text(
      text =
        if (observedOnly || comparison.possible.isEmpty()) {
          stringResource(R.string.savedstats_range_observed, lowest, highest)
        } else {
          stringResource(
            R.string.savedstats_range,
            lowest,
            highest,
            comparison.possible.first,
            comparison.possible.last,
          )
        },
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
      modifier = Modifier.testTag(SavedStatsTestTags.RANGE),
    )
  }
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
    style = MaterialTheme.typography.labelSmall,
    color = if (comparison.worthALook) MaterialTheme.colorScheme.primary else Ink.muted,
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
  val mark = MaterialTheme.colorScheme.primary
  // Which rectangle is the bar and which the distribution's mark is carried by
  // colour alone, on a `Canvas` that hands a screen reader an empty rectangle.
  // What the picture claims is a count, and a count can be said out loud
  // (`docs/architecture.md`, "Accessibility").
  val reading = TotalsReading.of(bars)
  val said =
    listOfNotNull(
      reading?.let { pluralStringResource(R.plurals.savedstats_chart_shape, it.bars, it.bars) },
      reading?.let { stringResource(R.string.savedstats_chart_range, it.lowest, it.highest) },
      reading?.let { pluralStringResource(R.plurals.savedstats_chart_above, it.above, it.above) },
    ).joinToString(separator = " ")
  Canvas(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(CHART_HEIGHT)
        .semantics { contentDescription = said }
        .testTag(SavedStatsTestTags.CHART),
  ) {
    // Where every rectangle goes is arithmetic and lives in `ChartShapes`,
    // which a test can read. What is left here is `drawRect`, which is the one
    // thing a test cannot (`docs/statistics.md`).
    ChartShapes.of(bars, size.width, size.height).forEach { shape ->
      drawRect(
        color = if (shape.isMark) mark else ink,
        topLeft = Offset(shape.left, shape.top),
        size = Size(shape.width, shape.height),
      )
    }
  }
}

/** Two decimals, or an em dash when there is no number. Not a composable: it composes nothing. */
private fun format(value: Double?): String = value?.let { "%.2f".format(it) } ?: "—"

private val CHART_HEIGHT = Modernist.x8 * 4

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

  /** The 2 dp rule the whole screen hangs from. */
  const val HEADER_RULE: String = "savedstats:header-rule"

  fun rollOf(id: String): String = "savedstats:roll:$id"
}
