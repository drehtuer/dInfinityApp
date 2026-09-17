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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import de.drehtuer.dinfinity.feature.designer.DesignerScreen
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.graph.GraphPresenter
import de.drehtuer.dinfinity.feature.graph.GraphScreen
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.roll.RollScreen
import de.drehtuer.dinfinity.feature.roll.WhatIsThere
import de.drehtuer.dinfinity.feature.saved.Editing
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import de.drehtuer.dinfinity.feature.saved.EditorScreen
import de.drehtuer.dinfinity.feature.saved.GroupPresenter
import de.drehtuer.dinfinity.feature.saved.HomeStrip
import de.drehtuer.dinfinity.feature.saved.ImportPresenter
import de.drehtuer.dinfinity.feature.saved.ImportScreen
import de.drehtuer.dinfinity.feature.saved.SavedPresenter
import de.drehtuer.dinfinity.feature.saved.SavedScreen
import de.drehtuer.dinfinity.feature.sets.SetDetailScreen
import de.drehtuer.dinfinity.feature.sets.SetsPresenter
import de.drehtuer.dinfinity.feature.sets.SetsScreen
import de.drehtuer.dinfinity.feature.settings.DeveloperPresenter
import de.drehtuer.dinfinity.feature.settings.DeveloperScreen
import de.drehtuer.dinfinity.feature.settings.MenuButton
import de.drehtuer.dinfinity.feature.settings.MenuEntry
import de.drehtuer.dinfinity.feature.settings.MenuHeader
import de.drehtuer.dinfinity.feature.settings.MenuScreen
import de.drehtuer.dinfinity.feature.settings.MenuSection
import de.drehtuer.dinfinity.feature.settings.NotationScreen
import de.drehtuer.dinfinity.feature.settings.SettingsScreen
import de.drehtuer.dinfinity.feature.stats.HistoryScreen
import de.drehtuer.dinfinity.feature.stats.SavedStatsScreen
import de.drehtuer.dinfinity.feature.stats.SessionsScreen
import de.drehtuer.dinfinity.feature.stats.StatsScreen
import de.drehtuer.dinfinity.feature.tables.PickedPhoto
import de.drehtuer.dinfinity.feature.tables.TablesPresenter
import de.drehtuer.dinfinity.feature.tables.TablesScreen
import de.drehtuer.dinfinity.navigation.DesignerArgument
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.navigation.EditorArgument
import de.drehtuer.dinfinity.navigation.GraphArgument
import de.drehtuer.dinfinity.navigation.MenuGroup
import de.drehtuer.dinfinity.navigation.SetArgument
import de.drehtuer.dinfinity.theme.LocalModernistColors
import de.drehtuer.dinfinity.ui.common.Modernist
import kotlinx.coroutines.flow.Flow
import java.io.File

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
 * @param sessions the same, for the buckets statistics are filtered by.
 * @param navController taken rather than only made, so a test can open a
 *   screen the way a control would rather than by pressing its way there.
 */
