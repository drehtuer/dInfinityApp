package de.drehtuer.dinfinity.feature.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.designer.FaceDrawing
import de.drehtuer.dinfinity.designer.FaceFill
import de.drehtuer.dinfinity.designer.FaceTransform
import de.drehtuer.dinfinity.designer.Fill
import de.drehtuer.dinfinity.designer.Stroke
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drawing the faces of a die (`docs/face-designer.md`).
 *
 * The die is copied, so nothing here can change what it scores — which is what
 * makes the guide trustworthy. The rest is about the pen, and about a d4
 * showing three numbers because its numbers belong to corners.
 */
class DesignerPresenterTest {
  @Test
  fun `it opens on the first face, with the guide showing`() {
    val presenter = DesignerPresenter(d6)

    assertEquals(0, presenter.state.cell)
    assertTrue(presenter.state.guideShown)
    assertEquals(listOf(1), presenter.state.guide.map { it.value })
  }

  @Test
  fun `a stroke lands on the face in front of the player`() {
    val presenter = DesignerPresenter(d6)
    presenter.show(3)

    presenter.drew(line())

    assertEquals(
      1,
      presenter.state.draft
        .face(3)
        .marks.size,
    )
    assertTrue(
      presenter.state.draft
        .face(0)
        .blank,
    )
  }

  @Test
  fun `a tap is not a mark`() {
    // One point is a finger touching the glass, not a line. Recording it would
    // put an undo step behind every accidental touch.
    val presenter = DesignerPresenter(d6)

    presenter.drew(listOf(Dot(0.5f, 0.5f)))

    assertTrue(presenter.state.face.blank)
  }

  @Test
  fun `the stroke carries the pen that drew it`() {
    val presenter = DesignerPresenter(d6)
    presenter.use(Nib.Broad)
    presenter.ink(0xFFEC3013.toInt())

    presenter.drew(line())

    val stroke =
      presenter.state.face.marks
        .single() as Stroke
    assertEquals(Nib.Broad.width, stroke.width, 1e-6f)
    assertEquals(0xFFEC3013.toInt(), stroke.colorArgb)
    assertFalse(stroke.erases)
  }

  @Test
  fun `the eraser is a stroke, so it can be taken back like one`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(line())
    presenter.use(Nib.Eraser)

    presenter.drew(line())

