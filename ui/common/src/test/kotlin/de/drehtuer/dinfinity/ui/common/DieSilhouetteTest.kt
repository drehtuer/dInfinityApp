package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.notation.Sides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Which picture each die wears on the picker row
 * (`design/dInfinity.dc.html`, option 1h).
 *
 * The picture is what a player picks a die out of a row by, so the mapping is
 * asserted on its own, and the drawing — three Compose calls — once, on the
 * pixels. These are the prototype's outlines, and the test that matters is that
 * each die still gets its own one: two dice sharing a silhouette is a row that
 * cannot be read.
 */
@RunWith(RobolectricTestRunner::class)
class DieSilhouetteTest {
  @get:Rule
  val compose = createComposeRule()

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
  fun `a die known only by its id is drawn as a die too`() {
    // `skull-d6` says nothing about the solid; the picker passes the face
    // count instead when it knows it, and this is the floor when it does not.
    assertEquals(outlineOf(Sides.Numeric(6)), outlineOf(Sides.Named("skull-d6")))
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

  /**
   * The drawing, read off the pixels, at the one place the two kinds of
   * picture differ: a little way in from the corner, which is inside the
   * cube's square and on the coin's rim. A coin drawn as a cube — or a cube
   * whose corners came out round — fails here.
   */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Config(qualifiers = "xxhdpi")
  fun `the coin is drawn round and the cube square, both filled in`() {
    compose.setContent {
      Row {
        DieSilhouette(Sides.Numeric(COIN), fill = FILL, ink = INK, modifier = Modifier.size(SIDE).testTag("coin"))
        DieSilhouette(Sides.Numeric(6), fill = FILL, ink = INK, modifier = Modifier.size(SIDE).testTag("cube"))
      }
    }

    val coin = compose.onNodeWithTag("coin").captureToImage().toPixelMap()
    val cube = compose.onNodeWithTag("cube").captureToImage().toPixelMap()
    val near = (coin.width * NEAR_THE_CORNER).toInt()

    assertEquals("the coin is not filled in", FILL, coin[coin.width / 2, coin.height / 2])
    assertEquals("the cube is not filled in", FILL, cube[cube.width / 2, cube.height / 2])
    assertEquals("the cube has no corner where a cube has one", FILL, cube[near, near])
    assertNotEquals("the coin has a corner", FILL, coin[near, near])
  }

  private companion object {
    const val COIN = 2
    const val UNKNOWN = 30
    const val DRAWN = 100f
    const val BOX = 64f
    const val TOLERANCE = 0.01f

    /** Inside the cube's square (inset 14 of 100) and past the fill of the coin's disc. */
    const val NEAR_THE_CORNER = 0.19f
    val SIDE = 100.dp
    val FILL = Color(0xFFEC3013)
    val INK = Color(0xFF101010)
  }
}
