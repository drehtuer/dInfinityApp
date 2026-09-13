package de.drehtuer.dinfinity.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Every screen the app has, in a list (`design/dInfinity.dc.html`, option 1q).
 *
 * **This is what connects the navigation graph.** The graph has had all of its
 * destinations from the first commit; what it did not have was a way in to any
 * of them, so every screen but the tray could only be reached by not being
 * there yet (`docs/architecture.md`, "Screens and the states behind them").
 *
 * A full-screen list rather than a drawer or a bar. Ten screens is too many for
 * a bar and a drawer would be a thing to learn; a list of ten rows with a line
 * under each is a thing to read. Each row says what its screen is *for* as well
 * as what it is called, because "Sessions" means nothing until it does.
 *
 * It takes its rows rather than knowing them: the navigation graph belongs to
 * `:app`, and a feature module that knew every destination would be a feature
 * module that depends on all of them.
 */
@Composable
fun MenuScreen(
  sections: List<MenuSection>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .testTag(MenuTestTags.SCREEN),
  ) {
    sections.forEach { section ->
      Text(
        text = section.name.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
      )
      section.entries.forEach { entry ->
        HorizontalDivider()
        Entry(entry)
      }
    }
    HorizontalDivider()
    Text(
      text = stringResource(R.string.menu_offline),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(16.dp),
    )
  }
}

@Composable
private fun Entry(entry: MenuEntry) {
  Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = entry.open)
        // One node for TalkBack: "Saved rolls, groups per game and per
        // character" is one thing to choose, not two to read past.
        .semantics(mergeDescendants = true) {}
        .testTag(MenuTestTags.entryOf(entry.id))
        .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Text(
      text = entry.title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = entry.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** One heading of the menu and the screens under it. */
data class MenuSection(
  val name: String,
  val entries: List<MenuEntry>,
)

/**
 * One row: a screen, what it is for, and how to open it.
 *
 * @param id a stable handle for tests, which is the destination's route.
 */
data class MenuEntry(
  val id: String,
  val title: String,
  val description: String,
  val open: () -> Unit,
)

/** Stable handles for tests. */
object MenuTestTags {
  const val SCREEN: String = "menu:screen"

  /** The way in, on every screen that has one. */
  const val BUTTON: String = "menu:button"

  fun entryOf(id: String): String = "menu:entry:$id"
}
