package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A frame read back off the GPU, and the two things about it that can be wrong
 * without a device to say so: which way up it is, and whether it is a picture
 * at all.
 */
class SnapshotTest {
  @Test
  fun `a frame is as many bytes as it has pixels`() {
    val failed =
      assertThrows(IllegalArgumentException::class.java) {
        Snapshot(width = 2, height = 2, pixels = ByteArray(2 * 2 * Snapshot.CHANNELS - 1))
      }

    assertTrue(failed.message, failed.message!!.contains("16 bytes"))
  }

  @Test
  fun `a frame with no pixels in either direction is not a frame`() {
    assertThrows(IllegalArgumentException::class.java) { Snapshot(width = 0, height = 1, pixels = ByteArray(0)) }
    assertThrows(IllegalArgumentException::class.java) { Snapshot(width = 1, height = 0, pixels = ByteArray(0)) }
  }

  @Test
  fun `the rows are turned over, because a driver counts them from the bottom`() {
    // Two rows of one pixel: the driver hands back the bottom one first.
    val bottomUp = byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2)

    val turned = Snapshot.fromBottomUp(width = 1, height = 2, rows = bottomUp)

    assertArrayEquals(byteArrayOf(2, 2, 2, 2, 1, 1, 1, 1), turned.pixels)
  }

  @Test
  fun `a wider frame keeps the order of the pixels within each row`() {
    // Two rows of two, so that a flip that also reversed each row would show.
    val bottomUp = byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4)

    val turned = Snapshot.fromBottomUp(width = 2, height = 2, rows = bottomUp)

    assertArrayEquals(byteArrayOf(3, 3, 3, 3, 4, 4, 4, 4, 1, 1, 1, 1, 2, 2, 2, 2), turned.pixels)
    assertEquals(2, turned.width)
    assertEquals(2, turned.height)
  }

  @Test
  fun `a single row is already the right way up`() {
    val rows = byteArrayOf(9, 8, 7, 6)

    assertArrayEquals(rows, Snapshot.fromBottomUp(width = 1, height = 1, rows = rows).pixels)
  }

  @Test
  fun `a frame that is not the size it says it is refuses to be turned over`() {
    assertThrows(IllegalArgumentException::class.java) {
      Snapshot.fromBottomUp(width = 2, height = 2, rows = ByteArray(4))
    }
  }

  @Test
  fun `a frame of one colour is what nothing having been drawn looks like`() {
    val black = Snapshot(width = 3, height = 2, pixels = ByteArray(3 * 2 * Snapshot.CHANNELS))

    assertTrue(black.uniform)
  }

  @Test
  fun `one pixel out of the whole frame is enough to make it a picture`() {
    val pixels = ByteArray(4 * 4 * Snapshot.CHANNELS)
    pixels[pixels.size - 2] = 1

    assertFalse(Snapshot(width = 4, height = 4, pixels = pixels).uniform)
  }

  @Test
  fun `a frame that is all the same colour but not black is still one colour`() {
    val grey = ByteArray(2 * 2 * Snapshot.CHANNELS) { if (it % Snapshot.CHANNELS == 3) -1 else 0x40 }

    assertTrue(Snapshot(width = 2, height = 2, pixels = grey).uniform)
  }

  @Test
  fun `packed as ints, the channels stay where they were`() {
    // Red, green, blue and a half-transparent white — the order a channel
    // swap would show up in straight away.
    val red = byteArrayOf(-1, 0, 0, -1)
    val green = byteArrayOf(0, -1, 0, -1)
    val blue = byteArrayOf(0, 0, -1, -1)
    val faded = byteArrayOf(-1, -1, -1, 0x80.toByte())
    val frame = Snapshot(width = 4, height = 1, pixels = red + green + blue + faded)

    assertArrayEquals(
      intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0x80FFFFFF.toInt()),
      frame.argb(),
    )
  }

  @Test
  fun `there is one int per pixel and they are in the order the rows are`() {
    val frame = Snapshot(width = 2, height = 3, pixels = ByteArray(2 * 3 * Snapshot.CHANNELS))

    assertEquals(2 * 3, frame.argb().size)
  }

  @Test
  fun `a frame of one pixel has nothing to compare and is one colour`() {
    assertTrue(Snapshot(width = 1, height = 1, pixels = byteArrayOf(1, 2, 3, 4)).uniform)
  }
}
