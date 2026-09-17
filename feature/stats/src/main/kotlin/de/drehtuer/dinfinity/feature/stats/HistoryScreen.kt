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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.StoredDie
import de.drehtuer.dinfinity.data.StoredGroup
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.SectionKicker
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
import kotlin.math.abs

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
    Header(
      offerExport = !state.empty,
      onExport = { exporting = true },
      offerForget = state.forgettable,
      onForget = { presenter.confirmForget(true) },
      menu = menu,
    )

    Asking(
      state = state,
      presenter = presenter,
      exporting = exporting,
      onExportDone = { exporting = false },
      onExport = onExport,
    )

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
          HorizontalDivider(thickness = Modernist.hairline, color = Ink.divider)
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
  offerForget: Boolean,
  onForget: () -> Unit,
  menu: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = Modernist.x4, vertical = Modernist.x2),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.history_title),
      // Already 800, the system's heading weight; `Bold` is 700 and would
      // make this heading lighter than the design's.
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    // Only when there is something to export. A button that writes an empty
    // file is a button that lies about having done something.
    if (offerExport) {
      TextButton(
        onClick = onExport,
        shape = Modernist.square,
        modifier = Modifier.testTag(HistoryTestTags.EXPORT),
      ) {
        Text(stringResource(R.string.history_export))
      }
    }
    // Beside Export and only with a filter on, because the two are the same
    // act on the same rolls: keep a copy of what you are looking at, or be rid
    // of it.
    if (offerForget) {
      TextButton(
        onClick = onForget,
        shape = Modernist.square,
        modifier = Modifier.testTag(HistoryTestTags.FORGET),
      ) {
        Text(stringResource(R.string.history_forget), color = MaterialTheme.colorScheme.primary)
      }
    }
    menu()
  }
  // The rule every screen in the prototype hangs from.
  HorizontalDivider(
    thickness = Modernist.rule,
    color = Ink.divider,
    modifier = Modifier.testTag(HistoryTestTags.HEADER_RULE),
  )
}

/**
 * Whichever question is open: whether to forget, or which shape a file takes.
 *
 * Both together because only one can be open at a time and neither draws
 * anything when it is not — and because the screen they sit on was at detekt's
 * length limit, which is the limit doing its job.
 */
@Composable
private fun Asking(
  state: HistoryState,
  presenter: HistoryPresenter,
  exporting: Boolean,
  onExportDone: () -> Unit,
  onExport: (ExportFile) -> Unit,
) {
  if (state.confirmingForget) {
    ForgetDialog(
      filter = state.filter,
      onYes = { presenter.forget() },
      onNo = { presenter.confirmForget(false) },
    )
  }

  if (exporting) {
    ExportChoice(
      tagPrefix = HistoryTestTags.EXPORT,
      body = stringResource(R.string.history_export_body),
      onDismiss = onExportDone,
      onChosen = { format ->
        onExportDone()
        presenter.export(format, onExport)
      },
    )
  }
}

/**
 * The one question worth asking twice (`docs/statistics.md`, "Export and reset").
 *
 * It says what stays as well as what goes. The per-die statistics count these
 * throws whether or not the history lists them, so a dialog that only said
 * "this cannot be undone" would leave somebody expecting their d20's record to
 * change and then wondering why it had not.
 */
@Composable
private fun ForgetDialog(
  filter: HistoryFilter,
  onYes: () -> Unit,
  onNo: () -> Unit,
) {
  val explanation =
    when (filter) {
      is HistoryFilter.InSession -> stringResource(R.string.history_forget_session, filter.name)
      is HistoryFilter.OfSavedRoll -> stringResource(R.string.history_forget_roll, filter.name)
      HistoryFilter.Everything -> return
    }
  AlertDialog(
    modifier = Modifier.testTag(HistoryTestTags.FORGET_DIALOG),
    onDismissRequest = onNo,
    title = { Text(stringResource(R.string.history_forget_title)) },
    text = { Text(explanation) },
    confirmButton = {
      TextButton(onClick = onYes, shape = Modernist.square, modifier = Modifier.testTag(HistoryTestTags.FORGET_YES)) {
        Text(stringResource(R.string.history_forget_yes), color = MaterialTheme.colorScheme.primary)
      }
    },
    dismissButton = {
      TextButton(onClick = onNo, shape = Modernist.square, modifier = Modifier.testTag(HistoryTestTags.FORGET_NO)) {
        Text(stringResource(R.string.history_forget_no))
      }
    },
  )
}

/**
 * Which session the rolls below belong to.
 *
 * A [SectionKicker] over a [Rule], which is how the prototype heads a run of
 * rows (`design/dInfinityPhone.dc.html`, the History screen): the accent,
 * tracked out, with a 2 dp line under it. Not a filled grey band — the system
 * has one surface colour and does not tint a heading with it.
 *
 * It was the same idea written out by hand, a step too large at `labelSmall`'s
 * eleven sp; the shared component is the design system's ten, so every kicker
 * in the app is one size.
 */
