package de.drehtuer.dinfinity.feature.tables

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin

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
 */
@Composable
fun TablesScreen(
  presenter: TablesPresenter,
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
      return@Column
    }

    Text(
      text = stringResource(R.string.tables_note),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

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
        )
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
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onChoose)
        // One node for TalkBack: "Green felt, chosen" is one thing to hear.
        .semantics(mergeDescendants = true) {}
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

  fun tableOf(pin: TablePin): String = "tables:table:${pin.setId}/${pin.tableId}"

  fun chosenOf(pin: TablePin): String = "tables:chosen:${pin.setId}/${pin.tableId}"
}
