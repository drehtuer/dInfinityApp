package de.drehtuer.dinfinity.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.Ground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [Modernist] says what the design system says
 * (`design/_ds/modernist-.../styles.css`).
 *
 * The tokens are a transcription — Kotlin cannot read a stylesheet at build
 * time — and a transcription that nothing checks is a copy that drifts. So
 * this reads the stylesheet itself: re-import the design system with a
 * different scale, ramp or radius and a test fails, rather than a screen
 * quietly going out of step with the prototype.
 *
 * It is one test rather than the three that grew in `feature/graph`,
 * `feature/saved` and `feature/stats`, because there is now one transcription
 * rather than six.
 */
class ModernistTest {
  @Test
  fun `the spacing scale is the stylesheet's`() {
    assertEquals(space(1), Modernist.x1)
    assertEquals(space(2), Modernist.x2)
    assertEquals(space(3), Modernist.x3)
    assertEquals(space(4), Modernist.x4)
    assertEquals(space(6), Modernist.x6)
    assertEquals(space(8), Modernist.x8)
  }

  /**
   * `core/model`'s [Ground] is the same two pages and the same two inks.
   *
   * It has to be written twice: the accent's clamp and its ramp are arithmetic
   * with no Compose in them, so they live where a JVM test can measure them,
   * and `core/model` cannot see a `Color`. Two copies of a palette is exactly
   * what this file exists to stop, so the second one is pinned to the first
   * here rather than trusted.
   */
  @Test
  fun `the grounds the accent is clamped against are these grounds`() {
    assertEquals(Modernist.Light.background.toArgb(), Ground.Light.backgroundArgb)
    assertEquals(Modernist.Light.text.toArgb(), Ground.Light.textArgb)
    assertEquals(Modernist.Dark.background.toArgb(), Ground.Dark.backgroundArgb)
    assertEquals(Modernist.Dark.text.toArgb(), Ground.Dark.textArgb)
  }

  @Test
  fun `nothing in this system has a rounded corner`() {
    // `--radius-sm`, `--radius-md` and `--radius-lg` are all `0px`, and a
    // Material button is a pill until it is handed a shape that is not one.
    listOf("sm", "md", "lg").forEach { step ->
      assertEquals("--radius-$step", 0.0, pixels("--radius-$step"), 0.0)
    }
    assertEquals(0.dp, Modernist.radius)
    assertEquals(RectangleShape, Modernist.square)
  }

  @Test
  fun `a rule is 2 px and the line inside a list is 1`() {
    val hr = Regex("""\.hr\s*\{[^}]*height:\s*([\d.]+)px""").find(css)
    assertTrue("the stylesheet has no .hr", hr != null)
    assertEquals(hr!!.groupValues[1].toDouble(), Modernist.rule.value.toDouble(), 0.0)

    val cell = Regex("""\.table\s+td\s*\{[^}]*border-bottom:\s*([\d.]+)px""").find(css)
    assertTrue("the stylesheet has no .table td border", cell != null)
    assertEquals(cell!!.groupValues[1].toDouble(), Modernist.hairline.value.toDouble(), 0.0)
  }

  @Test
  fun `the ground, the ink and the one accent are the stylesheet's`() {
    assertEquals(colour("--color-bg"), Modernist.Light.background)
    assertEquals(colour("--color-surface"), Modernist.Light.surface)
    assertEquals(colour("--color-text"), Modernist.Light.text)
    assertEquals(colour("--color-accent"), Modernist.accent)
  }

  @Test
  fun `the dark ground is the ink and the ink is the ground`() {
    // The design swaps them rather than mixing a second palette
    // (`design/dInfinity.dc.html`, option 2b), so the two grounds are each
    // other's ink and there is nothing else to check them against.
    assertEquals(Modernist.Light.text, Modernist.Dark.background)
    assertEquals(Modernist.Light.background, Modernist.Dark.text)
    assertEquals("the accent is not swapped with the ground", colour("--color-accent"), Modernist.accent)
  }

  @Test
  fun `the accent ramp is the stylesheet's, every step of it`() {
    // Every step, not the three the app happened to need: a tag is filled with
    // one end of this ramp and labelled with the other, so a missing step is a
    // component that cannot be drawn.
    val ramp =
      listOf(
        100 to Modernist.Accent.v100,
        200 to Modernist.Accent.v200,
        300 to Modernist.Accent.v300,
        400 to Modernist.Accent.v400,
        500 to Modernist.Accent.v500,
        600 to Modernist.Accent.v600,
        700 to Modernist.Accent.v700,
        800 to Modernist.Accent.v800,
        900 to Modernist.Accent.v900,
      )
    ramp.forEach { (step, value) ->
      assertEquals("--color-accent-$step", colour("--color-accent-$step"), value)
    }
  }

