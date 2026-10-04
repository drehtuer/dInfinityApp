package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Ground
import de.drehtuer.dinfinity.render.filament.PicturePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The outline and the label round a die a later pass of the roll put down
 * (`docs/architecture.md`, decision 85).
 *
 * Captured for the colour, as [PickRingsTest] is: the outline is the accent's
 * 700 step mixed from whatever accent the theme carries, inside a band of the
 * ground — so an outline that took the accent itself, or the ink, or ignored
 * the theme, fails here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PassMarksTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the outline is the accent's 700 step, with the ground either side of it`() {
    val accent = Color(0xFF1E8C3A)
    val ground = Color(0xFFF3F2F2)

    val (onLine, inHalo) = lineAndHalo(lightColorScheme(primary = accent, background = ground))

    assertEquals("the outline is not accent-700", Color(AccentRamp.of(accent.toArgb(), Ground.Light).v700), onLine)
    assertEquals("the outline sits on the felt with no ground round it", ground, inHalo)
  }

  @Test
  fun `on a dark ground the step is mixed against the dark ground`() {
    val accent = Color(0xFFFF8A00)
    val ground = Color(0xFF201E1D)

    val (onLine, inHalo) = lineAndHalo(darkColorScheme(primary = accent, background = ground))

    assertEquals(Color(AccentRamp.of(accent.toArgb(), Ground.Dark).v700), onLine)
    assertEquals(ground, inHalo)
  }

  @Test
  fun `each die is labelled with its pass, under its lowest point`() {
    show(
      lightColorScheme(),
      listOf(PassMark(square, pass = 2), PassMark(square.map { it.copy(acrossFraction = it.acrossFraction - 0.1) }, 3)),
    )

    compose.onAllNodesWithTag(RollTestTags.PASS_LABEL, useUnmergedTree = true).assertCountEquals(2)
    compose.onNodeWithText("PASS 2", useUnmergedTree = true).assertExists()
    val label = compose.onNodeWithText("PASS 3", useUnmergedTree = true).getBoundsInRoot()
    val top = SIDE * BOTTOM_OF_SQUARE + LABEL_GAP
    assertTrue("the label is not under the die: ${label.top}", (label.top - top).value in -1f..1f)
  }

  @Test
  fun `no marks, nothing drawn`() {
    show(lightColorScheme(), emptyList())

    compose.onNodeWithTag(RollTestTags.PASSES).assertDoesNotExist()
  }

  @Test
  fun `a label hangs from the middle of the die across and its lowest point down`() {
    val at = labelAt(listOf(PicturePoint(0.1, 0.2), PicturePoint(0.5, 0.6), PicturePoint(0.3, 0.1)), 200, 100)

    assertEquals(Offset(60f, 60f), at)
  }

  /**
   * Draws one square outline and reads two pixels on its left edge, half way
   * down: on the 4 dp line, and 3 dp inside it — past the line's 2 dp, within
   * the halo's 4.
   */
  private fun lineAndHalo(scheme: ColorScheme): Pair<Color, Color> {
    show(scheme, listOf(PassMark(square, pass = 2)))
    val pixels = compose.onNodeWithTag(RollTestTags.PASSES).captureToImage().toPixelMap()
    val px = { dp: Float -> (dp * DENSITY).roundToInt() }
    val down = px(SIDE.value / 2)
    val edge = SIDE.value * LEFT_OF_SQUARE
    return pixels[px(edge), down] to pixels[px(edge + HALO_INSIDE), down]
  }

  private fun show(
    scheme: ColorScheme,
    marks: List<PassMark>,
  ) {
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
        MaterialTheme(colorScheme = scheme) {
          Box(modifier = Modifier.size(SIDE)) {
            PassMarks(marks = marks, modifier = Modifier.fillMaxSize())
          }
        }
      }
    }
  }

  private val square =
    listOf(
      PicturePoint(LEFT_OF_SQUARE.toDouble(), TOP_OF_SQUARE),
      PicturePoint(RIGHT_OF_SQUARE, TOP_OF_SQUARE),
      PicturePoint(RIGHT_OF_SQUARE, BOTTOM_OF_SQUARE.toDouble()),
      PicturePoint(LEFT_OF_SQUARE.toDouble(), BOTTOM_OF_SQUARE.toDouble()),
    )

  private companion object {
    const val DENSITY = 3f
    val SIDE = 100.dp
    const val LEFT_OF_SQUARE = 0.3f
    const val RIGHT_OF_SQUARE = 0.7
    const val TOP_OF_SQUARE = 0.2
    const val BOTTOM_OF_SQUARE = 0.6f
    const val HALO_INSIDE = 3f
    val LABEL_GAP = 2.dp
  }
}
