package de.drehtuer.dinfinity.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.StoredDie
import de.drehtuer.dinfinity.data.StoredGroup

/**
 * Every roll, with what it was made of
 * (`design/dInfinity.dc.html`, option 1x; `docs/statistics.md`, "History").
 *
 * **A past roll is a record, not something to re-run.** There is no replay
 * action here and no seed anywhere on the screen, and that is not enforced by
 * remembering it: `HistoryEntry` has no seed to show. Re-rolling a formula
 * means rolling it again.
 *
 * A tap opens one roll's breakdown, and only one. Fifty open breakdowns is not
 * a list.
 */
@Composable
fun HistoryScreen(
  presenter: HistoryPresenter,
  modifier: Modifier = Modifier,
  formatter: (Long) -> String = TimeStamp::of,
  onExport: (ExportFile) -> Unit = {},
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  // Which format, asked once and here rather than in the presenter: it is a
  // question about this tap, and nothing on the screen depends on the answer
  // afterwards.
  var exporting by remember { mutableStateOf(false) }
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(HistoryTestTags.SCREEN),
  ) {
    Header(offerExport = !state.empty, onExport = { exporting = true }, menu = menu)

    if (exporting) {
      ExportChoice(
        tagPrefix = HistoryTestTags.EXPORT,
        body = stringResource(R.string.history_export_body),
        onDismiss = { exporting = false },
        onChosen = { format ->
          exporting = false
          presenter.export(format, onExport)
        },
      )
    }

    Choosers(state, presenter)

    if (state.empty) {
      Empty()
      return@Column
    }

    if (state.filteredToNothing) {
      FilteredToNothing(presenter)
      return@Column
    }

    LazyColumn(modifier = Modifier.fillMaxSize().testTag(HistoryTestTags.LIST)) {
      var session: String? = null
      state.rolls.forEach { roll ->
        // Only when there is more than one, because a heading repeated down the
        // whole list says nothing. Until Step 4.9 there is exactly one.
        if (state.bySession && roll.sessionId != session) {
          session = roll.sessionId
          item(key = "session:${roll.id}") { SessionHeading(roll.sessionId) }
        }
        item(key = roll.id) {
          HorizontalDivider()
          Entry(
            roll = roll,
            open = state.openId == roll.id,
            at = formatter(roll.atEpochMs),
            onOpen = { presenter.open(roll.id) },
          )
        }
      }
    }
  }
}

/**
 * The title, the way out to a file, and the menu.
 *
 * Its own composable because the screen it sits on was at detekt's length
 * limit, which is the limit doing its job: a screen function that is a list of
 * everything on the screen is one nobody reads.
 */
@Composable
private fun Header(
  offerExport: Boolean,
  onExport: () -> Unit,
  menu: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.history_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    // Only when there is something to export. A button that writes an empty
    // file is a button that lies about having done something.
    if (offerExport) {
      TextButton(onClick = onExport, modifier = Modifier.testTag(HistoryTestTags.EXPORT)) {
        Text(stringResource(R.string.history_export))
      }
    }
    menu()
  }
}

@Composable
private fun SessionHeading(name: String) {
  Text(
    text = name,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .testTag(HistoryTestTags.sessionOf(name)),
  )
}

@Composable
private fun Entry(
  roll: HistoryEntry,
  open: Boolean,
  at: String,
  onOpen: () -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = roll.hasBreakdown, onClick = onOpen)
        .testTag(HistoryTestTags.rollOf(roll.id))
        .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = roll.formula,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = at,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = roll.total.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        // A roll with a natural maximum in it prints in the accent, which is
        // the one thing a player scanning their history is looking for.
        color =
          if (roll.hasNaturalMax) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onBackground
          },
        modifier = Modifier.testTag(HistoryTestTags.totalOf(roll.id)),
      )
    }
    if (open) Groups(roll)
  }
}

/**
 * What the roll was made of, as it was recorded.
 *
 * Read out of the stored breakdown rather than looked up, so it says what it
 * said then even if the set that threw it has since been uninstalled
 * (`docs/statistics.md`, "Storage").
 */
@Composable
private fun Groups(roll: HistoryEntry) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(HistoryTestTags.breakdownOf(roll.id)),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    roll.groups.forEach { group -> Group(group) }
    if (roll.anomalies > 0) {
      Text(
        text = pluralStringResource(R.plurals.history_anomalies, roll.anomalies, roll.anomalies),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(HistoryTestTags.anomaliesOf(roll.id)),
      )
    }
  }
}

