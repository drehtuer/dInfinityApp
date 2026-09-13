package de.drehtuer.dinfinity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.setAccentColor
import de.drehtuer.dinfinity.data.setActiveGroup
import de.drehtuer.dinfinity.data.setAppearance
import de.drehtuer.dinfinity.data.setPowerSaving
import de.drehtuer.dinfinity.data.setRounding
import de.drehtuer.dinfinity.data.setShakeToRoll
import de.drehtuer.dinfinity.data.setWelcomeSeen
import de.drehtuer.dinfinity.feature.saved.R
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
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

  /** Opens the project's page in a browser. The app's only outward link. */
  private fun openRepository() {
    runCatching {
      startActivity(Intent(Intent.ACTION_VIEW, REPOSITORY))
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val app = application as DInfinityApplication
    val repository = app.settingsRepository
    val saved =
      SavedWiring(
        app = app,
        catalog = app.rolls.catalog,
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
        rollPresenter = {
          app.rolls.presenter(
            powerSaving = settings.powerSaving,
            rounding = settings.rounding,
            scope = lifecycleScope,
          )
        },
        graphMachine = { app.rolls.graph() },
        savedRolls = {
          saved.list(activeGroupId = settings.activeGroupId) { groupId ->
            lifecycleScope.launch { repository.setActiveGroup(groupId) }
          }
        },
        savedGroups = saved::groups,
        savedRollEditor = { editing -> saved.editor(editing, settings.activeGroupId) },
        collectionImport = saved::importing,
        history = { HistoryPresenter(history = app.history, scope = lifecycleScope) },
        statistics = {
          StatsPresenter(
            statistics = app.dieStatistics,
            writer = app.statistics,
            catalog = app.rolls.catalog,
            scope = lifecycleScope,
          )
        },
      )
    }
  }

  private companion object {
    /** Where this came from (`README.md`). */
    val REPOSITORY: Uri = "https://github.com/drehtuer/dInfinityApp".toUri()
  }
}
