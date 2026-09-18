package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.model.AccentRamp
import de.drehtuer.dinfinity.core.model.Contrast
import de.drehtuer.dinfinity.core.model.Ground
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
  fun `a hex colour is read as typed, long or short, with or without the hash`() {
    assertEquals(0xFF123456.toInt(), RollColour.parseHex("#123456"))
    assertEquals(0xFF123456.toInt(), RollColour.parseHex("123456"))
    assertEquals(0xFFAABBCC.toInt(), RollColour.parseHex("#abc"))
    assertEquals(0xFFFFFFFF.toInt(), RollColour.parseHex("#ffffff"))
    assertEquals(0xFF000000.toInt(), RollColour.parseHex("  #000000  "))
  }

  @Test
  fun `what is not a colour chooses nothing`() {
    // Half-typed is somebody in the middle of typing, not a mistake to shout
    // about — so it is null and the tag simply does not change yet.
    assertNull(RollColour.parseHex(""))
    assertNull(RollColour.parseHex("#"))
    assertNull(RollColour.parseHex("#12"))
    assertNull(RollColour.parseHex("#12345"))
    assertNull(RollColour.parseHex("#1234567"))
    assertNull(RollColour.parseHex("#12345g"))
    assertNull(RollColour.parseHex("cobalt"))
  }

  @Test
  fun `a colour written out reads back as itself`() {
    RollColour.entries.forEach { tag ->
      assertEquals(tag.argb, RollColour.parseHex(RollColour.hexOf(tag.argb)))
    }
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