@Composable
private fun Group(group: StoredGroup) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = group.notation,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = group.dice.joinToString(" ") { it.label },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(2f),
    )
    Text(
      text = group.subtotal.toString(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
  // A dropped die is shown struck through rather than hidden: a player wants
  // to see the 1 that `4d6dl1` threw away (`docs/dice-notation.md`).
  val dropped = group.dice.filterNot(StoredDie::kept)
  if (dropped.isNotEmpty()) {
    Text(
      text = dropped.joinToString(" ") { it.label },
      style = MaterialTheme.typography.bodySmall,
      textDecoration = TextDecoration.LineThrough,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  // Which set actually supplied the dice, when it was not the one asked for.
  if (group.fellBack) {
    Text(
      text = stringResource(R.string.history_fell_back, group.requestedSetId, group.setId),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun Empty() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp).testTag(HistoryTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = stringResource(R.string.history_empty_title),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.history_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Which rolls are being looked at (`docs/statistics.md`; design `6c`).
 *
 * A row of choices rather than a menu, because there are rarely many and a
 * tap beats two. Not drawn at all until there is more than one thing to choose
 * between: a chooser whose only option is "everything" is a control that
 * cannot do anything.
 */
@Composable
private fun Choosers(
  state: HistoryState,
  presenter: HistoryPresenter,
) {
  if (!state.choosable) return
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Choice(
      label = stringResource(R.string.history_all),
      chosen = state.filter == HistoryFilter.Everything,
      tag = HistoryTestTags.ALL,
      onChoose = { presenter.filterBy(HistoryFilter.Everything) },
    )
    state.sessionChoices.forEach { session ->
      Choice(
        label = session.name,
        chosen = (state.filter as? HistoryFilter.InSession)?.id == session.id,
        tag = HistoryTestTags.sessionChoiceOf(session.id),
        onChoose = { presenter.filterBy(HistoryFilter.InSession(session.id, session.name)) },
      )
    }
    state.rollChoices.forEach { roll ->
      Choice(
        label = roll.name,
        chosen = (state.filter as? HistoryFilter.OfSavedRoll)?.id == roll.id,
        tag = HistoryTestTags.savedRollOf(roll.id),
        onChoose = { presenter.filterBy(HistoryFilter.OfSavedRoll(roll.id, roll.name)) },
      )
    }
  }
}

@Composable
private fun Choice(
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

/**
 * A filter that has left nothing to show.
 *
 * Deliberately not [Empty]: "you have never rolled anything" is wrong and
 * discouraging in front of somebody who has rolled hundreds of times and
 * picked a quiet session.
 */
@Composable
private fun FilteredToNothing(presenter: HistoryPresenter) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(HistoryTestTags.FILTERED_EMPTY),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(text = stringResource(R.string.history_filtered_empty), style = MaterialTheme.typography.bodyMedium)
    TextButton(
      onClick = { presenter.filterBy(HistoryFilter.Everything) },
      modifier = Modifier.testTag(HistoryTestTags.CLEAR_FILTER),
    ) {
      Text(stringResource(R.string.history_clear_filter))
    }
  }
}

/** What the tests reach the history screen by. */
object HistoryTestTags {
  const val SCREEN: String = "history:screen"
  const val LIST: String = "history:list"
  const val EMPTY: String = "history:empty"
  const val ALL: String = "history:all"
  const val FILTERED_EMPTY: String = "history:filtered-empty"
  const val CLEAR_FILTER: String = "history:clear-filter"

  /** Also the prefix the export dialog's own tags are built from. */
  const val EXPORT: String = "history:export"
  const val EXPORT_DIALOG: String = "$EXPORT:dialog"
  const val EXPORT_JSON: String = "$EXPORT:json"
  const val EXPORT_CSV: String = "$EXPORT:csv"

  /** The chooser's button for one session — not the heading, which is `sessionOf`. */
  fun sessionChoiceOf(id: String): String = "history:choose-session:$id"

  fun savedRollOf(id: String): String = "history:savedroll:$id"

  fun rollOf(id: Long): String = "history:roll:$id"

  fun totalOf(id: Long): String = "history:roll:$id:total"

  fun breakdownOf(id: Long): String = "history:roll:$id:breakdown"

  fun anomaliesOf(id: Long): String = "history:roll:$id:anomalies"

  fun sessionOf(name: String): String = "history:session:$name"
}
