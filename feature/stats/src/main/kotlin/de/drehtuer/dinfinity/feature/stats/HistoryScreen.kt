package de.drehtuer.dinfinity.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
  menu: @Composable () -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(HistoryTestTags.SCREEN),
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
      menu()
    }

    if (state.empty) {
      Empty()
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

/** What the tests reach the history screen by. */
object HistoryTestTags {
  const val SCREEN: String = "history:screen"
  const val LIST: String = "history:list"
  const val EMPTY: String = "history:empty"

  fun rollOf(id: Long): String = "history:roll:$id"

  fun totalOf(id: Long): String = "history:roll:$id:total"

  fun breakdownOf(id: Long): String = "history:roll:$id:breakdown"

  fun anomaliesOf(id: Long): String = "history:roll:$id:anomalies"

  fun sessionOf(name: String): String = "history:session:$name"
}
