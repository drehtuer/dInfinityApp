package de.drehtuer.dinfinity.feature.graph

import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window between the outcome graph's state and Compose.
 *
 * It decides nothing, which is the thing to check: every call goes to the
 * machine and every answer comes back from it.
 */
class GraphPresenterTest {
  @Test
  fun `a graph opens on the formula it was given`() {
    val presenter = presenter("2d6")

    assertEquals("2d6", presenter.text)
    assertTrue(presenter.state is GraphState.Graphed)
  }

  @Test
  fun `a graph opened with no formula has nothing to draw`() {
    assertEquals(GraphState.Empty, presenter("").state)
  }

  @Test
  fun `the roll that opened it is marked from the very first frame`() {
    // Set before the formula, so the first state published already carries the
    // mark rather than having it arrive a frame later.
    val presenter = presenter("2d6", rolled = 7)

    assertNotNull((presenter.state as GraphState.Graphed).rolled)
  }

  @Test
  fun `retyping the formula regraphs it`() {
    val presenter = presenter("2d6")

    presenter.type("1d20")

    assertEquals("1d20", presenter.text)
    assertEquals(20, (presenter.state as GraphState.Graphed).stats.highest)
  }

  @Test
  fun `retyping a different formula takes the roll's mark with it`() {
    val presenter = presenter("2d6", rolled = 7)

    presenter.type("1d20")

    assertNull((presenter.state as GraphState.Graphed).rolled)
  }

  @Test
  fun `the question can be changed`() {
    val presenter = presenter("2d6")

    presenter.show(GraphMode.AtLeast)

    assertEquals(GraphMode.AtLeast, (presenter.state as GraphState.Graphed).mode)
  }

  @Test
  fun `a bar can be picked`() {
    val presenter = presenter("2d6")

    presenter.pick(7)

    assertEquals(7, (presenter.state as GraphState.Graphed).picked?.value)
  }

  private fun presenter(
    formula: String,
    rolled: Int? = null,
  ) = GraphPresenter(
    machine = GraphMachine(DiceCatalog.of(listOf(BuiltinDiceSet.set))),
    formula = formula,
    rolled = rolled,
  )
}