@Composable
fun DInfinityApp(
  settings: AppSettings = AppSettings(),
  onAccentSelected: (AccentColor) -> Unit = {},
  onAppearanceSelected: (Appearance) -> Unit = {},
  onShakeChanged: (Boolean) -> Unit = {},
  onHapticsChanged: (Boolean) -> Unit = {},
  onSoundChanged: (Boolean) -> Unit = {},
  onRoundingSelected: (Rounding) -> Unit = {},
  onDeveloperToolsChanged: (Boolean) -> Unit = {},
  onRepository: () -> Unit = {},
  /**
   * How a piece of text leaves the app — the anomaly log, and nothing else
   * (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * Separate from the statistics export on purpose: that one is built from
   * `HistoryEntry`, which has no seed on it, and this one carries seeds and is
   * reachable only from the developer screen (`docs/statistics.md`).
   */
  onShareText: (String) -> Unit = {},
  version: String = "",
  /**
   * How to build every screen, or null to draw placeholders for all of them.
   *
   * All or nothing on purpose: half a set of screens is how the sessions
   * screen went missing for a whole step ([Presenters]).
   */
  screens: Presenters? = null,
  onSource: (String) -> Unit = {},
  onPowerSavingChanged: (Boolean) -> Unit = {},
  onWelcomeSeen: () -> Unit = {},
  menuHeader: MenuHeader? = null,
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
        // Split in three, by what the screens are *about* rather than for
        // tidiness: the graph's own table outgrew both the length and the
        // complexity limits when sessions arrived, and each of these is one
        // question — what the app does, what a player saved, what they have
        // done. Each returns whether it recognised the destination, so exactly
        // one of them draws and nothing falls through silently.
        val drawn =
          playing(destination, entry, navController, settings, screens, onWelcomeSeen) ||
            saving(destination, entry, navController, screens) ||
            lookingBack(destination, entry, navController, screens) ||
            counting(destination, entry, navController, screens) ||
            customising(destination, entry, navController, screens, onSource) ||
            chrome(
              destination = destination,
              entry = entry,
              navController = navController,
              settings = settings,
              onAccentSelected = onAccentSelected,
              onAppearanceSelected = onAppearanceSelected,
              onPowerSavingChanged = onPowerSavingChanged,
              onShakeChanged = onShakeChanged,
              onHapticsChanged = onHapticsChanged,
              onSoundChanged = onSoundChanged,
              onRoundingSelected = onRoundingSelected,
              onDeveloperToolsChanged = onDeveloperToolsChanged,
              onRepository = onRepository,
              version = version,
              menuHeader = menuHeader,
              developer = screens?.developer,
              onShareText = onShareText,
            )
        // A destination whose screen is not built yet, or whose presenter was
        // not supplied — a Robolectric test of the graph has neither a GPU nor
        // a physics engine, and a placeholder is the honest thing to draw.
        if (!drawn) PlaceholderScreen(destination = destination, menu = { MenuTo(navController) })
      }
    }
  }
}

/**
 * How a test says "this destination drew a placeholder rather than a screen".
 *
 * A tag rather than the words on it, so the sentence can be reworded without
 * quietly turning the check off.
 */
internal fun notBuiltTag(destination: Destination): String = "notbuilt:${destination.route}"

/** The way to the menu, which every screen carries in the same place. */
@Composable
private fun MenuTo(navController: NavHostController) {
  MenuButton(onOpen = { navController.navigate(Destination.Menu.route) })
}

/**
 * The tray, remembered per visit rather than held by the application: a
 * presenter owns a physics world and a scene, and leaving the screen gives
 * both back (`docs/architecture.md`, decision 49). The thread and the Filament
 * engine underneath outlive the visit (decision 50).
 */
