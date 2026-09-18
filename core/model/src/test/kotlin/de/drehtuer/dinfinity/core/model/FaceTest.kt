package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceTest {
  @Test
  fun `a face with no label prints its own value`() {
    assertEquals(Face(index = 0, value = 7, label = "7"), Face.labelled(index = 0, value = 7))
  }

  @Test
  fun `a negative value prints its sign, as a fudge die needs`() {
    // A typographic minus, not the hyphen `toString` writes: a hyphen is drawn
    // short, high and thin, and beside the `+` on the next face of the same
    // die it does not read as the other half of a pair. The built-in font
    // carries both, so this is a choice rather than a limit.
    assertEquals("\u22121", Face.labelled(index = 0, value = -1).label)
    assertEquals('\u2212', Face.labelled(index = 0, value = -1).label.first())
  }

  @Test
  fun `the hyphen a keyboard has is not the minus a die is printed with`() {
    // Worth saying out loud, because the two are indistinguishable in a diff
    // and the wrong one is what `Int.toString` hands you.
    assertEquals(false, Face.printed(-4).contains('-'))
    assertEquals("\u22129999", Face.printed(-9999))
  }

  @Test
  fun `a value that is not negative is written exactly as the number is`() {
    // No sign, no padding, no cleverness: `0` is `0` and `10` is `10`.
    assertEquals("0", Face.printed(0))
    assertEquals("10", Face.printed(10))
    assertEquals("9999", Face.printed(9999))
  }

  @Test
  fun `the documented value range is what set files may use`() {
    assertTrue(-9999 in Face.ValueRange)
    assertTrue(9999 in Face.ValueRange)
    assertTrue(10000 !in Face.ValueRange)
  }

  @Test
  fun `a label longer than four characters is not legible on a phone`() {
    assertEquals(4, Face.MAX_LABEL_LENGTH)
    assertTrue("00".length <= Face.MAX_LABEL_LENGTH)
  }

  @Test
  fun `a face carries a symbol just as well as a number`() {
    val skull = Face(index = 0, value = 1, label = "💀")
    assertEquals(1, skull.value)
    assertEquals("💀", skull.label)
  }
}
