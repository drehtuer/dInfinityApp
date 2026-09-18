package de.drehtuer.dinfinity.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import de.drehtuer.dinfinity.core.stats.FaceBar
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.RuleWeight
import de.drehtuer.dinfinity.ui.common.Sheet
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET

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
    modifier = Modifier.fillMaxWidth().padding(horizontal = Modernist.x4, vertical = Modernist.x2),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    if (open != null) {
      // An arrow is a picture. What TalkBack reads is the label, because "left
      // arrow" is not a thing anybody wants done to their screen.
      val back = stringResource(R.string.stats_back)
      ModernistButton(
        text = "←",
        onClick = onClose,
        kind = ModernistButtonKind.Ghost,
        modifier =
          Modifier
            .sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET)
            .semantics { contentDescription = back }
            .testTag(StatsTestTags.BACK),
      )
    }
    Text(
      text = open?.row?.name ?: stringResource(R.string.stats_title),
      // `titleLarge` is already the heading face at 800 — the system's heading
      // weight. Asking for `Bold` on top of it is asking for 700, which is a
      // lighter heading than the design has.
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    if (offerExport) {
      ModernistButton(
        text = stringResource(R.string.stats_export),
        onClick = onExport,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(StatsTestTags.EXPORT),
      )
    }
    menu()
  }
  // Every screen in the prototype hangs from a 2 dp rule under its title.
  Rule(modifier = Modifier.testTag(StatsTestTags.HEADER_RULE))
}

/**
 * Which order the list is in (`docs/TODO.md`, 4.7).
 *
 * Drawn only once there is more than one die, because an order is a choice
 * between arrangements and one row has none. The same rule the set chooser
 * follows a line above.
 */
@Composable
private fun Orders(
  state: StatsState,
  presenter: StatsPresenter,
) {
  if (state.dice.size < 2) return
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = Modernist.x2),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    DieOrder.entries.forEach { order ->
      Cut(
        label = stringResource(labelFor(order)),
        chosen = state.order == order,
        tag = StatsTestTags.orderOf(order),
        onChoose = { presenter.orderBy(order) },
      )
    }
  }
}

private fun labelFor(order: DieOrder): Int =
  when (order) {
    DieOrder.Recent -> R.string.stats_sort_recent
    DieOrder.Throws -> R.string.stats_sort_throws
    DieOrder.Average -> R.string.stats_sort_average
  }

/**
 * The line above the list, which says what the order actually is.
 *
 * The roll-up's own note wins: what the rows *are* is more surprising than
 * what order they are in, and two lines of explanation over one list is one
 * line too many.
 */
private fun noteFor(state: StatsState): Int =
  when {
    state.acrossSets -> R.string.stats_across_sets_note
    state.cutToSession -> R.string.stats_session_note
    state.order == DieOrder.Throws -> R.string.stats_order_throws
    state.order == DieOrder.Average -> R.string.stats_order_average
    else -> R.string.stats_order
  }

/**
 * Which session the statistics are cut to (`docs/statistics.md`, per session;
 * design option `6c`).
 *
 * Its own row above the set chooser rather than another chip in it, because it
 * cuts across the others: a session and a set are two different questions, and
 * "the brass d20, this campaign" is a sentence a player would actually say.
 * That is the opposite of the roll-up and the set filter, which cancel each
 * other out and share a row for exactly that reason.
 *
 * Not drawn until there are two sessions to choose between — which is every
 * install that has not made one.
 */
@Composable
private fun Sessions(
  state: StatsState,
  presenter: StatsPresenter,
) {
  if (!state.sessionsChoosable) return
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = Modernist.x2),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Cut(
      label = stringResource(R.string.stats_all_sessions),
      chosen = state.sessionFilter == null,
      tag = StatsTestTags.ALL_SESSIONS,
      onChoose = { presenter.inSession(null) },
    )
    state.sessionChoices.forEach { session ->
      Cut(
        label = session.name,
        chosen = state.sessionFilter == session.id,
        tag = StatsTestTags.sessionOf(session.id),
        onChoose = { presenter.inSession(session.id) },
      )
    }
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
        .padding(horizontal = Modernist.x2),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
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