@Composable
private fun Roll(
  presenter: () -> RollPresenter,
  savedRolls: (() -> SavedPresenter)?,
  whatIsThere: Flow<WhatIsThere>,
  entry: NavBackStackEntry,
  navController: NavHostController,
  settings: AppSettings,
  onWelcomeSeen: () -> Unit,
) {
  val saved = savedRolls?.let { make -> remember(entry) { make() } }
  // Only while the welcome is up: after that nothing reads it, and a flow
  // collected for a line nobody is looking at is a database watched for
  // nothing.
  val what by
    if (settings.welcomeSeen) {
      remember { mutableStateOf(WhatIsThere()) }
    } else {
      whatIsThere.collectAsState(WhatIsThere())
    }
  RollScreen(
    // Keyed on the visit, like every other screen here, and emphatically not
    // on the lambda: that one is built afresh every time `settings` changes,
    // so keying on it made a *new* presenter — and a new tray — whenever any
    // preference arrived. On a cold launch one always does, because the
    // defaults stand in until the file has been read. The surface had already
    // been handed to the tray that was just thrown away, and nothing hands it
    // to the new one, so the dice rolled onto a tray nobody could see.
    presenter = remember(entry) { presenter() },
    firstLaunch = !settings.welcomeSeen,
    whatIsThere = what,
    onWelcomeSeen = onWelcomeSeen,
    // The welcome's other two ways in. Neither dismisses it: somebody who goes
    // to fetch something comes back to a count line that says so
    // (`design/dInfinity.dc.html`, option 9a).
    onImportCollection = { navController.navigate(Destination.CollectionImport.route) },
    onAddSets = { navController.navigate(Destination.DiceSets.route) },
    shakeToRoll = settings.shakeToRoll,
    onSeeTheOdds = { formula, total -> navController.navigate(graphRoute(formula, total)) },
    // Quick mode: a long press on a die that landed opens the designer on that
    // die (`docs/face-designer.md`, "Quick mode"). The tray is left behind
    // like any other way off this screen, and back comes to it again — which
    // is why the drawing is a draft on disk rather than something to save.
    onDoodle = { dieId -> navController.navigate(designerRoute(dieId)) },
    menu = { MenuTo(navController) },
    // The active group's saved rolls, handed to the tray as a slot: the roll
    // screen does not know what a saved roll is, and does not have to
    // (`design/dInfinity.dc.html`, option 9a).
    strip = { rollIt ->
      if (saved != null) {
        HomeStrip(
          presenter = saved,
          onRoll = { formula, source -> rollIt(formula, source) },
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
    onRoll = { formula -> navController.navigate(rollRoute(formula)) },
    onSave = { formula -> navController.navigate(editorRoute(rollId = null, formula = formula)) },
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

/**
 * What the app *does*: the tray, the odds, and the way between them.
 *
 * @return true when this is one of them, so the caller knows it has been drawn.
 */
@Composable
private fun playing(
  destination: Destination,
  entry: NavBackStackEntry,
  navController: NavHostController,
  settings: AppSettings,
  screens: Presenters?,
  onWelcomeSeen: () -> Unit,
): Boolean =
  when (destination) {
    // Remembered per visit, not held by the application: a presenter owns a
    // physics world and a scene, and leaving the screen gives both back
    // (`docs/architecture.md`, decision 49). The thread and the Filament engine
    // underneath outlive the visit (decision 50).
    Destination.Roll if screens != null -> {
      Roll(
        screens.roll,
        screens.savedRolls,
        screens.whatIsThere,
        entry,
        navController,
        settings,
        onWelcomeSeen,
      )
      true
    }

    Destination.Graph if screens != null -> {
      Graph(screens.graph, entry, navController)
      true
    }

    else -> false
  }

/** What a player wrote down: the list, the editor, and a collection arriving. */
@Composable
private fun saving(
  destination: Destination,
  entry: NavBackStackEntry,
  navController: NavHostController,
  screens: Presenters?,
): Boolean =
  when (destination) {
    Destination.SavedRolls if screens != null -> {
      Saved(screens.savedRolls, screens.savedGroups, entry, navController)
      true
    }

    Destination.SavedRollEditor if screens != null -> {
      Editor(screens.savedRollEditor, screens.savedGroups, entry, navController)
      true
    }

    Destination.CollectionImport if screens != null -> {
      Import(screens.collectionImport, entry, navController)
      true
    }

    else -> false
  }

/**
 * The dice-set list, with the file picker that installs one.
 *
 * The picker is here rather than in `feature/sets` because a content URI is the
 * application's business: it is reached through a `ContentResolver`, and the
 * presenter takes a `File`. The bytes are copied bounded into the app's own
 * cache and the copy is deleted however the install ends, including when it
 * throws (`docs/dice-sets.md`).
 */
@Composable
private fun Sets(
  presenter: SetsPresenter,
  navController: NavHostController,
) {
  val context = LocalContext.current
  val choose =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      // A null uri is the picker being dismissed, which is not a failure and
      // has nothing to say.
      if (uri == null) return@rememberLauncherForActivityResult
      when (val copied = PackageFileReading.copy(context.contentResolver, uri, File(context.cacheDir, CHOSEN))) {
        is PackageFileReading.Result.Copied ->
          // A stranger's archive in a cache nobody empties, and the set it
          // held is on disk by the time anybody wants it again. A filesystem
          // that will not let go of it gets asked once more on the way out.
          presenter.install(copied.file) { if (!copied.file.delete()) copied.file.deleteOnExit() }
        is PackageFileReading.Result.Failed -> presenter.refused(copied.why)
      }
    }
  SetsScreen(
    presenter = presenter,
    onOpen = { row -> navController.navigate("${Destination.SetDetail.route}?${SetArgument.SET}=${row.id}") },
    // Anything, not just an archive type: a dice set downloaded through a
    // browser and three apps arrives as application/octet-stream as often as
    // not, and a picker that hides the file somebody is looking at is worse
    // than one that lets them choose the wrong thing and be told so.
    onInstall = { choose.launch(arrayOf("*/*")) },
    menu = { MenuTo(navController) },
  )
}

/** Where a chosen archive is copied to before the installer opens it. */
private const val CHOSEN = "chosen-packages"

/**
 * The table picker, with the photo picker that makes a look out of a
 * photograph (`docs/tables.md`, "Your own photo").
 *
 * The launcher is here rather than in `feature/tables` for the reason [Sets]'s
 * is: a content URI is the application's business. What crosses into the
 * feature module is a *way of opening a stream* and the file's display name —
 * never a `Uri`, and never bytes, because the photo is opened twice and the
 * second open must not have to rewind the first.
 *
 * Read permission is taken for the length of the pick and no longer. The photo
 * is scaled and written into the personal package while the sheet is up; after
 * that the app has its own copy and has no business holding a handle to
 * somebody's photo library.
 */
@Composable
private fun Tables(
  presenter: TablesPresenter,
  navController: NavHostController,
) {
  val context = LocalContext.current
  val choose =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      // A null uri is the picker being dismissed, which is not a failure and
      // has nothing to say.
      if (uri == null) return@rememberLauncherForActivityResult
      presenter.picked(
        PickedPhoto(
          label = PhotoNames.of(context.contentResolver, uri),
          open = { context.contentResolver.openInputStream(uri) },
        ),
      )
    }
  TablesScreen(
    presenter = presenter,
    menu = { MenuTo(navController) },
    // Pictures only. Unlike a dice set — which arrives as
    // `application/octet-stream` as often as not and so may not be filtered —
    // a photo picker that offered every file on the phone would be offering
    // files that cannot possibly work.
    onPickPhoto = { choose.launch(arrayOf(IMAGES)) },
  )
}

/** What the photo picker is allowed to offer. */
private const val IMAGES = "image/*"

/**
 * What the saved rolls have come to, against what they should.
 *
 * Its own branch rather than one more parameter on [lookingBack], which is
 * already at the limit: a table of screens that has to be split is split by
 * what the screens are about, and this one is about the maths
 * (`docs/statistics.md`).
 */
@Composable
private fun counting(
  destination: Destination,
  entry: NavBackStackEntry,
  navController: NavHostController,
  screens: Presenters?,
): Boolean =
  when (destination) {
    Destination.SavedRollStats if screens != null -> {
      SavedStatsScreen(presenter = remember(entry) { screens.savedStatistics() }, menu = { MenuTo(navController) })
      true
    }

    else -> false
  }

/**
 * What the player has installed, and what they may change about it.
 *
 * Its own question rather than a branch of [chrome]: settings are the app's
 * own knobs, and these are the things the player has brought to it
 * (`docs/dice-sets.md`). The table picker and the face designer land here too
 * (`docs/TODO.md`, Steps 4.5 and 4.6).
 */
@Composable
private fun customising(
  destination: Destination,
  entry: NavBackStackEntry,
  navController: NavHostController,
  screens: Presenters?,
  onSource: (String) -> Unit,
): Boolean =
  when (destination) {
    Destination.DiceSets if screens != null -> {
      Sets(remember(entry) { screens.diceSets() }, navController)
      true
    }

    Destination.Tables if screens != null -> {
      Tables(remember(entry) { screens.tables() }, navController)
      true
    }

    Destination.FaceDesigner if screens != null -> {
      // On the die the route names — "Doodle this die" off a long press in the
      // breakdown — and on the usual one when it names none
      // (`docs/face-designer.md`, "Quick mode").
      val die = entry.arguments?.getString(DesignerArgument.DIE).orEmpty()
      DesignerScreen(
        presenter = remember(entry) { screens.faceDesigner(die) },
        // Straight to the tray with the die in the field, unrolled — the same
        // answer the notation screen's examples give, and for the same reason:
        // the throw is the player's to make.
        onRoll = { formula -> navController.navigate(rollRoute(formula)) },
        menu = { MenuTo(navController) },
      )
      true
    }

    Destination.SetDetail if screens != null -> {
      val id = entry.arguments?.getString(SetArgument.SET).orEmpty()
      SetDetailScreen(
        // Removing the set leaves the screen that was showing it: there is
        // nothing left to show, and staying would be a page about a folder
        // that is not there.
        presenter = remember(entry) { screens.diceSet(id) { navController.popBackStack() } },
        onSource = onSource,
        menu = { MenuTo(navController) },
      )
      true
    }

    else -> false
  }

/** What a player has done: the history, the dice, and the buckets they are in. */
@Composable
private fun lookingBack(
  destination: Destination,
  entry: NavBackStackEntry,
  navController: NavHostController,
  screens: Presenters?,
): Boolean =
  when (destination) {
    Destination.History if screens != null -> {
      val context = LocalContext.current
      HistoryScreen(
        presenter = remember(entry) { screens.history() },
        onExport = { file -> NumbersSharing.share(context, file) },
        menu = { MenuTo(navController) },
      )
      true
    }

    Destination.Statistics if screens != null -> {
      val context = LocalContext.current
      StatsScreen(
        presenter = remember(entry) { screens.statistics() },
        onExport = { file -> NumbersSharing.share(context, file) },
        menu = { MenuTo(navController) },
      )
      true
    }

    Destination.Sessions if screens != null -> {
      SessionsScreen(presenter = remember(entry) { screens.sessions() }, menu = { MenuTo(navController) })
      true
    }

    else -> false
  }

/**
 * The app's own screens: the menu that reaches every other, Settings, and the
 * notation reference.
 *
 * None of them takes a presenter. The menu *is* a list of destinations,
 * Settings is driven from above the navigation graph because a preference
 * outlives the screen that changed it (`docs/architecture.md`, decision 49),
 * and the notation reference has nothing to remember at all — it is
 * `NotationReference` with a layout on it.
 */
@Composable
@Suppress("LongParameterList")
private fun chrome(
  destination: Destination,
  entry: NavBackStackEntry,
  navController: NavHostController,
  settings: AppSettings,
  onAccentSelected: (AccentColor) -> Unit,
  onAppearanceSelected: (Appearance) -> Unit,
  onPowerSavingChanged: (Boolean) -> Unit,
  onShakeChanged: (Boolean) -> Unit,
  onHapticsChanged: (Boolean) -> Unit,
  onSoundChanged: (Boolean) -> Unit,
  onRoundingSelected: (Rounding) -> Unit,
  onDeveloperToolsChanged: (Boolean) -> Unit,
  onRepository: () -> Unit,
  version: String,
  menuHeader: MenuHeader?,
  developer: (() -> DeveloperPresenter)?,
  onShareText: (String) -> Unit,
): Boolean =
  when (destination) {
    Destination.Menu -> {
      MenuScreen(
        sections = menuSections(navController, developerTools = settings.developerTools),
        header = menuHeader,
      )
      true
    }

    Destination.Developer -> {
      // A separate surface, and only while the toggle is on. Without a
      // presenter it draws the placeholder every unbuilt screen draws, which
      // is what a Robolectric test of the graph has.
      developer?.let { make ->
        DeveloperScreen(
          presenter = remember(entry) { make() },
          onShare = onShareText,
          menu = { MenuTo(navController) },
        )
      }
      developer != null
    }

    Destination.Notation -> {
      NotationScreen(
        // Straight to the tray with the example in the field, unrolled. The
        // screen exists to answer "what does this do", and the answer is the
        // throw the player makes next — not one the app makes for them
        // (`docs/dice-notation.md`).
        onRoll = { formula -> navController.navigate(rollRoute(formula)) },
        menu = { MenuTo(navController) },
      )
      true
    }

    Destination.Settings -> {
      SettingsScreen(
        settings = settings,
        onAccentSelected = onAccentSelected,
        onAppearanceSelected = onAppearanceSelected,
        onPowerSavingChanged = onPowerSavingChanged,
        onShakeChanged = onShakeChanged,
        onHapticsChanged = onHapticsChanged,
        onSoundChanged = onSoundChanged,
        onRoundingSelected = onRoundingSelected,
        onDeveloperToolsChanged = onDeveloperToolsChanged,
        onRepository = onRepository,
        version = version,
        menu = { MenuTo(navController) },
      )
      true
    }

    else -> false
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
  presenter: (Editing) -> EditorPresenter,
  groups: () -> GroupPresenter,
  entry: NavBackStackEntry,
  navController: NavHostController,
) {
  // The id decides which case this is: a roll that exists has a formula
  // already, and one arriving in the link would be editing it by being
  // followed (`Editing`).
  val opening =
    entry.arguments
      ?.getString(EditorArgument.ROLL)
      ?.ifBlank { null }
      ?.let(Editing::Existing)
      ?: Editing.New(entry.arguments?.getString(EditorArgument.FORMULA).orEmpty())
  EditorScreen(
    presenter = remember(entry) { presenter(opening) },
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

/**
 * The route that opens the editor on [rollId], or on a new roll for null.
 *
 * @param formula what a new roll starts from, which is what the outcome
 *   graph's "Save as roll" carries. Encoded like every other formula in a
 *   route: it is made of characters a URI reserves.
 */
internal fun editorRoute(
  rollId: String?,
  formula: String = "",
): String =
  buildString {
    append(Destination.SavedRollEditor.route)
    val arguments =
      listOfNotNull(
        rollId?.let { "${EditorArgument.ROLL}=${Uri.encode(it)}" },
        formula.ifBlank { null }?.let { "${EditorArgument.FORMULA}=${Uri.encode(it)}" },
      )
    if (arguments.isNotEmpty()) append("?" + arguments.joinToString("&"))
  }

/**
 * The route that opens the face designer on the die with this id.
 *
 * Encoded like every other argument, although a die id is tamer than a
 * formula: an id comes out of a dice set file, and what a stranger may put in
 * one is not this function's to assume (`docs/dice-sets.md`).
 */
internal fun designerRoute(dieId: String): String =
  "${Destination.FaceDesigner.route}?${DesignerArgument.DIE}=${Uri.encode(dieId)}"

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
@Composable
private fun menuSections(
  navController: NavHostController,
  developerTools: Boolean,
): List<MenuSection> =
  MenuGroup.entries.mapNotNull { group ->
    val entries =
      // The developer screen is a row only while the toggle is on. With it off
      // there is no way to it from anywhere a player can reach
      // (`docs/physics-and-rendering.md`, "Debug tooling").
      Destination
        .inTheMenu(developerTools)
        .filter { it.group == group }
        .map { destination ->
          MenuEntry(
            id = destination.route,
            title = stringResource(destination.title),
            // Every screen the menu lists has a line; the ones that do not are
            // exactly the ones `inTheMenu` leaves out, and `DestinationTest`
            // holds both halves of that to each other.
            description = destination.description?.let { stringResource(it) }.orEmpty(),
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
    if (entries.isEmpty()) null else MenuSection(name = stringResource(group.title), entries = entries)
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

/**
 * Stands in for a screen that has not been built yet.
 *
 * It carries the same `screen:<route>` tag a real screen would, because from
 * the navigation graph's point of view it *is* the screen at that route. It
 * also carries [notBuiltTag], which is how a test tells the difference — and
 * the difference is the whole of the bug this guards: a screen that was
 * written and never plugged in looks exactly like a screen that was never
 * written ([Presenters]).
 */
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
      verticalArrangement = Arrangement.spacedBy(Modernist.x2),
      horizontalAlignment = Alignment.Start,
      modifier = Modifier.padding(Modernist.x6),
    ) {
      Text(
        text = stringResource(destination.title),
        style = MaterialTheme.typography.headlineMedium,
        color = colors.text,
      )
      Text(
        text = stringResource(R.string.screen_not_built),
        style = MaterialTheme.typography.labelSmall,
        color = colors.accent,
        modifier = Modifier.testTag(notBuiltTag(destination)),
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
