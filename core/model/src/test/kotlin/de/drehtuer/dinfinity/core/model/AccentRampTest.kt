package de.drehtuer.dinfinity.core.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The clamp, and the ramp mixed from what it returns.
 *
 * This is where the accent's accessibility claim lives now. It used to be six
 * colours measured one at a time, which was a true statement about six
 * colours; a picker makes that useless, because the seventh colour is whatever
 * somebody's finger lands on. So the claim is made about **every** colour
 * instead — which is both the stronger statement and the only one that can
 * still be made (`docs/architecture.md`, "Settings").
 */
class AccentRampTest {
  /**
   * The property the whole picker rests on: whatever arrives, on either
   * ground, what comes out can be told apart from the page it is read on.
   *
   * Random rather than a list, and both grounds for each, because the cases
   * that break a clamp are the ones nobody would think to write down — a
   * colour that is already as dark as the page, a colour exactly at the bar, a
   * pure blue whose luminance is almost nothing.
   */
  @Test
  fun `any colour on either ground comes back legible`() {
    val random = Random(SEED)
    repeat(COLOURS) {
      val chosen = random.nextInt()
      Ground.entries.forEach { ground ->
        val clamped = AccentRamp.clamp(chosen, ground)
        val ratio = Contrast.ratio(clamped, ground.backgroundArgb)
        assertTrue(
          ratio >= Contrast.COMPONENT,
          "${"#%06X".format(chosen and RGB)} on $ground came back at ${"%.2f".format(ratio)}:1",
        )
      }
    }
  }

  /** The corners a random run will not reach often enough to be trusted with. */
  @Test
  fun `the colours nearest each ground are still pushed clear of it`() {
    listOf(0xFFFFFFFF, 0xFF000000, 0xFFF3F2F2, 0xFF201E1D, 0xFF808080, 0xFF0000FF, 0xFFFFFF00).forEach { colour ->
      Ground.entries.forEach { ground ->
        val clamped = AccentRamp.clamp(colour.toInt(), ground)
        assertTrue(
          Contrast.meets(clamped, ground.backgroundArgb, Contrast.COMPONENT),
          "${"#%06X".format(colour.toInt() and RGB)} on $ground",
        )
      }
    }
  }

  /**
   * A colour that already clears the bar is not touched at all.
   *
   * The clamp is a floor, not a filter: an accent the design chose deliberately
   * must arrive on the screen as the colour it is, or nobody can put the app
   * and the prototype side by side.
   */
  @Test
  fun `a colour that already clears the bar is returned untouched`() {
    assertEquals(
      AccentColor.ModernistRed.argb,
      AccentRamp.clamp(AccentColor.ModernistRed.argb, Ground.Light),
    )
    assertEquals(
      AccentColor.Amber.argb,
      AccentRamp.clamp(AccentColor.Amber.argb, Ground.Dark),
    )
  }