/**
 * One cut of the list, chosen or not.
 *
 * Which one is on is drawn in the accent and in bold — both of which are marks
 * only an eye can read. `selected` is the same fact said in the semantics tree,
 * so TalkBack announces "selected" rather than leaving the state of the whole
 * row a guess (`docs/architecture.md`, "Accessibility").
 */

@Composable
private fun Dice(
  state: StatsState,
  presenter: StatsPresenter,
) {
  val dice = state.dice
  Sessions(state, presenter)
  Cuts(state, presenter)
  Orders(state, presenter)
  Text(
    text = stringResource(noteFor(state)),
    style = MaterialTheme.typography.labelSmall,
    color = Ink.muted,
    modifier = Modifier.padding(horizontal = Modernist.x4, vertical = Modernist.x1),
  )
  if (state.filteredToNothing) {
    Text(
      text = stringResource(R.string.stats_filtered_empty),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(Modernist.x4).testTag(StatsTestTags.FILTERED_EMPTY),
    )
  }
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(StatsTestTags.LIST)) {
    items(dice, key = { "${it.setId}/${it.dieId}" }) { row ->
      Rule(weight = RuleWeight.Hairline)
      DieLine(row = row, onOpen = { presenter.select(row.setId, row.dieId) })
    }
    item(key = "reset-everything") {
      Rule()
      ModernistButton(
        text = stringResource(R.string.stats_reset_all),
        onClick = { presenter.confirm(Reset.Everything) },
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.fillMaxWidth().testTag(StatsTestTags.RESET_ALL),
      )
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
        .padding(horizontal = Modernist.x4, vertical = Modernist.x3),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x3),
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
        color = Ink.muted,
      )
    }
    Text(
      text = pluralStringResource(R.plurals.stats_throws, row.summary.throws.toInt(), row.summary.throws),
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
    )
    Text(
      text = row.summary.mean?.let { "%.2f".format(it) } ?: "—",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.testTag(StatsTestTags.meanOf(row.setId, row.dieId)),
    )
  }
}

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
        .padding(Modernist.x4)
        .testTag(StatsTestTags.DETAIL),
    verticalArrangement = Arrangement.spacedBy(Modernist.x3),
  ) {
    Tiles(detail)

    // Said before the bars, not after: a player should know the line is a
    // guess before they read anything into the shape.
    if (detail.fairLineIsAGuess) {
      Text(
        text = stringResource(R.string.stats_guessed_line),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(StatsTestTags.GUESSED),
      )
    }

    Histogram(detail.bars)

    ModernistButton(
      text = stringResource(R.string.stats_reset_die),
      onClick = {
        presenter.confirm(Reset.OneDie(detail.setId, detail.dieId, detail.row.name))
      },
      kind = ModernistButtonKind.Ghost,
      modifier = Modifier.testTag(StatsTestTags.RESET_DIE),
    )
  }
}

/**
 * The four numbers, drawn the way the prototype draws them: a two-column grid
 * whose cells are divided by rules.
 *
 * Not four filled cards. The system has one surface colour, no rounded corner
 * and no filled tile — what marks a cell off from the one beside it is a line
 * (`design/dInfinityPhone.dc.html`, the Statistics screen).
 */
@Composable
private fun Tiles(detail: DieDetail) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Rule()
    TileRow {
      Tile(
        label = stringResource(R.string.stats_natural_high, detail.extremes.highestValue),
        value = detail.extremes.highs.toString(),
        tag = StatsTestTags.HIGHS,
        modifier = Modifier.weight(1f),
      )
      VerticalDivider(thickness = Modernist.hairline, color = Ink.divider)
      Tile(
        label = stringResource(R.string.stats_natural_low, detail.extremes.lowestValue),
        value = detail.extremes.lows.toString(),
        tag = StatsTestTags.LOWS,
        modifier = Modifier.weight(1f),
      )
    }
    Rule(weight = RuleWeight.Hairline)
    TileRow {
      Tile(
        label = stringResource(R.string.stats_average),
        value =
          detail.row.summary.mean
            ?.let { "%.2f".format(it) } ?: "—",
        tag = StatsTestTags.MEAN,
        modifier = Modifier.weight(1f),
      )
      VerticalDivider(thickness = Modernist.hairline, color = Ink.divider)
      Tile(
        label = stringResource(R.string.stats_total_throws),
        value =
          detail.row.summary.throws
            .toString(),
        tag = StatsTestTags.THROWS,
        modifier = Modifier.weight(1f),
      )
    }
    Rule(weight = RuleWeight.Hairline)
  }
}

