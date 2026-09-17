package de.drehtuer.dinfinity.feature.tables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist
import de.drehtuer.dinfinity.ui.common.ModernistButton
import de.drehtuer.dinfinity.ui.common.ModernistButtonKind
import de.drehtuer.dinfinity.ui.common.Rule
import de.drehtuer.dinfinity.ui.common.RuleWeight
import de.drehtuer.dinfinity.ui.common.Sheet

/**
 * Which table the dice are thrown onto
 * (`design/dInfinity.dc.html`, option `1u`; `docs/tables.md`).
 *
 * **Each row is a picture of its own tray.** The real box mesh, lit the way the
 * roll screen lights it, with a d20 standing in the corner of it — drawn by the
 * renderer that draws the tray, on the thread that owns it, and asked for as
 * the row comes on screen (`docs/tables.md`, "Thumbnails"). A look whose
 * picture has not arrived, and every look at all on a device that cannot draw
 * one, shows the swatch instead: the two colours a look is made of, which is
 * the whole difference between the bundled five and is honest about being a
 * swatch rather than a picture of a table.
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
        // `titleLarge` is already the heading font at 800; a `Bold` here
        // pulled it back to Material's 700 (`--font-heading-weight: 800`).
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
      )
      menu()
    }

    if (state.empty) {
      Text(
        text = stringResource(R.string.tables_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = Ink.muted,
        modifier = Modifier.padding(16.dp).testTag(TablesTestTags.EMPTY),
      )
    } else {
      Text(
        text = stringResource(R.string.tables_note),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
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
      // A picture is asked for when the row it belongs to is composed, which
      // in a `LazyColumn` is when the player can see it. Asking again costs
      // nothing (`TablesPresenter.wants`).
      LaunchedEffect(choice.pin) { presenter.wants(choice.pin) }
      Rule(weight = RuleWeight.Hairline)
      TableRow(
        choice = choice,
        chosen = choice.pin == state.chosen,
        picture = state.thumbnails[choice.pin],
        // Only once there is more than one package with a table. Repeating
        // "Built-in dice" down a list of five says nothing.
        showSet = state.manyPackages,
        onChoose = { presenter.choose(choice.pin) },
        onRemove = { presenter.removePhoto(choice.pin) },
      )
    }
    if (state.photosOffered) {
      item {
        Rule(weight = RuleWeight.Hairline)
        UsePhotoRow(full = !state.roomForAPhoto, onUse = presenter::usePhoto)
      }
    }
  }
}

@Composable
private fun TableRow(
  choice: TableChoice,
  chosen: Boolean,
  picture: ImageBitmap?,
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
    Thumbnail(choice, picture)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = choice.look.name,
        style = MaterialTheme.typography.titleMedium,
        // A card title is the heading font at 800 whatever else is true of it
        // (`.card-title`). Which look is chosen is said in the accent beside
        // it and in the semantics, not by thickening the name.
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (showSet) {
        Text(
          text = choice.setName,
          style = MaterialTheme.typography.labelSmall,
          color = Ink.muted,
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
      ModernistButton(
        text = stringResource(R.string.tables_photo_remove),
        onClick = onRemove,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(TablesTestTags.removeOf(choice.pin)),
      )
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
    // `border:1px dashed var(--color-divider)`, and square: `--radius-*` is
    // `0px` and nothing in this system has a rounded corner. Drawn rather than
    // bordered because Compose's `border` has no dash.
    val edge = MaterialTheme.colorScheme.outline
    Box(
      modifier =
        Modifier
          .size(width = TableThumbnailBox.WIDTH, height = TableThumbnailBox.HEIGHT)
          .drawBehind {
            drawRoundRect(
              color = edge,
              cornerRadius = CornerRadius.Zero,
              style =
                Stroke(
                  width = HAIRLINE.toPx(),
                  pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx())),
                ),
            )
          },
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
        color = Ink.muted,
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
  Sheet(
    title = stringResource(R.string.tables_photo_title),
    onDismiss = presenter::dismissPhoto,
    modifier = Modifier.testTag(TablesTestTags.PHOTO_SHEET),
    actions = {
      // `.dialog-actions` leads with the `btn-primary`; the way out beside it
      // is the `btn-ghost`.
      ModernistButton(
        text = stringResource(R.string.tables_photo_confirm),
        onClick = presenter::confirmPhoto,
        kind = ModernistButtonKind.Primary,
        enabled = draft.ready,
        modifier = Modifier.testTag(TablesTestTags.PHOTO_CONFIRM),
      )
      ModernistButton(
        text = stringResource(R.string.tables_photo_cancel),
        onClick = presenter::dismissPhoto,
        kind = ModernistButtonKind.Ghost,
        modifier = Modifier.testTag(TablesTestTags.PHOTO_CANCEL),
      )
    },
  ) {
    PhotoSheetBody(draft, presenter, onPickPhoto)
  }
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
      color = Ink.muted,
    )
    ModernistButton(
      text = stringResource(R.string.tables_photo_choose),
      onClick = onPickPhoto,
      kind = ModernistButtonKind.Ghost,
      modifier = Modifier.testTag(TablesTestTags.PHOTO_CHOOSE),
    )
    Text(
      text = draft.picked?.label ?: stringResource(R.string.tables_photo_none),
      style = MaterialTheme.typography.labelMedium,
      color = Ink.muted,
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
        color = Ink.muted,
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
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.testTag(TablesTestTags.PHOTO_REFUSED),
  ) {
    Text(
      text = stringResource(R.string.tables_photo_refused),
      style = MaterialTheme.typography.labelMedium,
      color = Ink.accent,
    )
    reasons.forEach { reason ->
      Text(
        text = reason,
        style = MaterialTheme.typography.bodySmall,
        color = Ink.muted,
      )
    }
  }
}

/**
 * The look, as a picture of the tray it makes — or as its colours until one
 * arrives (`docs/tables.md`, "Thumbnails").
 *
 * The picture is the real box mesh with a d20 standing in the corner of it,
 * drawn by the renderer that draws the tray. It is not always there: it is
 * drawn on a graphics engine, off the main thread, and a device that has none
 * never gets one. So the swatch is not dead code waiting to be deleted — it is
 * the fallback, and it is drawn at exactly the size the picture will be so the
 * list does not jump as pictures land in it.
 *
 * No content description on either. The row is one node for a screen reader
 * and already announces the look's name and whether it is chosen; a picture
 * that repeats the name is a second thing to listen to for no information
 * (`docs/architecture.md`, "Accessibility").
 */
