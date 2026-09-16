package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.core.notation.DicePicker
import de.drehtuer.dinfinity.data.Session
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.setActiveGroup
import de.drehtuer.dinfinity.data.setActiveSession
import de.drehtuer.dinfinity.data.setDefaultSet
import de.drehtuer.dinfinity.data.setDefaultTable
import de.drehtuer.dinfinity.designer.OpeningDie
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.feature.designer.DesignerPresenter
import de.drehtuer.dinfinity.feature.roll.WhatIsThere
import de.drehtuer.dinfinity.feature.sets.SetDetailPresenter
import de.drehtuer.dinfinity.feature.sets.SetsPresenter
import de.drehtuer.dinfinity.feature.settings.DeveloperPresenter
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.SavedStatsPresenter
import de.drehtuer.dinfinity.feature.stats.SessionsPresenter
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
import de.drehtuer.dinfinity.feature.tables.TablesPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  /**
   * Where a settings write is launched.
   *
   * A plain [CoroutineScope] rather than the activity's own type: launching is
   * all this class does with it, and the narrower type was the only thing
   * keeping the app's real wiring out of a test.
   */
  private val scope: CoroutineScope,
) {
  fun presenters(): Presenters =
    Presenters(
      roll = {
        app.rolls.presenter(
          powerSaving = settings.powerSaving,
          rounding = settings.rounding,
          haptics = settings.haptics,
          sound = settings.sound,
          developerTools = settings.developerTools,
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
      savedRollEditor = { opening -> saved.editor(opening, settings.activeGroupId) },
      collectionImport = saved::importing,
      history = { past() },
      statistics = {
        StatsPresenter(
          statistics = app.dieStatistics,
          writer = app.statistics,
          catalog = app.setLibrary.catalogue,
          scope = scope,
          sessions = app.sessions,
        )
      },
      sessions = { sessions() },
      savedStatistics = { savedStatistics() },
      diceSets = { diceSets() },
      tables = { tables() },
      faceDesigner = { die -> faceDesigner(die) },
      developer = { developer() },
      diceSet = { id, onGone -> diceSet(id, onGone) },
      whatIsThere = whatIsThere(app.savedRolls.all, app.sessions.sessions),
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

  /**
   * Drawing the faces of a die (`docs/face-designer.md`).
   *
   * Opened on [wanted] when quick mode named a die — "Doodle this die", off a
   * long press in the breakdown — and otherwise on the default set's **d6**.
   * Which die that comes to is `designer`'s [OpeningDie], not a rule written
   * out here: it has cases in it, and a case in a wiring function is a case no
   * unit test reaches.
   *
   * Whichever die it opens on, it opens on that die's own draft — so somebody
   * who was drawing a d20 yesterday is one tap from it rather than back at a
   * blank d6.
   */
  private fun faceDesigner(wanted: String): DesignerPresenter {
    val catalogue = app.setLibrary.catalogue
    // Every die of every usable set, so somebody else's d18 can be drawn on as
    // readily as the bundled d6 (`docs/face-designer.md`, "Flow"). Two sets
    // may both define a `d20`, so the chooser is built from distinct ids:
    // a row with the same name on it twice is a row nobody can choose from.
    val everything = catalogue.installed.flatMap { it.dice }.distinctBy { it.id }
    val fromDefault = catalogue.set(catalogue.defaultSetId)?.dice.orEmpty()
    return DesignerPresenter(
      // Null only when nothing at all is installed, which no install is: the
      // bundled set cannot be removed (`docs/dice-sets.md`).
      die = OpeningDie.of(choosable = everything, fromDefaultSet = fromDefault, wanted = wanted) ?: everything.first(),
      choosable = everything,
      // So a drawing outlives the screen it was made on, and each die keeps
      // its own (`docs/face-designer.md`, "Drawing tools").
      drafts = app.drafts,
      notationOf = { die -> spellingOf(die, catalogue) },
    )
  }

  /**
   * Which table the dice are thrown onto (`docs/tables.md`).
   *
   * Every look from every usable package, which is what the catalogue already
   * holds — a dice set never brings its own table along, so there is no
   * per-set filtering to do here.
   */
  private fun tables() =
    TablesPresenter(
      sets = { app.setLibrary.catalogue.installed },
      chosen = settings.defaultTable,
      onChosen = { pin -> scope.launch { repository.setDefaultTable(pin) } },
      scope = scope,
      // The Android half of "use a photo": a decoder, the personal package and
      // the catalogue that has to be re-read once one lands (`TablePhotoLibrary`).
      photos = app.tablePhotos,
    )

  /**
   * The debugging tools (`docs/physics-and-rendering.md`, "Debug tooling").
   *
   * The replay is handed in as a function rather than a simulator, so this
   * screen cannot open a physics world and cannot run one on the thread
   * Compose draws on. `Dispatchers.Default` because a replay is a solver
   * running flat out for up to twelve simulated seconds, and that is not work
   * for the main thread (`docs/architecture.md`, "Threading").
   */
  private fun developer() =
    DeveloperPresenter(
      log = app.developerLog,
      throwAgain = { spec -> withContext(Dispatchers.Default) { app.rolls.replay(spec) } },
      scope = scope,
    )

  /** What is installed, and what may be done to it (`docs/dice-sets.md`). */
  private fun diceSets() =
    SetsPresenter(
      library = app.setLibrary,
      scope = scope,
      // The platform half of a link: a cache directory and an HTTP client,
      // neither of which a screen that lists dice sets should have to carry.
      download = PackageDownload(app.cacheDir)::fetch,
      // Which of the two questions a set needs is decided by what its install
      // recorded, not by reading its link again (`docs/dice-sets.md`).
      latestCommit = { source, commit ->
        latestOf(source, commit, commits = CommitLookup()::latest, stamps = ArchiveLookup()::latest)
      },
    )

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
    // The zip leaves the app the same way an exported collection does: the
    // share sheet, over the FileProvider, out of a cache directory emptied
    // first (`PackageSharing`).
    onShare = { file -> PackageSharing.share(app, file) },
  )
}

/**
 * How [die] is written in a formula, or null when notation cannot name it.
 *
 * Through the same picker the roll screen's row uses, so **Roll it** and a tap
 * on the row write the same thing. A set's own `skull-d6` has no spelling a
 * formula could carry and comes back null (`docs/architecture.md`,
 * decision 31).
 *
 * **Which set to name is decided by what would resolve**, not by where the die
 * came from — because the designer's row has no answer to "where from": it
 * lists dice by id across every installed set, and two sets may both define a
 * `d20`. So: a bare `1d20` when the set a plain `d20` already means has one,
 * and `brass:1d18` when it does not and `brass` does. A bare `1d18` in the
 * second case would be a formula that refuses to resolve, which is a worse
 * answer to "roll this" than no button at all.
 */
internal fun spellingOf(
  die: Die,
  catalogue: DiceCatalog,
): String? {
  val default = catalogue.set(catalogue.defaultSetId)?.takeIf { it.die(die.id) != null }
  val set = default ?: catalogue.installed.firstOrNull { it.die(die.id) != null } ?: return null
  return DicePicker
    .offeredBy(set, setRef = set.id.takeIf { default == null })
    .firstOrNull { it.notation == die.id }
    ?.notation(1)
}

/**
 * What a fresh install already has, for the first-launch count line
 * (`design/dInfinity.dc.html`, option 9a).
 *
 * The two counts the welcome's own module cannot know. **Combined rather than
 * collected apart**, so the line is never drawn from one new number and one
 * old one — the same reason the saved-rolls list combines its two flows.
 *
 * It takes the flows rather than the repositories, which is what lets it be
 * tested at all: `ScreenWiring` needs a real `Application` and so has never
 * been under test, and a rule that lives only in there is a rule nobody
 * checks. It is also why nothing here *creates* anything — a sessions
 * presenter would make the default session as a side effect, and saying hello
 * is not a reason to write to a database.
 */
internal fun whatIsThere(
  savedRolls: Flow<List<SavedRoll>>,
  sessions: Flow<List<Session>>,
): Flow<WhatIsThere> =
  combine(savedRolls, sessions) { rolls, all ->
    WhatIsThere(savedRolls = rolls.size, sessions = all.size)
  }
