package de.drehtuer.dinfinity.feature.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.designer.PhotoScaling
import de.drehtuer.dinfinity.designer.PhotoTable

/**
 * Which table the dice are thrown onto
 * (`design/dInfinity.dc.html`, option `1u`; `docs/tables.md`).
 *
 * **The swatch is the floor and the wall, in their own colours.** The design
 * asks for a thumbnail rendered on the real box mesh with a "roll a d20 here"
 * preview, and that wants the renderer on a screen that is not the tray —
 * which is Step 4.5's remaining work. Until then the two colours a look is
 * actually made of are drawn directly: for the five bundled looks that is the
 * whole difference between them, and it is honest about being a swatch rather
 * than a picture of a table.
 *
 * At the foot of the list is **Use a photo**, which is the one row that makes a
 * table rather than choosing one (`docs/tables.md`, "Your own photo"). It is
 * last because it is the unusual thing to want, and it is in the list rather
 * than in a corner because what it produces is one more row of the same list.
 *
 * @param onPickPhoto opens the system picker. It is `:app`'s, for the reason
 *   the dice-set screen's file picker is: a content URI is reached through a
 *   `ContentResolver` and nothing in a feature module knows what one is.
 */
@Composable
fun TablesScreen(
  presenter: TablesPresenter,
  modifier: Modifier = Modifier,
  menu: @Composable () -> Unit = {},
  onPickPhoto: () -> Unit = {},
) {
  val state = presenter.state
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .testTag(TablesTestTags.SCREEN),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.tables_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
      )
      menu()
    }

    if (state.empty) {
      Text(
        text = stringResource(R.string.tables_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp).testTag(TablesTestTags.EMPTY),
      )
    } else {
      Text(
        text = stringResource(R.string.tables_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
      Looks(state, presenter)
    }
  }
  state.adding?.let { draft -> PhotoSheet(draft, presenter, onPickPhoto) }
}

/** The looks, and the row that makes one more. */
@Composable
private fun Looks(
  state: TablesState,
  presenter: TablesPresenter,
) {
  LazyColumn(modifier = Modifier.fillMaxSize().testTag(TablesTestTags.LIST)) {
    items(state.tables, key = { "${it.pin.setId}/${it.pin.tableId}" }) { choice ->
      HorizontalDivider()
      TableRow(
        choice = choice,
        chosen = choice.pin == state.chosen,
        // Only once there is more than one package with a table. Repeating
        // "Built-in dice" down a list of five says nothing.
        showSet = state.manyPackages,
        onChoose = { presenter.choose(choice.pin) },
        onRemove = { presenter.removePhoto(choice.pin) },
      )
    }
    if (state.photosOffered) {
      item {
        HorizontalDivider()
        UsePhotoRow(full = !state.roomForAPhoto, onUse = presenter::usePhoto)
      }
    }
  }
}

@Composable
private fun TableRow(
  choice: TableChoice,
  chosen: Boolean,
  showSet: Boolean,
  onChoose: () -> Unit,
  onRemove: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onChoose)
        // One node for TalkBack: "Green felt, chosen" is one thing to hear —
        // and `selected` as well as the word, so the state is in the semantics
        // tree rather than only in the accent
        // (`docs/architecture.md`, "Accessibility").
        .semantics(mergeDescendants = true) { selected = chosen }
        .testTag(TablesTestTags.tableOf(choice.pin))
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Swatch(choice.look)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = choice.look.name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (showSet) {
        Text(
          text = choice.setName,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (chosen) {
      Text(
        text = stringResource(R.string.tables_chosen),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(TablesTestTags.chosenOf(choice.pin)),
      )
    }
    // Only the player's own photo tables. Everything else belongs to a
    // package, and a package is removed where packages are.
    if (choice.own) {
      TextButton(onClick = onRemove, modifier = Modifier.testTag(TablesTestTags.removeOf(choice.pin))) {
        Text(stringResource(R.string.tables_photo_remove))
      }
    }
  }
}

/**
 * The row that makes a table out of a photograph.
 *
 * Shown even when there is no room for another, and saying so: a control that
 * disappeared once six photos were kept would leave nobody any way to find out
 * that six is the number.
 */
