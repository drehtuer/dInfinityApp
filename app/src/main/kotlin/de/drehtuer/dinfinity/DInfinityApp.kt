package de.drehtuer.dinfinity

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.graph.GraphPresenter
import de.drehtuer.dinfinity.feature.graph.GraphScreen
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.roll.RollScreen
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import de.drehtuer.dinfinity.feature.saved.EditorScreen
import de.drehtuer.dinfinity.feature.saved.GroupPresenter
import de.drehtuer.dinfinity.feature.saved.HomeStrip
import de.drehtuer.dinfinity.feature.saved.ImportPresenter
import de.drehtuer.dinfinity.feature.saved.ImportScreen
import de.drehtuer.dinfinity.feature.saved.SavedPresenter
import de.drehtuer.dinfinity.feature.saved.SavedScreen
import de.drehtuer.dinfinity.feature.settings.MenuButton
import de.drehtuer.dinfinity.feature.settings.MenuEntry
import de.drehtuer.dinfinity.feature.settings.MenuScreen
import de.drehtuer.dinfinity.feature.settings.MenuSection
import de.drehtuer.dinfinity.feature.settings.SettingsScreen
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.HistoryScreen
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
import de.drehtuer.dinfinity.feature.stats.StatsScreen
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.navigation.EditorArgument
import de.drehtuer.dinfinity.navigation.GraphArgument
import de.drehtuer.dinfinity.navigation.MenuGroup
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
 * @param savedRolls the same, for the saved-rolls screen, which needs the
 *   database.
 * @param savedGroups the same, for the group sheet the saved-rolls list and
 *   the editor both open.
 * @param collectionImport the same, for the screen that takes a collection in.
 * @param history the same, for the list of past rolls.
 * @param statistics the same, for what every die has done.
 * @param navController taken rather than only made, so a test can open a
 *   screen the way a control would rather than by pressing its way there.
 */
@Composable
fun DInfinityApp(
  settings: AppSettings = AppSettings(),
  onAccentSelected: (AccentColor) -> Unit = {},
  onAppearanceSelected: (Appearance) -> Unit = {},
  onShakeChanged: (Boolean) -> Unit = {},
  onRoundingSelected: (Rounding) -> Unit = {},
  onRepository: () -> Unit = {},
  version: String = "",
  rollPresenter: (() -> RollPresenter)? = null,
  graphMachine: (() -> GraphMachine)? = null,
  savedRolls: (() -> SavedPresenter)? = null,
  savedRollEditor: ((String?) -> EditorPresenter)? = null,
  savedGroups: (() -> GroupPresenter)? = null,
  collectionImport: (() -> ImportPresenter)? = null,
  history: (() -> HistoryPresenter)? = null,
  statistics: (() -> StatsPresenter)? = null,
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
            Roll(rollPresenter, savedRolls, entry, navController, settings, onWelcomeSeen)

          // Every screen the app has, and the only way to most of them
          // (`docs/architecture.md`, "Screens and the states behind them").
          Destination.Menu ->
            MenuScreen(sections = menuSections(navController))

          Destination.Graph if graphMachine != null -> Graph(graphMachine, entry, navController)

          Destination.SavedRolls if savedRolls != null && savedGroups != null ->
            Saved(savedRolls, savedGroups, entry, navController)

          Destination.SavedRollEditor if savedRollEditor != null && savedGroups != null ->
            Editor(savedRollEditor, savedGroups, entry, navController)

          Destination.CollectionImport if collectionImport != null ->
            Import(collectionImport, entry, navController)

          Destination.History if history != null ->
            HistoryScreen(
              presenter = remember(entry) { history() },
              menu = { MenuTo(navController) },
            )

          Destination.Statistics if statistics != null ->
            StatsScreen(
              presenter = remember(entry) { statistics() },
              menu = { MenuTo(navController) },
            )

          Destination.Settings ->
            SettingsScreen(
              settings = settings,
              onAccentSelected = onAccentSelected,
              onAppearanceSelected = onAppearanceSelected,
              onPowerSavingChanged = onPowerSavingChanged,
              onShakeChanged = onShakeChanged,
              onRoundingSelected = onRoundingSelected,
              onRepository = onRepository,
              version = version,
              menu = { MenuTo(navController) },
            )

          else -> PlaceholderScreen(destination = destination, menu = { MenuTo(navController) })
        }
      }
    }
  }
}

