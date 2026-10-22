package de.drehtuer.dinfinity.dicesets.builtin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The sample archives the app carries ([SampleSets]) are exactly the committed ones. */
class SampleSetsTest {
  private val samples: File =
    generateSequence(File("").absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "sample-sets").isDirectory }
      ?.let { File(it, "sample-sets") }
      ?: error("no repository root above ${File("").absolutePath}")

  @Test
  fun `every committed archive is carried, and nothing else`() {
    val committed =
      samples
        .listFiles { file -> file.extension == "zip" }
        .orEmpty()
        .map(File::getName)
        .sorted()
    assertEquals(committed, SampleSets.ARCHIVES.sorted())
  }

  @Test
  fun `each archive carried is the committed file, byte for byte`() {
    SampleSets.ARCHIVES.forEach { name ->
      assertArrayEquals("$name differs from sample-sets/$name", File(samples, name).readBytes(), SampleSets.read(name))
    }
  }

  @Test
  fun `an archive the app does not carry reads as nothing`() {
    assertNull(SampleSets.read("velvet.zip"))
  }
}
