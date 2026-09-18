package de.drehtuer.dinfinity.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Ground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A badge says what state a row is in, and it has to be legible in it.
 *
 * The thing worth asserting about a tag is its **contrast**, because the whole
 * of what it is is a colour pairing: a fill from one end of a ramp and letters
 * from the other. Getting the ends the wrong way round, or mirroring only one
 * of them on a dark ground, produces a chip that renders perfectly and cannot
 * be read — which no layout assertion would catch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a neutral tag reads on the ground it is drawn on`() {
    // Both ends move together or neither does. `.dz-dark` does not redefine
    // either of the two steps this is made of, so the app mirrors both by the
    // rule the other eight follow — and mirroring only one would leave deep
    // grey letters on a deep grey chip.
    val light = contrast(Modernist.Neutral.v800, Modernist.Neutral.v100)
    val dark = contrast(Modernist.Neutral.v200, Modernist.Neutral.v900)

    assertTrue("a neutral tag is unreadable on paper: $light", light >= BODY_TEXT)
    assertTrue("a neutral tag is unreadable on a dark page: $dark", dark >= BODY_TEXT)
  }

  @Test
  fun `the two kinds do not look the same`() {
    // They say different things — a state that is simply true, and one
    // somebody chose — so they must not come out as the same chip.
    compose.setContent {
      Row {
        Tag("default", kind = TagKind.Neutral, modifier = Modifier.testTag("neutral"))
        Tag("disabled", kind = TagKind.Outline, modifier = Modifier.testTag("outline"))
      }
    }

    val neutral = corner("neutral")
    val outline = corner("outline")
    assertNotEquals("a neutral tag and an outlined one are the same chip", neutral, outline)
  }

  @Test
  fun `an outlined tag follows the accent the player chose`() {
    // The one kind that needs no ramp, and therefore the one that can follow a
    // chosen accent. A hard-coded red here would be a red badge on a moss app.
    var accent: Color? = null
    compose.setContent {
      accent = MaterialTheme.colorScheme.primary
      Tag("disabled", kind = TagKind.Outline, modifier = Modifier.testTag("outline"))
    }

    val drawn = compose.onNodeWithTag("outline").captureToImage().toPixelMap()
    val edge = (0 until drawn.width).any { x -> drawn[x, 0] == accent }
    assertTrue("an outlined tag was not edged in the theme's accent", edge)
  }

  /**
   * The filled accent tag, which is the whole reason the ramp is mixed rather
   * than looked up: both ends of it are made from the accent the player
   * picked, so the pairing has to be measured for a colour nobody chose in
   * advance.
   */
  @Test
  fun `an accent tag reads on both grounds`() {
    listOf(Ground.Light, Ground.Dark).forEach { ground ->
      ACCENTS.forEach { accent ->
        val ramp = AccentRamp.of(accent, ground)
        val ratio = contrast(Color(ramp.v800), Color(ramp.v100))
        assertTrue("an accent tag on $ground is ${"%.2f".format(ratio)}:1", ratio >= BODY_TEXT)
      }
    }
  }

  @Test
  fun `an accent tag is filled with the pale end of the chosen accent's ramp`() {
    val accent = Color(0xFF0F7A50)
    compose.setContent {
      MaterialTheme(
        colorScheme =
          lightColorScheme(background = Modernist.Light.background, primary = accent),
      ) {
        Tag("update available", kind = TagKind.Accent, modifier = Modifier.testTag("accent"))
      }
    }

    // Mixed from the accent, not a colour of the tag's own: a hard-coded pale
    // red here would be a red chip on a pine app, which is exactly the bug
    // that kept this tag unbuilt.
    assertEquals(Color(AccentRamp.of(accent.toArgb(), Ground.Light).v100), corner("accent"))
  }

  @Test
  fun `an accent tag darkens with the page`() {
    val accent = Color(0xFF0F7A50)
    compose.setContent {
      MaterialTheme(
        colorScheme = darkColorScheme(background = Modernist.Dark.background, primary = accent),
      ) {
        Tag("update available", kind = TagKind.Accent, modifier = Modifier.testTag("accent"))
      }
    }

    val fill = corner("accent")
    assertEquals(Color(AccentRamp.of(accent.toArgb(), Ground.Dark).v100), fill)
    assertTrue("an accent tag stayed pale on a dark page", fill.luminance() < HALF)
  }

  @Test
  fun `the three kinds do not look the same`() {
    compose.setContent {
      Row {
        Tag("default", kind = TagKind.Neutral, modifier = Modifier.testTag("neutral"))
        Tag("disabled", kind = TagKind.Outline, modifier = Modifier.testTag("outline"))
        Tag("update available", kind = TagKind.Accent, modifier = Modifier.testTag("accent"))
      }
    }

    val chips = listOf(corner("neutral"), corner("outline"), corner("accent"))
    assertEquals("two of the three tag kinds are the same chip", chips.size, chips.toSet().size)
  }

  @Test
  fun `a tag prints its words and follows them when they change`() {
    val state = mutableStateOf("default")
    compose.setContent { Tag(state.value, modifier = Modifier.testTag("tag")) }

    compose.onNodeWithText("default").assertExists()
    state.value = "update available"
    compose.onNodeWithText("update available").assertExists()
  }

  @Test
  fun `a tag is a badge, not a button`() {
    // `.tag` is `padding: 3px 10px` — deliberately smaller than anything
    // pressable, because nothing about it is pressable. If one ever grows a
    // tap, it needs a touch target and this is where that shows up.
    compose.setContent { Tag("default", modifier = Modifier.testTag("tag")) }

    val height =
      compose
        .onNodeWithTag("tag")
        .fetchSemanticsNode()
        .size.height
    assertTrue("a tag is not drawn at all", height > 0)
    assertTrue("a tag has grown to the size of a control", height < TOUCH_TARGET.value)
  }

  @Test
  fun `a neutral tag darkens with the page rather than staying a pale chip`() {
    // This is the inference, drawn. `.dz-dark` does not redefine the two steps
    // a neutral tag is made of, so the app mirrors them the way the other
    // eight are mirrored — which means the chip follows the ground. Taking the
    // override list literally instead would leave a near-white badge glowing
    // on a dark page, and that is the reading this asserts against.
    compose.setContent {
      MaterialTheme(colorScheme = darkColorScheme(background = Modernist.Dark.background)) {
        Tag("default", modifier = Modifier.testTag("tag"))
      }
    }

    val fill = corner("tag")
    assertTrue("a neutral tag stayed pale on a dark page", fill.luminance() < HALF)
    assertEquals(Modernist.Neutral.v900, fill)
  }

  @Test
  fun `a neutral tag stays pale on a light page`() {
    compose.setContent {
      MaterialTheme(colorScheme = lightColorScheme(background = Modernist.Light.background)) {
        Tag("default", modifier = Modifier.testTag("tag"))
      }
    }

    assertEquals(Modernist.Neutral.v100, corner("tag"))
  }

  private fun corner(tag: String): Color =
    compose
      .onNodeWithTag(tag)
      .captureToImage()
      .toPixelMap()
      .let { it[it.width / 2, 1] }

  private companion object {
    /** WCAG's floor for body text. A badge's words are body text. */
    const val BODY_TEXT = 4.5

    /**
     * The six presets, plus the two colours a clamp has the most work to do
     * with. The tag has to be legible in any of them, because the accent is
     * whatever the player handed the app (`core/model/AccentRamp`).
     */
    val ACCENTS =
      listOf(0xFF38A8DC, 0xFFEC3013, 0xFFC2186F, 0xFF1D5FD4, 0xFF0F7A50, 0xFFC07000, 0xFFFFFF00, 0xFF000000)
        .map { it.toInt() }

    /** Halfway up the luminance range: what divides a dark page from a light one. */
    const val HALF = 0.5f

    fun contrast(
      ink: Color,
      ground: Color,
    ): Double {
      val a = relative(ink)
      val b = relative(ground)
      return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    fun relative(colour: Color): Double {
      fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
      }
      return 0.2126 * channel(colour.red) + 0.7152 * channel(colour.green) + 0.0722 * channel(colour.blue)
    }
  }
}
