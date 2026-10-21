package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The studio's folded cube on disk: the first launch folds and keeps it, every
 * launch after reads it, and anything wrong with the file folds again
 * (`StudioCache`).
 */
class StudioCacheTest {
  @get:Rule
  val folder = TemporaryFolder()

  private var folded = 0

  private val hdr = "a panorama".toByteArray()

  /** A cube of [SIZE] pixels whose every float is [value]. */
  private fun fold(value: Float = 1.5f): () -> FloatArray? =
    {
      folded++
      FloatArray(StudioCache.bytesOf(SIZE) / Float.SIZE_BYTES) { value + it % 7 }
    }

  private fun ByteBuffer.floats(): FloatArray {
    val view = duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
    return FloatArray(view.remaining()).also { view.get(it) }
  }

  private fun cubeFile(dir: File): File = File(dir, StudioCache.keyOf(hdr, SIZE) + ".cube")

  @Test
  fun `the first launch folds and the next one reads it back`() {
    val dir = File(folder.root, "studio")

    val first = requireNotNull(StudioCache(dir).faces(hdr, SIZE, fold()))
    val second = requireNotNull(StudioCache(dir).faces(hdr, SIZE, fold(value = 9f)))

    assertEquals(1, folded)
    assertFalse(first.fromDisk)
    assertTrue(second.fromDisk)
    assertArrayEquals(first.buffer.floats(), second.buffer.floats(), 0f)
  }

  @Test
  fun `the faces handed back are whole, in native order, whether folded or read`() {
    val made = requireNotNull(StudioCache(folder.root).faces(hdr, SIZE, fold()))
    val read = requireNotNull(StudioCache(folder.root).faces(hdr, SIZE, fold()))

    listOf(made, read).forEach { faces ->
      assertTrue(faces.buffer.isDirect)
      assertEquals(0, faces.buffer.position())
      assertEquals(StudioCache.bytesOf(SIZE), faces.buffer.remaining())
      assertEquals(ByteOrder.nativeOrder(), faces.buffer.order())
    }
  }

  @Test
  fun `no directory folds every time and writes nothing`() {
    StudioCache.NONE.faces(hdr, SIZE, fold())
    StudioCache.NONE.faces(hdr, SIZE, fold())

    assertEquals(2, folded)
  }

  @Test
  fun `a file that is not a panorama is not remembered`() {
    val faces = StudioCache(folder.root).faces(hdr, SIZE) { null }

    assertNull(faces)
    assertFalse(cubeFile(folder.root).exists())
  }

  @Test
  fun `a cube cut short is folded again`() {
    StudioCache(folder.root).faces(hdr, SIZE, fold())
    RandomAccessFile(cubeFile(folder.root), "rw").use { it.setLength(it.length() - 1) }

    val faces = requireNotNull(StudioCache(folder.root).faces(hdr, SIZE, fold()))

    assertEquals(2, folded)
    assertFalse(faces.fromDisk)
  }

  @Test
  fun `a cube whose bytes changed under it is folded again`() {
    StudioCache(folder.root).faces(hdr, SIZE, fold())
    RandomAccessFile(cubeFile(folder.root), "rw").use { file ->
      file.seek(FLIPPED_AT)
      val byte = file.read()
      file.seek(FLIPPED_AT)
      file.write(byte xor 0xFF)
    }

    val faces = requireNotNull(StudioCache(folder.root).faces(hdr, SIZE, fold()))

    assertEquals(2, folded)
    assertFalse(faces.fromDisk)
    // And the bad file was replaced by a good one.
    assertTrue(requireNotNull(StudioCache(folder.root).faces(hdr, SIZE, fold())).fromDisk)
  }

  @Test
  fun `a directory that cannot be made still gives the folded faces`() {
    val blocker = folder.newFile("not-a-directory")

    val faces = StudioCache(File(blocker, "studio")).faces(hdr, SIZE, fold())

    assertEquals(StudioCache.bytesOf(SIZE), requireNotNull(faces).buffer.remaining())
    assertFalse(File(blocker, "studio").exists())
  }

  @Test
  fun `an unreadable entry is folded instead and leaves nothing behind`() {
    cubeFile(folder.root).mkdirs()

    val faces = StudioCache(folder.root).faces(hdr, SIZE, fold())

    assertEquals(1, folded)
    assertFalse(requireNotNull(faces).fromDisk)
    assertFalse(File(folder.root, cubeFile(folder.root).name + ".partial").exists())
  }

  @Test
  fun `a fold of the wrong size is a mistake, not a cube`() {
    assertThrows(IllegalArgumentException::class.java) {
      StudioCache(folder.root).faces(hdr, SIZE) { FloatArray(3) }
    }
  }

  @Test
  fun `six faces of RGB floats`() {
    assertEquals(6 * SIZE * SIZE * 3 * 4, StudioCache.bytesOf(SIZE))
    // The shipped size: 4.7 MB, which is what a later launch reads.
    assertEquals(4_718_592, StudioCache.bytesOf(StudioCube.FACE_SIZE))
    assertThrows(IllegalArgumentException::class.java) { StudioCache.bytesOf(0) }
  }

  @Test
  fun `the key names the file, the size, the fold and the byte order`() {
    val key = StudioCache.keyOf(hdr, SIZE, ByteOrder.LITTLE_ENDIAN)

    assertTrue(key, key.startsWith("studio-"))
    assertTrue(key, key.endsWith("-$SIZE-v${StudioCache.FOLD_VERSION}-le"))
    assertEquals(key, StudioCache.keyOf(hdr.copyOf(), SIZE, ByteOrder.LITTLE_ENDIAN))
    assertNotEquals(key, StudioCache.keyOf("another panorama".toByteArray(), SIZE, ByteOrder.LITTLE_ENDIAN))
    assertNotEquals(key, StudioCache.keyOf(hdr, SIZE * 2, ByteOrder.LITTLE_ENDIAN))
    assertNotEquals(key, StudioCache.keyOf(hdr, SIZE, ByteOrder.BIG_ENDIAN))
  }

  @Test
  fun `the checksum sees a single changed byte`() {
    val bytes = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
    val changed = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 5))

    assertEquals(StudioCache.checksumOf(bytes.duplicate()), StudioCache.checksumOf(bytes.duplicate()))
    assertNotEquals(StudioCache.checksumOf(bytes), StudioCache.checksumOf(changed))
  }

  private companion object {
    const val SIZE = 4
    const val FLIPPED_AT = 17L
  }
}
