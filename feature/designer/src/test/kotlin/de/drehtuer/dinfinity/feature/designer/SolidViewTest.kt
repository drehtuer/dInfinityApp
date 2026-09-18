package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Solid tab of the face designer (`docs/face-designer.md`, "The solid, not
 * just the face"; `design/dInfinityPhone.dc.html`).
 *
 * The picture itself is a `Canvas` draw lambda and the arithmetic under it is
 * `SolidStageTest`'s. What is asserted here is the furniture: that the two tabs
 * swap what is under them and nothing else, that the stage is one drag surface
 * with nothing on it to hit, and that dragging it takes the die off its own
 * spin.
 *
 * **The turn the die makes on its own is not driven here.** It is an infinite
 * animation, which a test is idle in the middle of by design, so how far a
 * moment of it turns the die is `DesignerPresenterTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
class SolidViewTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the screen opens on the flat editor`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.viewOf(DesignerView.Face)).assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.viewOf(DesignerView.Solid)).assertIsNotSelected()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.SOLID).assertDoesNotExist()
  }

  @Test
  fun `the Solid tab puts the die in the hand and takes the tools away`() {
    show(d6)

    solid()

    compose.onNodeWithTag(DesignerTestTags.SOLID).assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.SOLID_NOTE).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.CLIPBOARD).assertDoesNotExist()
  }

  @Test
  fun `the face strip is under both tabs`() {
    // Which face is in front of the player is where the screen is steered
    // from, on either tab: nothing on the die itself is selectable.
    show(d6)

    solid()

    (0 until 6).forEach { compose.onNodeWithTag(DesignerTestTags.faceOf(it)).performScrollTo().assertIsDisplayed() }
  }

  @Test
  fun `the strip still moves between faces while the die is in the hand`() {
    val presenter = show(d6)
    solid()

    compose.onNodeWithTag(DesignerTestTags.faceOf(4)).performScrollTo().performClick()

    assertEquals("the strip does not steer the solid", 4, presenter.state.cell)
  }

  @Test
  fun `nothing on the die can be tapped`() {
    // One drag surface: a tap that sometimes rotates and sometimes selects is
    // a tap nobody trusts, so the stage answers a drag and nothing else.
    show(d20)

    solid()

    compose.onNodeWithTag(DesignerTestTags.SOLID).assertHasNoClickAction()
  }

  @Test
  fun `dragging the die turns it, and unticks Spin`() {
    val presenter = show(d20)
    solid()
    val before = presenter.state.turn

    compose.onNodeWithTag(DesignerTestTags.SOLID).performTouchInput {
      swipe(start = center, end = center + Offset(width / 4f, 0f))
    }

    assertNotEquals("the drag did not turn the die", before, presenter.state.turn)
    assertFalse("the die went on turning under the finger", presenter.state.spinning)
  }

  @Test
  fun `Spin can be put back on again`() {
    val presenter = show(d6)
    solid()
    presenter.spin(false)

    compose
      .onNodeWithTag(DesignerTestTags.SPIN)
      .performScrollTo()
      .assertIsNotSelected()
      .performClick()

    assertTrue("the tick did not start the die turning", presenter.state.spinning)
    compose.onNodeWithTag(DesignerTestTags.SPIN).assertIsSelected()
  }

  @Test
  fun `the die stays as it was left when the tab is left and come back to`() {
    val presenter = show(d20)
    solid()
    presenter.turned(across = 0.3f, down = 0.1f)
    val held = presenter.state.turn

    compose.onNodeWithTag(DesignerTestTags.viewOf(DesignerView.Face)).performClick()
    compose.onNodeWithTag(DesignerTestTags.CANVAS).assertIsDisplayed()
    solid()

    assertEquals("the die was put down between looks at it", held, presenter.state.turn)
  }

  /** Opens the Solid tab, which is what every test here does first. */
  private fun solid() = compose.onNodeWithTag(DesignerTestTags.viewOf(DesignerView.Solid)).performClick()

  private fun show(die: Die): DesignerPresenter {
    val presenter = DesignerPresenter(die)
    compose.setContent { DesignerScreen(presenter = presenter) }
    return presenter
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }
  private val d20 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron }
}
