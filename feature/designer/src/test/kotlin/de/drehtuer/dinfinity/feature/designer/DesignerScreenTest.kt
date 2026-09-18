package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Hex
import de.drehtuer.dinfinity.core.model.Hsv
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.designer.Eyes
import de.drehtuer.dinfinity.designer.FaceDrawing
import de.drehtuer.dinfinity.designer.Fill
import de.drehtuer.dinfinity.designer.Stamp
import de.drehtuer.dinfinity.designer.StampSize
import de.drehtuer.dinfinity.designer.Stroke
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The face designer (`design/dInfinity.dc.html`, options `1v`, `4c`, `8d`).
 *
 * The canvas itself is a `Canvas` draw lambda, which a test cannot read — the
 * arithmetic under it is `FaceShapesTest`'s. What is asserted here is the
 * furniture: which tools are offered, which are reachable, and that the strip
 * moves between faces.
 */
@RunWith(RobolectricTestRunner::class)
class DesignerScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the canvas and the face strip are there`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).assertIsDisplayed()
    // The strip scrolls, so a face is reached before it is looked at — the
    // later ones are off-screen on a narrow phone, which is the point of it.
    (0 until 6).forEach { compose.onNodeWithTag(DesignerTestTags.faceOf(it)).performScrollTo().assertIsDisplayed() }
  }

  @Test
  fun `a d20 has twenty faces to move between`() {
    show(BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron })

    compose.onNodeWithTag(DesignerTestTags.faceOf(19)).assertExists()
  }

  @Test
  fun `the strip has its row to itself, and the fill buttons the next one`() {
    // What this is the fix for: the two shared a line, so the strip got
    // whatever three buttons left — about one face of a d20 on a phone. The
    // buttons are still outside the scroll, which is the other half of it:
    // they are about every face, so scrolling to the twentieth must not take
    // them off the screen.
    show(BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron })

    val strip = compose.onNodeWithTag(DesignerTestTags.faceOf(0)).getUnclippedBoundsInRoot()
    val fill = compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).getUnclippedBoundsInRoot()

    assertTrue("the fill buttons are still on the strip's row", fill.top >= strip.bottom)
  }

  @Test
  fun `scrolling to the last face leaves the fill buttons where they were`() {
    show(BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron })

    val before = compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).getUnclippedBoundsInRoot()
    compose.onNodeWithTag(DesignerTestTags.faceOf(19)).performScrollTo()
    val after = compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).getUnclippedBoundsInRoot()

    assertEquals("the fill buttons scrolled away with the faces", before.left, after.left)
  }

  @Test
  fun `tapping the strip moves to that face`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.faceOf(4)).performScrollTo().performClick()

    assertEquals(4, presenter.state.cell)
  }

  @Test
  fun `undo and redo are offered only when there is something to take back`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.UNDO).assertIsNotEnabled()
    compose.onNodeWithTag(DesignerTestTags.REDO).assertIsNotEnabled()

    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))

    compose.onNodeWithTag(DesignerTestTags.UNDO).assertIsEnabled()
    compose.onNodeWithTag(DesignerTestTags.UNDO).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.REDO).assertIsEnabled()
  }

  @Test
  fun `clear is offered only on a face with something on it`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.CLEAR).performScrollTo().assertIsNotEnabled()

    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))

    compose.onNodeWithTag(DesignerTestTags.CLEAR).performScrollTo().assertIsEnabled()
    compose.onNodeWithTag(DesignerTestTags.CLEAR).performScrollTo().performClick()
    assertEquals(true, presenter.state.face.blank)
  }

  @Test
  fun `every nib is offered, the eraser among them`() {
    show(d6)

    Nib.entries.forEach { compose.onNodeWithTag(DesignerTestTags.nibOf(it)).performScrollTo().assertIsDisplayed() }
  }

  @Test
  fun `choosing a nib chooses it`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Broad)).performScrollTo().performClick()

    assertEquals(Nib.Broad, presenter.state.nib)
  }

  @Test
  fun `the tool in hand is announced as the one that is chosen`() {
    // The design shows a chosen option filled rather than merely recoloured
    // (`.seg-opt:has(input:checked)`), and a screen reader hears the same fact
    // because it is in the semantics as well as in the paint.
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Medium)).performScrollTo().assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Broad)).performScrollTo().assertIsNotSelected()

    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Broad)).performScrollTo().performClick()

    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Broad)).performScrollTo().assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Medium)).performScrollTo().assertIsNotSelected()
  }

  @Test
  fun `the die being drawn on is announced as the one that is chosen`() {
    show(d6, choosable = listOf(d6, d4))

    compose.onNodeWithTag(DesignerTestTags.baseOf(d6.id)).assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.baseOf(d4.id)).assertIsNotSelected()

    compose.onNodeWithTag(DesignerTestTags.baseOf(d4.id)).performClick()

    compose.onNodeWithTag(DesignerTestTags.baseOf(d4.id)).assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.baseOf(d6.id)).assertIsNotSelected()
  }

  @Test
  fun `the face in front of the player is announced as the one that is chosen`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.faceOf(0)).performScrollTo().assertIsSelected()

    compose.onNodeWithTag(DesignerTestTags.faceOf(3)).performScrollTo().performClick()

    compose.onNodeWithTag(DesignerTestTags.faceOf(3)).performScrollTo().assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.faceOf(0)).performScrollTo().assertIsNotSelected()
  }

  @Test
  fun `an action is never a chosen option`() {
    // Undo, redo, clear and "fill all with numbers" do a thing rather than
    // stand for a state, so nothing about them is ever selected — a filled
    // Undo would read as a mode the screen was stuck in.
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.UNDO).performScrollTo().assertIsNotSelected()
    compose.onNodeWithTag(DesignerTestTags.CLEAR).performScrollTo().assertIsNotSelected()
    compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).assertIsNotSelected()
  }

  @Test
  fun `the guide can be turned off and on from the screen`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.GUIDE).performScrollTo().performClick()
    assertEquals(false, presenter.state.guideShown)

    compose.onNodeWithTag(DesignerTestTags.GUIDE).performScrollTo().performClick()
    assertEquals(true, presenter.state.guideShown)
  }

  @Test
  fun `nothing warns about a limit nobody is near`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.WARNING).assertDoesNotExist()
  }

  @Test
  fun `a face near the limit says so before it refuses`() {
    val presenter = show(d6)

    repeat(FaceDrawing.MAX_MARKS - 1) { presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f))) }

    // Reached rather than looked at: the body scrolls, and the tab pair above
    // the canvas takes the last row on a short screen below the fold.
    compose.onNodeWithTag(DesignerTestTags.WARNING).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `dragging a finger across the canvas draws a stroke`() {
    // The whole screen, in one gesture. Everything else here is furniture
    // around this.
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.CANVAS).performTouchInput {
      down(percentOffset(.25f, .25f))
      moveTo(percentOffset(.5f, .5f))
      moveTo(percentOffset(.75f, .75f))
      up()
    }

    val stroke =
      presenter.state.face.marks
        .single()
    assertTrue("a stroke of one point is not a line", stroke.dots.size >= 2)
    assertTrue("the stroke left the canvas", stroke.dots.all { it.x in 0f..1f && it.y in 0f..1f })
  }

  @Test
  fun `the stroke is in fractions of the canvas, not in pixels`() {
    // What lets a draft outlive the screen it was drawn on and be re-rendered
    // at export resolution.
    //
    // The *first* dot is not asserted: `detectDragGestures` starts a drag only
    // once the touch slop is passed, so where the finger went down and where
    // the stroke begins are deliberately not the same point. What is asserted
    // is that every dot is a fraction and that the line went the way the
    // finger did.
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.CANVAS).performTouchInput {
      down(percentOffset(.2f, .2f))
      moveTo(percentOffset(.4f, .4f))
      moveTo(percentOffset(.6f, .6f))
      moveTo(percentOffset(.8f, .8f))
      up()
    }

    val dots =
      presenter.state.face.marks
        .single()
        .dots
    assertTrue("not a fraction of the canvas: $dots", dots.all { it.x in 0f..1f && it.y in 0f..1f })
    assertTrue("the stroke did not follow the finger: $dots", dots.last().x > dots.first().x)
  }

  @Test
  fun `a finger that touches and lifts without moving leaves nothing`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.CANVAS).performTouchInput {
      down(center)
      up()
    }

    assertTrue(presenter.state.face.blank)
  }

  @Test
  fun `the pen that is chosen is the pen that draws`() {
    val presenter = show(d6)
    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Eraser)).performScrollTo().performClick()

    compose.onNodeWithTag(DesignerTestTags.CANVAS).performTouchInput {
      down(percentOffset(.3f, .3f))
      moveTo(percentOffset(.7f, .7f))
      up()
    }

    assertTrue(
      "the eraser did not erase",
      (
        presenter.state.face.marks
          .single() as Stroke
      ).erases,
    )
  }

  @Test
  fun `with one die to draw on there is no chooser`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.BASES).assertDoesNotExist()
  }

  @Test
  fun `tapping another die opens it`() {
    val presenter = show(d6, choosable = listOf(d6, d4))

    compose.onNodeWithTag(DesignerTestTags.baseOf(d4.id)).performClick()

    assertEquals(d4.id, presenter.state.die.id)
  }

  @Test
  fun `the drawing on the die you left is there when you come back to it`() {
    // It used to ask before throwing the drawing away, and now there is
    // nothing to throw away: each die keeps its own (`docs/face-designer.md`,
    // "Drawing tools").
    val presenter = show(d6, choosable = listOf(d6, d4), drafts = Remembered())
    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))

    compose.onNodeWithTag(DesignerTestTags.baseOf(d4.id)).performClick()
    assertTrue("the other die opened on somebody else's drawing", presenter.state.draft.blank)
    compose.onNodeWithTag(DesignerTestTags.baseOf(d6.id)).performClick()

    assertEquals(d6.id, presenter.state.die.id)
    assertFalse("the drawing was lost on the way there and back", presenter.state.draft.blank)
  }

  @Test
  fun `a recomposition around it that changes nothing leaves it alone`() {
    // The chooser and the canvas are drawn from one state, so an ordinary
    // recomposition has to skip them. One that skipped wrongly would come back
    // without its row of dice, which a single-pass test would never see.
    var tick by mutableStateOf(0)
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4))
    compose.setContent {
      Column {
        Text("tick $tick")
        DesignerScreen(presenter = presenter)
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.BASES).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.baseOf(d4.id)).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).assertIsDisplayed()
  }

  @Test
  fun `Roll it hands up the formula for the die being drawn`() {
    val thrown = mutableListOf<String>()
    show(d6, choosable = listOf(d6, d4), notationOf = { "1${it.id}" }, onRoll = thrown::add)

    compose.onNodeWithTag(DesignerTestTags.ROLL).performClick()

    assertEquals(listOf("1d6"), thrown)
  }

  @Test
  fun `Roll it follows the die that is being drawn on`() {
    val thrown = mutableListOf<String>()
    show(d6, choosable = listOf(d6, d4), notationOf = { "1${it.id}" }, onRoll = thrown::add)

    compose.onNodeWithTag(DesignerTestTags.baseOf(d4.id)).performClick()
    compose.onNodeWithTag(DesignerTestTags.ROLL).performClick()

    assertEquals(listOf("1d4"), thrown)
  }

  @Test
  fun `a die no formula can name is not offered a Roll button`() {
    show(d6, choosable = listOf(d6, d4))

    compose.onNodeWithTag(DesignerTestTags.ROLL).assertDoesNotExist()
  }

  @Test
  fun `the bucket is a tool like the pens, and a tap with it fills the face`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Bucket)).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).performClick()

    assertTrue(
      "the bucket left no fill",
      presenter.state.face.marks
        .single() is Fill,
    )
  }

  @Test
  fun `a tap on the canvas with a pen in hand leaves nothing`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.CANVAS).performClick()

    assertTrue(presenter.state.face.blank)
  }

  @Test
  fun `copy and paste are offered, and disabled until there is something to do`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.COPY).performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag(DesignerTestTags.PASTE).performScrollTo().assertIsNotEnabled()

    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))
    compose.onNodeWithTag(DesignerTestTags.COPY).performScrollTo().performClick()

    compose.onNodeWithTag(DesignerTestTags.PASTE).performScrollTo().assertIsEnabled()
  }

  @Test
  fun `a face copied from the screen lands on the face the strip moved to`() {
    val presenter = show(d6)
    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))

    compose.onNodeWithTag(DesignerTestTags.COPY).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.faceOf(2)).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.PASTE).performScrollTo().performClick()

    assertEquals(1, presenter.state.face.marks.size)
    assertEquals(2, presenter.state.cell)
  }

  @Test
  fun `the turn and the mirror are what the next paste does`() {
    val presenter = show(d6)
    presenter.drew(listOf(Dot(0.2f, 0.3f), Dot(0.2f, 0.4f)))

    compose.onNodeWithTag(DesignerTestTags.COPY).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.MIRROR).performScrollTo().performClick()
    repeat(2) { compose.onNodeWithTag(DesignerTestTags.TURN).performScrollTo().performClick() }
    compose.onNodeWithTag(DesignerTestTags.faceOf(1)).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.PASTE).performScrollTo().performClick()

    val landed =
      presenter.state.face.marks
        .single()
    assertEquals(0.2f, landed.dots.first().x, 1e-4f)
    assertEquals(0.7f, landed.dots.first().y, 1e-4f)
  }

  @Test
  fun `a die whose cells have no turn is offered the mirror and not the turn`() {
    // A kite's only symmetry is the mirror, and a turn would carry the drawing
    // off the face (`docs/face-designer.md`, "Copy and paste").
    show(BuiltinDiceSet.set.dice.first { it.shape == DieShape.PentagonalTrapezohedron })

    compose.onNodeWithTag(DesignerTestTags.TURN).performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag(DesignerTestTags.MIRROR).performScrollTo().assertIsEnabled()
  }

  @Test
  fun `every swatch says what colour it is and is big enough to hit`() {
    show(d6)

    compose
      .onNodeWithTag(DesignerTestTags.colourOf(0xFFEC3013.toInt()))
      .performScrollTo()
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
    compose.onNodeWithContentDescription("Ink #EC3013").assertExists()
    compose
      .onNodeWithTag(DesignerTestTags.MORE_COLOURS)
      .performScrollTo()
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun `the twelve presets are still the fast path`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.colourOf(0xFF4A90D9.toInt())).performScrollTo().performClick()

    assertEquals(0xFF4A90D9.toInt(), presenter.state.colorArgb)
    compose.onNodeWithTag(DesignerTestTags.PICKER.sheet).assertDoesNotExist()
  }

  @Test
  fun `the picker takes the pen past the twelve`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.MORE_COLOURS).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.PICKER.sheet).assertExists()
    compose.onNodeWithTag(DesignerTestTags.PICKER.hue).performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
    compose.onNodeWithTag(DesignerTestTags.PICKER.depth).performSemanticsAction(SemanticsActions.SetProgress) { it(0.6f) }
    compose.onNodeWithTag(DesignerTestTags.PICKER.brightness).performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
    compose.onNodeWithTag(DesignerTestTags.PICKER.use).performClick()

    assertEquals(Hsv(150f, 0.6f, 0.8f).argb, presenter.state.colorArgb)
    compose.onNodeWithTag(DesignerTestTags.PICKER.sheet).assertDoesNotExist()
  }

  @Test
  fun `each of the picker's three sliders says which one it is`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.MORE_COLOURS).performScrollTo().performClick()

    listOf("Hue", "Depth", "Brightness").forEach { compose.onNodeWithContentDescription(it).assertExists() }
  }

  @Test
  fun `a picker that is cancelled leaves the pen alone`() {
    val presenter = show(d6)
    val before = presenter.state.colorArgb

    compose.onNodeWithTag(DesignerTestTags.MORE_COLOURS).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.PICKER.hue).performSemanticsAction(SemanticsActions.SetProgress) { it(300f) }
    compose.onNodeWithTag(DesignerTestTags.PICKER.cancel).performClick()

    assertEquals(before, presenter.state.colorArgb)
    compose.onNodeWithTag(DesignerTestTags.PICKER.sheet).assertDoesNotExist()
  }

  @Test
  fun `the ink being drawn with is written where it can be read`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.INK_HEX).performScrollTo().assertIsDisplayed()
    compose.onNodeWithText(Hex.of(presenter.state.colorArgb)).assertExists()
  }

  @Test
  fun `the stamp is a tool like the pens, and a tap with it puts a glyph down`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Stamp)).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).performClick()

    assertTrue(
      "the stamp left nothing",
      presenter.state.face.marks
        .single() is Stamp,
    )
  }

  @Test
  fun `what to stamp is asked for only while the stamp is in hand`() {
    // Two more rows on a screen that already scrolls, and they mean nothing to
    // a pen (`design/dInfinity.dc.html`, option `1v`).
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.STAMP_BAR).assertDoesNotExist()

    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Stamp)).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.STAMP_BAR).performScrollTo().assertIsDisplayed()
    StampSize.entries.forEach {
      compose.onNodeWithTag(DesignerTestTags.stampSizeOf(it)).performScrollTo().assertIsDisplayed()
    }
  }

  @Test
  fun `typing a glyph the font cannot draw says so rather than swallowing the tap`() {
    val presenter = show(d6)
    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Stamp)).performScrollTo().performClick()

    compose.onNodeWithTag(DesignerTestTags.STAMP_TEXT).performScrollTo().performTextReplacement("crit")

    compose.onNodeWithTag(DesignerTestTags.STAMP_REFUSED).performScrollTo().assertIsDisplayed()
    assertEquals("crit", presenter.state.stamping)
  }

  @Test
  fun `a bigger stamp is a bigger glyph`() {
    val presenter = show(d6)
    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Stamp)).performScrollTo().performClick()

    compose.onNodeWithTag(DesignerTestTags.stampSizeOf(StampSize.Small)).performScrollTo().performClick()
    // Scrolled to before it is pressed, like everything else on this screen:
    // the column is taller than a phone and a tap lands on the middle of the
    // node, which is off the window when the canvas is only half in it.
    compose.onNodeWithTag(DesignerTestTags.CANVAS).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.faceOf(1)).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.stampSizeOf(StampSize.Large)).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).performScrollTo().performClick()

    assertEquals(StampSize.Large, presenter.state.stampSize)
    assertTrue("the large stamp is no larger than the small one", tall(presenter, 1) > tall(presenter, 0))
  }

  @Test
  fun `the stamp row and the fill survive a recomposition around them`() {
    // Everything under the canvas is drawn from one state, so an ordinary
    // recomposition has to skip it — and one that skipped wrongly would come
    // back without the row it was typing in.
    var tick by mutableStateOf(0)
    val presenter = DesignerPresenter(d6)
    compose.setContent {
      Column {
        Text("tick $tick")
        DesignerScreen(presenter = presenter)
      }
    }
    compose.onNodeWithTag(DesignerTestTags.nibOf(Nib.Stamp)).performScrollTo().performClick()

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.STAMP_BAR).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).assertIsDisplayed()
  }

  /** How tall the glyph stamped on [cell] came out. */
  private fun tall(
    presenter: DesignerPresenter,
    cell: Int,
  ): Float {
    val drawing = presenter.state.draft.face(cell)
    val dots = (drawing.marks.single() as Stamp).dots
    return dots.maxOf { it.y } - dots.minOf { it.y }
  }

  @Test
  fun `fill all with numbers is under the strip, and numbers every face`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).assertIsDisplayed().performClick()

    val draft = presenter.state.draft
    assertEquals(6, (0 until 6).count { cell -> draft.face(cell).marks.any { it is Stamp } })
  }

  @Test
  fun `fill all with eyes pips every face of a d6, and clear eyes takes them off`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.FILL_EYES).assertIsDisplayed().performClick()
    assertEquals(
      6,
      (0 until 6).count { cell ->
        presenter.state.draft
          .face(cell)
          .marks
          .any { it is Eyes }
      },
    )

    compose.onNodeWithTag(DesignerTestTags.CLEAR_EYES).assertIsEnabled().performClick()
    assertEquals(
      0,
      (0 until 6).count { cell ->
        presenter.state.draft
          .face(cell)
          .marks
          .any { it is Eyes }
      },
    )
  }

  @Test
  fun `clear eyes is dead until there is something to clear`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.CLEAR_EYES).assertIsNotEnabled()
  }

  @Test
  fun `a die that is not a d6 is offered no eyes at all`() {
    // A pip pattern writes one to six and nothing else, so the buttons are
    // absent rather than there and refusing (`docs/face-designer.md`).
    show(d20)

    compose.onNodeWithTag(DesignerTestTags.FILL_EYES).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.CLEAR_EYES).assertDoesNotExist()
  }

  @Test
  fun `pips and numerals are never both on a face`() {
    val presenter = show(d6)

    compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).performClick()
    compose.onNodeWithTag(DesignerTestTags.FILL_EYES).performClick()
    assertTrue(
      presenter.state.draft
        .face(0)
        .marks
        .none { it is Stamp },
    )

    compose.onNodeWithTag(DesignerTestTags.FILL_NUMBERS).performClick()
    assertTrue(
      presenter.state.draft
        .face(0)
        .marks
        .none { it is Eyes },
    )
  }

  private fun show(
    die: Die,
    choosable: List<Die> = emptyList(),
    drafts: Drafts = Drafts.NONE,
    notationOf: (Die) -> String? = { null },
    onRoll: (String) -> Unit = {},
  ): DesignerPresenter {
    val presenter = DesignerPresenter(die, choosable, drafts, notationOf)
    compose.setContent { DesignerScreen(presenter = presenter, onRoll = onRoll) }
    return presenter
  }

  /** Drafts that outlive a swap but not the test: a disk without the disk. */
  private class Remembered : Drafts {
    private val kept = mutableMapOf<String, Draft>()

    override fun load(die: Die): Draft = kept[die.id] ?: Draft(die = die)

    override fun save(draft: Draft) {
      kept[draft.die.id] = draft
    }
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }
  private val d4 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Tetrahedron }
  private val d20 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron }
}
