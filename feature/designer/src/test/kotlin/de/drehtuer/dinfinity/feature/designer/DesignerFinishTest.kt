package de.drehtuer.dinfinity.feature.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.DieFinish
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.Drafts
import de.drehtuer.dinfinity.designer.MaterialPreset
import de.drehtuer.dinfinity.designer.Roundness
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Material menu and the Edges control, behind the screen
 * (`docs/face-designer.md`, "Material and edges").
 */
class DesignerFinishTest {
  @Test
  fun `a built-in die opens as standard plastic`() {
    val presenter = DesignerPresenter(d6)

    assertEquals(MaterialPreset.Plastic, presenter.state.preset)
    assertEquals(Roundness.Standard, presenter.state.roundness)
    assertNull(presenter.state.draft.finish)
  }

  @Test
  fun `a die copied from somebody else's set opens as custom`() {
    val brass =
      Die.standard(
        "brass-d6",
        DieShape.Cube,
        DieMaterial(metallic = 0.8, roughness = 0.2, edgeRounding = 0.05),
      )

    val presenter = DesignerPresenter(brass)

    assertNull(presenter.state.preset)
    assertNull(presenter.state.roundness)
  }

  @Test
  fun `choosing a material changes what the die is made of and is written down`() {
    val drafts = Remembered()
    val presenter = DesignerPresenter(d6, drafts = drafts)

    presenter.madeOf(MaterialPreset.Glass)

    assertEquals(MaterialPreset.Glass, presenter.state.preset)
    assertEquals(Roundness.Standard, presenter.state.roundness)
    assertEquals(presenter.state.draft, drafts.load(d6))
  }

  @Test
  fun `choosing the edges changes how round it is and keeps the material`() {
    val drafts = Remembered()
    val presenter = DesignerPresenter(d6, drafts = drafts)
    presenter.madeOf(MaterialPreset.Metal)

    presenter.rounded(Roundness.VeryRounded)

    assertEquals(MaterialPreset.Metal, presenter.state.preset)
    assertEquals(Roundness.VeryRounded, presenter.state.roundness)
    assertEquals(0.12, drafts.load(d6).finish?.edgeRounding)
  }

  @Test
  fun `choosing what is already shown makes it a choice, once`() {
    val drafts = Remembered()
    val presenter = DesignerPresenter(d6, drafts = drafts)

    presenter.madeOf(MaterialPreset.Plastic)
    val saves = drafts.saves
    presenter.madeOf(MaterialPreset.Plastic)

    assertEquals(DieFinish.STANDARD, presenter.state.draft.finish)
    assertEquals("a second press of the same name wrote again", saves, drafts.saves)
  }

  @Test
  fun `a custom die keeps its own rounding when a material is chosen`() {
    val brass =
      Die.standard(
        "brass-d6",
        DieShape.Cube,
        DieMaterial(metallic = 0.8, roughness = 0.2, edgeRounding = 0.05),
      )
    val presenter = DesignerPresenter(brass)

    presenter.madeOf(MaterialPreset.Stone)

    assertEquals(MaterialPreset.Stone, presenter.state.preset)
    assertEquals(0.05, presenter.state.draft.shownFinish.edgeRounding, 0.0)
    assertNull(presenter.state.roundness)
  }

  @Test
  fun `the finish belongs to the die, and another die has its own`() {
    val drafts = Remembered()
    val presenter = DesignerPresenter(d6, choosable = listOf(d6, d20), drafts = drafts)
    presenter.madeOf(MaterialPreset.Resin)

    presenter.base(d20)
    assertEquals(MaterialPreset.Plastic, presenter.state.preset)

    presenter.base(d6)
    assertEquals(MaterialPreset.Resin, presenter.state.preset)
  }

  @Test
  fun `the swatch lets the chequer through as far as light goes through the die`() {
    val plastic = SwatchLook.of(DieFinish.STANDARD)
    val glass = SwatchLook.of(DieFinish.STANDARD.madeOf(MaterialPreset.Glass))
    val metal = SwatchLook.of(DieFinish.STANDARD.madeOf(MaterialPreset.Metal))
    val stone = SwatchLook.of(DieFinish.STANDARD.madeOf(MaterialPreset.Stone))

    assertEquals(1f, plastic.body, 0f)
    assertTrue("glass hid the chequer", glass.body < plastic.body)
    assertTrue("glass vanished", glass.body > 0f)
    assertTrue("metal did not shine more than plastic", metal.glint > plastic.glint)
    assertTrue("stone shone like plastic", stone.glint < plastic.glint)
    assertTrue("stone's highlight was not broader", stone.spread > glass.spread)
  }

  /** Drafts that outlive a presenter but not the test, counting what was written. */
  private class Remembered : Drafts {
    private val kept = mutableMapOf<String, Draft>()
    var saves = 0
      private set

    override fun load(die: Die): Draft = kept[die.id] ?: Draft(die = die)

    override fun save(draft: Draft) {
      saves++
      kept[draft.die.id] = draft
    }
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }
  private val d20 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Icosahedron }
}
