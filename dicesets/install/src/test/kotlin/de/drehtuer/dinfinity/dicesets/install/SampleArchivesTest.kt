package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.core.model.DiceSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * `sample-sets/`, the downloadable sample sets (`sample-sets/README.md`),
 * installed the way a player installs them: each committed zip through
 * [PackageInstaller], the same extract, validate and move the file picker
 * runs.
 *
 * Two things can go wrong with a committed archive that a folder cannot: it
 * can be left behind when its folder changes, and it can be packed in a way
 * the extractor refuses. The first is checked byte for byte against the
 * folder, so `build-archives.py` has to be run again; the second is the
 * install itself.
 */
class SampleArchivesTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-samples").toFile()
  private val installer = PackageInstaller(File(temporary, "dicesets"))
  private val samples = File(repositoryRoot(), "sample-sets")

  @After
  fun clean() {
    temporary.deleteRecursively()
  }

  @Test
  fun `there are the four sample sets, each with its archive`() {
    assertEquals(SETS, folders().map(File::getName))
    folders().forEach { assertTrue("${it.name}.zip is missing", File(samples, "${it.name}.zip").isFile) }
  }

  @Test
  fun `every archive holds exactly its folder, so none is out of date`() {
    folders().forEach { folder ->
      val inFolder =
        folder
          .walkTopDown()
          .filter(File::isFile)
          .associate { "${folder.name}/${it.relativeTo(folder).invariantPath()}" to it.readBytes().toList() }
      val inZip =
        ZipFile(File(samples, "${folder.name}.zip")).use { zip ->
          zip.entries().asSequence().filterNot { it.isDirectory }.associate {
            it.name to zip.getInputStream(it).readBytes().toList()
          }
        }
      assertEquals(
        "${folder.name}.zip differs from its folder — run sample-sets/build-archives.py",
        inFolder.keys.sorted(),
        inZip.keys.sorted(),
      )
      inFolder.forEach { (name, bytes) -> assertTrue("$name differs in the zip", bytes == inZip[name]) }
    }
  }

  @Test
  fun `every archive installs from the file picker, with nothing to complain about`() {
    SETS.forEach { id ->
      val result = installer.installFrom(File(samples, "$id.zip"))
      assertTrue("$id.zip did not install: $result", result is PackageInstaller.Result.Installed)
      val installed = result as PackageInstaller.Result.Installed
      assertEquals(id, installed.set.id)
      assertEquals("$id installed with warnings", emptyList<Any>(), installed.warnings)
      assertEquals(STANDARD_DICE, installed.set.dice.map { it.id })
    }
  }

  @Test
  fun `each set is made of what its name says`() {
    val sets = SETS.associateWith(::install)
    val marble = sets.getValue("marble")
    assertTrue("marble is drawn, every die", marble.dice.all { it.texturePath != null })
    assertTrue("marble is stone, not plastic", marble.dice.all { it.material.density > STONE_DENSITY })

    val steel = sets.getValue("steel")
    assertTrue("steel is metal", steel.dice.all { it.material.metallic == 1.0 && it.material.density > STONE_DENSITY })

    val resin = sets.getValue("green-resin")
    assertTrue(
      "green resin is somewhat, not wholly, see-through",
      resin.dice.all { it.material.translucency in SOMEWHAT && it.material.metallic == 0.0 },
    )

    val glass = sets.getValue("red-glass")
    assertTrue("red glass is clear", glass.dice.all { it.material.translucency == 1.0 })
    assertTrue("red glass is red", glass.dice.all { isRed(it.material.colorArgb) })
  }

  private fun install(id: String): DiceSet =
    (installer.installFrom(File(samples, "$id.zip")) as PackageInstaller.Result.Installed).set

  private fun folders(): List<File> = samples.listFiles { file -> file.isDirectory }.orEmpty().sortedBy(File::getName)

  private fun isRed(argb: Int): Boolean {
    val red = argb shr RED_SHIFT and BYTE
    val green = argb shr GREEN_SHIFT and BYTE
    val blue = argb and BYTE
    return red > green * 2 && red > blue * 2
  }

  private companion object {
    val SETS = listOf("green-resin", "marble", "red-glass", "steel")
    val STANDARD_DICE = listOf("d2", "d4", "d6", "d8", "d10", "d10-tens", "d12", "d18", "d20", "df")
    val SOMEWHAT = 0.25..0.6
    const val STONE_DENSITY = 2.0
    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8
    const val BYTE = 0xFF

    fun repositoryRoot(): File =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "sample-sets").isDirectory }
        ?: error("no repository root above ${File("").absolutePath}")
  }
}