@Composable
private fun SessionHeading(name: String) {
  Column(modifier = Modifier.fillMaxWidth().testTag(HistoryTestTags.sessionOf(name))) {
    SectionKicker(
      text = name,
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = Modernist.x4, end = Modernist.x4, top = Modernist.x3, bottom = Modernist.x1),
    )
    Rule()
  }
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
        .padding(horizontal = Modernist.x4, vertical = Modernist.x3),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Modernist.x3),
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
          color = Ink.muted,
        )
      }
      // A roll with a natural maximum in it prints in the accent, which is the
      // one thing a player scanning their history is looking for — and a
      // colour is the one thing a screen reader never gets. So it is said
      // (`docs/architecture.md`, "Accessibility").
      val natural = stringResource(R.string.history_natural_max, roll.total)
      Text(
        text = roll.total.toString(),
        style = MaterialTheme.typography.titleLarge,
        color =
          if (roll.hasNaturalMax) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onBackground
          },
        modifier =
          Modifier
            .then(if (roll.hasNaturalMax) Modifier.semantics { contentDescription = natural } else Modifier)
            .testTag(HistoryTestTags.totalOf(roll.id)),
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
    modifier = Modifier.fillMaxWidth().padding(top = Modernist.x2).testTag(HistoryTestTags.breakdownOf(roll.id)),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    roll.groups.forEach { group -> Group(group) }
    // And what the formula added, so the rows add up to the total the way they
    // do on the result sheet (`docs/dice-notation.md`, "Evaluation", step 7).
    // A roll recorded before these were written down has none, and that is the
    // truth about it: nobody knows what it added.
    roll.adjustments.forEach { amount -> Adjustment(id = roll.id, amount = amount) }
    if (roll.anomalies > 0) {
      Text(
        text = pluralStringResource(R.plurals.history_anomalies, roll.anomalies, roll.anomalies),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
        modifier = Modifier.testTag(HistoryTestTags.anomaliesOf(roll.id)),
      )
    }
  }
}

/**
 * One number the formula added or took away, on a row of its own.
 *
 * The same shape as a group's row, because it is the same claim: a thing
 * somebody wrote, and what it contributed. Before it existed a past roll of
 * `3d6 + 4` showed rows adding to eleven under a total of fifteen.
 */
@Composable
private fun Adjustment(
  id: Long,
  amount: Long,
) {
  Row(
    modifier = Modifier.fillMaxWidth().testTag(HistoryTestTags.adjustmentOf(id, amount)),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Text(
      text = stringResource(if (amount < 0) R.string.history_minus else R.string.history_plus),
      style = MaterialTheme.typography.labelLarge,
      color = Ink.muted,
      modifier = Modifier.weight(3f),
    )
    Text(
      text = abs(amount).toString(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Composable
private fun Group(group: StoredGroup) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Text(
      text = group.notation,
      style = MaterialTheme.typography.labelLarge,
      color = Ink.muted,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = group.dice.joinToString(" ") { it.label },
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.weight(2f),
    )
    Text(
      text = group.subtotal.toString(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
  // A dropped die is shown struck through rather than hidden: a player wants
  // to see the 1 that `4d6dl1` threw away (`docs/dice-notation.md`).
  val dropped = group.dice.filterNot(StoredDie::kept)
  if (dropped.isNotEmpty()) {
    val labels = dropped.joinToString(" ") { it.label }
    // A strike is drawn, not spoken. Without this the line reads as a second
    // handful of dice that counted.
    val said = stringResource(R.string.history_dropped, labels)
    Text(
      text = labels,
      style = MaterialTheme.typography.labelLarge,
      textDecoration = TextDecoration.LineThrough,
      color = Ink.muted,
      modifier = Modifier.semantics { contentDescription = said },
    )
  }
  // Which set actually supplied the dice, when it was not the one asked for.
  if (group.fellBack) {
    Text(
      text = stringResource(R.string.history_fell_back, group.requestedSetId, group.setId),
      style = MaterialTheme.typography.labelSmall,
      color = Ink.muted,
    )
  }
}

@Composable
private fun Empty() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(Modernist.x6).testTag(HistoryTestTags.EMPTY),
    verticalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Text(
      text = stringResource(R.string.history_empty_title),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.history_empty_body),
      style = MaterialTheme.typography.bodyLarge,
      color = Ink.muted,
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
        .padding(horizontal = Modernist.x2),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x1),
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
  TextButton(
    onClick = onChoose,
    shape = Modernist.square,
    modifier =
      Modifier
        .sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET)
        // The accent and the bold are marks only an eye reads; this is the
        // same fact in the semantics tree.
        .semantics { selected = chosen }
        .testTag(tag),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = if (chosen) MaterialTheme.colorScheme.primary else Ink.muted,
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
    modifier = Modifier.fillMaxWidth().padding(Modernist.x4).testTag(HistoryTestTags.FILTERED_EMPTY),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    Text(text = stringResource(R.string.history_filtered_empty), style = MaterialTheme.typography.bodyLarge)
    TextButton(
      onClick = { presenter.filterBy(HistoryFilter.Everything) },
      shape = Modernist.square,
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

  /** The 2 dp rule the whole screen hangs from. */
  const val HEADER_RULE: String = "history:header-rule"

  /** Also the prefix the export dialog's own tags are built from. */
  const val EXPORT: String = "history:export"
  const val EXPORT_DIALOG: String = "$EXPORT:dialog"
  const val EXPORT_JSON: String = "$EXPORT:json"
  const val EXPORT_CSV: String = "$EXPORT:csv"

  const val FORGET: String = "history:forget"
  const val FORGET_DIALOG: String = "$FORGET:dialog"
  const val FORGET_YES: String = "$FORGET:yes"
  const val FORGET_NO: String = "$FORGET:no"

  /** The chooser's button for one session — not the heading, which is `sessionOf`. */
  fun sessionChoiceOf(id: String): String = "history:choose-session:$id"

  fun savedRollOf(id: String): String = "history:savedroll:$id"

  fun rollOf(id: Long): String = "history:roll:$id"

  fun totalOf(id: Long): String = "history:roll:$id:total"

  fun breakdownOf(id: Long): String = "history:roll:$id:breakdown"

  fun anomaliesOf(id: Long): String = "history:roll:$id:anomalies"

  fun adjustmentOf(
    id: Long,
    amount: Long,
  ): String = "history:adjustment:$id:$amount"

  fun sessionOf(name: String): String = "history:session:$name"
}