/** The way to the menu, which every screen carries in the same place. */
@Composable
private fun MenuTo(navController: NavHostController) {
  MenuButton(onOpen = { navController.navigate(Destination.Menu.route) })
}

/**
 * The tray, remembered per visit rather than held by the application: a
 * presenter owns the roll thread and, through it, a Filament engine and a
 * physics world. Leaving the screen gives all three back
 * (`docs/architecture.md`, decision 49).
 */
@Composable
private fun Roll(
  presenter: () -> RollPresenter,
  savedRolls: (() -> SavedPresenter)?,
  entry: NavBackStackEntry,
  navController: NavHostController,
  settings: AppSettings,
  onWelcomeSeen: () -> Unit,
) {
  val saved = savedRolls?.let { make -> remember(entry) { make() } }
  RollScreen(
    presenter = remember(presenter) { presenter() },
    firstLaunch = !settings.welcomeSeen,
    onWelcomeSeen = onWelcomeSeen,
    shakeToRoll = settings.shakeToRoll,
    onSeeTheOdds = { formula, total -> navController.navigate(graphRoute(formula, total)) },
    menu = { MenuTo(navController) },
    // The active group's saved rolls, handed to the tray as a slot: the roll
    // screen does not know what a saved roll is, and does not have to
    // (`design/dInfinity.dc.html`, option 9a).
    strip = { rollIt ->
      if (saved != null) {
        HomeStrip(
          presenter = saved,
          onRoll = rollIt,
          onEdit = { rollId -> navController.navigate(editorRoute(rollId)) },
          onNew = { navController.navigate(editorRoute(null)) },
        )
      }
    },
    openWith = entry.arguments?.getString(GraphArgument.FORMULA).orEmpty(),
  )
}

/**
 * The outcome graph, built from its arguments and nothing else — so the graph
 * a link opens is the graph that link named, and the same link opened again,
 * or restored from a back stack, is the same graph.
 */
@Composable
private fun Graph(
  machine: () -> GraphMachine,
  entry: NavBackStackEntry,
  navController: NavHostController,
) {
  GraphScreen(
    menu = { MenuTo(navController) },
    presenter =
      remember(entry) {
        GraphPresenter(
          machine = machine(),
          formula = entry.arguments?.getString(GraphArgument.FORMULA).orEmpty(),
          rolled = entry.arguments?.getString(GraphArgument.TOTAL)?.toIntOrNull(),
        )
      },
  )
}

/** Saved rolls, and the way from one back to the tray. */
@Composable
private fun Saved(
  presenter: () -> SavedPresenter,
  groups: () -> GroupPresenter,
  entry: NavBackStackEntry,
  navController: NavHostController,
) {
  val context = LocalContext.current
  SavedScreen(
    presenter = remember(entry) { presenter() },
    groups = remember(entry) { groups() },
    onRoll = { saved ->
      // Back to the tray with that formula in the field. The throw itself is
      // the player's to make: a saved roll is a formula with a name, not a
      // roll waiting to happen.
      navController.navigate(rollRoute(saved.roll.formula)) {
        popUpTo(Destination.home.route) { inclusive = true }
      }
    },
    onEdit = { saved -> navController.navigate(editorRoute(saved.roll.id)) },
    onNew = { navController.navigate(editorRoute(null)) },
    // The screen decides what is in the file; the app knows how to hand a
    // file to another app, because the provider that does it is declared in
    // this module's manifest.
    onExport = { file -> CollectionSharing.share(context, file) },
    onImport = { navController.navigate(Destination.CollectionImport.route) },
    menu = { MenuTo(navController) },
  )
}

/**
 * Writing down one saved roll.
 *
 * Leaves when it is saved or deleted, by going back rather than forward: the
 * editor is a detour from the list, and finishing one is arriving back where
 * it started.
 */
