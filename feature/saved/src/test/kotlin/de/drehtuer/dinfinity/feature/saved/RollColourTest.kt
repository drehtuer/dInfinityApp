package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Contrast
import de.drehtuer.dinfinity.core.model.Ground
import de.drehtuer.dinfinity.core.model.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The twelve colour tags, exactly as the design pass of 2026-09-17 named them
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * Written out rather than derived, because the point of the list is that these
 * particular twelve span the hue circle: a test that recomputed them would
 * only be asserting its own arithmetic.
 */
class RollColourTest {
  @Test
  fun `the twelve are the twelve`() {
    assertEquals(
      listOf(
        "#201E1D",
        "#7D7979",
        "#EC3013",
        "#AE1800",
        "#C05A00",
        "#B8870A",
        "#0F7A50",
        "#0D7F86",
        "#1D5FD4",
        "#6B2FD0",
        "#C2186F",
        "#BAB6B6",
      ),
      RollColour.entries.map { RollColour.hexOf(it.argb) },
    )
  }

  @Test
  fun `every one of them is opaque`() {
    // A tag at less than full alpha has no contrast ratio of its own, and the
    // clamp would be measuring a colour that is not on screen.
    RollColour.entries.forEach { tag ->
      assertEquals("${tag.name} is not opaque", 0xFF, tag.argb ushr 24)
    }
  }

  @Test
  fun `each of them has a name of its own for a screen reader`() {
    assertEquals(
      RollColour.entries.size,
      RollColour.entries
        .map { it.label }
        .toSet()
        .size,
    )
  }

  @Test
  fun `a stored colour is recognised as one of the twelve, or as somebody's own`() {
    assertEquals(RollColour.Cobalt, RollColour.of(RollColour.Cobalt.argb))
    assertNull("a colour nobody offered is a custom one", RollColour.of(0xFF123456.toInt()))
    assertNull("no colour is no colour, not the first one", RollColour.of(null))
  }

  @Test
  fun `a colour written out is the six digits the picker's own readout shows`() {
    // The editor prints a tag as `#RRGGBB` beside the swatches, for somebody
    // copying it onto a character sheet. It is the one description of a
    // colour that is exact, and it is `core/model`'s rather than a fourth
    // copy of six digits.
    RollColour.entries.forEach { tag ->
      assertEquals(Hex.of(tag.argb), RollColour.hexOf(tag.argb))
    }
    assertEquals("#1D5FD4", RollColour.hexOf(RollColour.Cobalt.argb))
  }

  @Test
  fun `a colour of somebody's own is written out the same way as one of the twelve`() {
    // A tag is stored as the colour itself, so a colour off the picker and
    // one off the palette are the same kind of thing all the way down.
    assertEquals("#123456", RollColour.hexOf(0xFF123456.toInt()))
    assertNull("a colour nobody offered is a custom one", RollColour.of(0xFF123456.toInt()))
  }

  @Test
  fun `every tag can be seen on both grounds, once it has been clamped`() {
    // The twelve span the hue circle, which means two of them are nearly the
    // page and nearly the ink: `bone` on paper and `ink` at night are each a
    // mark that is simply not there. They go through the same clamp the accent
    // does, so the promise holds for all twelve rather than for ten of them.
    Ground.entries.forEach { ground ->
      RollColour.entries.forEach { tag ->
        val drawn = AccentRamp.clamp(tag.argb, ground)
        assertTrue(
          "${'$'}{tag.name} is invisible on ${'$'}ground",
          Contrast.meets(drawn, ground.backgroundArgb, Contrast.COMPONENT),
        )
      }
    }
  }

  @Test
  fun `a tag that already reads is drawn exactly as it was chosen`() {
    assertEquals(RollColour.Cobalt.argb, AccentRamp.clamp(RollColour.Cobalt.argb, Ground.Light))
  }

  @Test
  fun `and the two that do not are moved`() {
    assertNotEquals(RollColour.Bone.argb, AccentRamp.clamp(RollColour.Bone.argb, Ground.Light))
    assertNotEquals(RollColour.Ink.argb, AccentRamp.clamp(RollColour.Ink.argb, Ground.Dark))
  }
}
