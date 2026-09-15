package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** The one file a package is shared as (`docs/face-designer.md`). */
class PackageZipTest {
  @Test
  fun `every file goes in and comes back out unchanged`() {
    val files =
      mapOf(
        "diceset.toml" to "format = 1".encodeToByteArray(),
        "textures/d6.png" to Drawings.png(768, 512),
      )

    val back = unzip(PackageZip.of(files))

    assertEquals(files.keys, back.keys)
    files.forEach { (path, bytes) -> assertArrayEquals(path, bytes, back[path]) }
  }

  @Test
  fun `entries are in name order, whatever order they were built in`() {
    val files =
      linkedMapOf(
        "textures/d6.png" to Drawings.png(8, 8),
        "diceset.toml" to "format = 1".encodeToByteArray(),
        "textures/d4.png" to Drawings.png(8, 8),
      )

    val order = unzip(PackageZip.of(files)).keys.toList()

    assertEquals(listOf("diceset.toml", "textures/d4.png", "textures/d6.png"), order)
  }

  @Test
  fun `the same package exports to the same bytes twice`() {
    // Nothing in the archive is a clock, so "has this changed since I uploaded
    // it" is a question somebody can answer by looking at the file.
    val files = mapOf("diceset.toml" to "format = 1".encodeToByteArray())

    assertArrayEquals(PackageZip.of(files), PackageZip.of(files))
  }

  @Test
  fun `the set file is at the root, where an install looks for it`() {
    val zip = PackageZip.of(mapOf("diceset.toml" to "format = 1".encodeToByteArray()))

    assertTrue(unzip(zip).containsKey("diceset.toml"))
  }

  @Test
  fun `a package of nothing is still a zip`() {
    assertTrue(unzip(PackageZip.of(emptyMap())).isEmpty())
  }

  private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.getNextEntry() ?: break
        out[entry.name] = zip.readBytes()
      }
    }
    return out
  }
}
