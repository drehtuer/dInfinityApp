package de.drehtuer.dinfinity.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.stats.FaceBar

/**
 * What every die has done (`design/dInfinity.dc.html`, option 1w).
 *
 * Two screens in one destination: the list of every die ever thrown, and one
 * die opened. Opened rather than pushed, because going back from a histogram
 * to the list is the same gesture as closing it, and a second destination for
 * "the same screen about one row" is a back stack entry nobody wanted.
 */
@Composable
fun StatsScreen(
  presenter: StatsPresenter,
  modifier: Modifier = Modifier,
  onExport: (ExportFile) -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  var exporting by remember { mutableStateOf(false) }
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(StatsTestTags.SCREEN),
  ) {
    Header(
      open = state.selected,
      onClose = presenter::close,
      // Not while one die is open: what the button would write is the whole
      // record either way, and a button that says one thing and does another
      // on one screen out of two is worse than a button that waits.
      offerExport = state.selected == null && !state.empty,
      onExport = { exporting = true },
      menu = menu,
    )

    if (exporting) {
      ExportChoice(
        tagPrefix = StatsTestTags.EXPORT,
        body = stringResource(R.string.stats_export_body),
        onDismiss = { exporting = false },
        onChosen = { format ->
          exporting = false
          presenter.export(format, onExport)
        },
      )
    }

    state.confirming?.let { what ->
      Confirm(what = what, onYes = presenter::reset, onNo = { presenter.confirm(null) })
    }

    when {
      state.selected != null -> Detail(state.selected, presenter)
      state.empty -> Empty()
      else -> Dice(state, presenter)
    }
  }
}

@Composable
private fun Header(
  open: DieDetail?,
  onClose: () -> Unit,
  offerExport: Boolean,
  onExport: () -> Unit,
  menu: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (open != null) {
      TextButton(onClick = onClose, modifier = Modifier.testTag(StatsTestTags.BACK)) { Text("←") }
    }
    Text(
      text = open?.row?.name ?: stringResource(R.string.stats_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    if (offerExport) {
      TextButton(onClick = onExport, modifier = Modifier.testTag(StatsTestTags.EXPORT)) {
        Text(stringResource(R.string.stats_export))
      }
    }
    menu()
  }
}

/**
 * How the list is cut: by set, or not at all, or across all of them
 * (design options `5b` and `5c`).
 *
 * The two are exclusive on purpose. "All my d20s, but only the brass ones" is
 * the same thing as looking at the brass d20, and offering it would put two
 * controls on screen that cancel each other out.
 */
@Composable
private fun Cuts(
  state: StatsState,
  presenter: StatsPresenter,
) {
  if (state.sets.size < 2 && !state.acrossSets) return
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Cut(
      label = stringResource(R.string.stats_all_sets),
      chosen = state.setFilter == null && !state.acrossSets,
      tag = StatsTestTags.ALL_SETS,
      onChoose = { presenter.filterBy(null) },
    )
    state.sets.forEach { setId ->
      Cut(
        label = setId,
        chosen = state.setFilter == setId,
        tag = StatsTestTags.setOf(setId),
        onChoose = { presenter.filterBy(setId) },
      )
    }
    Cut(
      label = stringResource(R.string.stats_across_sets),
      chosen = state.acrossSets,
      tag = StatsTestTags.ACROSS_SETS,
      onChoose = { presenter.rollUp(!state.acrossSets) },
    )
  }
}

