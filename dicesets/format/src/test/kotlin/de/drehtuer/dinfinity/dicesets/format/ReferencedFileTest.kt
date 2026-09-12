package de.drehtuer.dinfinity.dicesets.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rule that makes a package a sandbox, tested with the paths an archive
 * written by somebody unfriendly would actually contain
 * (`SECURITY.md`, `docs/dice-sets.md`).
 */
class ReferencedFileTest {
  private val images = DiceSetLimits.IMAGE_EXTENSIONS

  @Test
  fun `an ordinary relative path is fine`() {
    val reference = ReferencedFile.parse("textures/d20.png", images)
    assertNotNull(reference)
    assertEquals("textures/d20.png", reference.path)
    assertEquals("png", reference.extension)
  }

  @Test
  fun `a path that climbs out of the package is refused`() {
    listOf("../secrets.png", "textures/../../secrets.png", "a/b/../../../c.png").forEach { raw ->
      assertNull(ReferencedFile.parse(raw, images), raw)
      assertTrue("outside the package" in ReferencedFile.reasonToRefuse(raw, images).orEmpty(), raw)
    }
  }

  @Test
  fun `a path that climbs out with backslashes is refused too`() {
    val raw = "..\\..\\etc\\passwd.png"
    assertNull(ReferencedFile.parse(raw, images))
    assertTrue("outside the package" in ReferencedFile.reasonToRefuse(raw, images).orEmpty())
  }

  @Test
  fun `an absolute path is refused, in either dialect`() {
    listOf("/etc/passwd.png", "\\windows\\system32.png", "C:/windows/system32.png").forEach { raw ->
      assertNull(ReferencedFile.parse(raw, images), raw)
      assertTrue("absolute" in ReferencedFile.reasonToRefuse(raw, images).orEmpty(), raw)
    }
  }

  @Test
  fun `an empty path is refused`() {
    assertNull(ReferencedFile.parse("", images))
    assertNull(ReferencedFile.parse("   ", images))
  }

  @Test
  fun `a path with an empty segment is refused`() {
    assertNull(ReferencedFile.parse("textures//d6.png", images))
  }

  @Test
  fun `an extension not on the allowlist is refused`() {
    listOf("d6.svg", "d6.so", "d6", "d6.png.exe").forEach { raw ->
      assertNull(ReferencedFile.parse(raw, images), raw)
    }
  }

  @Test
  fun `an extension is matched whatever case it is written in`() {
    assertNotNull(ReferencedFile.parse("textures/D6.PNG", images))
    assertEquals("png", ReferencedFile.parse("textures/D6.PNG", images)?.extension)
  }

  @Test
  fun `the wider allowlist takes the files a package may carry`() {
    listOf("README.md", "LICENSE.txt", "meshes/d7.obj", "diceset.toml").forEach { raw ->
      assertNotNull(ReferencedFile.parse(raw), raw)
    }
  }

  @Test
  fun `a single dot segment is left alone, because it goes nowhere`() {
    assertNotNull(ReferencedFile.parse("./d6.png", images))
  }

  @Test
  fun `a backslash path is normalised to forward slashes`() {
    assertEquals("textures/d6.png", ReferencedFile.parse("textures\\d6.png", images)?.path)
  }

  @Test
  fun `a good path has nothing to say about it`() {
    assertNull(ReferencedFile.reasonToRefuse("textures/d6.png", images))
  }
}
