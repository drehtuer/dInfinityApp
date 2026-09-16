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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
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
  header: MenuHeader? = null,
) {
  // A `Surface` rather than a bare `Column`, for the content colour it brings
  // with it. Without one `LocalContentColor` is plain black, and a `Text` that
  // does not name a colour is drawn black on the dark background — invisible,
  // and invisible in a way no assertion about text catches. Every other `Text`
  // here names its colour, so the app name was the only one that went missing.
  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .verticalScroll(rememberScrollState())
          .testTag(MenuTestTags.SCREEN),
    ) {
      header?.let { Header(it) }
      sections.forEach { section ->
        Text(
          text = section.name.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          // A section name is small, capitalised and in the accent — three
          // ways of saying "heading" that a screen reader gets none of.
          // Marked as one, so the menu can be jumped through by section
          // instead of swiped through row by row
          // (`docs/architecture.md`, "Accessibility").
          modifier =
            Modifier
              .semantics { heading() }
              .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
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
}

/**
 * What the menu is the menu *of*, and where the rolls are going
 * (`design/dInfinity.dc.html`, option 1q).
 *
 * The session is here rather than on the roll screen for the reason it is a
 * preference at all: it outlives every screen, nothing on the tray should have
 * to carry it, and the menu is the one place a player already looks to find
 * out where they are. A roll is filed under it whether or not anybody
 * remembers that (`docs/statistics.md`).
 *
 * @param appName the app's own name.
 * @param session the active session's name, or null when there is only the one
 *   every install starts with — naming it would be a line that never changes
 *   and never means anything.
 */
data class MenuHeader(
  val appName: String,
  val session: String? = null,
) {
  companion object {
    /**
     * The header for an app with [sessions] sessions, of which [activeSession]
     * is the current one's name.
     *
     * The session is named only when there is more than one. Every install
     * starts with exactly one — the one the rolls made before anybody thought
     * about sessions belong to — and naming it would be a line that never
     * changes and never tells anybody anything.
     */
    fun of(
      appName: String,
      activeSession: String?,
      sessions: Int,
    ): MenuHeader = MenuHeader(appName = appName, session = activeSession?.takeIf { sessions > 1 })
  }
}

@Composable
private fun Header(header: MenuHeader) {
  Column(
    // One block for a screen reader: "dInfinity, rolling into Tuesday
    // campaign" is the sentence, and two nodes would read it as two.
    modifier =
      Modifier
        .semantics(mergeDescendants = true) { }
        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        .testTag(MenuTestTags.HEADER),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = header.appName,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    header.session?.let { name ->
      Text(
        text = stringResource(R.string.menu_session, name),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(MenuTestTags.SESSION),
      )
    }
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
  const val HEADER: String = "menu:header"
  const val SESSION: String = "menu:session"

  /** The way in, on every screen that has one. */
  const val BUTTON: String = "menu:button"

  fun entryOf(id: String): String = "menu:entry:$id"
}
