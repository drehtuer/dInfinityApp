package de.drehtuer.dinfinity

import android.content.Context
import android.view.Surface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.CollectionImporter
import de.drehtuer.dinfinity.data.DieStatisticsRepository
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.SavedRollGroupRepository
import de.drehtuer.dinfinity.data.SavedRollLibrary
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.SessionRepository
import de.drehtuer.dinfinity.data.StatisticsRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.designer.OpeningDie
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.feature.designer.DesignerPresenter
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.roll.Outside
import de.drehtuer.dinfinity.feature.roll.RollMachine
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.roll.ThrowRecorder
import de.drehtuer.dinfinity.feature.roll.WhatIsThere
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import de.drehtuer.dinfinity.feature.saved.GroupPresenter
import de.drehtuer.dinfinity.feature.saved.ImportPresenter
import de.drehtuer.dinfinity.feature.saved.SavedPresenter
import de.drehtuer.dinfinity.feature.sets.SetDetailPresenter
import de.drehtuer.dinfinity.feature.sets.SetLibrary
import de.drehtuer.dinfinity.feature.sets.SetsPresenter
import de.drehtuer.dinfinity.feature.settings.DeveloperPresenter
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.SavedStatsPresenter
import de.drehtuer.dinfinity.feature.stats.SessionsPresenter
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
import de.drehtuer.dinfinity.feature.tables.TablesPresenter
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DeveloperNotes
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import java.io.File
import java.nio.file.Files

/**
 * Every screen, built over a real database, for a test that wants the whole
 * app rather than the navigation graph on its own.
 *
 * It is a full [Presenters] deliberately. Half a set is how the sessions
 * screen went missing for a whole step: `MainActivity` never passed its
 * presenter and the test that should have noticed was itself only wiring the
 * screens somebody had remembered. A factory that cannot be partial is the
 * point of the container, and a test that used a partial one would be back
 * where it started.
 *
 * The tray is the one thing faked. A Robolectric test has no GPU and no
 * physics engine, so the roll screen is given a [Tray] that is asked for
 * nothing — enough to *draw*, which is what "the menu reaches a real screen"
 * means, and not a pretence that dice were rolled.
 */
internal fun testPresenters(
  database: DInfinityDatabase,
  scope: CoroutineScope,
  library: SetLibrary,
  catalog: DiceCatalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
  recorder: ThrowRecorder = ThrowRecorder.NONE,
): Presenters {
  val saved = SavedRollLibrary(SavedRollRepository(database), SavedRollGroupRepository(database))
  return Presenters(
    roll = { rollPresenter(catalog, recorder) },
    graph = { GraphMachine(catalog) },
    savedRolls = { SavedPresenter(library = saved, catalog = catalog, scope = scope, unfiledName = UNFILED) },
    savedRollEditor = { opening ->
      EditorPresenter(library = saved, catalog = catalog, scope = scope, opening = opening)
    },
    savedGroups = { GroupPresenter(saved, catalog, scope, UNFILED) },
    collectionImport = {
      ImportPresenter(
        importer = CollectionImporter(database),
        catalog = catalog,
        scope = scope,
        unfiledName = UNFILED,
      )
    },
    history = { HistoryPresenter(history = HistoryRepository(database), scope = scope) },
    statistics = { statsPresenter(database, catalog, scope) },
    sessions = {
      SessionsPresenter(repository = SessionRepository(database), scope = scope, defaultName = "First rolls")
    },
    savedStatistics = {
      SavedStatsPresenter(
        saved = saved.rolls,
        history = HistoryRepository(database),
        catalog = catalog,
        scope = scope,
        groupId = SavedRollGroup.UNFILED_ID,
      )
    },
    diceSets = { SetsPresenter(library, scope) },
    // No photo library: a navigation test has no decoder and no personal
    // package, and "use a photo" is then absent rather than present and dead.
    tables = { TablesPresenter(sets = { catalog.installed }, chosen = null, onChosen = {}, scope = scope) },
    faceDesigner = { die -> designerPresenter(catalog, die) },
    developer = { developerPresenter(scope) },
    // Nothing is saved and no session exists in a test until one is made, and
    // the welcome's line is the one place that shows. Watched the same way the
    // activity watches it, so a test that imports something sees it change.
    whatIsThere =
      combine(saved.rolls.all, SessionRepository(database).sessions) { rolls, sessions ->
        WhatIsThere(savedRolls = rolls.size, sessions = sessions.size)
      },
    diceSet = { id, onGone ->
      SetDetailPresenter(
        id = id.ifEmpty { BuiltinDiceSet.set.id },
        library = library,
        scope = scope,
        onGone = onGone,
        defaultSetId = { BuiltinDiceSet.set.id },
        onDefault = {},
      )
    },
  )
}

private const val UNFILED = "Unfiled"

/**
 * The tray, with the two things a Robolectric test cannot have faked.
 *
 * There is no GPU and no physics engine, so the tray is asked for nothing and
 * the simulator answers every die with its first face. That is enough for the
 * screen to *draw*, which is all a test about wiring needs, and it is not a
 * pretence that any dice were rolled.
 */
private fun rollPresenter(
  catalog: DiceCatalog,
  recorder: ThrowRecorder = ThrowRecorder.NONE,
) = RollPresenter(
  machine =
    RollMachine(
      catalog = catalog,
      geometry = TableGeometry.referenceDevice(),
      look = { TableLook(id = "plain", name = "Plain") },
      outside = Outside(seeds = { 1L }, clock = { 0L }),
    ),
  driver = SilentTray(),
  rolls = LandingRolls,
  toTheScreen = { it() },
  recorder = recorder,
)

