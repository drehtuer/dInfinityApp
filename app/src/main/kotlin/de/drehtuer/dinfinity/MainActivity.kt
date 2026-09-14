package de.drehtuer.dinfinity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.setAccentColor
import de.drehtuer.dinfinity.data.setAppearance
import de.drehtuer.dinfinity.data.setPowerSaving
import de.drehtuer.dinfinity.data.setRounding
import de.drehtuer.dinfinity.data.setShakeToRoll
import de.drehtuer.dinfinity.data.setWelcomeSeen
import de.drehtuer.dinfinity.feature.saved.R
import de.drehtuer.dinfinity.feature.settings.MenuHeader
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import kotlinx.coroutines.launch

/**
 * The single activity. The app is one Compose tree; screens are navigation
 * destinations, not activities (`docs/architecture.md`).
 */
class MainActivity : ComponentActivity() {
  /**
   * What was actually installed.
   *
   * Read from the package manager rather than from a generated constant, so it
   * is the version on the phone rather than the version some build thought it
   * was compiling (`design/dInfinity.dc.html`, option 2d).
   */
  private fun installedVersion(): String =
    runCatching {
      packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull().orEmpty()

  /** Opens the project's page in a browser. */
  private fun openRepository() = open(REPOSITORY)

  /**
   * Hands a link to whatever the phone opens links with.
   *
   * Only `https`. The links this app produces are the repository's and the one
   * a dice set recorded as where it came from — and the second of those came
   * off a file on disk, so it is not a string to hand an intent without
   * looking at it first (`docs/dice-sets.md`).
   */
  private fun open(url: String) {
    if (!url.startsWith("https://")) return
    open(url.toUri())
  }

  private fun open(uri: Uri) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val app = application as DInfinityApplication
    val repository = app.settingsRepository
    val saved =
      SavedWiring(
        app = app,
        catalog = app.setLibrary.catalogue,
        scope = lifecycleScope,
        unfiledName = getString(R.string.saved_unfiled),
      )
    setContent { Screens(app, repository, saved) }
  }

  /**
   * Everything on screen, which is one Compose tree
   * (`docs/architecture.md`, "Screens and the states behind them").
   *
   * A method rather than the body of `onCreate`, because what it is is a list
   * of which presenter belongs to which destination, and that list only grows.
   */
  @Composable
  private fun Screens(
    app: DInfinityApplication,
    repository: SettingsRepository,
    saved: SavedWiring,
  ) {
    // Until the file has been read the defaults stand in, which is one frame
    // of the design's own accent rather than a blank screen.
    val settings by repository.settings.collectAsStateWithLifecycle(AppSettings())
    // The choice, or the phone's own answer when the choice is to follow it.
    DInfinityTheme(
      darkTheme = settings.appearance.isDark(isSystemInDarkTheme()),
      accent = settings.accentColor,
    ) {
      Wiring(settings, app, repository, saved)
    }
  }

  /**
   * Which presenter belongs to which destination, and what a settings row
   * writes when it is touched.
   *
   * Split from [Screens] because the two are different lists that happen to be
   * adjacent: one is the theme the whole tree is drawn in, the other is the
   * table of screens — and only the second one grows every time a screen
   * lands.
   */
  @Composable
  private fun Wiring(
    settings: AppSettings,
    app: DInfinityApplication,
    repository: SettingsRepository,
    saved: SavedWiring,
  ) {
    // What a finished throw is filed under. `RollRecording` asks for it on the
    // roll thread, which cannot suspend and so cannot read a preference — so
    // the value is pushed here, every time the settings say it has changed.
    LaunchedEffect(settings.activeSessionId) { app.activeSession = settings.activeSessionId }
    // The same arrangement, for the set a plain `d20` comes from. `SetLibrary`
    // reads it while building a catalogue, which is not a place that can
    // collect a flow (`docs/dice-sets.md`).
    LaunchedEffect(settings.defaultSetId) { app.defaultSet = settings.defaultSetId }

    DInfinityApp(
      settings = settings,
      onAccentSelected = { accent ->
        lifecycleScope.launch { repository.setAccentColor(accent) }
      },
      onAppearanceSelected = { appearance ->
        lifecycleScope.launch { repository.setAppearance(appearance) }
      },
      onPowerSavingChanged = { on ->
        lifecycleScope.launch { repository.setPowerSaving(on) }
      },
      onShakeChanged = { on ->
        lifecycleScope.launch { repository.setShakeToRoll(on) }
      },
      onRoundingSelected = { rounding ->
        lifecycleScope.launch { repository.setRounding(rounding) }
      },
      onRepository = { openRepository() },
      version = installedVersion(),
      onWelcomeSeen = { lifecycleScope.launch { repository.setWelcomeSeen() } },
      menuHeader = menuHeader(app, settings),
      screens = ScreenWiring(app, settings, repository, saved, lifecycleScope).presenters(),
      onSource = { url -> open(url) },
    )
  }

  /**
   * What the menu says it is the menu of, and where the rolls are going.
   *
   * The session is looked up by name rather than carried as an id, because the
   * id is what the settings hold and a name is what a person reads. Whether it
   * is worth saying at all is [MenuHeader]'s rule, and it is tested there.
   *
   * Lowercase because it returns a value: Android Lint's `ComposableNaming`
   * asks for that, and it is right — this is a reading, not a piece of screen.
   */
  @Composable
  private fun menuHeader(
    app: DInfinityApplication,
    settings: AppSettings,
  ): MenuHeader {
    val sessions by app.sessions.sessions.collectAsStateWithLifecycle(emptyList())
    return MenuHeader.of(
      appName = Destination.Menu.title,
      activeSession = sessions.firstOrNull { it.id == settings.activeSessionId }?.name,
      sessions = sessions.size,
    )
  }

  private companion object {
    /** Where this came from (`README.md`). */
    val REPOSITORY: Uri = "https://github.com/drehtuer/dInfinityApp".toUri()
  }
}