  /** Clamping twice is clamping once, which is what lets any layer do it safely. */
  @Test
  fun `the clamp is idempotent`() {
    val random = Random(SEED)
    repeat(COLOURS) {
      val chosen = random.nextInt()
      Ground.entries.forEach { ground ->
        val once = AccentRamp.clamp(chosen, ground)
        assertEquals(once, AccentRamp.clamp(once, ground), "clamping twice moved ${"#%06X".format(once and RGB)}")
      }
    }
  }

  /** Whatever it does to a colour, it does not make one up: the alpha stays full. */
  @Test
  fun `what comes back is an opaque colour`() {
    val random = Random(SEED)
    repeat(COLOURS) {
      Ground.entries.forEach { ground ->
        assertEquals(0xFF, (AccentRamp.clamp(random.nextInt(), ground) ushr ALPHA_SHIFT) and BYTE)
      }
    }
  }

  /** A colour too pale for paper is deepened on paper and left alone on a dark page. */
  @Test
  fun `the same colour is clamped differently on the two grounds`() {
    val pale = AccentColor.LightBlue.argb
    assertNotEquals(pale, AccentRamp.clamp(pale, Ground.Light))
    assertEquals(pale, AccentRamp.clamp(pale, Ground.Dark))
  }

  /**
   * The ramp is `color-mix` and nothing else, at the five shares the design
   * names. Recomputing it here rather than pinning hex values is what stops a
   * step drifting away from the rule it is supposed to be made by.
   */
  @Test
  fun `every step is the design's mix of the clamped accent`() {
    AccentColor.entries.forEach { accent ->
      Ground.entries.forEach { ground ->
        val ramp = AccentRamp.of(accent.argb, ground)
        assertEquals(AccentRamp.clamp(accent.argb, ground), ramp.accent)
        assertEquals(Contrast.over(ramp.accent, AccentRamp.SHARE_100, ground.backgroundArgb), ramp.v100)
        assertEquals(Contrast.over(ramp.accent, AccentRamp.SHARE_200, ground.backgroundArgb), ramp.v200)
        assertEquals(Contrast.over(ramp.accent, AccentRamp.SHARE_600, ground.textArgb), ramp.v600)
        assertEquals(Contrast.over(ramp.accent, AccentRamp.SHARE_700, ground.textArgb), ramp.v700)
        assertEquals(Contrast.over(ramp.accent, AccentRamp.SHARE_800, ground.textArgb), ramp.v800)
      }
    }
  }

  /**
   * The pressed step carries body copy in the accent, which is the 4.5:1 bar
   * rather than 3:1 — that is the whole reason it exists rather than the
   * accent being used for everything.
   */
  @Test
  fun `the pressed step reads as body copy on its own ground, for any colour`() {
    val random = Random(SEED)
    repeat(COLOURS) {
      val chosen = random.nextInt()
      Ground.entries.forEach { ground ->
        val ramp = AccentRamp.of(chosen, ground)
        val ratio = Contrast.ratio(ramp.v700, ground.backgroundArgb)
        assertTrue(
          ratio >= Contrast.BODY_TEXT,
          "accent-700 of ${"#%06X".format(chosen and RGB)} on $ground is ${"%.2f".format(ratio)}:1",
        )
      }
    }
  }

  /**
   * The pale end and the deep end are what a filled accent tag is made of, so
   * they have to be legible *against each other* — and that is a pairing
   * neither one of them can be measured for alone.
   */
  @Test
  fun `the two ends of the ramp read against each other, for any colour`() {
    val random = Random(SEED)
    repeat(COLOURS) {
      val chosen = random.nextInt()
      Ground.entries.forEach { ground ->
        val ramp = AccentRamp.of(chosen, ground)
        val ratio = Contrast.ratio(ramp.v800, ramp.v100)
        assertTrue(
          ratio >= Contrast.BODY_TEXT,
          "a tag in ${"#%06X".format(chosen and RGB)} on $ground is ${"%.2f".format(ratio)}:1",
        )
      }
    }
  }

  /** A step is a depth, and the depths have to be in order or they are not steps. */
  @Test
  fun `the ramp runs from the page to the ink`() {
    AccentColor.entries.forEach { accent ->
      Ground.entries.forEach { ground ->
        val ramp = AccentRamp.of(accent.argb, ground)
        val pale = Contrast.ratio(ramp.v100, ground.backgroundArgb)
        val paler = Contrast.ratio(ramp.v200, ground.backgroundArgb)
        assertTrue(paler > pale, "${accent.id} on $ground: -200 is no deeper than -100")
        val six = Contrast.ratio(ramp.v600, ground.backgroundArgb)
        val seven = Contrast.ratio(ramp.v700, ground.backgroundArgb)
        val eight = Contrast.ratio(ramp.v800, ground.backgroundArgb)
        assertTrue(seven > six, "${accent.id} on $ground: -700 is no deeper than -600")
        assertTrue(eight > seven, "${accent.id} on $ground: -800 is no deeper than -700")
      }
    }
  }

  /** The two grounds are each other, swapped — the design's own rule for dark mode. */
  @Test
  fun `the grounds are the palette's, and they are mirrors`() {
    assertEquals(Ground.Light.backgroundArgb, Ground.Dark.textArgb)
    assertEquals(Ground.Light.textArgb, Ground.Dark.backgroundArgb)
  }

  private companion object {
    const val SEED = 20260917
    const val COLOURS = 500
    const val RGB = 0xFFFFFF
    const val BYTE = 0xFF
    const val ALPHA_SHIFT = 24
  }
}
