package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A frame read back off the GPU, and what about it can be wrong without a
 * device to say so: whether it is a picture at all, and which order its
 * channels are in. Which way up it arrives is a device's question
 * (`PrintedNumbersDeviceTest`).
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
  fun `a frame keeps its rows in the order they were handed over`() {
    // Filament hands a frame over top row first, and it is kept that way: the
    // turn this once made is what put every thumbnail upside down.
    val rows = byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4)

    assertArrayEquals(rows, Snapshot(width = 2, height = 2, pixels = rows).pixels)
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
