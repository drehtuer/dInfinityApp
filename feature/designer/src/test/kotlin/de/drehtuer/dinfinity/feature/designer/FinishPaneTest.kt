package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.MaterialPreset
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The Material menu, the body colour and the Edges slider on the designer's
 * second step (`docs/face-designer.md`, "Material, colour and edges";
 * `design/dInfinityPhone.dc.html`).
 */
@RunWith(RobolectricTestRunner::class)
// A real bitmap behind the screen, so the swatch and the turning die are drawn
// rather than recorded.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FinishPaneTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the controls are on the Material step and not on the Faces step`() {
    show(d6)

    compose.onNodeWithTag(DesignerTestTags.MATERIAL).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.EDGES).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.MORE_BODY_COLOURS).performScrollTo().assertIsDisplayed()

    compose.onNodeWithTag(DesignerTestTags.stepOf(DesignerStep.Faces)).performClick()
    compose.onNodeWithTag(DesignerTestTags.viewOf(DesignerView.Solid)).performClick()

    compose.onNodeWithTag(DesignerTestTags.MATERIAL).assertDoesNotExist()
    compose.onNodeWithTag(DesignerTestTags.EDGES).assertDoesNotExist()
  }

  @Test
  fun `the menu says what the die is made of, to the eye and to TalkBack`() {
    show(d6)
    solid()

    compose
      .onNodeWithTag(DesignerTestTags.MATERIAL)
      .performScrollTo()
      .assertContentDescriptionEquals("Material: Plastic")
      .assertHasClickAction()
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
  }

  @Test
  fun `choosing from the menu changes the material`() {
    val presenter = show(d6)
    solid()

    compose.onNodeWithTag(DesignerTestTags.MATERIAL).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.materialOf(MaterialPreset.Glass)).performClick()

    assertEquals(MaterialPreset.Glass, presenter.state.preset)
    compose.onNodeWithTag(DesignerTestTags.MATERIAL).assertContentDescriptionEquals("Material: Glass")
  }

  @Test
  fun `the menu offers every material`() {
    show(d6)
    solid()

    compose.onNodeWithTag(DesignerTestTags.MATERIAL).performScrollTo().performClick()

    MaterialPreset.entries.forEach { compose.onNodeWithTag(DesignerTestTags.materialOf(it)).assertIsDisplayed() }
  }

  @Test
  fun `a die from somebody else's set is custom until a material is chosen`() {
    show(Die.standard("brass-d6", DieShape.Cube, DieMaterial(metallic = 0.8, roughness = 0.2, edgeRounding = 0.05)))
    solid()

    compose
      .onNodeWithTag(
        DesignerTestTags.MATERIAL,
      ).performScrollTo()
      .assertContentDescriptionEquals("Material: Custom")
    compose.onNodeWithTag(DesignerTestTags.EDGES_SAID).performScrollTo().assertTextContains("5.0 %", substring = true)
  }

  @Test
  fun `the edges are a slider over what a set may ask for, and moving it rounds the die`() {
    val presenter = show(d6)
    solid()
    val range =
      compose
        .onNodeWithTag(DesignerTestTags.EDGES)
        .performScrollTo()
        .fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo]
    assertEquals(0.03f, range.current, 1e-6f)
    assertEquals(0.015f, range.range.start, 1e-6f)
    assertEquals(0.12f, range.range.endInclusive, 1e-6f)
    assertEquals(20, range.steps)

    compose.onNodeWithTag(DesignerTestTags.EDGES).performSemanticsAction(SemanticsActions.SetProgress) { it(0.09f) }

    assertEquals(0.09, presenter.state.edgeRounding, 1e-9)
    compose.onNodeWithTag(DesignerTestTags.EDGES_SAID).assertTextContains("9.0 %", substring = true)
    compose.onNodeWithTag(DesignerTestTags.EDGES_SAID).assertTextContains("1.44 mm", substring = true)
  }

  @Test
  fun `TalkBack hears what the edges are and where the slider stands`() {
    show(d6)
    solid()

    compose
      .onNodeWithTag(DesignerTestTags.EDGES)
      .performScrollTo()
      .assertContentDescriptionEquals("Edges")
      .assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Rounded by 3.0 % of its size: 0.48 mm"),
      )
  }

  @Test
  fun `a body colour is chosen from the twelve, and says which it is`() {
    val presenter = show(d6)
    solid()

    compose.onNodeWithTag(DesignerTestTags.bodyOf(GREEN)).performScrollTo().performClick()

    assertEquals(GREEN, presenter.state.bodyArgb)
    compose.onNodeWithTag(DesignerTestTags.BODY_HEX).performScrollTo().assertTextContains("#1F5E3A")
    compose.onNodeWithContentDescription("Body colour #1F5E3A").assertExists()
  }

  @Test
  fun `a body colour past the twelve is picked in the shared picker`() {
    val presenter = show(d6)
    solid()

    compose.onNodeWithTag(DesignerTestTags.MORE_BODY_COLOURS).performScrollTo().performClick()
    compose.onNodeWithTag(DesignerTestTags.BODY_PICKER.sheet).assertExists()
    compose.onNodeWithTag(DesignerTestTags.BODY_PICKER.cancel).performClick()

    compose.onNodeWithTag(DesignerTestTags.BODY_PICKER.sheet).assertDoesNotExist()
    assertEquals(DieMaterial.DEFAULT_COLOR_ARGB, presenter.state.bodyArgb)

    compose.onNodeWithTag(DesignerTestTags.MORE_BODY_COLOURS).performScrollTo().performClick()
    compose
      .onNodeWithTag(DesignerTestTags.BODY_PICKER.brightness)
      .performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
    compose.onNodeWithTag(DesignerTestTags.BODY_PICKER.use).performClick()

    compose.onNodeWithTag(DesignerTestTags.BODY_PICKER.sheet).assertDoesNotExist()
    assertTrue("the colour picked was not the die's", presenter.state.bodyArgb != DieMaterial.DEFAULT_COLOR_ARGB)
  }

  @Test
  fun `the swatch is silent, because the menu says it in words`() {
    show(d6)
    solid()

    compose
      .onNodeWithTag(DesignerTestTags.SWATCH)
      .performScrollTo()
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
  }

  /** The controls are on the Material step, which [show] opens on. */
  private fun solid() = Unit

  private fun show(die: Die): DesignerPresenter {
    val presenter = DesignerPresenter(die, step = DesignerStep.Material)
    compose.setContent { DesignerScreen(presenter = presenter) }
    return presenter
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }

  private companion object {
    /** One of the twelve. */
    const val GREEN: Int = 0xFF1F5E3A.toInt()
  }
}
