package de.drehtuer.dinfinity.feature.designer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.designer.MaterialPreset
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The three steps a die is designed in: shape, material, faces
 * (`docs/face-designer.md`, "Flow").
 */
@RunWith(RobolectricTestRunner::class)
class DesignerStepsTest {
  @get:Rule
  val compose = createComposeRule()

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }
  private val d20 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron }

  @Test
  fun `the steps are in order, and each knows its neighbours`() {
    assertEquals(listOf(DesignerStep.Shape, DesignerStep.Material, DesignerStep.Faces), DesignerStep.entries)
    assertEquals(DesignerStep.Material, DesignerStep.Shape.next)
    assertNull(DesignerStep.Faces.next)
    assertNull(DesignerStep.Shape.previous)
    assertEquals(DesignerStep.Material, DesignerStep.Faces.previous)
  }

  @Test
  fun `the designer opens on the first step unless told otherwise`() {
    assertEquals(DesignerStep.Shape, DesignerPresenter(d6).state.step)
    assertEquals(DesignerStep.Faces, DesignerPresenter(d6, step = DesignerStep.Faces).state.step)
  }

  @Test
  fun `a route that names a die opens on its faces, and one that names none on the first step`() {
    assertEquals(DesignerStep.Shape, DesignerStep.openingFor(""))
    assertEquals(DesignerStep.Faces, DesignerStep.openingFor("d20"))
  }

  @Test
  fun `next and back walk the steps and stop at the ends`() {
    val presenter = DesignerPresenter(d6)

    presenter.back()
    assertEquals(DesignerStep.Shape, presenter.state.step)
    presenter.next()
    presenter.next()
    assertEquals(DesignerStep.Faces, presenter.state.step)
    presenter.next()
    assertEquals(DesignerStep.Faces, presenter.state.step)
    presenter.back()
    assertEquals(DesignerStep.Material, presenter.state.step)
  }

  @Test
  fun `going between steps loses nothing and writes the drawing down`() {
    val drafts = Kept()
    val presenter = DesignerPresenter(d6, drafts = drafts, step = DesignerStep.Faces)
    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))

    presenter.go(DesignerStep.Material)
    presenter.madeOf(MaterialPreset.Glass)
    presenter.go(DesignerStep.Faces)

    assertEquals(1, presenter.state.face.marks.size)
    assertEquals(MaterialPreset.Glass, presenter.state.preset)
    assertEquals(presenter.state.draft, drafts.load(d6))
    val writes = drafts.saves
    presenter.go(DesignerStep.Faces)
    assertEquals("staying on a step is not a step", writes, drafts.saves)
  }

  @Test
  fun `changing die on the first step keeps the step`() {
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d20))

    presenter.base(d20)

    assertEquals(DesignerStep.Shape, presenter.state.step)
  }

  @Test
  fun `the die turns on the first two steps, and on the third only on its Solid tab`() {
    val presenter = DesignerPresenter(d6)
    assertTrue(presenter.state.showsSolid)
    presenter.next()
    assertTrue(presenter.state.showsSolid)
    presenter.next()
    assertEquals(false, presenter.state.showsSolid)
    presenter.look(DesignerView.Solid)
    assertTrue(presenter.state.showsSolid)
  }

  @Test
  fun `the first step is the dice and the turning die, with Next and no Back`() {
    show(DesignerPresenter(d6, choosable = listOf(d6, d20)))

    compose.onNodeWithTag(DesignerTestTags.stepOf(DesignerStep.Shape)).assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.BASES).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.SOLID).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.WHICH_STEP).assertTextContains("Step 1 of 3", substring = true)
    compose.onNodeWithTag(DesignerTestTags.NEXT).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.BACK).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.UNDO).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.ROLL).assertDoesNotExist()
  }

  @Test
  fun `Next goes to the material, and Back comes back`() {
    val presenter = show(DesignerPresenter(d6))

    compose.onNodeWithTag(DesignerTestTags.NEXT).performClick()

    assertEquals(DesignerStep.Material, presenter.state.step)
    compose.onNodeWithTag(DesignerTestTags.MATERIAL).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.BASES).assertDoesNotExist()

    compose.onNodeWithTag(DesignerTestTags.BACK).performClick()

    assertEquals(DesignerStep.Shape, presenter.state.step)
  }

  @Test
  fun `the last step is the drawing, its strip, and the ways out`() {
    show(DesignerPresenter(d6, notationOf = { "1${it.id}" }, step = DesignerStep.Faces))

    compose.onNodeWithTag(DesignerTestTags.CANVAS).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.faceOf(0)).assertExists()
    compose.onNodeWithTag(DesignerTestTags.UNDO).assertExists()
    compose.onNodeWithTag(DesignerTestTags.ROLL).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.BACK).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.NEXT).assertDoesNotExist()
  }

  @Test
  fun `the step bar goes anywhere, and says each step's number and name`() {
    val presenter = show(DesignerPresenter(d6))

    compose.onNodeWithTag(DesignerTestTags.stepOf(DesignerStep.Faces)).assertTextContains("3 Faces").performClick()

    assertEquals(DesignerStep.Faces, presenter.state.step)
    compose.onNodeWithTag(DesignerTestTags.stepOf(DesignerStep.Material)).assertTextContains("2 Material")
  }

  @Test
  fun `undo and redo on the last step take a stroke back and put it back`() {
    val presenter = show(DesignerPresenter(d6, step = DesignerStep.Faces))
    presenter.drew(listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)))

    compose.onNodeWithTag(DesignerTestTags.UNDO).performClick()
    assertTrue(presenter.state.face.blank)
    compose.onNodeWithTag(DesignerTestTags.REDO).performClick()

    assertEquals(1, presenter.state.face.marks.size)
  }

  @Test
  fun `a screen nobody gave a way to the tray still rolls into nothing`() {
    // The default `onRoll` is a lambda that does nothing; pressing Roll it on
    // a screen with no tray behind it is a press that goes nowhere, not a crash.
    val presenter = show(DesignerPresenter(d6, notationOf = { "1${it.id}" }, step = DesignerStep.Faces))

    compose.onNodeWithTag(DesignerTestTags.ROLL).performClick()

    assertEquals(DesignerStep.Faces, presenter.state.step)
  }

  @Test
  fun `a recomposition on every step leaves each of them as it was`() {
    // Every step is drawn from one state, so an ordinary recomposition has to
    // skip them; one that skipped wrongly would come back without its controls.
    var tick by mutableStateOf(0)
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d20))
    compose.setContent {
      Column {
        Text("tick $tick")
        DesignerScreen(presenter = presenter)
      }
    }
    listOf(DesignerStep.Shape, DesignerStep.Material, DesignerStep.Faces).forEach { step ->
      compose.runOnIdle { presenter.go(step) }
      compose.runOnIdle { tick++ }
      compose.onNodeWithTag(DesignerTestTags.stepOf(step)).assertIsSelected()
    }
    compose.runOnIdle { presenter.look(DesignerView.Solid) }
    compose.runOnIdle { tick++ }
    compose.onNodeWithTag(DesignerTestTags.SOLID).assertExists()
  }

  private fun show(presenter: DesignerPresenter): DesignerPresenter {
    compose.setContent { DesignerScreen(presenter = presenter) }
    return presenter
  }

  /** Drafts that count their writes. */
  private class Kept : Drafts {
    private val drawings = mutableMapOf<String, Draft>()
    var saves = 0
      private set

    override fun load(die: Die): Draft = drawings[die.id] ?: Draft(die = die)

    override fun save(draft: Draft) {
      saves++
      drawings[draft.die.id] = draft
    }
  }
}
