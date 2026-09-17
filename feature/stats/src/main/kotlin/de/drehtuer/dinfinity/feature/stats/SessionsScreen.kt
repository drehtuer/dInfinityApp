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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import de.drehtuer.dinfinity.data.Session

/**
 * The buckets statistics are filtered by
 * (`design/dInfinity.dc.html`, option 6c).
 *
 * A list and nothing else. There is no "start" and no "stop": a session is a
 * label somebody puts on a stretch of rolls, and choosing one here is all that
 * filing a roll under it takes. A session left running overnight is not a
 * thing that can happen, because nothing is running.
 *
 * Each row says how many rolls are in it and how many of those had a die
 * showing its highest face, which is the number a player is actually looking
 * for.
 */
@Composable
fun SessionsScreen(
  presenter: SessionsPresenter,
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
        .testTag(SessionsTestTags.SCREEN),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Modernist.x4, vertical = Modernist.x2),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
    ) {
      Text(
        text = stringResource(R.string.sessions_title),
        // `titleLarge` carries the heading weight the system asks for (800).
        // `Bold` is 700, which is a lighter heading than the design has.
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
      )
      Button(
        onClick = { presenter.edit(SessionDraft()) },
        shape = Modernist.square,
        modifier = Modifier.testTag(SessionsTestTags.NEW),
      ) {
        Text(stringResource(R.string.sessions_new))
      }
      menu()
    }
    // The rule every screen in the prototype hangs from.
    HorizontalDivider(
      thickness = Modernist.rule,
      color = divider,
      modifier = Modifier.testTag(SessionsTestTags.HEADER_RULE),
    )

    state.editing?.let { draft -> NameSheet(draft = draft, presenter = presenter) }

    Text(
      text = stringResource(R.string.sessions_explanation),
      style = MaterialTheme.typography.labelSmall,
      color = muted,
      modifier = Modifier.padding(horizontal = Modernist.x4, vertical = Modernist.x1),
    )

    LazyColumn(modifier = Modifier.fillMaxSize().testTag(SessionsTestTags.LIST)) {
      items(state.sessions, key = { it.id }) { session ->
        HorizontalDivider(thickness = Modernist.hairline, color = divider)
        SessionRow(
          session = session,
          active = session.id == state.activeId,
          onActivate = { presenter.activate(session.id) },
          onRename = { presenter.edit(SessionDraft(id = session.id, name = session.name)) },
          onDelete = { presenter.delete(session.id) },
        )
      }
    }
  }
}

@Composable
private fun SessionRow(
  session: Session,
  active: Boolean,
  onActivate: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onActivate)
        .testTag(SessionsTestTags.sessionOf(session.id))
        .padding(horizontal = Modernist.x4, vertical = Modernist.x3),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
      Text(
        text = if (active) stringResource(R.string.sessions_active, session.name) else session.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text =
          stringResource(
            R.string.sessions_counts,
            pluralStringResource(R.plurals.sessions_rolls, session.rolls.toInt(), session.rolls),
            pluralStringResource(R.plurals.sessions_naturals, session.naturals.toInt(), session.naturals),
          ),
        style = MaterialTheme.typography.labelSmall,
        color = muted,
        modifier = Modifier.testTag(SessionsTestTags.countsOf(session.id)),
      )
    }
    TextButton(
      onClick = onRename,
      shape = Modernist.square,
      modifier = Modifier.testTag(SessionsTestTags.renameOf(session.id)),
    ) {
      Text(stringResource(R.string.sessions_rename))
    }
    // The first session has no Delete: it is where a deleted session's rolls
    // go, so it has to be there to go to.
    if (session.deletable) {
      TextButton(
        onClick = onDelete,
        shape = Modernist.square,
        modifier = Modifier.testTag(SessionsTestTags.deleteOf(session.id)),
      ) {
        Text(stringResource(R.string.sessions_delete), color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

@Composable
private fun NameSheet(
  draft: SessionDraft,
  presenter: SessionsPresenter,
) {
  AlertDialog(
    modifier = Modifier.testTag(SessionsTestTags.SHEET),
    onDismissRequest = { presenter.edit(null) },
    title = {
      Text(stringResource(if (draft.fresh) R.string.sessions_new else R.string.sessions_rename))
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Modernist.x2)) {
        OutlinedTextField(
          value = draft.name,
          onValueChange = presenter::name,
          singleLine = true,
          label = { Text(stringResource(R.string.sessions_name)) },
          placeholder = { Text(stringResource(R.string.sessions_name_hint)) },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          shape = Modernist.square,
          modifier = Modifier.fillMaxWidth().testTag(SessionsTestTags.NAME),
        )
        if (draft.fresh) {
          Text(
            text = stringResource(R.string.sessions_new_note),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = presenter::save,
        enabled = draft.savable,
        shape = Modernist.square,
        modifier = Modifier.testTag(SessionsTestTags.SAVE),
      ) {
        Text(stringResource(R.string.sessions_save))
      }
    },
    dismissButton = {
      TextButton(
        onClick = { presenter.edit(null) },
        shape = Modernist.square,
        modifier = Modifier.testTag(SessionsTestTags.CANCEL),
      ) {
        Text(stringResource(R.string.sessions_cancel))
      }
    },
  )
}

/** What the tests reach the sessions screen by. */
object SessionsTestTags {
  const val SCREEN: String = "sessions:screen"
  const val LIST: String = "sessions:list"
  const val NEW: String = "sessions:new"
  const val SHEET: String = "sessions:sheet"
  const val NAME: String = "sessions:name"
  const val SAVE: String = "sessions:save"
  const val CANCEL: String = "sessions:cancel"

  /** The 2 dp rule the whole screen hangs from. */
  const val HEADER_RULE: String = "sessions:header-rule"

  fun sessionOf(id: String): String = "sessions:session:$id"

  fun countsOf(id: String): String = "sessions:session:$id:counts"

  fun renameOf(id: String): String = "sessions:session:$id:rename"

  fun deleteOf(id: String): String = "sessions:session:$id:delete"
}
