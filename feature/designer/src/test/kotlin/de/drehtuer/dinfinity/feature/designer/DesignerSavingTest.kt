package de.drehtuer.dinfinity.feature.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Making a drawing real, and throwing the die that carries it
 * (`docs/face-designer.md`, "Save to set" and "Flow", step 4).
 *
 * Apart from [DesignerPresenterTest] because it is a different subject: that
 * one is about the pen and what it leaves on a face, and this is about what
 * happens to the whole drawing afterwards — which set it goes into, what came
 * of putting it there, and what the tray is then told to throw.
 */
class DesignerSavingTest {
  @Test
  fun `Roll it makes the drawing real before it names it`() {
    // The bug this is the fix for: the drafts are the record and
    // `dicesets/mine/` is a view of them, and nothing on this screen ever
    // wrote that view — so the formula named a set in which the drawing did
    // not exist, and the tray threw a plain die
    // (`docs/face-designer.md`, "Flow", step 4).
    val sets = OneSet()
    val presenter = DesignerPresenter(d6, notationOf = { "1${it.id}" }, sets = sets)
    presenter.drew(line())
    val thrown = mutableListOf<String>()

    presenter.roll(thrown::add)

    assertEquals("the drawing was not saved before the throw", 1, sets.saved.size)
    assertEquals(listOf("mine:1d6"), thrown)
  }

  @Test
  fun `Roll it hands over the drawing that is on the canvas`() {
    val sets = OneSet()
    val presenter = DesignerPresenter(d6, notationOf = { "1${it.id}" }, sets = sets)
    presenter.drew(line())

    presenter.roll {}

    assertFalse("a blank draft was handed over", sets.saved.single().blank)
  }

  @Test
  fun `a save that comes to nothing still throws the die`() {
    // The artwork is what is lost, not the roll: a plain `1d6` is still a die
    // the tray can throw, and refusing to throw it would be the worse answer.
    val sets = OneSet(answer = { SaveResult.Refused })
    val presenter = DesignerPresenter(d6, notationOf = { "1${it.id}" }, sets = sets)
    val thrown = mutableListOf<String>()

    presenter.roll(thrown::add)

    assertEquals(listOf("1d6"), thrown)
  }

  @Test
  fun `with nowhere to save, Roll it is what it always was`() {
    val presenter = DesignerPresenter(d6, notationOf = { "1${it.id}" })
    val thrown = mutableListOf<String>()

    presenter.roll(thrown::add)

    assertEquals(listOf("1d6"), thrown)
    assertTrue("a designer with no library offers a save", presenter.writable.isEmpty())
  }

  @Test
  fun `a die no formula can name is not thrown, with or without a library`() {
    // The screen does not draw the button at all in this case, so nothing
    // should ever call this — which is exactly why the presenter has to be
    // the one that says so rather than trusting the screen to.
    val thrown = mutableListOf<String>()

    DesignerPresenter(d6).roll(thrown::add)

    assertTrue("something was thrown for a die notation cannot name", thrown.isEmpty())
  }

  @Test
  fun `a die no formula can name is not thrown by a save that worked`() {
    val sets = OneSet(answer = { SaveResult.Saved(OneSet.MINE, rollable = null) })
    val presenter = DesignerPresenter(d6, sets = sets)
    val thrown = mutableListOf<String>()

    presenter.roll(thrown::add)

    assertTrue("something was thrown for a die notation cannot name", thrown.isEmpty())
  }

  @Test
  fun `the save sheet opens on the first writable set`() {
    val presenter = DesignerPresenter(d6, sets = OneSet())

    presenter.offerSave()

    assertEquals(OneSet.MINE.id, presenter.state.saving?.into)
    assertNull("it opened with an answer to a question nobody asked", presenter.state.saving?.done)
  }

  @Test
  fun `saving says which set it went to, and the sheet stays open on the answer`() {
    val presenter = DesignerPresenter(d6, sets = OneSet())
    presenter.drew(line())
    presenter.offerSave()

    presenter.save()

    assertEquals(SaveResult.Saved(OneSet.MINE, "mine:1d6"), presenter.state.saving?.done)
    assertFalse("it was still busy afterwards", presenter.state.saving?.busy ?: true)
  }

  @Test
  fun `a refusal is a reason rather than a silent nothing`() {
    val presenter = DesignerPresenter(d6, sets = OneSet(answer = { SaveResult.Blank }))
    presenter.offerSave()

    presenter.save()

    assertEquals(SaveResult.Blank, presenter.state.saving?.done)
  }

