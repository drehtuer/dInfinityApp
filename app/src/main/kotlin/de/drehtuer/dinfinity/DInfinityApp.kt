package de.drehtuer.dinfinity

import android.net.Uri
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.graph.GraphPresenter
import de.drehtuer.dinfinity.feature.graph.GraphScreen
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.roll.RollScreen
import de.drehtuer.dinfinity.feature.settings.SettingsScreen
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.navigation.GraphArgument
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
 * @param graphMachine the same, for the outcome graph, which needs only the
 *   installed sets.
 * @param navController taken rather than only made, so a test can open a
 *   screen the way a control would rather than by pressing its way there.
 */
@Composable
fun DInfinityApp(
  settings: AppSettings = AppSettings(),
  onAccentSelected: (AccentColor) -> Unit = {},
  rollPresenter: (() -> RollPresenter)? = null,
  graphMachine: (() -> GraphMachine)? = null,
  onPowerSavingChanged: (Boolean) -> Unit = {},
  onWelcomeSeen: () -> Unit = {},
  navController: NavHostController = rememberNavController(),
) {
  NavHost(
    navController = navController,
    startDestination = Destination.home.route,
  ) {
    Destination.entries.forEach { destination ->
      composable(
        route = destination.pattern,
        // Every argument is optional and empty by default, so a destination is
        // still reachable by its bare route — from the menu, from a restored
        // back stack, from a test.
        arguments =
          destination.arguments.map { name ->
            navArgument(name) {
              type = NavType.StringType
              defaultValue = ""
            }
          },
      ) { entry ->
        when (destination) {
          // Remembered per visit, not held by the application: a presenter owns
          // the roll thread and, through it, a Filament engine and a physics
          // world. Leaving the screen gives all three back
          // (`docs/architecture.md`, decision 49).
          Destination.Roll if rollPresenter != null ->
            RollScreen(
              presenter = remember(rollPresenter) { rollPresenter() },
              firstLaunch = !settings.welcomeSeen,
              onWelcomeSeen = onWelcomeSeen,
              onSeeTheOdds = { formula, total ->
                navController.navigate(graphRoute(formula, total))
              },
            )

          // Built from its arguments and nothing else, so the graph a link
          // opens is the graph that link named — and the same link opened
          // again, or restored from a back stack, is the same graph.
          Destination.Graph if graphMachine != null ->
            GraphScreen(
              presenter =
                remember(entry) {
                  GraphPresenter(
                    machine = graphMachine(),
                    formula = entry.arguments?.getString(GraphArgument.FORMULA).orEmpty(),
                    rolled = entry.arguments?.getString(GraphArgument.TOTAL)?.toIntOrNull(),
                  )
                },
            )

          Destination.Settings ->
            SettingsScreen(
              settings = settings,
              onAccentSelected = onAccentSelected,
              onPowerSavingChanged = onPowerSavingChanged,
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

/**
 * The route that opens the outcome graph for [formula], marking [total].
 *
 * Encoded, because a formula is made of the characters a URI reserves: `3d6 +
 * 1d20` has a `+` in it, which unencoded would arrive as a space and graph a
 * different roll.
 *
 * A total is a `Long` and the distribution is indexed by `Int`, so a total too
 * big to be one simply does not mark the chart — which is right, because a
 * total that large is not on it either.
 */
internal fun graphRoute(
  formula: String,
  total: Long?,
): String =
  buildString {
    append(Destination.Graph.route)
    append("?${GraphArgument.FORMULA}=${Uri.encode(formula)}")
    if (total != null) append("&${GraphArgument.TOTAL}=$total")
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
