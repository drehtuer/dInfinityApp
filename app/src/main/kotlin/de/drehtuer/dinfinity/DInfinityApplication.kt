package de.drehtuer.dinfinity

import android.app.Application
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.data.CollectionImporter
import de.drehtuer.dinfinity.data.DieStatisticsRepository
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.RollRecording
import de.drehtuer.dinfinity.data.SavedRollGroupRepository
import de.drehtuer.dinfinity.data.SavedRollLibrary
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.SessionRepository
import de.drehtuer.dinfinity.data.SettingsRepository
import de.drehtuer.dinfinity.data.SettingsStorage
import de.drehtuer.dinfinity.data.StatisticsRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.feature.sets.SetLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Holds the few things that outlive an activity.
 *
 * Fields on the Application rather than a dependency-injection framework: a
 * handful of things hang off it, each built once and lazily. The list is
 * growing, and `docs/TODO.md` says when it becomes a real container — the
 * point is a judgement about honesty, not a count.
 */
class DInfinityApplication : Application() {
  val settingsRepository: SettingsRepository by lazy { SettingsStorage.create(this) }

  /**
   * The one database file, opened once.
   *
   * On the application rather than per screen because it is a file: two
   * screens opening it separately would be two connections to the same
   * SQLite database, and Room's own advice is one instance for the process.
   */
  val database: DInfinityDatabase by lazy { DInfinityDatabase.open(this) }

  /** The saved rolls (`docs/dice-notation.md`). */
  val savedRolls: SavedRollRepository by lazy { SavedRollRepository(database) }

  /**
   * The folders they live in.
   *
   * Apart from [savedRolls] rather than beside it: the two grew at different
   * rates and one class had reached the size detekt allows (`docs/TODO.md`,
   * 4.3). A screen that needs both takes both.
   */
  val savedRollGroups: SavedRollGroupRepository by lazy { SavedRollGroupRepository(database) }

  /** The two of them together, which is what a screen about saved rolls takes. */
  val savedRollLibrary: SavedRollLibrary by lazy { SavedRollLibrary(savedRolls, savedRollGroups) }

  /**
   * Taking a collection of saved rolls in.
   *
   * Its own thing rather than a method on [savedRolls], because importing is a
   * transaction with a refusal in front of it rather than a repository
   * operation (`docs/architecture.md`, decision 15).
   */
  val collectionImporter: CollectionImporter by lazy { CollectionImporter(database) }

  /** Statistics and history, written in one transaction per roll. */
  val statistics: StatisticsRepository by lazy { StatisticsRepository(database) }

  /**
   * What turns a finished throw into rows (`docs/statistics.md`).
   *
   * The active session is read per roll rather than captured, because it is a
   * preference and a preference changes while the app is running: a recorder
   * that took it once would file an evening's rolls under whichever session
   * was current when the roll screen opened.
   *
   * And checked against the sessions there actually are, because a preference
   * outlives the thing it names — a session deleted while some other screen
   * was in front would otherwise leave every throw filed under an id that is
   * not there.
   */
  val recording: RollRecording by lazy {
    RollRecording(statistics, sessions = sessions, sessionOf = { activeSession })
  }

  /**
   * The session new rolls are filed under, as last read from the settings.
   *
   * A field written from the activity rather than a flow collected here: the
   * application has no scope of its own to collect on, and the one thing that
   * reads it is called from a thread that cannot suspend.
   */
  @Volatile
  var activeSession: String = AppSettings.DEFAULT_SESSION_ID

  /**
   * The set plain notation resolves against first
   * (`docs/dice-sets.md`, design `6a`).
   *
   * A field for the same reason [activeSession] is one: it is a preference,
   * and what reads it is `SetLibrary` building a catalogue rather than a
   * composable that could collect a flow. The activity keeps it in step.
   */
  @Volatile
  var defaultSet: String = DiceSet.BUILTIN_ID

  /** Past rolls, to read. Apart from the one that writes them, and smaller. */
  val history: HistoryRepository by lazy { HistoryRepository(database) }

  /** What every die has done, to read. */
  val dieStatistics: DieStatisticsRepository by lazy { DieStatisticsRepository(database) }

  /** The buckets statistics are filtered by (`docs/statistics.md`). */
  val sessions: SessionRepository by lazy { SessionRepository(database) }

  /** The roll screen's engine and catalogue, named in one place (`RollWiring`). */
  val rolls: RollWiring by lazy {
    // Both named, and the trailing lambda given up deliberately: `catalogue`
    // used to be last, so `RollWiring(this, recording) { ... }` bound to it.
    // Adding a parameter after it would have moved that lambda silently onto
    // the new one.
    RollWiring(
      context = this,
      recording = recording,
      catalogue = { setLibrary.catalogue },
      chosenTable = { chosenTable },
    )
  }

  /**
   * The table look the player chose, as last read from the settings.
   *
   * A field for the reason [activeSession] and [defaultSet] are fields: it is
   * a preference, and what reads it is `RollWiring` building a tray rather
   * than a composable that could collect a flow. The activity keeps it in
   * step.
   */
  @Volatile
  var chosenTable: TablePin? = null

  /** Which sets the player has switched off (`docs/dice-sets.md`, design `5a`). */
  val installedSets: InstalledSetRepository by lazy { InstalledSetRepository(database) }

  /**
   * The packages on disk (`docs/architecture.md`, "Storage layout").
   *
   * `filesDir/dicesets/` is named here and nowhere else: every module below
   * takes the folder as a parameter, because which directory it is is the one
   * Android-shaped fact about it.
   */
  val packages: InstalledSets by lazy { InstalledSets(File(filesDir, DICE_SETS_FOLDER)) }

  /**
   * The two joined, which is what a screen asks for (`SetLibrary`).
   *
   * The bundled set is handed in here, so `feature/sets` never learns that
   * `dicesets:builtin` exists.
   */
  val setLibrary: SetLibrary by lazy {
    SetLibrary(
      bundled = BuiltinDiceSet.set,
      installed = packages,
      registry = installedSets,
      io = Dispatchers.IO,
      installer = PackageInstaller(File(filesDir, DICE_SETS_FOLDER)),
      defaultSetId = { defaultSet },
    )
  }

  /**
   * Reads what is installed, once, as the process starts.
   *
   * The roll screen is home, so the first formula can be typed a moment after
   * launch — and what a `d20` means depends on which packages are on disk and
   * still validate. Reading it here rather than when the dice-set screen is
   * first opened is the difference between a set that installs and rolls and a
   * set that installs and does nothing until somebody happens to visit a list.
   *
   * On a scope of the application's own, because nothing else here has one and
   * a scan must not hold up `onCreate`. A failure is not fatal: the catalogue
   * starts as the bundled set alone, which is what everything falls back to
   * anyway (`docs/dice-notation.md`).
   */
  override fun onCreate() {
    super.onCreate()
    background.launch { runCatching { setLibrary.all() } }
  }

  /**
   * The designer's drafts, kept under the app's own files
   * (`docs/face-designer.md`, "Drawing tools").
   *
   * On the application because the folder is one thing whichever screen is
   * looking at it, and the writes go to [background] so a stroke is never
   * waiting on a disk.
   */
  val drafts: Drafts by lazy { SavedDrafts(DraftStore(File(filesDir, DraftStore.DIRECTORY)), background) }

  private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private companion object {
    const val DICE_SETS_FOLDER = "dicesets"
  }
}
