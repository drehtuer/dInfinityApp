package de.drehtuer.dinfinity.feature.saved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The arithmetic of a drag, without a finger
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * A plain JVM test and no Robolectric, which is the whole reason the
 * arithmetic is not inside the gesture: what a reorder should come to is a
 * question about a list, and a question about a list can be asked directly.
 */
class SavedOrderTest {
  private val list = listOf("a", "b", "c", "d")

  @Test
  fun `a row dragged up lands where the finger is, and the rest close behind it`() {
    assertEquals(listOf("a", "d", "b", "c"), SavedOrder.moved(list, "d", 1))
  }

  @Test
  fun `a row dragged down lands where the finger is`() {
    assertEquals(listOf("b", "c", "a", "d"), SavedOrder.moved(list, "a", 2))
  }

  @Test
  fun `a row dragged to the top is the top`() {
    assertEquals(listOf("c", "a", "b", "d"), SavedOrder.moved(list, "c", 0))
  }

  @Test
  fun `a row dragged to the bottom is the bottom`() {
    assertEquals(listOf("a", "c", "d", "b"), SavedOrder.moved(list, "b", 3))
  }

  @Test
  fun `a finger past the ends means the ends`() {
    // Dragging off the top of the screen is how somebody moves a row to the
    // top without being able to aim at a row that is half off it.
    assertEquals(listOf("b", "a", "c", "d"), SavedOrder.moved(list, "b", -4))
    assertEquals(listOf("a", "c", "d", "b"), SavedOrder.moved(list, "b", 99))
  }

  @Test
  fun `a drag that ends where it began leaves the very list it was given`() {
    // Not an equal list: a reorder that returned a copy every frame would have
    // the screen recomposing through a drag that changed nothing.
    assertSame(list, SavedOrder.moved(list, "b", 1))
  }

  @Test
  fun `a list of one is a list that cannot be reordered`() {
    val one = listOf("only")
    assertSame(one, SavedOrder.moved(one, "only", 0))
    assertSame(one, SavedOrder.moved(one, "only", 7))
  }

  @Test
  fun `a row that is not in the list moves nothing`() {
    // The row a drag began on can be deleted under it — by an import, by
    // another screen — and a drag going on about a row that is gone must not
    // put it back.
    assertSame(list, SavedOrder.moved(list, "gone", 2))
    assertSame(emptyList<String>(), SavedOrder.moved(emptyList(), "gone", 0))
  }

  @Test
  fun `every row is still there afterwards, once`() {
    val moved = SavedOrder.moved(list, "a", 3)
    assertEquals(list.sorted(), moved.sorted())
    assertEquals(list.size, moved.size)
  }
}