  @Test
  fun `choosing another set forgets the answer that was about the last one`() {
    val presenter = DesignerPresenter(d6, sets = OneSet())
    presenter.offerSave()
    presenter.save()

    presenter.saveInto(ELSEWHERE)

    assertEquals(ELSEWHERE, presenter.state.saving?.into)
    assertNull("the old answer was left under the new set", presenter.state.saving?.done)
  }

  @Test
  fun `dismissing the sheet closes it`() {
    val presenter = DesignerPresenter(d6, sets = OneSet())
    presenter.offerSave()

    presenter.stopSaving()

    assertNull(presenter.state.saving)
  }

  @Test
  fun `saving with the sheet shut does nothing`() {
    val sets = OneSet()
    val presenter = DesignerPresenter(d6, sets = sets)

    presenter.save()

    assertTrue("a save ran with nothing asking for one", sets.saved.isEmpty())
  }

  @Test
  fun `which face it is is the face's own label, not its place in the list`() {
    // What the header says, and why it says a label: a set may call a face
    // `crit`, so "face 3 of 6" is a fact about a list where "face crit of 6"
    // is a fact about this die (`docs/face-designer.md`).
    val presenter = DesignerPresenter(lettered)

    presenter.show(2)

    assertEquals("crit", presenter.state.label)
  }

  @Test
  fun `re-opening the sheet opens it on the first writable set again`() {
    // A sheet is opened to *choose*, and the choice that matters is the one
    // made with the sheet in front of you — not one taken and abandoned
    // before it was ever saved to.
    val presenter = DesignerPresenter(d6, sets = OneSet())
    presenter.offerSave()
    presenter.saveInto(ELSEWHERE)
    presenter.stopSaving()

    presenter.offerSave()

    assertEquals(OneSet.MINE.id, presenter.state.saving?.into)
  }

  @Test
  fun `a sheet over nowhere has nowhere chosen, and saving does nothing`() {
    // Not a state the screen can reach — no writable set means no Save
    // button — but the presenter is what says so, and a save that ran
    // against no set at all is what it must not do.
    val presenter = DesignerPresenter(d6)

    presenter.offerSave()
    presenter.save()

    assertNull("a set was chosen out of none", presenter.state.saving?.into)
    assertNull("something was saved", presenter.state.saving?.done)
  }

  @Test
  fun `a cell no face answers to falls back to its place in the list`() {
    // Not reachable through `show`, which refuses a cell off the end — but
    // the label is read on every frame the header draws, and a state built
    // from a shorter die must not take the screen down.
    assertEquals("100", DesignerState(draft = Draft(die = d6), cell = 99).label)
  }

  @Test
  fun `a designer with nowhere to save saves nowhere`() {
    assertEquals(SaveResult.Blank, runBlocking { DesignerSets.NONE.save("mine", Draft(die = d6)) })
    assertTrue(DesignerSets.NONE.writable.isEmpty())
  }

  @Test
  fun `each answer a save can come to has a sentence of its own`() {
    // The decision, then the drawing: which of the three is said is a `when`
    // a plain test reads, where a composable looking three strings up is a
    // place a test cannot (`docs/architecture.md`).
    val said =
      listOf(
        SaveResult.Saved(OneSet.MINE, "mine:1d6"),
        SaveResult.Blank,
        SaveResult.Refused,
      ).map(::sentenceOf)

    assertEquals("two answers say the same thing", said.size, said.distinct().size)
  }

  /** A library with one writable set and no disk behind it. */
  private class OneSet(
    private val answer: (Draft) -> SaveResult = { SaveResult.Saved(MINE, "mine:1${it.die.id}") },
  ) : DesignerSets {
    val saved: MutableList<Draft> = mutableListOf()

    override val writable: List<WritableSet> = listOf(MINE)

    override suspend fun save(
      setId: String,
      draft: Draft,
    ): SaveResult {
      saved += draft
      return answer(draft)
    }

    companion object {
      val MINE = WritableSet(id = "mine", name = "My dice")
    }
  }

  private val d6 = BuiltinDiceSet.set.dice.first { it.shape == DieShape.Cube }

  /** A die one of whose faces is a word, which is a thing a set may do. */
  private val lettered =
    d6.copy(faces = d6.faces.mapIndexed { at, face -> if (at == 2) face.copy(label = "crit") else face })

  /** One line, which is the smallest thing a face can have on it. */
  private fun line(): List<Dot> = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f))

  private companion object {
    /** A second writable set, which no install has yet — but the sheet takes one. */
    const val ELSEWHERE = "somewhere-else"
  }
}
