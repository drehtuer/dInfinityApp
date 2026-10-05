package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceMapTest {
  @Test
  fun `only the colour picture is uploaded as a colour`() {
    // A normal or a roughness map read as sRGB would bend every number in it.
    assertTrue(SurfaceMap.ALBEDO.colour)
    assertFalse(SurfaceMap.NORMAL.colour)
    assertFalse(SurfaceMap.ROUGHNESS.colour)
  }

  @Test
  fun `a mip chain runs down to a single pixel on the longer side`() {
    assertEquals(11, SurfaceMap.mipLevelsOf(1024, 1024))
    assertEquals(12, SurfaceMap.mipLevelsOf(2048, 2048))
    assertEquals(12, SurfaceMap.mipLevelsOf(2048, 1024))
    assertEquals(12, SurfaceMap.mipLevelsOf(1024, 2048))
    // Not a power of two: 1542 halves to 771, 385, … 1, eleven levels.
    assertEquals(11, SurfaceMap.mipLevelsOf(2048 - 1, 1542))
    assertEquals(1, SurfaceMap.mipLevelsOf(1, 1))
    assertEquals("a picture of nothing still has its one level", 1, SurfaceMap.mipLevelsOf(0, 0))
  }
}
