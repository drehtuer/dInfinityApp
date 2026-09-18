package de.drehtuer.dinfinity.feature.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.SavedRollSource
import de.drehtuer.dinfinity.ui.common.Ink
import de.drehtuer.dinfinity.ui.common.Modernist

/**
 * The active group's saved rolls, on the tray
 * (`design/dInfinity.dc.html`, option 9a; `docs/dice-notation.md`:
 * "the home screen shows the active group as tiles").
 *
 * **A tap here fills the field and stops**, exactly as a tap on the
 * saved-rolls list does. It used to throw as well, which made the strip the
 * one control in the app that rolled without a hand — a saved roll brushed by
 * a thumb was dice already on the table, and the throw nobody watched was the
 * throw that counted. A saved roll is a named formula, not a roll waiting to
 * happen; the throw is the shake that follows, which is the only way to throw
 * anything (`docs/physics-and-rendering.md`, "Starting a roll").
 *
 * Tiles rather than rows, and one line of them: the tray is the screen, and
 * every millimetre this takes is table a player is not looking at. Long-press
 * edits, the same gesture as on the list.
 *
 * Handed to the roll screen as a slot rather than built by it. A feature module
 * that knew what a saved roll was would be one feature module depending on
 * another, which is the same rule the menu button follows
 * (`docs/architecture.md`, "Screens and the states behind them").
 */
@Composable
fun HomeStrip(
  presenter: SavedPresenter,
  modifier: Modifier = Modifier,
  /** The formula, and which saved roll put it there. It fills; it does not throw. */
  onPick: (String, SavedRollSource) -> Unit = { _, _ -> },
  onEdit: (String) -> Unit = {},
  onNew: () -> Unit = {},
) {
  val state = presenter.state
  // Nothing at all until the database has answered. An empty strip that fills
  // in a frame later is the tray jumping as it opens.
  if (!state.loaded) return

  LazyRow(
    modifier = modifier.fillMaxWidth().testTag(HomeStripTestTags.STRIP),
    horizontalArrangement = Arrangement.spacedBy(Modernist.x2),
  ) {
    items(state.rolls, key = { it.roll.id }) { entry ->
      Tile(
        entry = entry,
        onPick = {
          presenter.used(entry.roll.id)
          // Which roll, which group it is in and the table it lands on. The
          // first two so the throw that follows can be recorded as that
          // roll's — a throw that belongs to nothing is one the saved-roll
          // statistics can never count (`docs/statistics.md`) — and the third
          // because the pin that wins was decided where the roll and its
          // group were both in hand (`docs/tables.md`).
          onPick(entry.roll.formula, entry.source)
        },
        onEdit = { onEdit(entry.roll.id) },
      )
    }
    // Last, and the only thing there when the group is empty — which is what
    // "the strip invites the first save" means. A strip that simply vanished
    // would never tell anybody saved rolls exist.
    item(key = INVITATION) {
      Invitation(first = state.rolls.isEmpty(), onNew = onNew)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
  entry: SavedEntry,
  onPick: () -> Unit,
  onEdit: () -> Unit,
) {
  val roll = entry.roll
  Column(
    modifier =
      Modifier
        .size(width = TILE_WIDTH, height = TILE_HEIGHT)
        .background(MaterialTheme.colorScheme.surface)
        .combinedClickable(
          onClickLabel = stringResource(R.string.saved_pick_it, roll.name),
          onLongClickLabel = stringResource(R.string.saved_edit_it, roll.name),
          onClick = onPick,
          onLongClick = onEdit,
        ).semantics(mergeDescendants = true) {}
        .testTag(HomeStripTestTags.tileOf(roll.id))
        .padding(Modernist.x3),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    Text(
      text = roll.icon.ifBlank { STRIP_ICON },
      style = MaterialTheme.typography.titleLarge,
      color = markColour(roll.colorArgb),
    )
    // The mark sits at the top of the tile and the words at the bottom, which
    // is what the prototype's empty `<span style="flex:1">` between them does.
    Spacer(modifier = Modifier.weight(1f))
    Text(
      text = roll.name,
      // `labelLarge` is the heading face at 14 sp and 800 — the size and
      // weight the prototype sets a tile's name in.
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    // Marked rather than disabled. Tapping it puts the formula in the field
    // like any other, and the field says what is wrong — which is better than
    // a tile that does nothing and does not say why. It does *not* fall back:
    // a set reference gets no substitute (`SavedFormula`).
    Text(
      text = if (entry.broken) stringResource(R.string.strip_broken) else roll.formula,
      style = MaterialTheme.typography.labelSmall,
      color = if (entry.broken) Ink.accent else Ink.muted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.testTag(HomeStripTestTags.noteOf(roll.id)),
    )
  }
}

@Composable
private fun Invitation(
  first: Boolean,
  onNew: () -> Unit,
) {
  Column(
    modifier =
      Modifier
        .size(width = INVITATION_WIDTH, height = TILE_HEIGHT)
        // Outlined rather than filled: the tile beside it is a roll, and this
        // is not one. The prototype spends the surface colour on the things
        // that exist and gives the way to make another one a rule.
        .border(width = Modernist.hairline, color = Ink.divider)
        .combinedClickableInvitation(onNew)
        .testTag(HomeStripTestTags.NEW)
        .padding(Modernist.x3),
    verticalArrangement = Arrangement.spacedBy(Modernist.x1),
  ) {
    Text(
      text = "+",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.weight(1f))
    Text(
      text = stringResource(if (first) R.string.strip_first else R.string.strip_new),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableInvitation(onNew: () -> Unit): Modifier =
  combinedClickable(onClick = onNew).semantics(mergeDescendants = true) {}

/**
 * The tile the prototype draws: `width:128px;height:96px`
 * (`design/dInfinityPhone.dc.html`, the tray's saved-roll strip). A fixed size
 * rather than one that grows with the name — the strip is a row of cards and a
 * row of cards of different heights is not one.
 */
private val TILE_WIDTH: Dp = 128.dp
private val TILE_HEIGHT: Dp = 96.dp

/** The last tile is square and narrower, being a way on rather than a roll. */
private val INVITATION_WIDTH: Dp = 96.dp

/** What a roll with no mark of its own wears, small. */
private const val STRIP_ICON = "●"

/** The key the invitation keeps, so it is not re-made as rolls come and go. */
private const val INVITATION = "strip:new"

/** What the tests reach the home strip by. */
object HomeStripTestTags {
  const val STRIP: String = "strip"
  const val NEW: String = "strip:new"

  fun tileOf(id: String): String = "strip:tile:$id"

  fun noteOf(id: String): String = "strip:tile:$id:note"
}