/** Two cells and the rule between them, both as tall as the taller cell. */
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
  Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), content = content)
}

@Composable
private fun Tile(
  label: String,
  value: String,
  tag: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxHeight()
        // One thing, to a reader and to TalkBack alike: "3, natural 20" is a
        // fact, and two separate announcements of it are not.
        .semantics(mergeDescendants = true) {}
        .padding(Modernist.x3)
        .testTag(tag),
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
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
    // `.hr`'s own thickness, used here as the gap between two bars: the
    // smallest space the system draws with is 4 dp, which would be a chart
    // more gap than bar on a d100.
    verticalArrangement = Arrangement.spacedBy(Modernist.rule),
  ) {
    bars.forEach { bar ->
      // The fair line and the bar over it are told apart by colour and by
      // nothing else, and the fair share is not written anywhere on the row. So
      // the row says both numbers, as one node rather than three
      // (`docs/architecture.md`, "Accessibility").
      val said =
        pluralStringResource(
          R.plurals.stats_bar_against_fair,
          bar.count.toInt(),
          bar.value,
          bar.count,
          percent(bar.share),
          percent(bar.fairShare),
        )
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = said }
            .testTag(StatsTestTags.barOf(bar.value)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
      ) {
        Text(
          text = bar.value.toString(),
          style = MaterialTheme.typography.labelSmall,
          color = Ink.muted,
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
                .background(Ink.divider),
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
          color = Ink.muted,
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
  Sheet(
    title = stringResource(R.string.stats_confirm_title),
    onDismiss = onNo,
    modifier = Modifier.testTag(StatsTestTags.CONFIRM),
    actions = {
      ModernistButton(
        text = stringResource(R.string.stats_confirm_yes),
        onClick = onYes,
        kind = ModernistButtonKind.Primary,
        modifier = Modifier.testTag(StatsTestTags.CONFIRM_YES),
      )
      ModernistButton(
        text = stringResource(R.string.group_cancel_stats),
        onClick = onNo,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(StatsTestTags.CONFIRM_NO),
      )
    },
  ) {
    Text(
      when (what) {
        is Reset.OneDie -> stringResource(R.string.stats_confirm_die, what.name)
        Reset.Everything -> stringResource(R.string.stats_confirm_all)
      },
    )
  }
}

@Composable
private fun Empty() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(Modernist.x6).testTag(StatsTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Text(
      text = stringResource(R.string.stats_empty_title),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.stats_empty_body),
      style = MaterialTheme.typography.bodyLarge,
      color = Ink.muted,
    )
  }
}

private val LABEL = Modernist.x8
private val BAR = Modernist.x3

/** What the tests reach the statistics screen by. */
object StatsTestTags {
  const val SCREEN: String = "stats:screen"
  const val LIST: String = "stats:list"
  const val DETAIL: String = "stats:detail"
  const val EMPTY: String = "stats:empty"
  const val BACK: String = "stats:back"

  /** The 2 dp rule the whole screen hangs from. */
  const val HEADER_RULE: String = "stats:header-rule"

  /** Also the prefix the export dialog's own tags are built from. */
  const val EXPORT: String = "stats:export"
  const val EXPORT_DIALOG: String = "$EXPORT:dialog"
  const val EXPORT_JSON: String = "$EXPORT:json"
  const val EXPORT_CSV: String = "$EXPORT:csv"

  fun orderOf(order: DieOrder): String = "stats:order:${order.name.lowercase()}"

  const val HISTOGRAM: String = "stats:histogram"
  const val HIGHS: String = "stats:highs"
  const val LOWS: String = "stats:lows"
  const val MEAN: String = "stats:mean"
  const val THROWS: String = "stats:throws"
  const val GUESSED: String = "stats:guessed"
  const val ALL_SETS: String = "stats:allsets"
  const val ALL_SESSIONS: String = "stats:allsessions"

  fun sessionOf(sessionId: String): String = "stats:session:$sessionId"

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