  @Test
  fun `the neutral ramp is the stylesheet's, every step of it`() {
    val ramp =
      listOf(
        100 to Modernist.Neutral.v100,
        200 to Modernist.Neutral.v200,
        300 to Modernist.Neutral.v300,
        400 to Modernist.Neutral.v400,
        500 to Modernist.Neutral.v500,
        600 to Modernist.Neutral.v600,
        700 to Modernist.Neutral.v700,
        800 to Modernist.Neutral.v800,
        900 to Modernist.Neutral.v900,
      )
    ramp.forEach { (step, value) ->
      assertEquals("--color-neutral-$step", colour("--color-neutral-$step"), value)
    }
  }

  @Test
  fun `a dark ground reflects each ramp about its middle step`() {
    // `.dz-dark` does not redefine all nine steps of either ramp — only the
    // ones the prototype's own components reach for. Every one it does define
    // is the light step the same distance the other side of 500, which is what
    // makes 500 the fixed point and the only step a screen can name when it
    // wants a colour that does not follow the page.
    val reflected =
      listOf(
        Triple("accent", 100, Modernist.Accent.v900),
        Triple("accent", 200, Modernist.Accent.v800),
        Triple("accent", 700, Modernist.Accent.v300),
        Triple("accent", 800, Modernist.Accent.v200),
        Triple("neutral", 200, Modernist.Neutral.v800),
        Triple("neutral", 300, Modernist.Neutral.v700),
        Triple("neutral", 400, Modernist.Neutral.v600),
        Triple("neutral", 500, Modernist.Neutral.v500),
      )
    reflected.forEach { (ramp, step, mirror) ->
      assertEquals("--color-$ramp-$step on a dark ground", mirror, darkColour("--color-$ramp-$step"))
    }
  }

  @Test
  fun `the shortcuts into the ramp are steps of it, not colours of their own`() {
    assertEquals(Modernist.Accent.v600, Modernist.accent600)
    assertEquals(Modernist.Accent.v700, Modernist.accent700)
    assertEquals("body copy in the accent must use a deep step", Modernist.Accent.v700, Modernist.accentOnLightText)
  }

  @Test
  fun `a heading and a kicker are tracked the way the stylesheet tracks them`() {
    val h6 = Regex("""h6\s*\{[^}]*letter-spacing:\s*([\d.]+)em""").find(css)
    assertTrue("the stylesheet has no h6 tracking", h6 != null)
    assertEquals(h6!!.groupValues[1].toDouble(), Modernist.headingTracking.value.toDouble(), TOLERANCE)

    val kicker = Regex("""\.card-kicker\s*\{[^}]*letter-spacing:\s*([\d.]+)em""").find(css)
    assertTrue("the stylesheet has no .card-kicker tracking", kicker != null)
    assertEquals(kicker!!.groupValues[1].toDouble(), Modernist.kickerTracking.value.toDouble(), TOLERANCE)
  }

  @Test
  fun `the type scale is the stylesheet's headings, body and caption`() {
    // Seven steps, and each one is a rule in the stylesheet rather than a size
    // somebody liked. `h5` at 16 px is the one heading the app has no token
    // for — nothing in the design uses it, and inventing a step between 15 and
    // 20 because Material asks for one is how a scale stops being a scale.
    val scale =
      listOf(
        "h1" to Modernist.Type.display,
        "h2" to Modernist.Type.heading,
        "h3" to Modernist.Type.title,
        "h4" to Modernist.Type.subtitle,
        "h6" to Modernist.Type.label,
      )
    scale.forEach { (tag, step) -> assertEquals(tag, heading(tag), sp(step), 0.0) }

    assertEquals("body", rule("body"), sp(Modernist.Type.body), 0.0)
    assertEquals("figcaption", rule("figcaption"), sp(Modernist.Type.caption), 0.0)
  }

  @Test
  fun `a card title and a button label are their own component's size`() {
    // Neither is on the heading scale, and both were being faked at the call
    // site before they were tokens: a card title with an `ExtraBold` override
    // on a size from elsewhere, a button label at Material's own.
    assertEquals(".card-title", rule("\\.card-title"), sp(Modernist.Type.cardTitle), 0.0)
    assertEquals(".btn", rule("\\.btn"), sp(Modernist.Type.button), 0.0)
    assertEquals(".card-kicker", rule("\\.card-kicker"), sp(Modernist.Type.kicker), 0.0)
  }

  @Test
  fun `the dark ground has its own surface, a step off its background`() {
    assertNotEquals(Modernist.Dark.background, Modernist.Dark.surface)
    assertEquals(colour("--color-surface"), Modernist.Light.surface)
    // The dark surface is the neutral ramp's deepest step, which is what makes
    // a card read as lifted off the page rather than painted on it.
    assertEquals(Modernist.Neutral.v900, Modernist.Dark.surface)
  }

