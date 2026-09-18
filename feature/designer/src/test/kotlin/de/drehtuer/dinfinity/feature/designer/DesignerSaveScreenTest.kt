package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The footer, the Save sheet, and the screen actually putting ink down
 * (`docs/face-designer.md`, "Save to set" and "Flow", step 4).
 *
 * Apart from [DesignerScreenTest] because it is a different subject: that one
 * is about the furniture — which tools are offered and which are reachable —
 * and this is about the two ways out of the screen and about the pictures on
 * it being pictures rather than tags.
 */
@RunWith(RobolectricTestRunner::class)
// A real bitmap behind the screen, so that `captureToImage` rasterises rather
// than recording calls: the legacy canvas draws nothing, and a glyph that
// threw half way would look exactly like one that worked.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignerSaveScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `with nowhere to save there is no Save`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.SAVE).assertDoesNotExist()
  }

  @Test
  fun `Save to set asks which set, and says which one it went to`() {
    // There is one writable set today and it is a list all the same: what the
    // sheet answers is *which set*, and a screen that answers it by not
    // asking is one that has to be rebuilt for the second personal set
    // (`docs/face-designer.md`, "Save to set").
    show(d6, sets = OneSet())

    compose.onNodeWithTag(DesignerTestTags.SAVE).performClick()
    compose.onNodeWithTag(DesignerTestTags.SAVE_SHEET).assertExists()
    compose.onNodeWithTag(DesignerTestTags.saveInto(OneSet.MINE.id)).assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.SAVE_DO).performClick()

    compose.onNodeWithTag(DesignerTestTags.SAVE_SAID).assertIsDisplayed()
    compose.onNodeWithText("Saved to My dice. Roll it now throws the drawing.").assertExists()
  }

  @Test
  fun `a save with nothing drawn says so rather than claiming a set`() {
    show(d6, sets = OneSet(answer = SaveResult.Blank))

    compose.onNodeWithTag(DesignerTestTags.SAVE).performClick()
    compose.onNodeWithTag(DesignerTestTags.SAVE_DO).performClick()

    compose.onNodeWithText("There is nothing drawn to save yet.").assertExists()
  }

  @Test
  fun `the sheet closes when it is closed`() {
    show(d6, sets = OneSet())

    compose.onNodeWithTag(DesignerTestTags.SAVE).performClick()
    compose.onNodeWithTag(DesignerTestTags.SAVE_CLOSE).performClick()

    compose.onNodeWithTag(DesignerTestTags.SAVE_SHEET).assertDoesNotExist()
  }

  @Test
  fun `Roll it throws the die in the set the drawing was just saved into`() {
    // The whole of device feedback 1: the formula used to be a bare `1d6`,
    // which resolves to whichever set a plain `d6` means — never the personal
    // one — so the tray drew a plain die (`docs/face-designer.md`).
    val thrown = mutableListOf<String>()
    show(d6, notationOf = { "1${it.id}" }, sets = OneSet(), onRoll = thrown::add)

    compose.onNodeWithTag(DesignerTestTags.ROLL).performClick()

    assertEquals(listOf("mine:1d6"), thrown)
  }

  @Test
  fun `a save that is still running says so, and cannot be pressed twice`() {
    // Building the package rasterises every drawn face of every die, so it is
    // not instant — and a Save button that looked like it had done nothing
    // would be pressed again.
    val slow = Slow()
    show(d6, sets = slow)

    compose.onNodeWithTag(DesignerTestTags.SAVE).performClick()
    compose.onNodeWithTag(DesignerTestTags.SAVE_DO).performClick()

    compose.onNodeWithTag(DesignerTestTags.SAVE_BUSY).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.SAVE_DO).assertIsNotEnabled()

    slow.finish()

    compose.onNodeWithTag(DesignerTestTags.SAVE_BUSY).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.SAVE_DO).assertIsEnabled()
    assertEquals("the second press ran a second save", 1, slow.calls)
  }

  @Test
  fun `a save the disk refused says what it left behind`() {
    show(d6, sets = OneSet(answer = SaveResult.Refused))

    compose.onNodeWithTag(DesignerTestTags.SAVE).performClick()
    compose.onNodeWithTag(DesignerTestTags.SAVE_DO).performClick()

    compose.onNodeWithText("That could not be written, and nothing was changed.").assertExists()
  }

  @Test
  fun `a die no formula can name still has somewhere to save it`() {
    // The two footer actions are independent: one is about the tray and the
    // other about the disk, and a die notation cannot spell is still a
    // drawing somebody wants kept.
    show(d6, sets = OneSet())

    compose.onNodeWithTag(DesignerTestTags.ROLL).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.SAVE).assertIsDisplayed()
  }

  @Test
  fun `the screen survives a recomposition with the sheet open on an answer`() {
    // Everything the footer and the sheet draw comes from one state, so an
    // ordinary recomposition has to skip it — and one that skipped wrongly
    // would take the answer off the sheet somebody is reading.
    var tick by mutableStateOf(0)
    val presenter = DesignerPresenter(d6, notationOf = { "1${it.id}" }, sets = OneSet())
    compose.setContent {
      Column {
        Text("tick $tick")
        DesignerScreen(presenter = presenter)
      }
    }
    compose.onNodeWithTag(DesignerTestTags.SAVE).performClick()
    compose.onNodeWithTag(DesignerTestTags.SAVE_DO).performClick()

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.SAVE_SAID).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.SAVE_SHEET).assertExists()
  }

  @Test
  fun `the header, the strip and the footer survive a recomposition around them`() {
    var tick by mutableStateOf(0)
    val presenter = DesignerPresenter(d6, notationOf = { "1${it.id}" }, sets = OneSet())
    compose.setContent {
      Column {
        Text("tick $tick")
        DesignerScreen(presenter = presenter)
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.WHICH_FACE).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.UNDO).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.ROLL).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.SAVE).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.faceOf(0)).assertIsDisplayed()
  }

  @Test
  fun `the screen really draws, glyphs, thumbnails and all`() {
    // Every other test here reads the semantics tree, which a screen can
    // satisfy without ever putting a pixel down: a `Canvas` lambda that threw
    // would leave the tags, the names and the click actions exactly where
    // they are. This one makes the whole screen rasterise — the tool glyphs,
    // the face on the canvas and the twenty faces of the strip — so that a
    // path nobody can draw is caught here rather than on a phone.
    val presenter = show(d20, notationOf = { "1${it.id}" }, sets = OneSet())
    presenter.fillNumbers()
    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))

    val drawn = compose.onNodeWithTag(DesignerTestTags.SCREEN).captureToImage()

    assertTrue("the screen drew nothing at all", inked(drawn))
  }

  @Test
  fun `the solid really draws too, and the guide comes off the canvas`() {
    // The other two things on this screen that are pictures rather than
    // furniture: the polyhedron, and the numeral under the drawing that can
    // be turned off. Both are `Canvas` lambdas, and a lambda that threw
    // would leave every tag on the screen exactly where it is.
    val presenter = show(d20, sets = OneSet())
    presenter.fillNumbers()
    compose.onNodeWithTag(DesignerTestTags.GUIDE).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).captureToImage()

    compose.onNodeWithTag(DesignerTestTags.viewOf(DesignerView.Solid)).performScrollTo().performClick()

    assertTrue(
      "the solid drew nothing",
      inked(compose.onNodeWithTag(DesignerTestTags.SOLID).performScrollTo().captureToImage()),
    )
    assertFalse("the guide is still on", presenter.state.guideShown)
  }

  /** True when anything at all was put down. */
  private fun inked(drawn: ImageBitmap): Boolean {
    val pixels = IntArray(drawn.width * drawn.height)
    drawn.readPixels(pixels)
    return pixels.any { it != 0 }
  }

  private fun show(
    die: Die,
    notationOf: (Die) -> String? = { null },
    sets: DesignerSets = DesignerSets.NONE,
    onRoll: (String) -> Unit = {},
  ): DesignerPresenter {
    val presenter = DesignerPresenter(die, emptyList(), Drafts.NONE, notationOf, sets)
    compose.setContent { DesignerScreen(presenter = presenter, onRoll = onRoll) }
    return presenter
  }

  /** A library with one writable set and no disk behind it. */
  private class OneSet(
    private val answer: SaveResult? = null,
  ) : DesignerSets {
    override val writable: List<WritableSet> = listOf(MINE)

    override suspend fun save(
      setId: String,
      draft: Draft,
    ): SaveResult = answer ?: SaveResult.Saved(MINE, "mine:1${draft.die.id}")

    companion object {
      val MINE = WritableSet(id = "mine", name = "My dice")
    }
  }

  /** A save that is still going, so the screen can be looked at while it is. */
  private class Slow : DesignerSets {
    private val done = CompletableDeferred<Unit>()
    var calls = 0
      private set

    override val writable: List<WritableSet> = listOf(OneSet.MINE)

    override suspend fun save(
      setId: String,
      draft: Draft,
    ): SaveResult {
      calls++
      done.await()
      return SaveResult.Saved(OneSet.MINE, "mine:1${draft.die.id}")
    }

    fun finish() = done.complete(Unit)
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }
  private val d20 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron }
}
