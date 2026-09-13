package de.drehtuer.dinfinity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.drehtuer.dinfinity.core.model.AppSettings
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
    setContent {
      // Until the file has been read the defaults stand in, which is one frame
      // of the design's own accent rather than a blank screen.
      val settings by repository.settings.collectAsStateWithLifecycle(AppSettings())
      DInfinityTheme(accent = settings.accentColor) {
        DInfinityApp(
          settings = settings,
          onAccentSelected = { accent ->
            lifecycleScope.launch { repository.setAccentColor(accent) }
          },
          onPowerSavingChanged = { on ->
            lifecycleScope.launch { repository.setPowerSaving(on) }
          },
          onWelcomeSeen = { lifecycleScope.launch { repository.setWelcomeSeen() } },
          rollPresenter = { app.rolls.presenter(powerSaving = settings.powerSaving, scope = lifecycleScope) },
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
  }
}
