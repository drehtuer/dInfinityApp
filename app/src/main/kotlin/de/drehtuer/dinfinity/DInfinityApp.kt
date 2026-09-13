package de.drehtuer.dinfinity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.roll.RollScreen
import de.drehtuer.dinfinity.feature.settings.SettingsScreen
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.LocalModernistColors
import de.drehtuer.dinfinity.theme.ModernistTokens

/**
 * The navigation graph, with one destination per screen. Each destination is a
 * placeholder until the screen's own step builds it; the graph exists now so
 * navigation is never retro-fitted.
 *
 * @param rollPresenter how to build the roll screen's state, or null where
 *   there is nothing to build it with — a Robolectric test of the graph itself
 *   has no GPU and no physics engine, and the placeholder is the honest thing
 *   to show rather than a tray that cannot draw.
 */
@Composable
fun DInfinityApp(
  settings: AppSettings = AppSettings(),
  onAccentSelected: (AccentColor) -> Unit = {},
  rollPresenter: (() -> RollPresenter)? = null,
) {
  val navController = rememberNavController()
  NavHost(
    navController = navController,
    startDestination = Destination.home.route,
  ) {
    Destination.entries.forEach { destination ->
      composable(destination.route) {
        when (destination) {
          // Remembered per visit, not held by the application: a presenter owns
          // the roll thread and, through it, a Filament engine and a physics
          // world. Leaving the screen gives all three back
          // (`docs/architecture.md`, decision 49).
          Destination.Roll if rollPresenter != null ->
            RollScreen(presenter = remember(rollPresenter) { rollPresenter() })

          Destination.Settings ->
            SettingsScreen(
              settings = settings,
              onAccentSelected = onAccentSelected,
            )

          else ->
            PlaceholderScreen(
              destination = destination,
              onOpenSettings = { navController.navigate(Destination.Settings.route) },
            )
        }
      }
    }
  }
}

/** Stands in for a screen that has not been built yet. */
@Composable
internal fun PlaceholderScreen(
  destination: Destination,
  onOpenSettings: () -> Unit = {},
) {
  val colors = LocalModernistColors.current
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(colors.background)
        .testTag("screen:${destination.route}"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(ModernistTokens.Space.x2),
      horizontalAlignment = Alignment.Start,
      modifier = Modifier.padding(ModernistTokens.Space.x6),
    ) {
      Text(
        text = destination.title,
        style = MaterialTheme.typography.headlineMedium,
        color = colors.text,
      )
      Text(
        text = "Not built yet — see docs/TODO.md",
        style = MaterialTheme.typography.labelSmall,
        color = colors.accent,
      )
      // Scaffolding. The real way in is the full-screen menu of plan step
      // 4.10; until that exists, Settings would be unreachable on a device,
      // and a setting nobody can open is not a setting. Delete this with the
      // rest of PlaceholderScreen.
      Text(
        text = "Settings →",
        style = MaterialTheme.typography.labelLarge,
        color = colors.accent,
        modifier =
          Modifier
            .padding(top = ModernistTokens.Space.x4)
            .clickable(onClick = onOpenSettings)
            .testTag("placeholder:open-settings"),
      )
    }
  }
}