  @Test
  fun `a shadow is the blur the stylesheet blurs by`() {
    // The CSS shadows are three lengths and a tint and Compose takes one
    // number, so what is transcribed is the blur — the length that says how
    // far the shadow reaches. `--shadow-lg` has no token because nothing in
    // this app is lifted that far.
    assertEquals(
      "--shadow-sm",
      blur("--shadow-sm"),
      Modernist.Shadow.sm.value
        .toDouble(),
      0.0,
    )
    assertEquals(
      "--shadow-md",
      blur("--shadow-md"),
      Modernist.Shadow.md.value
        .toDouble(),
      0.0,
    )
  }

  @Test
  fun `a divider is the ink at forty per cent, on either ground`() {
    // `--color-divider` is written as a `color-mix` of the text colour, so what
    // is checked is the share rather than a hex — and that it follows the ink
    // when the ground flips, which a hex could not.
    assertEquals(Modernist.Light.text.toArgb(), Modernist.divider(Modernist.Light.text).toArgb() or ALPHA)
    assertEquals(0.4f, Modernist.divider(Modernist.Light.text).alpha, 0.001f)
    assertEquals(0.4f, Modernist.divider(Modernist.Dark.text).alpha, 0.001f)
  }

  /** A token's size in sp, as a number a stylesheet's px can be compared to. */
  private fun sp(step: TextUnit): Double = step.value.toDouble()

  /** A heading level's size, as the stylesheet sets it. */
  private fun heading(tag: String): Double = rule("(?m)^$tag")

  /** The `font-size` of the first rule whose selector matches [selector]. */
  private fun rule(selector: String): Double {
    val found =
      Regex(selector + """\s*\{[^}]*font-size:\s*([\d.]+)px""").find(css)
        ?: error("$selector has no font-size in the stylesheet")
    return found.groupValues[1].toDouble()
  }

  /**
   * The third length of a `box-shadow`: how far it reaches.
   *
   * The first length is a bare `0` rather than `0px` — a zero needs no unit in
   * CSS — so only the second and third are matched with one.
   */
  private fun blur(token: String): Double {
    val found =
      Regex(Regex.escape(token) + """:\s*[\d.]+(?:px)?\s+[\d.]+px\s+([\d.]+)px""").find(css)
        ?: error("$token is not a three-length shadow in the stylesheet")
    return found.groupValues[1].toDouble()
  }

  private fun space(step: Int) = pixels("--space-$step").toFloat().dp

  private fun pixels(token: String): Double {
    val found =
      Regex(Regex.escape(token) + """:\s*([\d.]+)px""").find(css)
        ?: error("$token is not in the stylesheet")
    return found.groupValues[1].toDouble()
  }

  /**
   * A token as `.dz-dark` redefines it — the prototype's dark ground, which
   * the exported stylesheet does not carry at all.
   */
  private fun darkColour(token: String): Color {
    val rule =
      Regex("""\.dz-dark\s*\{([^}]*)\}""").find(phone)
        ?: error("the prototype has no .dz-dark rule")
    val found =
      Regex(Regex.escape(token) + """:\s*#([0-9a-fA-F]{6})\b""").find(rule.groupValues[1])
        ?: error("$token is not redefined for the dark ground")
    return Color(found.groupValues[1].toLong(radix = 16) or OPAQUE)
  }

  private fun colour(token: String): Color {
    val found =
      Regex(Regex.escape(token) + """:\s*#([0-9a-fA-F]{6})\b""").find(css)
        ?: error("$token is not in the stylesheet")
    return Color(found.groupValues[1].toLong(radix = 16) or OPAQUE)
  }

  private companion object {
    const val OPAQUE = 0xFF000000L
    const val ALPHA = 0xFF000000.toInt()
    const val TOLERANCE = 0.0001

    /** The repository root, found by walking up from wherever the test was run. */
    val root: File by lazy {
      generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "design/_ds").isDirectory }
    }

    /**
     * The stylesheet: the design system as it was exported, and the one thing
     * the tokens are a transcription of.
     */
    val css: String by lazy {
      File(root, "design/_ds")
        .listFiles()!!
        .first { it.isDirectory && it.name.startsWith("modernist-") }
        .resolve("styles.css")
        .readText()
    }

    /**
     * The phone prototype, which is where the **dark** ground is written down.
     * `.dz-dark` lives with the screens that use it rather than in the export.
     */
    val phone: String by lazy { File(root, "design/dInfinityPhone.dc.html").readText() }
  }
}
