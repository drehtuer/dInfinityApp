package de.drehtuer.dinfinity.feature.designer

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.MaterialPreset
import de.drehtuer.dinfinity.designer.Roundness
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Material menu and the Edges control on the Solid tab
 * (`docs/face-designer.md`, "Material, colour and edges"; `design/dInfinityPhone.dc.html`).
 */
@RunWith(RobolectricTestRunner::class)
class FinishPaneTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the controls are on the Solid tab and not on the Face tab`() {
    show(d6)
    compose.onNodeWithTag(DesignerTestTags.MATERIAL).assertDoesNotExist()

    solid()

    compose.onNodeWithTag(DesignerTestTags.MATERIAL).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(DesignerTestTags.edgesOf(Roundness.Standard)).performScrollTo().assertIsDisplayed()
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
    Roundness.entries.forEach { compose.onNodeWithTag(DesignerTestTags.edgesOf(it)).assertIsNotSelected() }
    compose.onNodeWithTag(DesignerTestTags.FINISH_NOTE).performScrollTo().assertTextContains("5 %", substring = true)
  }

  @Test
  fun `the edges are one choice of four, and choosing one changes the die`() {
    val presenter = show(d6)
    solid()
    compose.onNodeWithTag(DesignerTestTags.edgesOf(Roundness.Standard)).performScrollTo().assertIsSelected()

    compose.onNodeWithTag(DesignerTestTags.edgesOf(Roundness.VeryRounded)).performScrollTo().performClick()

    assertEquals(Roundness.VeryRounded, presenter.state.roundness)
    compose.onNodeWithTag(DesignerTestTags.edgesOf(Roundness.VeryRounded)).assertIsSelected()
    compose.onNodeWithTag(DesignerTestTags.edgesOf(Roundness.Standard)).assertIsNotSelected()
    compose
      .onNodeWithTag(DesignerTestTags.edgesOf(Roundness.VeryRounded))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
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
    show(d6)
    solid()

    compose.onNodeWithTag(DesignerTestTags.MORE_BODY_COLOURS).performScrollTo().performClick()

    compose.onNodeWithTag(DesignerTestTags.BODY_PICKER.sheet).assertExists()
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

  private fun solid() = compose.onNodeWithTag(DesignerTestTags.viewOf(DesignerView.Solid)).performClick()

  private fun show(die: Die): DesignerPresenter {
    val presenter = DesignerPresenter(die)
    compose.setContent { DesignerScreen(presenter = presenter) }
    return presenter
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }

  private companion object {
    /** One of the twelve. */
    const val GREEN: Int = 0xFF1F5E3A.toInt()
  }
}
