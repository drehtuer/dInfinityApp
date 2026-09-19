package de.drehtuer.dinfinity.feature.roll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two pull-ups, one bottom edge ([BottomEdge]).
 *
 * The rule is small and it is the kind of thing that is wrong in one corner:
 * a result that lands on a strip somebody is reading, or a strip pulled up
 * over a total. So it is arithmetic rather than two composables each
 * remembering to shut the other, and this is where it is asked.
 */
class BottomEdgeTest {
  @Test
  fun `the screen opens with the felt and nothing over it`() {
    val edge = BottomEdge()

    assertEquals(SheetRest.Down, edge.result)
    assertEquals(SheetRest.Down, edge.saved)
  }

  @Test
  fun `a result that lands takes the edge and parks the saved rolls`() {
    // Nobody should have to reach for the number they have just rolled, and
    // a sheet arriving under a strip of saved rolls is a number nobody sees.
    val edge = BottomEdge().savedTo(SheetRest.Up).resultArrives()

    assertEquals(SheetRest.Up, edge.result)
    assertEquals(SheetRest.Down, edge.saved)
  }

  @Test
  fun `pulling the saved rolls up pushes the result down`() {
    val edge = BottomEdge().resultArrives().savedTo(SheetRest.Up)

    assertEquals(SheetRest.Down, edge.result)
    assertEquals(SheetRest.Up, edge.saved)
  }

  @Test
  fun `pushing one down leaves the other exactly where it was`() {
    // Only *opening* one moves the other. A sheet that dragged its neighbour
    // open on the way down would be a control nobody asked for.
    val edge = BottomEdge().savedTo(SheetRest.Up).resultTo(SheetRest.Down)

    assertEquals(SheetRest.Up, edge.saved)

    val other = BottomEdge().resultArrives().savedTo(SheetRest.Down)

    assertEquals(SheetRest.Up, other.result)
  }

  @Test
  fun `a roll put away takes its sheet with it and leaves the saved rolls alone`() {
    // A strip that sprang open every time a total went away would be a strip
    // that opens itself once per throw.
    val edge = BottomEdge().savedTo(SheetRest.Up).resultGone()

    assertEquals(SheetRest.Down, edge.result)
    assertEquals(SheetRest.Up, edge.saved)
  }

  @Test
  fun `no sequence of moves ever leaves both of them up`() {
    // The one thing this type exists for, asked of every order the two
    // controls can be pressed in rather than of the two the screen happens
    // to reach first.
    val moves: List<Pair<String, (BottomEdge) -> BottomEdge>> =
      listOf(
        "result up" to { it.resultTo(SheetRest.Up) },
        "result down" to { it.resultTo(SheetRest.Down) },
        "saved up" to { it.savedTo(SheetRest.Up) },
        "saved down" to { it.savedTo(SheetRest.Down) },
        "a roll lands" to { it.resultArrives() },
        "the roll is put away" to { it.resultGone() },
      )
    var edge = BottomEdge()
    val taken = mutableListOf<String>()
    repeat(TURNS) { turn ->
      val (name, move) = moves[turn % moves.size]
      taken += name
      edge = move(edge)
      assertTrue("both sheets were up after ${taken.joinToString(", ")}", edge.apart)
    }
    // And again in the other order, so it is not one walk through the list.
    edge = BottomEdge()
    moves.reversed().forEach { (name, move) ->
      edge = move(edge)
      assertTrue("both sheets were up after $name", edge.apart)
    }
  }

  @Test
  fun `and the invariant knows what it would look like to have failed`() {
    // Nothing the transitions do can build this, which is the point of them
    // — so it is built by hand, once, to say what `apart` is actually
    // asserting in the walk above.
    assertFalse("both up was not noticed", BottomEdge(SheetRest.Up, SheetRest.Up).apart)
    assertTrue(BottomEdge(SheetRest.Up, SheetRest.Down).apart)
    assertTrue(BottomEdge(SheetRest.Down, SheetRest.Up).apart)
    assertTrue(BottomEdge(SheetRest.Down, SheetRest.Down).apart)
  }

  private companion object {
    /** Long enough to wrap the list of moves several times over. */
    const val TURNS = 40
  }
}
