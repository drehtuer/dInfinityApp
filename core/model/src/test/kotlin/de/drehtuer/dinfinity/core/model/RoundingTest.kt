package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoundingTest {
  @Test
  fun `down is what most game rules say, and is the default`() {
    assertEquals(Rounding.Down, Rounding.Default)
    assertEquals(3L, Rounding.Down.divide(7, 2))
  }

  @Test
  fun `nearest rounds a half up and anything below a half down`() {
    assertEquals(4L, Rounding.Nearest.divide(7, 2))
    assertEquals(3L, Rounding.Nearest.divide(10, 3))
    assertEquals(4L, Rounding.Nearest.divide(11, 3))
  }

  @Test
  fun `up is the ceiling`() {
    assertEquals(4L, Rounding.Up.divide(7, 2))
    assertEquals(4L, Rounding.Up.divide(10, 3))
  }

  @Test
  fun `an exact division is the same under every mode`() {
    Rounding.entries.forEach { mode -> assertEquals(3L, mode.divide(6, 2), mode.id) }
  }

  @Test
  fun `down floors a negative rather than truncating towards zero`() {
    assertEquals(-4L, Rounding.Down.divide(-7, 2))
  }

  @Test
  fun `nearest and up both lift a negative half towards zero`() {
    assertEquals(-3L, Rounding.Nearest.divide(-7, 2))
    assertEquals(-3L, Rounding.Up.divide(-7, 2))
  }

  @Test
  fun `dividing by zero is refused rather than producing an infinity`() {
    assertFailsWith<IllegalArgumentException> { Rounding.Down.divide(1, 0) }
  }

  @Test
  fun `a negative divisor divides the same way round`() {
    assertEquals(-4L, Rounding.Down.divide(7, -2))
    assertEquals(-3L, Rounding.Up.divide(7, -2))
  }

  @Test
  fun `every mode resolves from the key it is stored under`() {
    Rounding.entries.forEach { mode -> assertEquals(mode, Rounding.ofId(mode.id)) }
  }

  @Test
  fun `an unreadable setting falls back to the default rather than throwing`() {
    assertEquals(Rounding.Default, Rounding.ofId("sideways"))
    assertEquals(Rounding.Default, Rounding.ofId(null))
  }

  @Test
  fun `apply rounds a value that never was a division`() {
    assertEquals(2L, Rounding.Down.apply(2.9))
    assertEquals(3L, Rounding.Nearest.apply(2.5))
    assertEquals(3L, Rounding.Up.apply(2.1))
  }
}
