package de.drehtuer.dinfinity.feature.roll

import androidx.compose.ui.geometry.Size
import de.drehtuer.dinfinity.core.notation.Sides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which picture each die wears on the picker row
 * (`design/dInfinity.dc.html`, option 1h).
 *
 * The picture is what a player picks a die out of a row by, so the mapping is
 * worth asserting even though the drawing itself — three Compose calls — is
 * not. These are the prototype's outlines, and the test that matters is that
 * each die still gets its own one: two dice sharing a silhouette is a row that
 * cannot be read.
 */
@RunWith(RobolectricTestRunner::class)
class DieSilhouetteTest {
  @Test
  fun `the coin is the one die with no corners`() {
    assertNull("the coin was given corners", outlineOf(Sides.Numeric(COIN)))
  }

  @Test
  fun `each die wears the outline it is recognised by`() {
    assertEquals(3, outlineOf(Sides.Numeric(4))?.size)
    assertEquals(4, outlineOf(Sides.Numeric(6))?.size)
    assertEquals(4, outlineOf(Sides.Numeric(10))?.size)
    assertEquals(5, outlineOf(Sides.Numeric(12))?.size)
    assertEquals(6, outlineOf(Sides.Numeric(8))?.size)
    assertEquals(6, outlineOf(Sides.Numeric(20))?.size)
    assertEquals(8, outlineOf(Sides.Numeric(18))?.size)
  }

  @Test
  fun `d percent is drawn as the d10 it is made of`() {
    assertEquals(outlineOf(Sides.Numeric(10)), outlineOf(Sides.Percentile))
  }

  @Test
  fun `the fudge die is a cube, which is what it is`() {
    assertEquals(outlineOf(Sides.Numeric(6)), outlineOf(Sides.Fudge))
  }

  @Test
  fun `a die the catalogue has no picture for is drawn as a die`() {
    // A gap in the row would read as a broken set. A cube reads as "a die",
    // which is the truth as far as anybody can tell at 26 dp.
    assertNotNull(outlineOf(Sides.Numeric(UNKNOWN)))
  }

  @Test
  fun `no die draws outside the square it is drawn on`() {
    // Every outline is scaled onto the button as a fraction of that square, so
    // a corner outside it is a die that leaks over its neighbour.
    val catalogue = listOf(2, 4, 6, 8, 10, 12, 18, 20).map { Sides.Numeric(it) }
    (catalogue + Sides.Percentile + Sides.Fudge).forEach { sides ->
      outlineOf(sides)?.forEach { corner ->
        assertTrue("$sides has a corner outside its square", corner.x in 0f..DRAWN && corner.y in 0f..DRAWN)
      }
    }
  }

  @Test
  fun `an outline is scaled onto the box it is drawn in`() {
    val path = pathOf(checkNotNull(outlineOf(Sides.Numeric(6))), Size(BOX, BOX))

    val bounds = path.getBounds()
    // The cube's own square is inset on the hundred-unit one, so it does not
    // fill the box — but it has to stay inside it and keep its proportions.
    assertTrue("the cube spilled out of its box", bounds.left >= 0f && bounds.right <= BOX)
    assertEquals(bounds.width, bounds.height, TOLERANCE)
  }

  private companion object {
    const val COIN = 2
    const val UNKNOWN = 30
    const val DRAWN = 100f
    const val BOX = 64f
    const val TOLERANCE = 0.01f
  }
}