@Composable
private fun Thumbnail(
  choice: TableChoice,
  picture: ImageBitmap?,
) {
  // Square: `--radius-*` is `0px` throughout the system.
  val box = Modifier.size(width = TableThumbnailBox.WIDTH, height = TableThumbnailBox.HEIGHT)
  if (picture == null) {
    Swatch(choice.look, box)
    return
  }
  Image(
    bitmap = picture,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = box.testTag(TablesTestTags.thumbnailOf(choice.pin)),
  )
}

/**
 * The floor in the middle, the wall around it — a tray seen from above.
 *
 * Two colours rather than one, because a look is two: `oak` and `dark-glass`
 * differ in the wall as much as the floor, and a single square would make
 * several of the bundled five look alike. It is honest about being a swatch
 * rather than a picture of a table, which is why it is what a device that
 * cannot draw the picture is left with.
 */
@Composable
private fun Swatch(
  look: TableLook,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.background(Color(look.wallColorArgb)),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(
            width = TableThumbnailBox.WIDTH - WALL * 2,
            height = TableThumbnailBox.HEIGHT - WALL * 2,
          ).background(Color(look.floorColorArgb)),
    )
  }
}

/**
 * How thick the wall around a tray reads, which is the table card's
 * `border:6px` in the prototype (`design/dInfinityPhone.dc.html`, the Tables
 * screen).
 *
 * Not a design-system token: 6 dp is off the spacing scale, and it is this
 * screen's own measurement of a tray wall rather than a number any other
 * screen shares.
 */
private val WALL = 6.dp

/** `.table td`'s `border-bottom: 1px` — the thinnest line the system draws. */
private val HAIRLINE = Modernist.hairline

/** How long each dash of the "use a photo" placeholder's edge is. */
private val DASH = 4.dp

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

  fun thumbnailOf(pin: TablePin): String = "tables:thumbnail:${pin.setId}/${pin.tableId}"
}
