package de.drehtuer.dinfinity

import android.content.Context
import android.view.Surface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.CollectionImporter
import de.drehtuer.dinfinity.data.DieStatisticsRepository
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.SessionRepository
import de.drehtuer.dinfinity.data.StatisticsRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.feature.graph.GraphMachine
import de.drehtuer.dinfinity.feature.roll.Outside
import de.drehtuer.dinfinity.feature.roll.RollMachine
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import de.drehtuer.dinfinity.feature.saved.GroupPresenter
import de.drehtuer.dinfinity.feature.saved.ImportPresenter
import de.drehtuer.dinfinity.feature.saved.SavedPresenter
import de.drehtuer.dinfinity.feature.sets.SetDetailPresenter
import de.drehtuer.dinfinity.feature.sets.SetLibrary
import de.drehtuer.dinfinity.feature.sets.SetsPresenter
import de.drehtuer.dinfinity.feature.stats.HistoryPresenter
import de.drehtuer.dinfinity.feature.stats.SavedStatsPresenter
import de.drehtuer.dinfinity.feature.stats.SessionsPresenter
import de.drehtuer.dinfinity.feature.stats.StatsPresenter
import de.drehtuer.dinfinity.feature.tables.TablesPresenter
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
): Presenters {
  val saved = SavedRollRepository(database)
  return Presenters(
    roll = { rollPresenter(catalog) },
    graph = { GraphMachine(catalog) },
    savedRolls = { SavedPresenter(repository = saved, catalog = catalog, scope = scope, unfiledName = UNFILED) },
    savedRollEditor = { opening ->
      EditorPresenter(repository = saved, catalog = catalog, scope = scope, opening = opening)
    },
    savedGroups = { GroupPresenter(saved, scope, UNFILED) },
    collectionImport = {
      ImportPresenter(
        importer = CollectionImporter(database),
        catalog = catalog,
        scope = scope,
        unfiledName = UNFILED,
      )
    },
    history = { HistoryPresenter(history = HistoryRepository(database), scope = scope) },
    statistics = {
      StatsPresenter(
        statistics = DieStatisticsRepository(database),
        writer = StatisticsRepository(database),
        catalog = catalog,
        scope = scope,
      )
    },
    sessions = {
      SessionsPresenter(repository = SessionRepository(database), scope = scope, defaultName = "First rolls")
    },
    savedStatistics = {
      SavedStatsPresenter(
        saved = saved,
        history = HistoryRepository(database),
        catalog = catalog,
        scope = scope,
        groupId = SavedRollGroup.UNFILED_ID,
      )
    },
    diceSets = { SetsPresenter(library, scope) },
    tables = { TablesPresenter(sets = { catalog.installed }, chosen = null, onChosen = {}) },
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
private fun rollPresenter(catalog: DiceCatalog) =
  RollPresenter(
    machine =
      RollMachine(
        catalog = catalog,
        geometry = TableGeometry.referenceDevice(),
        table = TableLook(id = "plain", name = "Plain"),
        simulator =
          object : DiceSimulator {
            override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
          },
        outside = Outside(seeds = { 1L }, clock = { 0L }),
      ),
    driver = SilentTray(),
    rolls = Rolls { _, _ -> error("this test never throws anything") },
    toTheScreen = { it() },
  )

/** A tray that is asked for nothing and answers nothing. */
private class SilentTray : Tray {
  override fun surfaceAvailable(
    surface: Surface,
    width: Int,
    height: Int,
  ) = Unit

  override fun surfaceLost() = Unit

  override fun roll(
    start: (Renderer) -> WatchedRoll,
    onSettled: (SimulationOutcome) -> Unit,
  ) = Unit

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