/** A tray that is asked for nothing and answers nothing. */
private class SilentTray : Tray {
  override fun surfaceAvailable(
    surface: Surface,
    width: Int,
    height: Int,
  ) = Unit

  override fun surfaceLost() = Unit

  /**
   * Runs the roll to its end where it stands.
   *
   * The app's own tests have no frame clock, so a tray that only accepted a
   * roll would never finish one — and a throw that cannot finish is a throw
   * whose *recording* cannot be tested, which is exactly the thing that was
   * broken between two modules (`docs/statistics.md`).
   */
  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onCounted: (Map<Int, Int>) -> Unit,
    onStalled: (List<Int>) -> Unit,
    onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
  ) {
    val live = start(HeadlessRenderer())
    while (live.running) live.advance(SettleRule.TIMESTEP_SECONDS)
    live.outcome?.let { onSettled(it, live.drivenBy) }
    live.close()
  }

  override fun table(
    geometry: TableGeometry,
    look: TableLook,
  ) = Unit

  override fun shake(sample: ShakeSample) = Unit

  override fun look(view: TrayView) = Unit

  override fun clear() = Unit

  override fun close() = Unit
}

/**
 * A database, a scope and a set library, for a test that wants the whole app.
 *
 * Here rather than repeated in each test because the interesting part is the
 * [Presenters] it builds, and three tests each assembling their own fixture is
 * three places for one of them to quietly assemble less than the others —
 * which is the shape of the bug the container exists to stop.
 */
internal class TestApp : AutoCloseable {
  val database: DInfinityDatabase =
    Room
      .inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        DInfinityDatabase::class.java,
      ).allowMainThreadQueries()
      // Queries and invalidation on the calling thread, so a `Flow` from a
      // `@Query` emits when the write happens rather than when a pool thread
      // gets to it. Without it the first wait in a class races Room's own
      // executors, which surfaces as an unrelated test failing now and then.
      .setQueryExecutor(Runnable::run)
      .setTransactionExecutor(Runnable::run)
      .build()

  val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

  private val folder: File = Files.createTempDirectory("dinfinity-test").toFile()

  val library: SetLibrary =
    SetLibrary(
      bundled = BuiltinDiceSet.set,
      installed = InstalledSets(File(folder, "dicesets")),
      registry = InstalledSetRepository(database),
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(File(folder, "dicesets")),
      defaultSetId = { BuiltinDiceSet.set.id },
    )

  /** Every screen, all of them. */
  fun presenters(): Presenters = testPresenters(database, scope, library)

  override fun close() {
    scope.cancel()
    database.close()
    folder.deleteRecursively()
  }
}

/**
 * A roll that lands the moment it is asked to advance, on face zero.
 *
 * The app's own tests have no physics engine and no renderer, but a throw that
 * cannot finish is a throw whose *recording* cannot be tested either — and
 * whether a throw is recorded as the saved roll it came from is exactly the
 * kind of thing that goes wrong between two modules rather than inside one
 * (`docs/statistics.md`).
 */
private object LandingRolls : Rolls {
  override fun start(
    spec: ThrowSpec,
    watcher: Renderer,
  ): WatchedRoll =
    object : WatchedRoll {
      private var landed = false

      override val running: Boolean get() = !landed

      override val outcome: SimulationOutcome? get() =
        if (landed) SimulationOutcome(faces = spec.dice.indices.associateWith { 0 }) else null

      override val drivenBy: List<ShakeSample> = emptyList()

      override val impacts: List<Impact> = emptyList()

      override fun advance(elapsedSeconds: Double): RenderFrame {
        landed = true
        return RenderFrame.still(
          spec.dice.indices.map { BodyTransform(it, Vector3(0.0, 0.0, 8.0), Quaternion.Identity) },
        )
      }

      override fun shake(sample: ShakeSample) = Unit

      override fun close() = Unit
    }
}

/**
 * The face designer, as the activity's own wiring builds it.
 *
 * Out here because [testPresenters] is at detekt's length limit — and because
 * the one thing worth saying about it is the spelling: it is the *real*
 * `spellingOf`, so a test that walks the graph to **Roll it** walks the answer
 * the app gives rather than a stand-in that agrees with it by luck.
 */
private fun designerPresenter(
  catalog: DiceCatalog,
  wanted: String = "",
) = DesignerPresenter(
  // The same rule the application's wiring follows, so a navigation test asks
  // the question the app asks (`designer`'s `OpeningDie`).
  die =
    OpeningDie.of(choosable = BuiltinDiceSet.set.dice, fromDefaultSet = BuiltinDiceSet.set.dice, wanted = wanted)
      ?: BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube },
  choosable = BuiltinDiceSet.set.dice,
  notationOf = { die -> spellingOf(die, catalog) },
)

/**
 * The debugging tools (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * A log of its own per set of presenters, so a test that turns the toggle on
 * gets an empty one rather than whatever the application has collected.
 */
private fun developerPresenter(scope: CoroutineScope) =
  DeveloperPresenter(
    log = DeveloperNotes(),
    throwAgain = { spec -> SimulationOutcome(faces = spec.dice.indices.associateWith { 0 }) },
    scope = scope,
  )

/** What every die has done, over an in-memory database. */
private fun statsPresenter(
  database: DInfinityDatabase,
  catalog: DiceCatalog,
  scope: CoroutineScope,
) = StatsPresenter(
  statistics = DieStatisticsRepository(database),
  writer = StatisticsRepository(database),
  catalog = catalog,
  scope = scope,
  sessions = SessionRepository(database),
)