    assertTrue(
      (
        presenter.state.face.marks
          .last() as Stroke
      ).erases,
    )
    presenter.take(Step.Back)
    assertEquals(1, presenter.state.face.marks.size)
  }

  @Test
  fun `choosing a colour puts the eraser down`() {
    // A coloured eraser is not a thing, and reaching for red should mean red.
    val presenter = DesignerPresenter(d6)
    presenter.use(Nib.Eraser)

    presenter.ink(0xFF4A90D9.toInt())

    assertFalse(presenter.state.nib.erases)
  }

  @Test
  fun `redo puts back what undo took, and drawing again throws it away`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(line())
    presenter.take(Step.Back)

    presenter.take(Step.Forward)
    assertEquals(1, presenter.state.face.marks.size)

    presenter.take(Step.Back)
    presenter.drew(line())
    assertFalse("the branch that was left was still waiting", presenter.state.canRedo)
  }

  @Test
  fun `undo and redo follow the face, not the die`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(line())
    presenter.show(1)

    assertFalse("undo reached back into another face", presenter.state.canUndo)

    presenter.show(0)
    assertTrue(presenter.state.canUndo)
  }

  @Test
  fun `a face that is not a face of this die is not shown`() {
    val presenter = DesignerPresenter(d6)

    presenter.show(99)

    assertEquals(0, presenter.state.cell)
  }

  @Test
  fun `the guide can be turned off, and the drawing stays`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(line())

    presenter.showGuide(false)

    assertTrue(presenter.state.guide.isEmpty())
    assertEquals(1, presenter.state.face.marks.size)
  }

  @Test
  fun `a d4 shows three numbers because its numbers belong to corners`() {
    // The rule `docs/dice-sets.md` sets out, arriving on the screen: cell 0 is
    // the triangle opposite corner 0, so it carries the other three.
    val presenter = DesignerPresenter(d4)

    assertEquals(3, presenter.state.guide.size)
    assertEquals(listOf(2, 3, 4), presenter.state.guide.map { it.value })
  }

  @Test
  fun `the warning comes before the refusal`() {
    // A face that simply stops taking strokes reads as a broken screen.
    val presenter = DesignerPresenter(d6)
    repeat(FaceDrawing.MAX_MARKS - DesignerState.ROOM_TO_WARN) { presenter.drew(line()) }

    assertTrue("no warning before the limit", presenter.state.nearlyFull)
    assertFalse("refused early", presenter.state.full)

    repeat(DesignerState.ROOM_TO_WARN) { presenter.drew(line()) }
    assertTrue(presenter.state.full)

    presenter.drew(line())
    assertEquals(FaceDrawing.MAX_MARKS, presenter.state.face.marks.size)
  }

  @Test
  fun `a die with no drawing on it opens blank`() {
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4))

    presenter.base(d4)

    assertEquals(d4.id, presenter.state.die.id)
    assertTrue("the new die came with the old one's strokes", presenter.state.draft.blank)
  }

  @Test
  fun `changing die writes down what was on the canvas`() {
    // What made the confirmation this used to ask unnecessary: the drawing is
    // not thrown away, it is put down (`docs/face-designer.md`).
    val drafts = Remembered()
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4), drafts = drafts)
    presenter.drew(line())

    presenter.base(d4)

    assertEquals(d4.id, presenter.state.die.id)
    assertFalse("the drawing on the die that was left is gone", drafts.load(d6).blank)
  }

  @Test
  fun `coming back to a die brings its drawing back`() {
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4), drafts = Remembered())
    presenter.drew(line())
    presenter.base(d4)

    presenter.base(d6)

    assertEquals(d6.id, presenter.state.die.id)
    assertFalse("the drawing was lost on the way there and back", presenter.state.draft.blank)
  }

  @Test
  fun `the designer opens on the drawing that was left there`() {
    // The whole of "drafts survive process death", from this end: a presenter
    // built afresh is a screen opened afresh.
    val drafts = Remembered()
    DesignerPresenter(d6, choosable = listOf(d6), drafts = drafts).drew(line())

    val again = DesignerPresenter(d6, choosable = listOf(d6), drafts = drafts)

    assertFalse("the screen opened blank on a die that had been drawn on", again.state.draft.blank)
  }

  @Test
  fun `undo is written down too, so what is on disk is what is on screen`() {
    val drafts = Remembered()
    val presenter = DesignerPresenter(d6, choosable = listOf(d6), drafts = drafts)
    presenter.drew(line())

    presenter.take(Step.Back)

    assertTrue("the drawing was taken back on screen but not on disk", drafts.load(d6).blank)
  }

  @Test
  fun `changing die keeps the pen where it was`() {
    // The pen, its colour and the guide are how somebody is working, not what
    // they are working on.
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4))
    presenter.use(Nib.Broad)
    presenter.ink(0xFF00FF00.toInt())
    presenter.showGuide(false)

    presenter.base(d4)

    assertEquals(Nib.Broad, presenter.state.nib)
    assertEquals(0xFF00FF00.toInt(), presenter.state.colorArgb)
    assertFalse("the guide came back on", presenter.state.guideShown)
  }

  @Test
  fun `choosing the die already being drawn on does nothing at all`() {
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4))
    presenter.drew(line())

    presenter.base(d6)

    assertFalse("it threw the drawing away", presenter.state.draft.blank)
  }

  @Test
  fun `with one die there is nothing to choose between`() {
    val presenter = DesignerPresenter(d6, choosable = listOf(d6))

    assertFalse("a chooser was offered for one die", presenter.state.baseChoosable)
  }

  @Test
  fun `the bucket on bare paper colours the whole face`() {
    val presenter = DesignerPresenter(d6)
    presenter.use(Nib.Bucket)
    presenter.ink(RED)

    presenter.drew(listOf(Dot(0.5f, 0.5f)))

    val fill =
      presenter.state.face.marks
        .single() as Fill
    assertEquals(FaceFill.FACE, fill.dots)
    assertEquals(RED, fill.colorArgb)
  }

  @Test
  fun `the bucket inside a drawn shape colours the shape`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(box())
    presenter.use(Nib.Bucket)

    presenter.drew(listOf(Dot(0.5f, 0.5f)))

    assertEquals(
      box(),
      (
        presenter.state.face.marks
          .first() as Fill
      ).dots,
    )
  }

  @Test
  fun `a fill is one step to take back, and it is written down`() {
    val drafts = Remembered()
    val presenter = DesignerPresenter(d6, drafts = drafts)
    presenter.use(Nib.Bucket)

    presenter.drew(listOf(Dot(0.5f, 0.5f)))

    assertFalse("the fill was not written to the draft", drafts.load(d6).blank)
    presenter.take(Step.Back)
    assertTrue(presenter.state.face.blank)
  }

  @Test
  fun `what a gesture leaves is the tool's business, not the gesture's`() {
    // A tap with a pen is not a mark; the bucket takes the place it was put
    // down whether the finger went on to move or not.
    val presenter = DesignerPresenter(d6)

    presenter.drew(listOf(Dot(0.5f, 0.5f)))
    assertTrue("a pen that has not moved has drawn", presenter.state.face.blank)

    presenter.use(Nib.Bucket)
    presenter.drew(line())
    assertTrue(
      "the bucket drew a line",
      presenter.state.face.marks
        .single() is Fill,
    )
  }

  @Test
  fun `copying a face and pasting it puts the drawing on another face`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(line())
    presenter.copyFace()
    presenter.show(3)

    presenter.paste()

    assertEquals(1, presenter.state.face.marks.size)
    assertEquals(
      presenter.state.draft
        .face(0)
        .marks,
      presenter.state.face.marks,
    )
  }

  @Test
  fun `a paste lands on what is there rather than over it`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(line())
    presenter.copyFace()
    presenter.show(1)
    presenter.drew(box())

    presenter.paste()

    assertEquals(2, presenter.state.face.marks.size)
  }

  @Test
  fun `a paste can be turned and mirrored on the way down`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(listOf(Dot(0.2f, 0.3f), Dot(0.2f, 0.4f)))
    presenter.copyFace()
    presenter.show(2)

    presenter.paste(FaceTransform(turns = 2, mirrored = true))

    val landed =
      presenter.state.face.marks
        .single()
    assertEquals(0.2f, landed.dots.first().x, 1e-5f)
    assertEquals(0.7f, landed.dots.first().y, 1e-5f)
  }

  @Test
  fun `a paste is one press of undo, however much it carried`() {
    val presenter = DesignerPresenter(d6)
    repeat(3) { presenter.drew(line()) }
    presenter.copyFace()
    presenter.show(4)

    presenter.paste()
    presenter.take(Step.Back)

    assertTrue(presenter.state.face.blank)
  }

  @Test
  fun `there is nothing to copy off a blank face and nothing to paste from an empty clipboard`() {
    val presenter = DesignerPresenter(d6)

    assertFalse(presenter.state.canCopy)
    assertFalse(presenter.state.canPaste)

    presenter.drew(line())
    assertTrue(presenter.state.canCopy)
    presenter.copyFace()
    assertTrue(presenter.state.canPaste)
  }

  @Test
  fun `a paste that would not fit is not offered`() {
    val presenter = DesignerPresenter(d6)
    presenter.drew(line())
    presenter.copyFace()
    presenter.show(1)
    repeat(FaceDrawing.MAX_MARKS) { presenter.drew(line()) }

    assertFalse("a paste was offered onto a full face", presenter.state.canPaste)
  }

  @Test
  fun `the clipboard outlives the die it was copied from`() {
    // It is how somebody is working rather than what they are working on,
    // which is the rule the pen and the colour already follow.
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4))
    presenter.drew(line())
    presenter.copyFace()

    presenter.base(d4)

    assertTrue(presenter.state.canPaste)
    presenter.paste()
    assertEquals(1, presenter.state.face.marks.size)
  }

  @Test
  fun `each die offers the turn its cells have`() {
    assertEquals(4, DesignerPresenter(d6).state.turnsOffered)
    assertEquals(3, DesignerPresenter(d4).state.turnsOffered)
    assertEquals(1, DesignerPresenter(d10).state.turnsOffered)
  }

  private fun box() = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.2f), Dot(0.8f, 0.8f), Dot(0.2f, 0.8f), Dot(0.2f, 0.2f))

  private fun line() = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f))

  @Test
  fun `the die being drawn has a formula that throws it`() {
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4), notationOf = { "1${it.id}" })

    assertEquals("1d6", presenter.rollable)
  }

  @Test
  fun `and it follows the die, not the screen`() {
    // The formula has to be the die in front of the player, not the one the
    // screen opened on.
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d4), notationOf = { "1${it.id}" })

    presenter.base(d4)

    assertEquals("1d4", presenter.rollable)
  }

  @Test
  fun `a die plain notation cannot name has no formula and no button`() {
    // A set's own `skull-d6` has no spelling a formula could carry
    // (`docs/architecture.md`, decision 31), and Roll it is not offered for it
    // rather than offered and broken.
    val presenter = DesignerPresenter(d6, choosable = listOf(d6))

    assertNull(presenter.rollable)
  }

  /** Drafts that outlive a presenter but not the test: a disk without the disk. */
  private class Remembered : Drafts {
    private val kept = mutableMapOf<String, Draft>()

    override fun load(die: Die): Draft = kept[die.id] ?: Draft(die = die)

    override fun save(draft: Draft) {
      kept[draft.die.id] = draft
    }
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }
  private val d4 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Tetrahedron }
  private val d10 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.PentagonalTrapezohedron }

  private companion object {
    const val RED = 0xFFEC3013.toInt()
  }
}