@Composable
private fun UsePhotoRow(
  full: Boolean,
  onUse: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = !full, onClick = onUse)
        .semantics(mergeDescendants = true) {}
        .testTag(TablesTestTags.USE_PHOTO)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(SWATCH)
          .clip(RoundedCornerShape(6.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.tables_photo_use),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text =
          if (full) {
            stringResource(R.string.tables_photo_full, PhotoTable.MAX_PHOTOS.toString())
          } else {
            stringResource(R.string.tables_photo_use_hint)
          },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Choosing the file and naming the table (`design/dInfinity.dc.html`, `1u`).
 *
 * A refusal stays **in** the sheet, under the name field, rather than
 * replacing it: the file and the name are still there to try again with, and
 * the lines are the validator's own — the same report a refused install shows
 * (`docs/dice-sets.md`, "Validation").
 */
@Composable
private fun PhotoSheet(
  draft: PhotoDraft,
  presenter: TablesPresenter,
  onPickPhoto: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = presenter::dismissPhoto,
    modifier = Modifier.testTag(TablesTestTags.PHOTO_SHEET),
    title = { Text(stringResource(R.string.tables_photo_title)) },
    text = { PhotoSheetBody(draft, presenter, onPickPhoto) },
    confirmButton = {
      TextButton(
        onClick = presenter::confirmPhoto,
        enabled = draft.ready,
        modifier = Modifier.testTag(TablesTestTags.PHOTO_CONFIRM),
      ) {
        Text(stringResource(R.string.tables_photo_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = presenter::dismissPhoto, modifier = Modifier.testTag(TablesTestTags.PHOTO_CANCEL)) {
        Text(stringResource(R.string.tables_photo_cancel))
      }
    },
  )
}

@Composable
private fun PhotoSheetBody(
  draft: PhotoDraft,
  presenter: TablesPresenter,
  onPickPhoto: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = stringResource(R.string.tables_photo_body, PhotoScaling.LONGEST_SIDE.toString()),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onPickPhoto, modifier = Modifier.testTag(TablesTestTags.PHOTO_CHOOSE)) {
      Text(stringResource(R.string.tables_photo_choose))
    }
    Text(
      text = draft.picked?.label ?: stringResource(R.string.tables_photo_none),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(TablesTestTags.PHOTO_FILE),
    )
    OutlinedTextField(
      value = draft.name,
      onValueChange = presenter::namePhoto,
      singleLine = true,
      label = { Text(stringResource(R.string.tables_photo_name)) },
      modifier = Modifier.fillMaxWidth().testTag(TablesTestTags.PHOTO_NAME),
    )
    if (draft.working) {
      Text(
        text = stringResource(R.string.tables_photo_working),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(TablesTestTags.PHOTO_WORKING),
      )
    }
    Refusal(draft.refused)
  }
}

/** Every line the refusal came with, as it was written. */
@Composable
private fun Refusal(reasons: List<String>) {
  if (reasons.isEmpty()) return
  Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = Modifier.testTag(TablesTestTags.PHOTO_REFUSED),
  ) {
    Text(
      text = stringResource(R.string.tables_photo_refused),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.error,
    )
    reasons.forEach { reason ->
      Text(
        text = reason,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * The floor in the middle, the wall around it — a tray seen from above.
 *
 * Two colours rather than one, because a look is two: `oak` and `dark-glass`
 * differ in the wall as much as the floor, and a single square would make
 * several of the bundled five look alike.
 */
@Composable
private fun Swatch(look: TableLook) {
  Box(
    modifier =
      Modifier
        .size(SWATCH)
        .clip(RoundedCornerShape(6.dp))
        .background(Color(look.wallColorArgb)),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(SWATCH - WALL * 2)
          .clip(RoundedCornerShape(3.dp))
          .background(Color(look.floorColorArgb)),
    )
  }
}

private val SWATCH = 44.dp
private val WALL = 7.dp

/** What the tests reach for. */
object TablesTestTags {
  const val SCREEN: String = "tables:screen"
  const val LIST: String = "tables:list"
  const val EMPTY: String = "tables:empty"
  const val USE_PHOTO: String = "tables:photo:use"
  const val PHOTO_SHEET: String = "tables:photo:sheet"
  const val PHOTO_CHOOSE: String = "tables:photo:choose"
  const val PHOTO_FILE: String = "tables:photo:file"
  const val PHOTO_NAME: String = "tables:photo:name"
  const val PHOTO_CONFIRM: String = "tables:photo:confirm"
  const val PHOTO_CANCEL: String = "tables:photo:cancel"
  const val PHOTO_WORKING: String = "tables:photo:working"
  const val PHOTO_REFUSED: String = "tables:photo:refused"

  fun tableOf(pin: TablePin): String = "tables:table:${pin.setId}/${pin.tableId}"

  fun chosenOf(pin: TablePin): String = "tables:chosen:${pin.setId}/${pin.tableId}"

  fun removeOf(pin: TablePin): String = "tables:remove:${pin.setId}/${pin.tableId}"
}
