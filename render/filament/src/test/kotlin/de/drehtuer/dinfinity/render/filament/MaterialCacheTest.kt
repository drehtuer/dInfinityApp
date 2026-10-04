package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer

class MaterialCacheTest {
  @get:Rule
  val folder = TemporaryFolder()

  private var compiled = 0

  private fun compile(vararg bytes: Byte): () -> ByteBuffer =
    {
      compiled++
      ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.flip() }
    }

  private fun ByteBuffer.bytes(): ByteArray = ByteArray(remaining()).also { duplicate().get(it) }

  @Test
  fun `the first launch compiles and the next one reads it back`() {
    val dir = File(folder.root, "materials")

    val first = MaterialCache(dir).packet("key", compile(1, 2, 3))
    val second = MaterialCache(dir).packet("key", compile(9))

    assertEquals(1, compiled)
    assertArrayEquals(byteArrayOf(1, 2, 3), first.bytes())
    assertArrayEquals(byteArrayOf(1, 2, 3), second.bytes())
  }

  @Test
  fun `the packet handed back is whole even after it was written`() {
    val packet = MaterialCache(folder.root).packet("key", compile(4, 5))

    assertEquals(0, packet.position())
    assertEquals(2, packet.remaining())
  }

  @Test
  fun `no directory compiles every time and writes nothing`() {
    MaterialCache.NONE.packet("key", compile(1))
    MaterialCache.NONE.packet("key", compile(1))

    assertEquals(2, compiled)
  }

  @Test
  fun `an empty file is a miss`() {
    File(folder.root, "key.filamat").writeBytes(ByteArray(0))

    val packet = MaterialCache(folder.root).packet("key", compile(7))

    assertEquals(1, compiled)
    assertArrayEquals(byteArrayOf(7), packet.bytes())
    assertArrayEquals(byteArrayOf(7), File(folder.root, "key.filamat").readBytes())
  }

  @Test
  fun `a directory that cannot be made still gives the compiled packet`() {
    val blocker = folder.newFile("not-a-directory")

    val packet = MaterialCache(File(blocker, "materials")).packet("key", compile(8))

    assertArrayEquals(byteArrayOf(8), packet.bytes())
    assertFalse(File(blocker, "materials").exists())
  }

  @Test
  fun `an unreadable entry is compiled instead`() {
    File(folder.root, "key.filamat").mkdirs()

    val packet = MaterialCache(folder.root).packet("key", compile(6))

    assertEquals(1, compiled)
    assertArrayEquals(byteArrayOf(6), packet.bytes())
    assertTrue(File(folder.root, "key.filamat").isDirectory)
    assertFalse(File(folder.root, "key.filamat.partial").exists())
  }

  @Test
  fun `the key names the source, the backend and the blending`() {
    val opaque = MaterialCache.keyOf("source", backend = "OPENGL", blended = false)

    assertTrue(opaque.startsWith("dice-opaque-opengl-"))
    assertTrue(MaterialCache.keyOf("source", backend = "OPENGL", blended = true).startsWith("dice-blended-opengl-"))
    assertNotEquals(opaque, MaterialCache.keyOf("source", backend = "VULKAN", blended = false))
    assertNotEquals(opaque, MaterialCache.keyOf("other source", backend = "OPENGL", blended = false))
    assertEquals(opaque, MaterialCache.keyOf("source", backend = "OPENGL", blended = false))
  }
}
