package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.Rounding
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * That this screen is drawn in the design system rather than in Material's
 * defaults (`design/_ds/modernist-.../styles.css`, `design/README.md`).
 *
 * The real theme lives in `:app` and a feature module cannot reach it, so what
 * stands in for it here is a `MaterialTheme` with the same two properties the
 * real one has: **zero corner radius everywhere**, and a type scale in which
 * only the slots the design system defines are filled in. That is enough to
 * catch both of the ways this screen had drifted.
 *
 * The first is Material's own shapes. `Button`, `TextButton` and `FilterChip`
 * take their shape from their own defaults, and `ButtonDefaults` resolves
 * `CornerFull` to `CircleShape` **without ever consulting the theme's
 * `Shapes`** — so every button on this screen was a pill under a design system
 * whose `--radius-*` is `0px`, and no amount of fixing the theme would have
 * changed it. What fixed it was drawing them out of `ui/common`'s own
 * components instead.
 *
 * The second is the type scale. The theme fills in seven of Material's
 * typography slots from the design tokens and leaves the rest at Material's
 * baseline; a screen that asked for `displayMedium` or `bodyMedium` therefore
 * got Material's face at Material's size, which is why the stand-in here gives
 * the defined slots sizes nothing else could be mistaken for.
 */
@RunWith(RobolectricTestRunner::class)
class DesignSystemTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the controls are square, because nothing in this system is not`() {
    compose.setContent {
      Modernist {
        ResultSheet(result = halved(), divides = true)
      }
    }

    // The chosen option of the segmented control is a solid field of accent.
    // A rounded corner would leave its top-left pixel showing the ground
    // through, which is exactly what a Material pill does.
    assertCorner(compose.onNodeWithTag(RollTestTags.roundingOf(Rounding.Down)), ACCENT)
  }

  @Test
  fun `the rounding control is one box, not three loose chips`() {
    compose.setContent { Modernist { ResultSheet(result = halved(), divides = true) } }

    val down = compose.onNodeWithTag(RollTestTags.roundingOf(Rounding.Down)).getUnclippedBoundsInRoot()
    val nearest = compose.onNodeWithTag(RollTestTags.roundingOf(Rounding.Nearest)).getUnclippedBoundsInRoot()

    // `.seg-opt + .seg-opt { border-left: 1px }` — the options touch, divided
    // by a hairline and nothing else. Chips would have sat apart.
    assertEquals(1.0, (nearest.left - down.right).value.toDouble(), HALF_A_PIXEL)
  }

  @Test
  fun `every die that landed is drawn in a cell of its own`() {
    compose.setContent { Modernist { ResultSheet(result = halved()) } }

    // `min-width:26px; height:26px` — the design draws the dice as cells on a
    // sheet, not as a sentence of loose digits.
    compose.onNodeWithTag(RollTestTags.dieAt(0)).assertWidthIsAtLeast(CELL)
    compose.onNodeWithTag(RollTestTags.dieAt(0)).assertHeightIsAtLeast(CELL)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the total is set in the display size the system defines`() {
    compose.setContent { Modernist { ResultSheet(result = halved()) } }

    // `displayMedium`, which this used to ask for, is not one of the slots the
    // theme fills in: the one number the screen exists to show was being set
    // in Material's own face at Material's own weight.
    compose.onNodeWithTag(RollTestTags.subtotalOf(0)).assertHeightIsAtLeast(TITLE)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the formula on the tray is a heading, not a caption`() {
    compose.setContent { Modernist { FormulaLine(text = "3d6 + 4", onEdit = {}) } }

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertHeightIsAtLeast(TITLE)
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `the welcome's title is the display size`() {
    compose.setContent { Modernist { Welcome(what = WhatIsThere(sets = 1), onRollNow = {}, onDismiss = {}) } }

    compose.onNodeWithTag(RollTestTags.WELCOME).assertIsDisplayed()
    // `displaySmall` — what this used to ask for — is not one of the slots the
    // theme fills in, so the first words a new install shows were set in
    // Material's own face at Material's own weight.
    compose.onNodeWithText(TITLE_TEXT).assertHeightIsAtLeast(DISPLAY)
  }

  @Test
  @Config(qualifiers = "w320dp-h400dp")
  fun `a screen too short for the welcome still shows every way out of it`() {
    // The words give way, never the buttons. Before the block was anchored to
    // the bottom the last of the four was measured into nothing on a short
    // screen, which left a welcome whose only ways on were off the bottom.
    compose.setContent { Modernist { Welcome(what = WhatIsThere(sets = 1), onRollNow = {}, onDismiss = {}) } }

    compose.onNodeWithTag(RollTestTags.WELCOME_ROLL).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_IMPORT).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS_ADD).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertIsDisplayed()
  }

  /** That the node's top-left pixel is painted, which a rounded corner is not. */
  private fun assertCorner(
    node: SemanticsNodeInteraction,
    expected: Color,
  ) {
    val pixels = node.captureToImage().toPixelMap()
    assertEquals("the corner is not the container's colour", expected, pixels[1, 1])
  }

  /**
   * The theme this module cannot reach, in the two respects this file is about.
   *
   * The sizes are deliberately nothing like Material's own: a slot asked for
   * by mistake comes back at Material's baseline, and the assertions above are
   * written so that baseline fails them.
   */
  @Composable
  private fun Modernist(content: @Composable () -> Unit) {
    MaterialTheme(
      colorScheme = MaterialTheme.colorScheme.copy(primary = ACCENT),
      shapes = SQUARE,
      typography = SCALE,
      content = content,
    )
  }

  private fun halved(): RollResult =
    RollResult(
      formula = "(3d6 + 5) / 2",
      total = 8,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "3d6",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 11,
            dice =
              listOf(
                RolledDie(instanceIndex = 0, dieId = "d6", value = 6, naturalMax = true),
                RolledDie(instanceIndex = 1, dieId = "d6", value = 3),
                RolledDie(instanceIndex = 2, dieId = "d6", value = 2),
              ),
          ),
        ),
      rounding = Rounding.Down,
    )

  private companion object {
    val ACCENT = Color(0xFFEC3013)

    /** `--radius-sm/md/lg: 0px`. */
    val SQUARE =
      Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
      )

    /**
     * Only the slots the design system defines, at sizes no Material default
     * shares (`theme/Theme.kt` fills in exactly these seven).
     */
    val SCALE =
      Typography(
        displayLarge = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.ExtraBold),
        headlineLarge = TextStyle(fontSize = 80.sp, fontWeight = FontWeight.ExtraBold),
        headlineMedium = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.ExtraBold),
        titleLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold),
        bodyLarge = TextStyle(fontSize = 15.sp),
        labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp),
      )

    /** The welcome's headline, which is not tagged and so is found by its words. */
    const val TITLE_TEXT = "Nothing to set up."

    /** What the stand-in scale makes those two slots at least as tall as. */
    val DISPLAY: Dp = 96.dp
    val TITLE: Dp = 64.dp

    /** One die's cell (`min-width:26px; height:26px`). */
    val CELL: Dp = 26.dp

    /** Rounding is to the nearest pixel, so a hairline is 1 dp give or take. */
    const val HALF_A_PIXEL = 0.5
  }
}