@Composable
private fun Editor(
  presenter: (String?) -> EditorPresenter,
  groups: () -> GroupPresenter,
  entry: NavBackStackEntry,
  navController: NavHostController,
) {
  val editing = entry.arguments?.getString(EditorArgument.ROLL)?.ifBlank { null }
  EditorScreen(
    presenter = remember(entry) { presenter(editing) },
    groups = remember(entry) { groups() },
    onDone = { navController.popBackStack() },
    onRollNow = { formula ->
      navController.navigate(rollRoute(formula)) {
        popUpTo(Destination.home.route) { inclusive = true }
      }
    },
  )
}

/**
 * Taking a collection in.
 *
 * The file picker is here rather than in the screen because a content URI is
 * the application's business: the screen takes text, and everything about
 * *getting* text out of something another app controls — the permission, the
 * bounded read, the failure to open — happens on this side of the seam.
 */
@Composable
private fun Import(
  presenter: () -> ImportPresenter,
  entry: NavBackStackEntry,
  navController: NavHostController,
) {
  val importer = remember(entry) { presenter() }
  val resolver = LocalContext.current.contentResolver
  val choose =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      // A null uri is the picker being dismissed, which is not a failure and
      // has nothing to say.
      if (uri == null) return@rememberLauncherForActivityResult
      when (val read = CollectionFileReading.read(resolver, uri)) {
        is CollectionFileReading.Result.Read -> importer.offer(read.text)
        is CollectionFileReading.Result.Failed -> importer.unopenable(read.why)
      }
    }
  ImportScreen(
    presenter = importer,
    // Anything, not just application/json: a collection mailed through three
    // apps arrives as text/plain or application/octet-stream as often as not,
    // and a picker that hides the file somebody is looking at is worse than
    // one that lets them choose the wrong thing and be told so.
    onChooseFile = { choose.launch(arrayOf("*/*")) },
    onDone = {
      navController.navigate(Destination.SavedRolls.route) {
        popUpTo(Destination.CollectionImport.route) { inclusive = true }
      }
    },
    menu = { MenuTo(navController) },
  )
}

/** The route that opens the editor on [rollId], or on a new roll for null. */
internal fun editorRoute(rollId: String?): String =
  buildString {
    append(Destination.SavedRollEditor.route)
    if (rollId != null) append("?${EditorArgument.ROLL}=${Uri.encode(rollId)}")
  }

/**
 * The route that opens the tray with [formula] already in the field.
 *
 * Encoded like the graph's, and for the same reason: a formula is made of the
 * characters a URI reserves.
 */
internal fun rollRoute(formula: String): String =
  "${Destination.Roll.route}?${GraphArgument.FORMULA}=${Uri.encode(formula)}"

/**
 * The menu's rows, one per screen, grouped as the design groups them.
 *
 * Choosing one **takes the menu off the back stack with it**, so back from the
 * screen it opened goes to wherever the menu was opened from rather than to
 * the menu again. A menu you have to press back through twice is a menu that
 * feels like a detour (`design/dInfinity.dc.html`, option 1q).
 */
private fun menuSections(navController: NavHostController): List<MenuSection> =
  MenuGroup.entries.mapNotNull { group ->
    val entries =
      Destination.inTheMenu
        .filter { it.group == group }
        .map { destination ->
          MenuEntry(
            id = destination.route,
            title = destination.title,
            description = destination.description,
            open = {
              navController.navigate(destination.route) {
                popUpTo(Destination.Menu.route) { inclusive = true }
                // Choosing the screen you are already on is not a second copy
                // of it.
                launchSingleTop = true
              }
            },
          )
        }
    if (entries.isEmpty()) null else MenuSection(name = group.title, entries = entries)
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
  menu: @Composable () -> Unit = {},
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
    }
    Box(
      modifier =
        Modifier
          .align(Alignment.TopEnd)
          .safeDrawingPadding()
          .padding(8.dp),
    ) {
      menu()
    }
  }
}
