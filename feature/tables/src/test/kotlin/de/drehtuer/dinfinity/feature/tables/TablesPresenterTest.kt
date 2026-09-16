package de.drehtuer.dinfinity.feature.tables

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which table the dice are thrown onto (`docs/tables.md`, "Selecting a table";
 * design option `1u`).
 *
 * The rule worth testing is that **tables are global**: a look is offered
 * whatever dice set shipped it, and a set never brings its own along. The rest
 * is about a choice outliving the package that supplied it, which is the one
 * way this screen can be asked about something that is not there.
 */
class TablesPresenterTest {
  @Test
  fun `every look from every installed package is offered`() {
    // Not the default set's, not the set a formula names — all of them. A roll
    // that mixes two sets happens on the one table that was picked.
    val presenter = presenter(sets = listOf(BuiltinDiceSet.set, brass))

    val names = presenter.state.tables.map { it.look.name }

    assertTrue("the bundled looks are missing", names.containsAll(BuiltinDiceSet.set.tables.map(TableLook::name)))
    assertTrue("another package's look is missing", "Brass" in names)
  }

  @Test
  fun `two packages may both ship a green felt, and they stay apart`() {
    // Ids are unique only within a package, which is why a pin is a pair.
    val other = brass.copy(id = "other", name = "Other", tables = listOf(felt("green-felt", "Green felt")))
    val one = brass.copy(id = "one", name = "One", tables = listOf(felt("green-felt", "Green felt")))

    val presenter = presenter(sets = listOf(one, other))

    assertEquals(2, presenter.state.tables.size)
    assertEquals(
      listOf(TablePin("one", "green-felt"), TablePin("other", "green-felt")),
      presenter.state.tables.map { it.pin },
    )
  }

  @Test
  fun `choosing one remembers it, and says so`() {
    val remembered = mutableListOf<TablePin>()
    val presenter = presenter(onChosen = remembered::add)
    val oak = TablePin(BuiltinDiceSet.set.id, "oak")

    presenter.choose(oak)

    assertEquals(oak, presenter.state.chosen)
    assertEquals(listOf(oak), remembered)
  }

  @Test
  fun `with nothing chosen the first look is the one in use`() {
    // What a new install rolls on, and what the screen should therefore show
    // as chosen rather than showing nothing chosen at all.
    val presenter = presenter(chosen = null)

    assertEquals(
      presenter.state.tables
        .first()
        .pin,
      presenter.state.chosen,
    )
  }

  @Test
  fun `a choice whose package is gone shows the one the tray would really use`() {
    // The setting is left alone — the package may be re-installed tomorrow —
    // but the screen must not tick a row that is not there.
    val presenter = presenter(chosen = TablePin("uninstalled", "green-felt"))

    assertEquals(
      presenter.state.tables
        .first()
        .pin,
      presenter.state.chosen,
    )
  }

  @Test
  fun `a look that is not on the list cannot be chosen`() {
    val remembered = mutableListOf<TablePin>()
    val presenter = presenter(onChosen = remembered::add)
    val before = presenter.state.chosen

    presenter.choose(TablePin("uninstalled", "green-felt"))

    assertEquals("the chosen table moved to one that is not installed", before, presenter.state.chosen)
    assertTrue("a table that is not installed was written to the settings", remembered.isEmpty())
  }

  @Test
  fun `the set is worth naming only when more than one package supplies a table`() {
    assertFalse(presenter(sets = listOf(BuiltinDiceSet.set)).state.manyPackages)
    assertTrue(presenter(sets = listOf(BuiltinDiceSet.set, brass)).state.manyPackages)
  }

  @Test
  fun `no tables at all is a state rather than an empty list nobody explains`() {
    // It should not happen: the bundled package ships five. It can, because a
    // package that stopped validating takes its tables with it.
    val presenter = presenter(sets = listOf(BuiltinDiceSet.set.copy(tables = emptyList())))

    assertTrue(presenter.state.empty)
    assertEquals(null, presenter.state.chosen)
  }

  private fun presenter(
    sets: List<DiceSet> = listOf(BuiltinDiceSet.set),
    chosen: TablePin? = null,
    onChosen: (TablePin) -> Unit = {},
    photos: TablePhotos? = null,
  ) = TablesPresenter(
    sets = { sets },
    chosen = chosen,
    onChosen = onChosen,
    // Unconfined, so a photo that is "added" has landed by the time the next
    // line asserts on it. Nothing here is about which thread anything is on.
    scope = CoroutineScope(Dispatchers.Unconfined),
    photos = photos,
  )

  private fun felt(
    id: String,
    name: String,
  ) = TableLook(id = id, name = name)

  private val brass =
    BuiltinDiceSet.set.copy(id = "brass", name = "Brass & Bone", tables = listOf(felt("brass", "Brass")))
}
