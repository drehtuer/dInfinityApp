package de.drehtuer.dinfinity

import androidx.lifecycle.LifecycleCoroutineScope
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.setActiveGroup
import de.drehtuer.dinfinity.data.setActiveSession
import de.drehtuer.dinfinity.data.setDefaultSet
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.feature.sets.SetDetailPresenter
import de.drehtuer.dinfinity.feature.sets.SetsPresenter
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.SavedStatsPresenter
import de.drehtuer.dinfinity.feature.stats.SessionsPresenter
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
import kotlinx.coroutines.launch
import de.drehtuer.dinfinity.feature.stats.R as StatsR

/**
 * How this application builds every screen (`docs/TODO.md`, 4.10).
 *
 * Apart from `MainActivity` because it is a different list: the activity is
 * about being an activity — a window, a theme, a link to the repository — and
 * this is about which presenter belongs to which destination. That list grows
 * every time a screen lands, and it had already pushed the activity past
 * detekt's function ceiling twice before it moved out here.
 *
 * It holds the settings it was built with rather than taking them per call,
 * because a set of presenters belongs to one reading of them: everything in
 * here is rebuilt when they change.
 */
internal class ScreenWiring(
  private val app: DInfinityApplication,
  private val settings: AppSettings,
  private val repository: SettingsRepository,
  private val saved: SavedWiring,
  private val scope: LifecycleCoroutineScope,
) {
  fun presenters(): Presenters =
    Presenters(
      roll = {
        app.rolls.presenter(
          powerSaving = settings.powerSaving,
          rounding = settings.rounding,
          scope = scope,
        )
      },
      graph = { app.rolls.graph() },
      savedRolls = {
        saved.list(activeGroupId = settings.activeGroupId) { groupId ->
          scope.launch { repository.setActiveGroup(groupId) }
        }
      },
      savedGroups = saved::groups,
      savedRollEditor = { editing -> saved.editor(editing, settings.activeGroupId) },
      collectionImport = saved::importing,
      history = { past() },
      statistics = {
        StatsPresenter(
          statistics = app.dieStatistics,
          writer = app.statistics,
          catalog = app.setLibrary.catalogue,
          scope = scope,
        )
      },
      sessions = { sessions() },
      savedStatistics = { savedStatistics() },
      diceSets = { diceSets() },
      diceSet = { id, onGone -> diceSet(id, onGone) },
    )

  /**
   * The buckets rolls are filed into.
   *
   * Which one is active lives in the settings rather than on this screen,
   * because the roll screen and the history both read it
   * (`docs/statistics.md`).
   */
  private fun sessions() =
    SessionsPresenter(
      repository = app.sessions,
      scope = scope,
      defaultName = app.getString(StatsR.string.sessions_first),
      activeId = settings.activeSessionId,
      onActive = { session -> scope.launch { repository.setActiveSession(session) } },
    )

  /**
   * Past rolls, and what the chooser may filter them by.
   *
   * The sessions and the saved rolls are given rather than reached for,
   * because which of each exist is the application's to know
   * (`docs/statistics.md`).
   */
  private fun past() =
    HistoryPresenter(
      history = app.history,
      scope = scope,
      sessions = app.sessions,
      saved = app.savedRolls,
    )

  /**
   * What each saved roll has come to, against what it should
   * (`docs/statistics.md`; design options `8b` and `9e`).
   *
   * About the active group, because a saved roll belongs to one and the roll
   * somebody wants is one they have been using.
   */
  private fun savedStatistics() =
    SavedStatsPresenter(
      saved = app.savedRolls,
      history = app.history,
      catalog = app.setLibrary.catalogue,
      scope = scope,
      groupId = settings.activeGroupId,
      rounding = settings.rounding,
    )

  /** What is installed, and what may be done to it (`docs/dice-sets.md`). */
  private fun diceSets() = SetsPresenter(app.setLibrary, scope)

  /** One of them, in detail (`design/dInfinity.dc.html`, options `6a` and `6b`). */
  private fun diceSet(
    id: String,
    onGone: () -> Unit,
  ) = SetDetailPresenter(
    id = id.ifEmpty { BuiltinDiceSet.set.id },
    library = app.setLibrary,
    scope = scope,
    onGone = onGone,
    defaultSetId = { app.defaultSet },
    onDefault = { setId -> scope.launch { repository.setDefaultSet(setId) } },
  )
}
