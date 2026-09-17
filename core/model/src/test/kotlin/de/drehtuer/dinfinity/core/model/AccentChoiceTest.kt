package de.drehtuer.dinfinity.core.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a chosen accent is written down as, and what comes back.
 *
 * A preset is a name and a custom colour is the colour itself, in one string,
 * because the settings store is a string per key and a second key for "is it
 * custom" is a second key that can disagree with the first.
 */
class AccentChoiceTest {
  @Test
  fun `a custom colour round-trips through storage`() {
    val chosen = AccentChoice.Custom(0xFF2B5AA8.toInt())
    assertEquals("custom:2B5AA8", chosen.id)
    assertEquals(chosen, AccentChoice.ofId(chosen.id))
  }

  /**
   * Every colour, not the one somebody thought of: a round trip that holds for
   * `#2B5AA8` and loses `#000080` would be a picker that eats leading zeros.
   */
  @Test
  fun `any colour at all round-trips`() {
    val random = Random(SEED)
    repeat(COLOURS) {
      val chosen = AccentChoice.Custom(random.nextInt())
      assertEquals(chosen, AccentChoice.ofId(chosen.id), "${chosen.id} did not come back")
      assertEquals(chosen.argb, AccentChoice.ofId(chosen.id).argb)
    }
  }

  /**
   * A translucent accent has no contrast ratio of its own, so the clamp would
   * have nothing to measure. Alpha is forced rather than refused: what arrives
   * here is a colour from a picker, and a picker is not a place to throw.
   */
  @Test
  fun `a custom colour is always opaque`() {
    assertEquals(0xFF38A8DC.toInt(), AccentChoice.Custom(0x0038A8DC).argb)
    assertEquals(AccentChoice.Custom(0x8038A8DC.toInt()), AccentChoice.Custom(0xFF38A8DC.toInt()))
    assertEquals("custom:38A8DC", AccentChoice.Custom(0x0038A8DC).id)
  }

  @Test
  fun `the hex is what was picked, and it is upper case`() {
    assertEquals("#38A8DC", AccentColor.LightBlue.hex)
    assertEquals("#000000", AccentChoice.Custom(0).hex)
    assertEquals("#FFFFFF", AccentChoice.Custom(0xFFFFFFFF.toInt()).hex)
  }

  /**
   * Storage is a string, and a string off a disk is not to be trusted. None of
   * these may throw, and none may produce a colour that is not one — every one
   * of them is the same repaint to the default.
   */
  @Test
  fun `an unreadable setting falls back to the default`() {
    listOf(
      null,
      "",
      "custom:",
      "custom:38A8D",
      "custom:38A8DCC",
      "custom:GGGGGG",
      "custom:+38A8D",
      "custom: 38A8DC",
      "custom:#38A8DC",
      "custom:38a8dc ",
      "CUSTOM:38A8DC",
      "chartreuse",
    ).forEach { stored ->
      assertEquals(AccentChoice.Default, AccentChoice.ofId(stored), "$stored was read as something")
    }
  }

  /** Lower case is read, because a file somebody edited by hand is still readable. */
  @Test
  fun `a custom colour written in lower case is read and rewritten in upper`() {
    val read = AccentChoice.ofId("custom:38a8dc")
    assertEquals(AccentChoice.Custom(0xFF38A8DC.toInt()), read)
    assertEquals("custom:38A8DC", read.id)
  }

  /** A preset is a choice too, so one string reads back as either kind. */
  @Test
  fun `a preset id reads back as its preset`() {
    assertEquals(AccentColor.Pine, AccentChoice.ofId("pine"))
    assertTrue(AccentChoice.ofId("pine") is AccentColor)
    assertTrue(AccentChoice.ofId("custom:0F7A50") is AccentChoice.Custom)
  }

  /** The default is the design's, whichever way it is asked for. */
  @Test
  fun `the default is the default preset`() {
    assertEquals(AccentColor.Default, AccentChoice.Default)
  }

  private companion object {
    const val SEED = 20260917
    const val COLOURS = 500
  }
}
