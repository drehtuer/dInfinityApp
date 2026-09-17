package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The six presets, and what happens to the ids the design of 2026-09-17 took
 * away.
 *
 * What this file used to be was six colours measured against both grounds.
 * That test has moved and got stronger: it is `AccentRampTest`'s property over
 * *every* colour now, because the clamp is what keeps an accent legible and
 * the clamp does not care which six are on the swatches
 * (`docs/architecture.md`, "Settings"). What is left here is the part a
 * property cannot say — which colours are offered, what they are called in
 * storage, and that nobody's choice was thrown away.
 */
class AccentColorTest {
  @Test
  fun `ids are unique and stable`() {
    val ids = AccentColor.entries.map(AccentColor::id)
    assertEquals(ids.size, ids.toSet().size, "two accents share an id")
    // Spelled out rather than derived from the enum: this is the set written
    // to storage, so a rename has to fail here before it resets everybody.
    assertEquals(
      listOf("light-blue", "vermilion", "magenta", "cobalt", "pine", "amber"),
      ids,
    )
  }

  /**
   * The two ids that outlived the palette they were named in.
   *
   * `vermilion` is Modernist red — the same `#EC3013`, re-labelled — and
   * `amber` is a deeper amber. Both keep their id because a player who chose
   * that swatch chose *that* colour, and a rename would have quietly moved
   * them to the default.
   */
  @Test
  fun `the ids that survived point at the same colour they always did`() {
    assertEquals(AccentColor.ModernistRed, AccentColor.ofId("vermilion"))
    assertEquals(0xFFEC3013.toInt(), AccentColor.ModernistRed.argb)
    assertEquals(AccentColor.Amber, AccentColor.ofId("amber"))
  }

  @Test
  fun `the swatches are the design's six, in the design's order`() {
    assertEquals(
      listOf(0xFF38A8DC, 0xFFEC3013, 0xFFC2186F, 0xFF1D5FD4, 0xFF0F7A50, 0xFFC07000).map { it.toInt() },
      AccentColor.entries.map(AccentColor::argb),
    )
  }

  @Test
  fun `the default is light blue, and it is what an unreadable setting falls back to`() {
    assertEquals(AccentColor.LightBlue, AccentColor.Default)
    assertEquals(AccentColor.Default, AccentColor.ofId(null))
    assertEquals(AccentColor.Default, AccentColor.ofId(""))
    assertEquals(AccentColor.Default, AccentColor.ofId("chartreuse"))
  }

  @Test
  fun `every id round-trips`() {
    AccentColor.entries.forEach { accent ->
      assertEquals(accent, AccentColor.ofId(accent.id))
    }
  }

  /**
   * The migration, which is the interesting half of changing a stored id.
   *
   * Four of the old six are gone, and the default changed in the same release.
   * Falling back would have repainted every phone that had chosen one of them
   * — blue, in place of the green or the purple they asked for — with nothing
   * on the screen to say why.
   */
  @Test
  fun `a retired id becomes the survivor nearest it rather than the default`() {
    assertEquals(AccentColor.ModernistRed, AccentColor.ofId("coral"))
    assertEquals(AccentColor.LightBlue, AccentColor.ofId("sky"))
    assertEquals(AccentColor.Pine, AccentColor.ofId("moss"))
    assertEquals(AccentColor.Cobalt, AccentColor.ofId("violet"))
    // Three of the four land somewhere other than the default, which is the
    // whole point of having a mapping at all.
    listOf("coral", "moss", "violet").forEach { retired ->
      assertNotEquals(AccentColor.Default, AccentColor.ofId(retired), "$retired was let fall to the default")
    }
  }

  @Test
  fun `only a retired id is migrated`() {
    assertNull(AccentColor.retired("chartreuse"))
    AccentColor.entries.forEach { accent ->
      assertNull(AccentColor.retired(accent.id), "${accent.id} is both offered and retired")
    }
  }

  /**
   * The presets are the design's colours, not six colours chosen for passing a
   * test — and this says so out loud, because it used to be the opposite.
   *
   * Light blue on paper is 2.41:1, which is under the 3:1 bar for a control's
   * own boundary. That is not a fault to fix by editing the hex: it is what
   * `AccentRamp.clamp` is for, and the ramp is what the theme paints with.
   */
  @Test
  fun `a preset may be too pale for its ground, and the clamp is what answers that`() {
    val raw = Contrast.ratio(AccentColor.LightBlue.argb, Ground.Light.backgroundArgb)
    assertTrue(raw < Contrast.COMPONENT, "light blue now clears the bar unclamped: ${"%.2f".format(raw)}:1")
    val clamped = AccentRamp.clamp(AccentColor.LightBlue.argb, Ground.Light)
    assertTrue(Contrast.meets(clamped, Ground.Light.backgroundArgb, Contrast.COMPONENT))
  }

  /** A preset is a choice like any other, so it is stored and read back like one. */
  @Test
  fun `every preset round-trips through the choice it is`() {
    AccentColor.entries.forEach { accent ->
      assertEquals(accent, AccentChoice.ofId(accent.id))
    }
  }
}
