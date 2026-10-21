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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.render.filament.PickMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The ring round a picked die is the player's accent, on a halo of the ground
 * (`docs/architecture.md`, decision 84).
 *
 * Captured rather than asserted on semantics, because a colour is the one
 * thing the semantics tree cannot show. The accent comes in through the
 * theme's `primary`, which is where Settings puts the player's choice — so a
 * ring that ignored the theme would fail here with any accent but its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickRingsTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the ring is the accent the theme carries, with the ground either side of it`() {
    val accent = Color(0xFF1E8C3A)
    val ground = Color(0xFFF3F2F2)

    val (onRing, inHalo) = ringAndHalo(lightColorScheme(primary = accent, background = ground))

    assertEquals("the ring is not the accent", accent, onRing)
    assertEquals("the accent sits on the felt with no ground round it", ground, inHalo)
  }

  @Test
  fun `on a dark ground it follows the dark theme's accent and ground`() {
    val accent = Color(0xFFFF8A00)
    val ground = Color(0xFF201E1D)

    val (onRing, inHalo) = ringAndHalo(darkColorScheme(primary = accent, background = ground))

    assertEquals(accent, onRing)
    assertEquals(ground, inHalo)
  }

  @Test
  fun `a die nearer the camera gets a ring outside its own ball, not the smallest one`() {
    // A ball three tenths of the picture's height across is 30 dp in this
    // square, so its ring is centred 8 dp further out — at 38 dp, where the
    // smallest ring's 20 dp would have been drawn over the die's own face.
    val accent = Color(0xFF1E8C3A)
    val ground = Color(0xFFF3F2F2)

    val (onRing, insideIt) =
      ringAndHalo(
        lightColorScheme(primary = accent, background = ground),
        radiusOfHeight = NEAR_BALL,
        ringAt = NEAR_RING,
        haloAt = RADIUS,
      )

    assertEquals("the ring is not outside the ball", accent, onRing)
    assertNotEquals("the ring was drawn at the smallest radius over the ball", accent, insideIt)
  }

  /**
   * Draws one ring round the middle of a square and reads two pixels to the
   * right of its centre: on the accent stroke, and in the ground just outside
   * it. A zero-sized ball gets the smallest ring, 12 dp, pushed out by the
   * 8 dp halo — so the stroke is centred 20 dp out and the accent is 4 dp of
   * the halo's 8.
   */
  private fun ringAndHalo(
    scheme: ColorScheme,
    radiusOfHeight: Double = 0.0,
    ringAt: Dp = RADIUS,
    haloAt: Dp = HALO_EDGE,
  ): Pair<Color, Color> {
    // A phone's density rather than the test's default of one pixel to the
    // dp, so a 2 dp band of colour is wide enough to read a pixel from its
    // middle without catching the anti-aliasing at either edge.
    val density = Density(DENSITY)
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = scheme) {
          Box(modifier = Modifier.size(SIDE)) {
            PickRings(
              marks = listOf(PickMark(acrossFraction = 0.5, downFraction = 0.5, radiusOfHeight = radiusOfHeight)),
              modifier = Modifier.fillMaxSize(),
            )
          }
        }
      }
    }
    val pixels = compose.onNodeWithTag(RollTestTags.PICKED).captureToImage().toPixelMap()
    val across = pixels.width / 2
    val down = pixels.height / 2
    val radius = with(density) { ringAt.toPx() }
    val halo = with(density) { haloAt.toPx() }
    return pixels[(across + radius).roundToInt(), down] to pixels[(across + halo).roundToInt(), down]
  }

  private companion object {
    const val DENSITY = 3f
    val SIDE = 100.dp
    val RADIUS = 20.dp

    /** Three dp outside the stroke's centre: past the 2 dp accent, inside the 4 dp halo. */
    val HALO_EDGE = 23.dp

    /** A ball 30 dp across in the 100 dp square, and its ring 8 dp beyond it. */
    const val NEAR_BALL = 0.3
    val NEAR_RING = 38.dp
  }
}
