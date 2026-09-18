package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A plate is the ground a control stands on over the table.
 *
 * Three things about it are load-bearing and none of them is decoration. It
 * **hugs**, because a plate that filled the width would put a dashed formula
 * rule across the screen — the fault that made `3d6 + 4` read as struck
 * through. It is **opaque**, because it is drawn over a lit 3D table whose
 * colour the player picked, and a translucent one would be tinted by whatever
 * is under it. And it has **no rounded corner**, because nothing in this
 * system has one (`docs/physics-and-rendering.md`, "What is drawn over the
 * table").
 */
@RunWith(RobolectricTestRunner::class)
class PlateTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a plate is no wider than the words on it need`() {
    compose.setContent {
      Box(modifier = Modifier.fillMaxSize()) {
        Plate(modifier = Modifier.testTag(PLATE)) { Text("3d6", modifier = Modifier.testTag(WORDS)) }
      }
    }

    val plate = compose.onNodeWithTag(PLATE).getUnclippedBoundsInRoot()
    val words = compose.onNodeWithTag(WORDS).getUnclippedBoundsInRoot()

    // The padding either side and nothing else. A plate that filled the width
    // would be tens of dp wider than this.
    assertEquals(
      "a plate is not hugging its content",
      (words.right - words.left + SIDES).value.toDouble(),
      (plate.right - plate.left).value.toDouble(),
      HALF_A_PIXEL,
    )
  }

  @Test
  fun `a plate is padded 7 over 11 and 8 under`() {
    compose.setContent {
      Plate(modifier = Modifier.testTag(PLATE)) { Text("3d6", modifier = Modifier.testTag(WORDS)) }
    }

    val plate = compose.onNodeWithTag(PLATE).getUnclippedBoundsInRoot()
    val words = compose.onNodeWithTag(WORDS).getUnclippedBoundsInRoot()

    assertEquals("the top padding is not 7 dp", 7.0, (words.top - plate.top).value.toDouble(), HALF_A_PIXEL)
    assertEquals("the left padding is not 11 dp", 11.0, (words.left - plate.left).value.toDouble(), HALF_A_PIXEL)
    assertEquals("the bottom padding is not 8 dp", 8.0, (plate.bottom - words.bottom).value.toDouble(), HALF_A_PIXEL)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `a plate is opaque in the ground colour, with a square corner`() {
    var ground: Color? = null
    compose.setContent {
      ground = MaterialTheme.colorScheme.background
      Plate(modifier = Modifier.testTag(PLATE)) { Text("3d6") }
    }

    // The top-left pixel: painted, in the ground colour. A rounded corner
    // would show whatever is behind the plate through it, and a translucent
    // one would show the table's colour mixed into it.
    val corner = compose.onNodeWithTag(PLATE).captureToImage().toPixelMap()[1, 1]
    assertEquals("a plate is not an opaque --color-bg ground", ground, corner)
  }

  @Test
  fun `a plate is tall enough for the words plus its padding`() {
    compose.setContent { Plate(modifier = Modifier.testTag(PLATE)) { Text("3d6") } }

    compose.onNodeWithTag(PLATE).assertHeightIsAtLeast(SIDES)
  }

  @Test
  fun `the shadow a plate is lifted by is the small one`() {
    // `--shadow-sm`. The medium step is what floats *over* a screen — a toast,
    // a menu — and a plate is part of the screen it is on.
    assertTrue("a plate is lifted as far as a toast", Modernist.Shadow.sm < Modernist.Shadow.md)
  }

  private companion object {
    const val PLATE = "plate"
    const val WORDS = "plate:words"

    /** `padding: 7px 11px 8px` — the two sides added together. */
    val SIDES = 22.dp

    const val HALF_A_PIXEL = 0.5
  }
}
