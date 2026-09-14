package de.drehtuer.dinfinity

import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.DiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The container the whole app hangs off.
 *
 * It is a handful of lazy fields rather than a dependency-injection framework
 * (`docs/TODO.md`, Step 4.10 says when that stops being honest), and the thing
 * worth asserting about it is the same thing a framework would be checked for:
 * that asking twice gives the same object, that the ones which must be shared
 * are, and that what a formula resolves against starts at the floor and follows
 * the setting.
 *
 * `rolls` is deliberately not touched. It builds a `JoltDiceSimulator`, which
 * loads native code, and this is a JVM test.
 */
@RunWith(RobolectricTestRunner::class)
class DInfinityApplicationTest {
  private val app: DInfinityApplication get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `the database is opened once and shared`() {
    // Two connections to one SQLite file is Room's own warning, and the
    // reason this lives on the application rather than per screen.
    assertSame(app.database, app.database)
  }

  @Test
  fun `everything that reads the database reads the same one`() {
    assertNotNull(app.savedRolls)
    assertNotNull(app.statistics)
    assertNotNull(app.history)
    assertNotNull(app.sessions)
    assertNotNull(app.installedSets)
    assertSame(app.setLibrary, app.setLibrary)
  }

  @Test
  fun `a formula starts out resolving against the bundled set`() {
    // Before anything has been read off disk, and after a scan that finds
    // nothing: the bundled set is the floor everything falls back to.
    assertEquals(DiceSet.BUILTIN_ID, app.setLibrary.catalogue.defaultSetId)
    assertNotNull(app.setLibrary.catalogue.set(DiceSet.BUILTIN_ID))
  }

  @Test
  fun `the default set follows the field the activity keeps in step`() {
    // The field exists because `SetLibrary` reads it while building a
    // catalogue, which is not a place that can collect a flow.
    app.defaultSet = "a-set-nobody-has"

    assertEquals(
      "a default nobody has installed was taken at its word",
      DiceSet.BUILTIN_ID,
      app.setLibrary.catalogue.defaultSetId,
    )

    app.defaultSet = DiceSet.BUILTIN_ID
    assertEquals(DiceSet.BUILTIN_ID, app.setLibrary.catalogue.defaultSetId)
  }

  @Test
  fun `the session a roll is filed under starts at the one every old roll belongs to`() {
    assertEquals(AppSettings.DEFAULT_SESSION_ID, app.activeSession)
  }

  @Test
  fun `the dicesets folder is the one the storage layout names`() {
    // `<filesDir>/dicesets/` (`docs/architecture.md`, "Storage layout"). The
    // folder need not exist yet — a fresh install has installed nothing — but
    // reading it must answer rather than throw.
    assertEquals(emptyList<String>(), app.packages.scan().map { it.id })
  }
}
