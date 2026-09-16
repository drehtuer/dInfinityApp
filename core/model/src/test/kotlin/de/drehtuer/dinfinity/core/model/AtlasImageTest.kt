package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlasImageTest {
  @Test
  fun `an atlas is as many bytes as its pixels have channels`() {
    val image = AtlasImage(width = 2, height = 3, pixels = ByteArray(2 * 3 * 4))

    assertEquals(2, image.width)
    assertEquals(3, image.height)
    assertEquals(0, image.alphaAt(1, 2))
  }

  @Test
  fun `an atlas whose bytes do not match its size is refused`() {
    val short = assertFailsWith<IllegalArgumentException> { AtlasImage(2, 2, ByteArray(4)) }

    assertTrue(short.message!!.contains("16 bytes"))
    assertFailsWith<IllegalArgumentException> { AtlasImage(0, 4, ByteArray(0)) }
  }

  @Test
  fun `a pixel outside the picture is not a pixel`() {
    val image = blank(4, 4)

    assertFailsWith<IllegalArgumentException> { image.alphaAt(4, 0) }
    assertFailsWith<IllegalArgumentException> { image.alphaAt(0, -1) }
  }

  @Test
  fun `alpha is read from the fourth channel of the pixel that owns it`() {
    val image = painted(2, 1) { x, _ -> if (x == 0) 0x00 else 0xFF }

    assertEquals(0x00, image.alphaAt(0, 0))
    assertEquals(0xFF, image.alphaAt(1, 0))
  }

  @Test
  fun `an atlas nobody drew in leaves every face to be printed`() {
    // A d6 is a 3x2 grid, so a clear 30x20 picture is six clear cells.
    val image = blank(30, 20)

    assertEquals(listOf(0, 1, 2, 3, 4, 5), image.emptyCells(faces = 6))
  }

  @Test
  fun `a cell with one drawn pixel in it is not empty`() {
    // Face 4 of a d6 is column 1, row 1 of a 3x2 grid: x in 10..19, y in 10..19.
    val image = painted(30, 20) { x, y -> if (x == 12 && y == 15) 0xFF else 0x00 }

    assertEquals(listOf(0, 1, 2, 3, 5), image.emptyCells(faces = 6))
    assertFalse(image.cellIsEmpty(faces = 6, index = 4))
  }

  @Test
  fun `a pixel an eraser left behind does not count as drawing`() {
    val image = painted(30, 20) { _, _ -> AtlasImage.CLEAR_ALPHA }

    // kotlin.test takes the message last, where JUnit takes it first.
    assertEquals(6, image.emptyCells(faces = 6).size, "an alpha of one is not a picture of anything")
    assertTrue(painted(30, 20) { _, _ -> AtlasImage.CLEAR_ALPHA + 1 }.emptyCells(faces = 6).isEmpty())
  }

  @Test
  fun `only the cells a face sits in are counted`() {
    // A d20 is 5x4 — twenty cells for twenty faces, none spare — and a d18 is
    // 5x4 as well, which leaves two cells belonging to no face at all.
    val image = blank(50, 40)

    assertEquals(18, image.emptyCells(faces = 18).size)
    assertEquals(20, image.emptyCells(faces = 20).size)
  }

  @Test
  fun `an atlas smaller than its grid has cells with nothing in them`() {
    // Two pixels cut into a d6's three columns: the first column gets none of
    // them at all, and a cell with no pixels is a cell nobody drew in.
    val image = painted(2, 2) { _, _ -> 0xFF }

    assertTrue(image.cellIsEmpty(faces = 6, index = 0))
    assertFalse(image.cellIsEmpty(faces = 6, index = 1))
  }

  @Test
  fun `a face the die does not have has no cell to ask about`() {
    val image = blank(30, 20)

    assertFailsWith<IllegalArgumentException> { image.cellIsEmpty(faces = 6, index = 6) }
    assertFailsWith<IllegalArgumentException> { image.cellIsEmpty(faces = 6, index = -1) }
  }

  private fun blank(
    width: Int,
    height: Int,
  ): AtlasImage = AtlasImage(width, height, ByteArray(width * height * AtlasImage.CHANNELS))

  private fun painted(
    width: Int,
    height: Int,
    alpha: (Int, Int) -> Int,
  ): AtlasImage {
    val pixels = ByteArray(width * height * AtlasImage.CHANNELS)
    for (y in 0 until height) {
      for (x in 0 until width) {
        pixels[(y * width + x) * AtlasImage.CHANNELS + 3] = alpha(x, y).toByte()
      }
    }
    return AtlasImage(width, height, pixels)
  }
}
