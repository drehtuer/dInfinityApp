package de.drehtuer.dinfinity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.drehtuer.dinfinity.feature.settings.MenuEntry
import de.drehtuer.dinfinity.feature.settings.MenuHeader
import de.drehtuer.dinfinity.feature.settings.MenuScreen
import de.drehtuer.dinfinity.feature.settings.MenuSection
import de.drehtuer.dinfinity.feature.settings.MenuTestTags
import de.drehtuer.dinfinity.theme.DInfinityTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * That the menu's header can actually be *seen*.
 *
 * This is here rather than beside `MenuScreenTest` because it needs the real
 * theme, and the theme belongs to `:app` — a feature module cannot reach it,
 * and the bug only exists once the two are put together.
 *
 * It reads pixels, which no other test in this project does, and the reason is
 * the defect it was written for. The app name was drawn in `LocalContentColor`
 * — plain black, because the menu was a bare `Column` and nothing had set a
 * content colour — on a near-black background. On the phone it was invisible.
 * Every assertion about it passed: the node was there, the text was there, the
 * semantics were there. Text was never the thing that was wrong.
 *
 * So the assertion is contrast, against the threshold that means something
 * (WCAG AA, 3:1 for large text) rather than against an exact colour, which
 * would break the next time the palette is touched for a good reason.
 *
 * **The window underneath the menu is part of the test**, and the first
 * version of it was worthless for leaving the window out: with nothing painted
 * behind, black text landed on a white default and the broken screen passed.
 * The activity paints its window `#201e1d` in dark mode
 * (`app/src/main/res/values-night/themes.xml`), which is `background` in the
 * same palette Compose is handed — so the ground is painted here too, and the
 * test fails on the code that shipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuHeaderContrastTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the app name is readable against the dark background`() {
    show(darkTheme = true)

    assertReadable(contrastOfHeader())
  }

  @Test
  fun `the app name is readable against the light background`() {
    // The mirror of the bug: a content colour that happens to suit one theme
    // is not a content colour that suits the other, and the black that
    // vanished on dark would have passed a test that only ever ran light.
    show(darkTheme = false)

    assertReadable(contrastOfHeader())
  }

  private fun assertReadable(contrast: Double) {
    assertTrue(
      "the app name is drawn at a contrast of ${"%.2f".format(contrast)}:1, " +
        "which is below the $READABLE:1 that large text needs to be legible",
      contrast >= READABLE,
    )
  }

  /**
   * The contrast between the header's lightest and darkest pixels.
   *
   * The header is text on a flat background, so its two extremes *are* the ink
   * and the ground. When the ink is the ground — which is what the defect
   * looked like — the two collapse together and the ratio falls to 1:1.
   */
  private fun contrastOfHeader(): Double {
    val pixels = compose.onNodeWithTag(MenuTestTags.HEADER).captureToImage().toPixelMap()
    val all =
      buildList {
        for (y in 0 until pixels.height) {
          for (x in 0 until pixels.width) {
            add(pixels[x, y])
          }
        }
      }
    val luminances = all.map(::luminanceOf)
    return contrastBetween(luminances.min(), luminances.max())
  }

  /** WCAG relative luminance: the sRGB channels linearised and weighted by eye sensitivity. */
  private fun luminanceOf(color: Color): Double {
    fun channel(value: Float): Double {
      val c = value.toDouble()
      return if (c <= LINEAR_BELOW) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
    }
    return RED_WEIGHT * channel(color.red) +
      GREEN_WEIGHT * channel(color.green) +
      BLUE_WEIGHT * channel(color.blue)
  }

  /** WCAG contrast ratio, which is between 1:1 (identical) and 21:1 (black on white). */
  private fun contrastBetween(
    one: Double,
    other: Double,
  ): Double = (max(one, other) + AMBIENT) / (min(one, other) + AMBIENT)

  private fun show(darkTheme: Boolean) {
    compose.setContent {
      DInfinityTheme(darkTheme = darkTheme) {
        // The window the activity puts behind the menu, painted the colour the
        // platform theme paints it. Without this the test is measuring text on
        // whatever Compose happens to leave behind, which is not what a player
        // is looking at.
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
          MenuScreen(
            header = MenuHeader(appName = "dInfinity"),
            sections =
              listOf(
                MenuSection(
                  name = "Play",
                  entries =
                    listOf(
                      MenuEntry(id = "roll", title = "Roll", description = "The tray.", open = {}),
                    ),
                ),
              ),
          )
        }
      }
    }
  }

  private companion object {
    /** WCAG AA for large text. The app name is `headlineSmall` and bold, which is large text. */
    const val READABLE = 3.0

    const val RED_WEIGHT = 0.2126
    const val GREEN_WEIGHT = 0.7152
    const val BLUE_WEIGHT = 0.0722
    const val LINEAR_BELOW = 0.03928
    const val LINEAR_DIVISOR = 12.92
    const val GAMMA_OFFSET = 0.055
    const val GAMMA_DIVISOR = 1.055
    const val GAMMA = 2.4
    const val AMBIENT = 0.05
  }
}
