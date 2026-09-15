package de.drehtuer.dinfinity.core.glyphs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GlyphTest {
  private val square = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)

  @Test
  fun `bounds the ink it is made of`() {
    val ink = assertNotNull(Glyph(advance = 1.0, contours = listOf(square)).inkBounds)
    assertEquals(0.0, ink.minX)
    assertEquals(1.0, ink.maxY)
    assertEquals(1.0, ink.width)
    assertEquals(1.0, ink.height)
    assertEquals(0.5, ink.centreX)
    assertEquals(0.5, ink.centreY)
  }

  @Test
  fun `bounds every contour together, counters included`() {
    val counter = doubleArrayOf(0.25, 0.25, 0.75, 0.25, 0.75, 2.0)
    val ink = assertNotNull(Glyph(advance = 1.0, contours = listOf(square, counter)).inkBounds)
    assertEquals(2.0, ink.maxY)
  }

  @Test
  fun `a glyph with no contours has no ink`() {
    assertEquals(null, Glyph(advance = 0.3, contours = emptyList()).inkBounds)
  }

  @Test
  fun `two rectangles add up to the one that holds both`() {
    val left = Glyph.Bounds(0.0, 0.0, 1.0, 1.0)
    val right = Glyph.Bounds(2.0, -1.0, 3.0, 0.5)
    assertEquals(Glyph.Bounds(0.0, -1.0, 3.0, 1.0), left + right)
  }

  @Test
  fun `refuses a glyph that advances by nothing`() {
    assertFailsWith<IllegalArgumentException> { Glyph(advance = 0.0, contours = listOf(square)) }
  }

  @Test
  fun `refuses a contour that is not a polygon`() {
    assertFailsWith<IllegalArgumentException> {
      Glyph(advance = 1.0, contours = listOf(doubleArrayOf(0.0, 0.0, 1.0, 1.0)))
    }
  }

  @Test
  fun `refuses a contour with a coordinate missing`() {
    assertFailsWith<IllegalArgumentException> {
      Glyph(advance = 1.0, contours = listOf(doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.5)))
    }
  }
}
