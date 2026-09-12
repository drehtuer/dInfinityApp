package de.drehtuer.dinfinity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.LocalModernistColors
import de.drehtuer.dinfinity.theme.ModernistTokens

/**
 * The navigation graph, with one destination per screen. Each destination is a
 * placeholder until the screen's own step builds it; the graph exists now so
 * navigation is never retro-fitted.
 */
@Composable
fun DInfinityApp() {
  val navController = rememberNavController()
  NavHost(
    navController = navController,
    startDestination = Destination.home.route,
  ) {
    Destination.entries.forEach { destination ->
      composable(destination.route) {
        PlaceholderScreen(destination)
      }
    }
  }
}

/** Stands in for a screen that has not been built yet. */
@Composable
internal fun PlaceholderScreen(destination: Destination) {
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
    }
  }
}
