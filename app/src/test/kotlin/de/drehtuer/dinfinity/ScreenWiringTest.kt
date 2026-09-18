package de.drehtuer.dinfinity

import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.data.FinishedRoll
import de.drehtuer.dinfinity.data.RollContext
import de.drehtuer.dinfinity.feature.saved.Editing
import de.drehtuer.dinfinity.feature.saved.EditorPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * That the wiring the app actually uses builds every screen
 * (`docs/TODO.md`, 4.10).
 *
 * `Presenters` makes a *missing* screen a compile error, and
 * `DInfinityScreensTest` proves every menu destination draws — but that test
 * builds its presenters itself, so a `ScreenWiring` that handed back the wrong
 * thing, or threw on the way, would pass both. This is the one place the real
 * factory is run.
 *
 * It calls each one rather than only constructing the container, because the
 * fields are lambdas: building `Presenters` proves nothing about what happens
 * when one of them is invoked.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenWiringTest {
  private val app: DInfinityApplication get() = ApplicationProvider.getApplicationContext()
  private val scope = CoroutineScope(Dispatchers.Unconfined)

  @After
  fun close() = scope.cancel()

  @Test
  fun `every screen the app wires can actually be built`() {
    val presenters = wiring().presenters()

    assertNotNull("the tray", presenters.roll())
    assertNotNull("the outcome graph", presenters.graph())
    assertNotNull("the saved-roll list", presenters.savedRolls())
    assertNotNull("the group sheet", presenters.savedGroups())
    assertNotNull("a collection arriving", presenters.collectionImport())
    assertNotNull("the history", presenters.history())
    assertNotNull("the statistics", presenters.statistics())
    assertNotNull("the sessions", presenters.sessions())
    assertNotNull("the saved-roll statistics", presenters.savedStatistics())
    assertNotNull("the dice sets", presenters.diceSets())
    assertNotNull("the table picker", presenters.tables())
    assertNotNull("the face designer", presenters.faceDesigner(""))
  }

  @Test
  fun `the designer opens on a die somebody would call a die`() {
    // The set's first die is the d2. Opening a drawing app on a coin is a poor
    // answer to "draw a die", so the d6 is picked out by shape.
    val designer = wiring().presenters().faceDesigner("")

    assertEquals(de.drehtuer.dinfinity.core.model.DieShape.Cube, designer.state.draft.die.shape)
  }

  @Test
  fun `the designer opens on the die quick mode named`() {
    // "Doodle this die" carries an id and nothing else, and the screen it
    // opens is the same screen (`docs/face-designer.md`, "Quick mode").
    val designer = wiring().presenters().faceDesigner("d20")

    assertEquals("d20", designer.state.draft.die.id)
  }

  @Test
  fun `the designer opens on the usual die when the named one is gone`() {
    // A package can be removed while a result it threw is still on the tray.
    val designer = wiring().presenters().faceDesigner("brass-d12")

    assertEquals(de.drehtuer.dinfinity.core.model.DieShape.Cube, designer.state.draft.die.shape)
  }

  @Test
  fun `the editor is built on a new roll and on one that exists`() {
    // Two cases in one lambda, and the one that reads a roll out of the
    // database is the one that can go wrong on its own.
    val presenters = wiring().presenters()

    assertNotNull(
      presenters.savedRollEditor(
        de.drehtuer.dinfinity.feature.saved.Editing
          .New("2d6"),
      ),
    )
    assertNotNull(
      presenters.savedRollEditor(
        de.drehtuer.dinfinity.feature.saved.Editing
          .Existing("fireball"),
      ),
    )
  }

  @Test
  fun `a new roll with nothing to start from opens on the last formula thrown`() {
    // The one place the real read runs. `EditorPresenter` is handed a lambda
    // and tested against a fake one; that a lambda reaching the history is
    // what the app actually passes is only true here.
    runBlocking {
      app.statistics.record(thrown("4d6dl1"))

      val editor = wiring().presenters().savedRollEditor(Editing.New())

      assertEquals("4d6dl1", editor.loaded().formula)
    }
  }

  @Test
  fun `nothing thrown yet leaves the editor blank rather than guessing`() {
    // A fresh install has no history, and `recent(1)` on an empty table is an
    // empty list rather than a failure.
    runBlocking {
      val editor = wiring().presenters().savedRollEditor(Editing.New())

      assertEquals("", editor.loaded().formula)
    }
  }

  /** The smallest roll the history will take: a formula, a total and a time. */
  private fun thrown(formula: String) =
    FinishedRoll(
      result = RollResult(formula = formula, total = 13, rolledAtEpochMs = 1_000),
      dice = emptyMap(),
      context = RollContext(sessionId = "tuesday"),
    )

  /**
   * The editor's state once its own launch has finished.
   *
   * It reads the groups, the roll and the history off the database, which
   * answers on its own executor — imperceptible on a phone, and this in a
   * test.
   */
  private suspend fun EditorPresenter.loaded() =
    withTimeout(PATIENCE) {
      while (!state.loaded) delay(POLL)
      state
    }

  @Test
  fun `a set's details are built for a set that is installed and for one that is not`() {
    // The id comes out of a route, so it can name anything at all.
    val presenters = wiring().presenters()

    assertNotNull(presenters.diceSet("builtin") {})
    assertNotNull(presenters.diceSet("never-installed") {})
  }

  @Test
  fun `a table already chosen is carried into the picker`() {
    // The settings the wiring was built with are the ones a screen opens on,
    // which is the half a compile error cannot check.
    val chosen =
      de.drehtuer.dinfinity.core.model
        .TablePin("builtin", "oak")

    val picker = wiring(AppSettings(defaultTable = chosen)).presenters().tables()

    assertNotNull(picker.state.chosen)
  }

  @Test
  fun `the roll screen is built with the lean the settings were read at`() {
    // The setting is read when the screen opens rather than watched, so this
    // wiring is where it is read — both positions have to build a tray
    // (`docs/architecture.md`, decision 16).
    TableView.entries.forEach { view ->
      assertNotNull("the tray, looking $view", wiring(AppSettings(tableView = view)).presenters().roll())
    }
  }

  private companion object {
    const val UNFILED = "Unfiled"

    /** How long a database answer is waited for before the test gives up. */
    const val PATIENCE = 2_000L
    const val POLL = 5L
  }

  private fun wiring(settings: AppSettings = AppSettings()) =
    ScreenWiring(
      app = app,
      settings = settings,
      repository = app.settingsRepository,
      saved = SavedWiring(app, app.setLibrary.catalogue, scope, UNFILED),
      scope = scope,
    )
}
