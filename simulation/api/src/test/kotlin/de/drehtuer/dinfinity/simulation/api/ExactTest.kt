package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That the seeded half of a roll comes out the same on every runtime.
 *
 * The values below are pinned as raw bit patterns rather than compared with a
 * tolerance, because a tolerance is exactly what this must not have. They are
 * fdlibm's doubles, which is what [StrictMath] is defined to be on every
 * platform; if one of them ever moves, every seeded spawn and every hull
 * vertex has moved with it and no recorded roll replays any more.
 */
class ExactTest {
  @Test
  fun `sine is fdlibm's sine, bit for bit`() {
    assertEquals(0x3FDE_AEE8_744B_05F0uL.toLong(), Exact.sin(HALF).toRawBits())
    assertEquals(0x3FEA_ED54_8F09_0CEEuL.toLong(), Exact.sin(ONE).toRawBits())
    assertEquals(0x3FEE_354A_E0DF_010AuL.toLong(), Exact.sin(AWKWARD).toRawBits())
  }

  @Test
  fun `cosine is fdlibm's cosine, bit for bit`() {
    assertEquals(0x3FEC_1528_065B_7D50uL.toLong(), Exact.cos(HALF).toRawBits())
    assertEquals(0x3FE1_4A28_0FB5_068CuL.toLong(), Exact.cos(ONE).toRawBits())
    assertEquals(0x3FD5_1D92_576A_34F6uL.toLong(), Exact.cos(AWKWARD).toRawBits())
  }

  @Test
  fun `atan2 is fdlibm's atan2, bit for bit`() {
    assertEquals(0x4005_E4C3_6CA0_118AuL.toLong(), Exact.atan2(0.3, -0.7).toRawBits())
  }

  @Test
  fun `the runtime's own trigonometry is allowed to disagree, and this does not`() {
    // Not an assertion that the two differ — on most runtimes they will not.
    // The point is that `Math` is *permitted* to be within an ulp and to
    // change with the hardware, and that this object is not, so the check is
    // against the definition rather than against today's JVM.
    SPREAD.forEach { angle ->
      assertEquals(StrictMath.sin(angle).toRawBits(), Exact.sin(angle).toRawBits(), "sin($angle)")
      assertEquals(StrictMath.cos(angle).toRawBits(), Exact.cos(angle).toRawBits(), "cos($angle)")
      assertEquals(
        StrictMath.atan2(angle, 1 - angle).toRawBits(),
        Exact.atan2(angle, 1 - angle).toRawBits(),
        "atan2($angle)",
      )
    }
  }

  private companion object {
    const val HALF = 0.5
    const val ONE = 1.0
    const val AWKWARD = 1.234567
    val SPREAD = List(32) { it * 0.37 - 5.0 }
  }
}
