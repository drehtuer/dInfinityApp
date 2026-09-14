package de.drehtuer.dinfinity

import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
    assertNotNull("the face designer", presenters.faceDesigner())
  }

  @Test
  fun `the designer opens on a die somebody would call a die`() {
    // The set's first die is the d2. Opening a drawing app on a coin is a poor
    // answer to "draw a die", so the d6 is picked out by shape.
    val designer = wiring().presenters().faceDesigner()

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

  private companion object {
    const val UNFILED = "Unfiled"
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
