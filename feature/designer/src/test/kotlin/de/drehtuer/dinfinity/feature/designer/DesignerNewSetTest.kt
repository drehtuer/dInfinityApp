package de.drehtuer.dinfinity.feature.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.NewSet
import de.drehtuer.dinfinity.designer.PersonalSetId
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "New set…" in the Save-to-set sheet, as the presenter keeps it
 * (`docs/face-designer.md`, "Save to set"; `docs/architecture.md`,
 * decision 79).
 *
 * Apart from [DesignerSavingTest] because it is one more question the sheet
 * asks — not *which* set, but *what to call* one that does not exist yet.
 */
class DesignerNewSetTest {
  @Test
  fun `choosing New set turns Save into making one, and Save waits for a name`() {
    val presenter = DesignerPresenter(d6, sets = Naming())
    presenter.offerSave()

    presenter.nameNewSet()

    val saving = presenter.state.saving ?: error("the sheet closed")
    assertTrue(saving.naming)
    assertNull("a set stayed chosen beside New set", saving.into)
    assertFalse("Save can be pressed with no name", saving.canSave)
    presenter.newSetName("   ")
    assertFalse("a name of spaces is a name", presenter.state.saving?.canSave ?: true)
  }

  @Test
  fun `the id under the field is the one the name comes to`() {
    val presenter = DesignerPresenter(d6, sets = Naming())
    presenter.offerSave()
    presenter.nameNewSet()

    presenter.newSetName("Brass & Bone")

    assertEquals("brass-bone", presenter.state.saving?.newId)
    assertTrue(presenter.state.saving?.canSave ?: false)
  }

  @Test
  fun `a set that was made is chosen from then on`() {
    val sets = Naming()
    val presenter = DesignerPresenter(d6, sets = sets)
    presenter.offerSave()
    presenter.nameNewSet()
    presenter.newSetName("Props")

    presenter.save()

    val saving = presenter.state.saving ?: error("the sheet closed")
    assertEquals("props", saving.into)
    assertFalse("the field stayed open on a set that exists", saving.naming)
    assertEquals(SaveResult.Saved(WritableSet("props", "Props"), "props:1d6"), saving.done)
    assertEquals(listOf("Props"), sets.named)
  }

  @Test
  fun `a set made with nothing drawn in it is still chosen`() {
    // The set exists whatever the save into it came to, so the next press
    // writes into it rather than trying to make it again.
    val presenter = DesignerPresenter(d6, sets = Naming(answer = { SaveResult.Blank }))
    presenter.offerSave()
    presenter.nameNewSet()
    presenter.newSetName("Props")

    presenter.save()

    assertEquals("props", presenter.state.saving?.into)
    assertEquals(SaveResult.Blank, presenter.state.saving?.done)
  }

  @Test
  fun `an answer for a set that cannot be found leaves the name where it was`() {
    val presenter = DesignerPresenter(d6, sets = Naming(answer = { SaveResult.Refused }, adds = false))
    presenter.offerSave()
    presenter.nameNewSet()
    presenter.newSetName("Props")

    presenter.save()

    val saving = presenter.state.saving ?: error("the sheet closed")
    assertTrue(saving.naming)
    assertEquals(SaveResult.Refused, saving.done)
  }

  @Test
  fun `a refused name keeps the name and says why, until it is changed`() {
    val presenter = DesignerPresenter(d6, sets = Naming(refuse = NewSet.Taken("mine")))
    presenter.offerSave()
    presenter.nameNewSet()
    presenter.newSetName("Mine")

    presenter.save()

    val saving = presenter.state.saving ?: error("the sheet closed")
    assertEquals(SaveResult.NotMade(NewSet.Taken("mine")), saving.done)
    assertEquals("Mine", saving.name)
    assertTrue(saving.naming)
    presenter.newSetName("Mine too")
    assertNull("the reason outlived the name it was about", presenter.state.saving?.done)
  }

  @Test
  fun `a name typed and abandoned for a set is still there when New set is chosen again`() {
    val presenter = DesignerPresenter(d6, sets = Naming())
    presenter.offerSave()
    presenter.nameNewSet()
    presenter.newSetName("Props")

    presenter.saveInto("mine")
    assertFalse(presenter.state.saving?.naming ?: true)
    presenter.nameNewSet()

    assertEquals("Props", presenter.state.saving?.name)
  }

  @Test
  fun `with the sheet shut, the name field does nothing`() {
    val presenter = DesignerPresenter(d6, sets = Naming())

    presenter.nameNewSet()
    presenter.newSetName("Props")

    assertNull(presenter.state.saving)
  }

  @Test
  fun `a busy sheet cannot be saved again`() {
    assertFalse(Saving(into = "mine", busy = true).canSave)
    assertFalse(Saving(into = null, busy = true, naming = true, name = "Props").canSave)
    assertTrue(Saving(into = "mine").canSave)
    assertFalse(Saving(into = null).canSave)
  }

  /** Sets that are made under the id the name comes to, unless told otherwise. */
  private class Naming(
    private val answer: ((String) -> SaveResult)? = null,
    private val refuse: NewSet? = null,
    private val adds: Boolean = true,
  ) : DesignerSets {
    private val sets = mutableListOf(WritableSet("mine", "My dice"))
    val named: MutableList<String> = mutableListOf()

    override val writable: List<WritableSet> get() = sets.toList()

    override suspend fun save(
      setId: String,
      draft: Draft,
    ): SaveResult = answer?.invoke(setId) ?: SaveResult.Saved(sets.first { it.id == setId }, "$setId:1${draft.die.id}")

    override suspend fun create(
      name: String,
      draft: Draft,
    ): SaveResult {
      refuse?.let { return SaveResult.NotMade(it) }
      named += name
      if (adds) sets += WritableSet(PersonalSetId.of(name), name)
      return save(PersonalSetId.of(name), draft)
    }
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }
}
