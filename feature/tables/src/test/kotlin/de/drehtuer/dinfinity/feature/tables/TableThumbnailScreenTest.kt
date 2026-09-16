package de.drehtuer.dinfinity.feature.tables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The table picker's pictures, on the screen (`docs/tables.md`, "Thumbnails").
 *
 * Three questions and nothing about how a table *looks*, which is a device's
 * and a person's to answer: does a row that has a picture draw it, does a row
 * without one still draw and still work, and does a recomposition that changes
 * nothing leave the list alone. The last is the other side of every skip
 * branch the Compose compiler emits (`docs/TODO.md`, "Coverage").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TableThumbnailScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private val oak = TablePin(BuiltinDiceSet.set.id, "oak")

  @Test
  fun `a row whose picture has arrived draws it`() {
    val source = FakeThumbnails()
    show(source)

    compose.runOnIdle { source.drawAll() }

    compose.onNodeWithTag(TablesTestTags.thumbnailOf(oak), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `a row whose picture has not arrived is still a row`() {
    // Which is every row for the first moment of every visit, and every row
    // for ever on a device that cannot draw one.
    show(FakeThumbnails())

    compose.onNodeWithTag(TablesTestTags.thumbnailOf(oak), useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithText("Oak").assertIsDisplayed()
    compose.onNodeWithTag(TablesTestTags.tableOf(oak)).assertIsDisplayed()
  }

  @Test
  fun `a device that cannot draw one shows the swatch and the list still works`() {
    val chosen = mutableListOf<TablePin>()
    val presenter = show(FakeThumbnails(answer = false), onChosen = chosen::add)

    compose.onNodeWithTag(TablesTestTags.tableOf(oak)).performClick()

    compose.onNodeWithTag(TablesTestTags.thumbnailOf(oak), useUnmergedTree = true).assertDoesNotExist()
    assertEquals(listOf(oak), chosen)
    assertEquals(oak, presenter.state.chosen)
  }

  @Test
  fun `a build with no renderer behind it never asks for one`() {
    show(thumbnails = null)

    compose.onNodeWithTag(TablesTestTags.LIST).assertIsDisplayed()
    compose.onNodeWithTag(TablesTestTags.thumbnailOf(oak), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `the rows that are on screen are the rows that are drawn`() {
    // A LazyColumn composes what fits, so the last of a long list is not asked
    // for until somebody scrolls to it — which is the whole reason a row asks
    // rather than the presenter asking for all of them at once.
    val source = FakeThumbnails()
    val many =
      BuiltinDiceSet.set.copy(
        tables =
          List(TABLES) {
            BuiltinDiceSet.set.tables
              .first()
              .copy(id = "look-$it", name = "Look $it")
          },
      )
    show(source, sets = listOf(many))

    compose.waitForIdle()

    assertTrue("nothing at all was asked for", source.asked.isNotEmpty())
    assertTrue("every look off the bottom of the screen was drawn too", source.asked.size < TABLES)
  }

  @Test
  fun `drawing it twice with nothing changed keeps the pictures where they were`() {
    val source = FakeThumbnails()
    var tick by mutableStateOf(0)
    val presenter = presenter(source)
    compose.setContent {
      Column {
        Text("tick $tick")
        TablesScreen(presenter = presenter)
      }
    }
    compose.runOnIdle { source.drawAll() }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(TablesTestTags.thumbnailOf(oak), useUnmergedTree = true).assertIsDisplayed()
    assertEquals("a look was asked for its picture again", source.asked.distinct().size, source.asked.size)
  }

  private fun show(
    thumbnails: TableThumbnails?,
    sets: List<DiceSet> = listOf(BuiltinDiceSet.set),
    onChosen: (TablePin) -> Unit = {},
  ): TablesPresenter {
    val presenter = presenter(thumbnails, sets, onChosen)
    compose.setContent { TablesScreen(presenter = presenter) }
    return presenter
  }

  private fun presenter(
    thumbnails: TableThumbnails?,
    sets: List<DiceSet> = listOf(BuiltinDiceSet.set),
    onChosen: (TablePin) -> Unit = {},
  ) = TablesPresenter(
    sets = { sets },
    chosen = null,
    onChosen = onChosen,
    scope = CoroutineScope(Dispatchers.Unconfined),
    thumbnails = thumbnails,
  )

  private companion object {
    /** More looks than a short screen can show at once. */
    const val TABLES = 40
  }
}
