package de.drehtuer.dinfinity.feature.tables

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The table picker (`design/dInfinity.dc.html`, option `1u`).
 *
 * The swatch is not asserted on: it is two colours in a box, and a test that
 * pinned them would be a test of `#1f5e3a`. What is asserted is which rows are
 * there, which one is marked, and that tapping one says so.
 */
@RunWith(RobolectricTestRunner::class)
class TablesScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `every installed look is a row`() {
    show()

    compose.onNodeWithTag(TablesTestTags.LIST).assertIsDisplayed()
    BuiltinDiceSet.set.tables.forEach { look ->
      compose.onNodeWithText(look.name).assertIsDisplayed()
    }
  }

  @Test
  fun `the one in use is marked`() {
    val oak = TablePin(BuiltinDiceSet.set.id, "oak")
    show(chosen = oak)

    compose.onNodeWithTag(TablesTestTags.chosenOf(oak), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `tapping a look chooses it`() {
    val remembered = mutableListOf<TablePin>()
    val presenter = show(onChosen = remembered::add)
    val glass = TablePin(BuiltinDiceSet.set.id, "dark-glass")

    compose.onNodeWithTag(TablesTestTags.tableOf(glass)).performClick()

    assertEquals(listOf(glass), remembered)
    assertEquals(glass, presenter.state.chosen)
  }

  @Test
  fun `with one package the set is not named on every row`() {
    // "Built-in dice" five times over says nothing.
    show()

    compose.onNodeWithText(BuiltinDiceSet.set.name).assertDoesNotExist()
  }

  @Test
  fun `with two packages it is, because a name alone no longer says which`() {
    // Once per row from that package, which is the point — the name is beside
    // each look rather than heading a section.
    show(sets = listOf(BuiltinDiceSet.set, brass))

    // "At least one" rather than a count: a `LazyColumn` composes the rows it
    // can see, so counting them would be asserting the height of the screen.
    assertTrue(
      "the package is not named on any row",
      compose.onAllNodesWithText(BuiltinDiceSet.set.name).fetchSemanticsNodes().isNotEmpty(),
    )
  }

  @Test
  fun `no looks at all says so rather than showing an empty list`() {
    show(sets = listOf(BuiltinDiceSet.set.copy(tables = emptyList())))

    compose.onNodeWithTag(TablesTestTags.EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(TablesTestTags.LIST).assertDoesNotExist()
  }

  private fun show(
    sets: List<DiceSet> = listOf(BuiltinDiceSet.set),
    chosen: TablePin? = null,
    onChosen: (TablePin) -> Unit = {},
  ): TablesPresenter {
    val presenter = TablesPresenter(sets = { sets }, chosen = chosen, onChosen = onChosen)
    compose.setContent { TablesScreen(presenter = presenter) }
    return presenter
  }

  private val brass =
    BuiltinDiceSet.set.copy(
      id = "brass",
      name = "Brass & Bone",
      tables = listOf(TableLook(id = "brass", name = "Brass")),
    )
}
