package de.drehtuer.dinfinity.feature.tables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.TableLook
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

  /**
   * The word "Chosen" on the row is drawn in the accent, and the row is a
   * merge root. `selected` puts the same fact in the semantics tree, so a
   * screen reader announces the state of the control rather than reading a
   * colour it cannot see (`docs/architecture.md`, "Accessibility").
   */
  @Test
  fun `the one in use is marked in the semantics as well as in the accent`() {
    val oak = TablePin(BuiltinDiceSet.set.id, "oak")
    val glass = TablePin(BuiltinDiceSet.set.id, "dark-glass")
    show(chosen = oak)

    compose.onNodeWithTag(TablesTestTags.tableOf(oak)).assertIsSelected()
    compose.onNodeWithTag(TablesTestTags.tableOf(glass)).assertIsNotSelected()
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

  @Test
  fun `use a photo is the last row, and only when something can make one`() {
    show(photos = FakePhotos())

    scrolledToUsePhoto().assertIsDisplayed()
  }

  @Test
  fun `with nothing wired to make one, the row is not there at all`() {
    show(photos = null)

    compose.onNodeWithTag(TablesTestTags.USE_PHOTO).assertDoesNotExist()
  }

  @Test
  fun `tapping it opens the sheet, and choosing asks the app for a file`() {
    var asked = 0
    show(photos = FakePhotos(), onPickPhoto = { asked++ })

    usePhoto()
    compose.onNodeWithTag(TablesTestTags.PHOTO_SHEET).assertIsDisplayed()
    compose.onNodeWithTag(TablesTestTags.PHOTO_CHOOSE).performClick()

    assertEquals(1, asked)
  }

  @Test
  fun `a photo chosen and named lands as a row of the same list`() {
    val photos = FakePhotos()
    val presenter = show(photos = photos)

    usePhoto()
    // A name no bundled look has, so what is found is the new row.
    presenter.picked(pickedPhoto(label = "meadow.jpg"))
    compose.onNodeWithTag(TablesTestTags.PHOTO_CONFIRM).performClick()

    compose.onNodeWithTag(TablesTestTags.PHOTO_SHEET).assertDoesNotExist()
    assertEquals(listOf("meadow.jpg" to "Meadow"), photos.asked)
    compose
      .onNodeWithTag(TablesTestTags.LIST)
      .performScrollToNode(hasTestTag(TablesTestTags.tableOf(TablePin(DiceSet.PERSONAL_ID, "photo-meadow"))))
    compose.onNodeWithText("Meadow").assertIsDisplayed()
  }

  @Test
  fun `a refusal is shown where the photo and the name still are`() {
    val presenter = show(photos = FakePhotos(refuse = listOf("diceset.toml:11: error: it is 4096 wide")))

    usePhoto()
    presenter.picked(pickedPhoto(label = "huge.jpg"))
    compose.onNodeWithTag(TablesTestTags.PHOTO_CONFIRM).performClick()

    compose.onNodeWithTag(TablesTestTags.PHOTO_SHEET).assertIsDisplayed()
    compose.onNodeWithTag(TablesTestTags.PHOTO_REFUSED, useUnmergedTree = true).assertIsDisplayed()
    compose.onNodeWithText("diceset.toml:11: error: it is 4096 wide").assertIsDisplayed()
  }

  @Test
  fun `the file that has been chosen is named, and nothing stands in for one that has not`() {
    val presenter = show(photos = FakePhotos())

    usePhoto()
    compose.onNodeWithTag(TablesTestTags.PHOTO_FILE, useUnmergedTree = true).assertIsDisplayed()

    presenter.picked(pickedPhoto(label = "oak.jpg"))
    compose.onNodeWithText("oak.jpg").assertIsDisplayed()
  }

  @Test
  fun `only the player's own tables offer to be removed`() {
    val photos = FakePhotos()
    val presenter = show(photos = photos)
    usePhoto()
    presenter.picked(pickedPhoto(label = "oak.jpg"))
    compose.onNodeWithTag(TablesTestTags.PHOTO_CONFIRM).performClick()
    val own = TablePin(DiceSet.PERSONAL_ID, "photo-oak")

    // The personal package is listed after the bundled five, so the row it
    // adds may be below the fold on a short screen.
    compose.onNodeWithTag(TablesTestTags.LIST).performScrollToNode(hasTestTag(TablesTestTags.tableOf(own)))
    compose.onNodeWithTag(TablesTestTags.removeOf(own), useUnmergedTree = true).assertIsDisplayed()
    compose
      .onNodeWithTag(TablesTestTags.removeOf(TablePin(BuiltinDiceSet.set.id, "oak")), useUnmergedTree = true)
      .assertDoesNotExist()

    compose.onNodeWithTag(TablesTestTags.removeOf(own), useUnmergedTree = true).performClick()
    assertEquals(listOf(own), photos.removed)
  }

  @Test
  fun `the sheet can be shut without making anything`() {
    val photos = FakePhotos()
    show(photos = photos)

    usePhoto()
    compose.onNodeWithTag(TablesTestTags.PHOTO_CANCEL).performClick()

    compose.onNodeWithTag(TablesTestTags.PHOTO_SHEET).assertDoesNotExist()
    assertTrue(photos.asked.isEmpty())
  }

  @Test
  fun `drawing it twice with nothing changed draws the same screen`() {
    // The other side of every skip branch the Compose compiler emits, which is
    // what a single-pass test can never reach (`docs/TODO.md`, "Coverage").
    var draws by mutableStateOf(0)
    val presenter =
      TablesPresenter(
        sets = { listOf(BuiltinDiceSet.set) },
        chosen = null,
        onChosen = {},
        scope = CoroutineScope(Dispatchers.Unconfined),
        photos = FakePhotos(),
      )
    compose.setContent {
      // Read so the whole composition is invalidated, while nothing the screen
      // takes has changed at all.
      @Suppress("UNUSED_EXPRESSION")
      draws
      TablesScreen(presenter = presenter)
    }

    compose.runOnIdle { draws++ }

    compose.onNodeWithTag(TablesTestTags.LIST).assertIsDisplayed()
    scrolledToUsePhoto().assertIsDisplayed()

    // With no `onPickPhoto` given, choosing does nothing at all — which is
    // what a screen drawn without the application behind it has to survive.
    usePhoto()
    compose.onNodeWithTag(TablesTestTags.PHOTO_CHOOSE).performClick()
    compose.onNodeWithTag(TablesTestTags.PHOTO_SHEET).assertIsDisplayed()
  }

  /**
   * **Use a photo**, scrolled to first.
   *
   * Each row is now a picture of its own tray rather than a swatch, so the
   * last row of five is below the fold on a short screen — the same reason the
   * personal package's own row is already scrolled to further up.
   */
  private fun usePhoto() = scrolledToUsePhoto().performClick()

  private fun scrolledToUsePhoto(): SemanticsNodeInteraction {
    compose.onNodeWithTag(TablesTestTags.LIST).performScrollToNode(hasTestTag(TablesTestTags.USE_PHOTO))
    return compose.onNodeWithTag(TablesTestTags.USE_PHOTO)
  }

  private fun show(
    sets: List<DiceSet> = listOf(BuiltinDiceSet.set),
    chosen: TablePin? = null,
    onChosen: (TablePin) -> Unit = {},
    photos: FakePhotos? = null,
    onPickPhoto: () -> Unit = {},
  ): TablesPresenter {
    val presenter =
      TablesPresenter(
        // The personal package joins the list as a photo lands, the way the
        // catalogue does once it has been re-read.
        sets = { sets + photos?.personal().orEmpty() },
        chosen = chosen,
        onChosen = onChosen,
        scope = CoroutineScope(Dispatchers.Unconfined),
        photos = photos,
      )
    compose.setContent { TablesScreen(presenter = presenter, onPickPhoto = onPickPhoto) }
    return presenter
  }

  private val brass =
    BuiltinDiceSet.set.copy(
      id = "brass",
      name = "Brass & Bone",
      tables = listOf(TableLook(id = "brass", name = "Brass")),
    )
}
