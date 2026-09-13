package de.drehtuer.dinfinity.dicesets.format

import java.io.File
import java.nio.file.Files
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackageFilesTest {
  @Test
  fun `a package in memory hands back what it was given`() {
    val files = PackageFiles.of(mapOf("a.png" to byteArrayOf(1, 2, 3)))
    assertEquals(3, files.size("a.png"))
    assertTrue(byteArrayOf(1, 2, 3).contentEquals(files.read("a.png")))
  }

  @Test
  fun `a file the package does not have is absent, not an error`() {
    val files = PackageFiles.of(emptyMap())
    assertNull(files.read("a.png"))
    assertNull(files.size("a.png"))
  }

  @Test
  fun `reading does not hand out the package's own array`() {
    val original = byteArrayOf(1, 2, 3)
    val files = PackageFiles.of(mapOf("a.png" to original))
    files.read("a.png")!![0] = 99
    assertEquals(1, files.read("a.png")!![0])
  }

  @Test
  fun `a package of one set file is the shorthand a test wants`() {
    val files = PackageFiles.ofDiceSetToml("format = 1")
    assertEquals("format = 1", files.read(DiceSetValidator.DICE_SET_FILE)?.decodeToString())
  }

  @Test
  fun `a package on disk reads its own files`() {
    val root = Files.createTempDirectory("dinfinity-package").toFile()
    File(root, "textures").mkdirs()
    File(root, "textures/d6.png").writeBytes(byteArrayOf(7, 7))
    val files = PackageFiles.of(root)
    assertEquals(2, files.size("textures/d6.png"))
    assertTrue(byteArrayOf(7, 7).contentEquals(files.read("textures/d6.png")))
    root.deleteRecursively()
  }

  @Test
  fun `a package on disk does not read a directory as a file`() {
    val root = Files.createTempDirectory("dinfinity-package").toFile()
    File(root, "textures").mkdirs()
    assertNull(PackageFiles.of(root).read("textures"))
    root.deleteRecursively()
  }

  @Test
  fun `a symbolic link out of the package reads as nothing at all`() {
    val root = Files.createTempDirectory("dinfinity-package").toFile()
    val outside = Files.createTempFile("dinfinity-secret", ".png").also { it.toFile().writeBytes(byteArrayOf(9)) }
    root.toPath().resolve("escape.png").createSymbolicLinkPointingTo(outside)
    val files = PackageFiles.of(root)
    assertNull(files.read("escape.png"), "a link out of the package must not be readable")
    assertNull(files.size("escape.png"))
    root.deleteRecursively()
    outside.toFile().delete()
  }

  @Test
  fun `a path that climbs out of the package reads as nothing`() {
    val root = Files.createTempDirectory("dinfinity-package").toFile()
    File(root.parentFile, "dinfinity-outside.png").writeBytes(byteArrayOf(5))
    assertNull(PackageFiles.of(root).read("../dinfinity-outside.png"))
    root.deleteRecursively()
    File(root.parentFile, "dinfinity-outside.png").delete()
  }
}
