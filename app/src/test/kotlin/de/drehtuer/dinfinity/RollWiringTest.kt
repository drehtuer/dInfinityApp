package de.drehtuer.dinfinity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The parts of the roll screen's wiring that decide something without an
 * engine underneath (`docs/tables.md`, "Selecting a table";
 * `docs/physics-and-rendering.md`, "Power-saving mode").
 *
 * Building one opens nothing: the simulator, the roll thread and the feedback
 * are made when a visit or a picture first asks for them, so everything here
 * runs on a JVM.
 */
@RunWith(RobolectricTestRunner::class)
class RollWiringTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private val felt = TableLook(id = "felt", name = "Felt")
  private val oak = TableLook(id = "oak", name = "Oak")
  private val brass = DiceSet(id = "brass", name = "Brass", version = "1.0.0", tables = listOf(felt, oak))
  private val bundled = BuiltinDiceSet.set.copy(tables = listOf(TableLook(id = "plain", name = "Plain")))

  @Test
  fun `a throw's own pin wins over the table chosen in the picker`() {
    val wiring = wiring(chosen = TablePin("brass", "felt"))

    assertEquals(oak, wiring.table(pinned = TablePin("brass", "oak")))
  }

  @Test
  fun `a throw that pins nothing lands on the table chosen in the picker`() {
    val wiring = wiring(chosen = TablePin("brass", "oak"))

    assertEquals(oak, wiring.table(pinned = null))
  }

  @Test
  fun `with nothing chosen it is the bundled package's first look, which is where a new install starts`() {
    assertEquals(bundled.tables.first(), wiring(chosen = null).table(pinned = null))
  }

  @Test
  fun `a pin naming a package that has gone falls back to the bundled look rather than to no look`() {
    // The setting itself is left alone: the package may be back tomorrow, the
    // rule the default dice set already follows.
    val wiring = wiring(chosen = null)

    assertEquals(bundled.tables.first(), wiring.table(pinned = TablePin("uninstalled", "felt")))
    assertEquals(bundled.tables.first(), wiring.table(pinned = TablePin("brass", "no-such-table")))
  }

  @Test
  fun `with no look installed anywhere the tray still has a table to draw`() {
    val bare = BuiltinDiceSet.set.copy(tables = emptyList())
    val wiring = RollWiring(context = context, catalogue = { DiceCatalog.of(listOf(bare)) })

    val table = wiring.table(pinned = null)

    assertEquals("default", table.id)
    assertEquals(context.getString(R.string.table_look_fallback), table.name)
  }

  @Test
  fun `the choice is read when a throw asks, not when the wiring was built`() {
    // A preference changes while the app runs: choosing a table and going back
    // to the tray has to land on it.
    var chosen: TablePin? = TablePin("brass", "felt")
    val wiring = RollWiring(context = context, catalogue = { catalog() }, chosenTable = { chosen })
    assertEquals(felt, wiring.table(pinned = null))

    chosen = TablePin("brass", "oak")

    assertEquals(oak, wiring.table(pinned = null))
  }

  @Test
  fun `the installed sets are read per visit, so a set installed meanwhile is there`() {
    var sets = listOf(bundled)
    val wiring = RollWiring(context = context, catalogue = { DiceCatalog.of(sets) })
    assertNull(wiring.catalog.set("brass"))

    sets = listOf(bundled, brass)

    assertEquals(brass, wiring.catalog.set("brass"))
  }

  @Test
  fun `power saving draws no table pictures, because it opens no engine`() {
    // Its promise is that no Filament engine is created at all, and a
    // thumbnail would create one on the way to a screen that is not even about
    // rolling (decision 38).
    assertNull(wiring(chosen = null).thumbnails(powerSaving = true, widthPx = 320, heightPx = 200))
  }

  private fun catalog(): DiceCatalog = DiceCatalog.of(listOf(bundled, brass))

  private fun wiring(chosen: TablePin?): RollWiring =
    RollWiring(context = context, catalogue = { catalog() }, chosenTable = { chosen })
}
