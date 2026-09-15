package de.drehtuer.dinfinity.core.glyphs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignedDistanceFieldTest {
  /** A square covering the middle half of the cell, wound anticlockwise. */
  private val middle = listOf(doubleArrayOf(0.25, 0.25, 0.75, 0.25, 0.75, 0.75, 0.25, 0.75))

  private fun ByteArray.at(
    column: Int,
    row: Int,
    size: Int = 32,
  ): Int = this[row * size + column].toInt() and 0xFF

  @Test
  fun `is as big as it was asked for`() {
    assertEquals(32 * 32, SignedDistanceField.cell(middle, size = 32).size)
    assertEquals(8 * 8, SignedDistanceField.cell(middle, size = 8).size)
  }

  @Test
  fun `says inside inside and outside outside`() {
    val field = SignedDistanceField.cell(middle, size = 32)
    assertTrue(field.at(16, 16) > SignedDistanceField.EDGE, "the middle of the square is not ink")
    assertTrue(field.at(1, 1) < SignedDistanceField.EDGE, "the corner of the cell is ink")
  }

  @Test
  fun `crosses the edge where the outline is`() {
    // Walking in from the corner, the first pixel that reads as ink is the one
    // the outline runs through — a quarter of the way across a 32-pixel cell.
    val field = SignedDistanceField.cell(middle, size = 32)
    val crossing = (0 until 32).first { field.at(it, 16) >= SignedDistanceField.EDGE }
    assertTrue(crossing in 7..9, "the edge came out at $crossing rather than at 8")
  }

  @Test
  fun `a hole in a ring is outside the ink`() {
    // The counter of a `0`: an inner contour wound the other way. Under the
    // nonzero rule the two cancel, so the middle is not ink.
    val ring =
      listOf(
        doubleArrayOf(0.1, 0.1, 0.9, 0.1, 0.9, 0.9, 0.1, 0.9),
        doubleArrayOf(0.3, 0.3, 0.3, 0.7, 0.7, 0.7, 0.7, 0.3),
      )
    val field = SignedDistanceField.cell(ring, size = 32)
    assertTrue(field.at(16, 16) < SignedDistanceField.EDGE, "the hole is filled in")
    assertTrue(field.at(16, 6) > SignedDistanceField.EDGE, "the ring itself is not ink")
  }

  @Test
  fun `a cell with no ink is a cell of zeroes`() {
    val field = SignedDistanceField.cell(emptyList(), size = 8)
    assertTrue(field.all { it.toInt() == 0 })
  }

  @Test
  fun `far from the ink it reads zero rather than something faint`() {
    // Everything past the spread is clamped, which is what lets the rasteriser
    // skip most of a cell without changing the answer.
    val field = SignedDistanceField.cell(middle, size = 32, spread = 1.0 / 32)
    assertEquals(0, field.at(0, 0))
  }

  @Test
  fun `the field is measured from the middle of each pixel`() {
    // Sampling a pixel's corner puts every number half a pixel out, which
    // shows as digits sitting low and to the left. A field of a square
    // centred in the cell is therefore symmetric about the middle.
    val field = SignedDistanceField.cell(middle, size = 32)
    (0 until 32).forEach { assertEquals(field.at(it, 16), field.at(31 - it, 16), "column $it") }
    (0 until 32).forEach { assertEquals(field.at(16, it), field.at(16, 31 - it), "row $it") }
  }

  @Test
  fun `turns a byte back into how far into the ink it is`() {
    assertEquals(0.0, SignedDistanceField.EDGE.toByte().asDistance())
    assertTrue(0.toByte().asDistance() < 0)
    assertTrue(SignedDistanceField.EDGE.toByte().isInk())
    assertTrue(!(SignedDistanceField.EDGE - 1).toByte().isInk())
    assertTrue(0xFF.toByte().isInk())
  }

  @Test
  fun `refuses a cell of no pixels`() {
    assertFailsWith<IllegalArgumentException> { SignedDistanceField.cell(middle, size = 0) }
  }

  @Test
  fun `refuses a spread of nothing`() {
    assertFailsWith<IllegalArgumentException> { SignedDistanceField.cell(middle, spread = 0.0) }
  }
}