@Composable
private fun Cut(
  label: String,
  chosen: Boolean,
  tag: String,
  onChoose: () -> Unit,
) {
  TextButton(onClick = onChoose, modifier = Modifier.testTag(tag)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

@Composable
private fun Dice(
  state: StatsState,
  presenter: StatsPresenter,
) {
  val dice = state.dice
  Cuts(state, presenter)
  Text(
    text = stringResource(if (state.acrossSets) R.string.stats_across_sets_note else R.string.stats_order),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
  )
  if (state.filteredToNothing) {
    Text(
      text = stringResource(R.string.stats_filtered_empty),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(16.dp).testTag(StatsTestTags.FILTERED_EMPTY),
    )
  }
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(StatsTestTags.LIST)) {
    items(dice, key = { "${it.setId}/${it.dieId}" }) { row ->
      HorizontalDivider()
      DieLine(row = row, onOpen = { presenter.select(row.setId, row.dieId) })
    }
    item(key = "reset-everything") {
      HorizontalDivider()
      TextButton(
        onClick = { presenter.confirm(Reset.Everything) },
        modifier = Modifier.fillMaxWidth().testTag(StatsTestTags.RESET_ALL),
      ) {
        Text(stringResource(R.string.stats_reset_all), color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

@Composable
private fun DieLine(
  row: DieRow,
  onOpen: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
        .semantics(mergeDescendants = true) {}
        .testTag(StatsTestTags.dieOf(row.setId, row.dieId))
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = row.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text =
          if (row.installed) {
            row.setId
          } else {
            stringResource(R.string.stats_set_gone, row.setId)
          },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
      text = pluralStringResource(R.plurals.stats_throws, row.summary.throws.toInt(), row.summary.throws),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = row.summary.mean?.let { "%.2f".format(it) } ?: "—",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.testTag(StatsTestTags.meanOf(row.setId, row.dieId)),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Detail(
  detail: DieDetail,
  presenter: StatsPresenter,
) {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .testTag(StatsTestTags.DETAIL),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Tile(
        label = stringResource(R.string.stats_natural_high, detail.extremes.highestValue),
        value = detail.extremes.highs.toString(),
        tag = StatsTestTags.HIGHS,
      )
      Tile(
        label = stringResource(R.string.stats_natural_low, detail.extremes.lowestValue),
        value = detail.extremes.lows.toString(),
        tag = StatsTestTags.LOWS,
      )
      Tile(
        label = stringResource(R.string.stats_average),
        value =
          detail.row.summary.mean
            ?.let { "%.2f".format(it) } ?: "—",
        tag = StatsTestTags.MEAN,
      )
      Tile(
        label = stringResource(R.string.stats_total_throws),
        value =
          detail.row.summary.throws
            .toString(),
        tag = StatsTestTags.THROWS,
      )
    }

    // Said before the bars, not after: a player should know the line is a
    // guess before they read anything into the shape.
    if (detail.fairLineIsAGuess) {
      Text(
        text = stringResource(R.string.stats_guessed_line),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(StatsTestTags.GUESSED),
      )
    }

    Histogram(detail.bars)

    TextButton(
      onClick = {
        presenter.confirm(Reset.OneDie(detail.setId, detail.dieId, detail.row.name))
      },
      modifier = Modifier.testTag(StatsTestTags.RESET_DIE),
    ) {
      Text(stringResource(R.string.stats_reset_die), color = MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
private fun Tile(
  label: String,
  value: String,
  tag: String,
) {
  Column(
    modifier =
      Modifier
        .width(TILE)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        // One thing, to a reader and to TalkBack alike: "3, natural 20" is a
        // fact, and two separate announcements of it are not.
        .semantics(mergeDescendants = true) {}
        .padding(10.dp)
        .testTag(tag),
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Every value the die can show, against what a fair one would do.
 *
 * Rows rather than a canvas: a d100 has a hundred bars, a phone is 360 dp
 * wide, and a horizontal chart of a hundred bars three pixels apart is a
 * smear. The arithmetic behind them is `core/stats`' and is tested there.
 */
@Composable
private fun Histogram(bars: List<FaceBar>) {
  val widest = bars.maxOfOrNull { maxOf(it.share, it.fairShare) }?.takeIf { it > 0.0 } ?: 1.0
  Column(
    modifier = Modifier.fillMaxWidth().testTag(StatsTestTags.HISTOGRAM),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    bars.forEach { bar ->
      Row(
        modifier = Modifier.fillMaxWidth().testTag(StatsTestTags.barOf(bar.value)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = bar.value.toString(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(LABEL),
        )
        Box(modifier = Modifier.weight(1f)) {
          // The fair line behind the bar, so "is this die cursed" is answered
          // by looking rather than by arithmetic.
          Box(
            modifier =
              Modifier
                .fillMaxWidth((bar.fairShare / widest).toFloat())
                .height(BAR)
                .background(MaterialTheme.colorScheme.outline),
          )
          Box(
            modifier =
              Modifier
                .fillMaxWidth((bar.share / widest).toFloat())
                .height(BAR)
                .background(MaterialTheme.colorScheme.primary),
          )
        }
        Text(
          text = bar.count.toString(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(LABEL),
        )
      }
    }
  }
}

@Composable
private fun Confirm(
  what: Reset,
  onYes: () -> Unit,
  onNo: () -> Unit,
) {
  AlertDialog(
    modifier = Modifier.testTag(StatsTestTags.CONFIRM),
    onDismissRequest = onNo,
    title = { Text(stringResource(R.string.stats_confirm_title)) },
    text = {
      Text(
        when (what) {
          is Reset.OneDie -> stringResource(R.string.stats_confirm_die, what.name)
          Reset.Everything -> stringResource(R.string.stats_confirm_all)
        },
      )
    },
    confirmButton = {
      Button(onClick = onYes, modifier = Modifier.testTag(StatsTestTags.CONFIRM_YES)) {
        Text(stringResource(R.string.stats_confirm_yes))
      }
    },
    dismissButton = {
      TextButton(onClick = onNo, modifier = Modifier.testTag(StatsTestTags.CONFIRM_NO)) {
        Text(stringResource(R.string.group_cancel_stats))
      }
    },
  )
}

@Composable
private fun Empty() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp).testTag(StatsTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = stringResource(R.string.stats_empty_title),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.stats_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private val TILE = 150.dp
private val LABEL = 32.dp
private val BAR = 14.dp

/** What the tests reach the statistics screen by. */
object StatsTestTags {
  const val SCREEN: String = "stats:screen"
  const val LIST: String = "stats:list"
  const val DETAIL: String = "stats:detail"
  const val EMPTY: String = "stats:empty"
  const val BACK: String = "stats:back"

  /** Also the prefix the export dialog's own tags are built from. */
  const val EXPORT: String = "stats:export"
  const val EXPORT_DIALOG: String = "$EXPORT:dialog"
  const val EXPORT_JSON: String = "$EXPORT:json"
  const val EXPORT_CSV: String = "$EXPORT:csv"
  const val HISTOGRAM: String = "stats:histogram"
  const val HIGHS: String = "stats:highs"
  const val LOWS: String = "stats:lows"
  const val MEAN: String = "stats:mean"
  const val THROWS: String = "stats:throws"
  const val GUESSED: String = "stats:guessed"
  const val ALL_SETS: String = "stats:allsets"
  const val ACROSS_SETS: String = "stats:acrosssets"
  const val FILTERED_EMPTY: String = "stats:filtered-empty"

  fun setOf(setId: String): String = "stats:set:$setId"

  const val RESET_DIE: String = "stats:reset-die"
  const val RESET_ALL: String = "stats:reset-all"
  const val CONFIRM: String = "stats:confirm"
  const val CONFIRM_YES: String = "stats:confirm:yes"
  const val CONFIRM_NO: String = "stats:confirm:no"

  fun dieOf(
    setId: String,
    dieId: String,
  ): String = "stats:die:$setId/$dieId"

  fun meanOf(
    setId: String,
    dieId: String,
  ): String = "stats:die:$setId/$dieId:mean"

  fun barOf(value: Int): String = "stats:bar:$value"
}
