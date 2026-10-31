package de.drehtuer.dinfinity.dicesets.install

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The sample sets installed on first launch ([PreinstalledSets]), from the
 * archives committed in `sample-sets/` — the ones the app carries.
 */
class PreinstalledSetsTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-preinstall").toFile()
  private val root = File(temporary, "dicesets")
  private val record = File(temporary, "preinstalled-sets.txt")
  private val seeder = PreinstalledSets(PackageInstaller(root), record, File(temporary, "work"))
  private val samples = File(repositoryRoot(), "sample-sets")
  private val archives = NAMES.map { name -> PreinstalledSets.Archive(name) { File(samples, name).readBytes() } }

  @After
  fun clean() {
    temporary.deleteRecursively()
  }

  private fun onDisk(): Map<String, String> =
    InstalledSets(root).scan().filterIsInstance<InstalledPackage.Ready>().associate { it.set.id to it.set.version }

  @Test
  fun `a fresh install gets every sample set, through the installer`() {
    val outcomes = seeder.seed("1", archives) { onDisk() }

    assertEquals(NAMES, outcomes.map { it.name })
    assertTrue(outcomes.all { it is PreinstalledSets.Outcome.Installed })
    assertEquals(setOf("green-resin", "halloween", "marble", "red-glass", "steel"), onDisk().keys)
    // Recorded as installed from the app, not from a file somebody picked.
    val meta = File(root, "steel/${PackageMeta.FILE_NAME}")
    assertTrue("no install note beside steel", meta.isFile)
    assertTrue(meta.readText().contains("${PreinstalledSets.SOURCE_PREFIX}steel.zip"))
  }

  @Test
  fun `a second launch of the same app reads nothing`() {
    seeder.seed("1", archives) { onDisk() }
    var asked = false
    val unread = NAMES.map { PreinstalledSets.Archive(it) { error("read") } }
    val again =
      seeder.seed("1", unread) {
        asked = true
        onDisk()
      }

    assertTrue(again.isEmpty())
    assertFalse("the installed sets were scanned for nothing", asked)
  }

  @Test
  fun `a set the player removed stays removed after an update`() {
    seeder.seed("1", archives) { onDisk() }
    File(root, "steel").deleteRecursively()

    val outcomes = seeder.seed("2", archives) { onDisk() }

    assertTrue(outcomes.single { it.name == "steel.zip" } is PreinstalledSets.Outcome.Kept)
    assertFalse("steel came back", "steel" in onDisk())
  }

  @Test
  fun `an older copy it installed is replaced by the newer one the app carries`() {
    seeder.seed("1", archives) { onDisk() }
    val older = onDisk().mapValues { (id, version) -> if (id == "steel") "0.9.0" else version }

    val outcomes = seeder.seed("2", archives) { older }

    assertTrue(outcomes.single { it.name == "steel.zip" } is PreinstalledSets.Outcome.Installed)
    assertTrue(outcomes.filter { it.name != "steel.zip" }.all { it is PreinstalledSets.Outcome.Kept })
  }

  @Test
  fun `a copy that no longer loads is put right by the newer one the app carries`() {
    // The sample sets installed before the long d4 named the tetrahedron, which
    // this version refuses: they are broken on disk, not removed.
    seeder.seed("1", archives) { onDisk() }
    val steel = File(root, "steel/diceset.toml")
    steel.writeText(steel.readText().replace("elongated-square-bipyramid", "tetrahedron"))
    val meta = File(root, "steel/${PackageMeta.FILE_NAME}")
    meta.writeText(meta.readText().replace(Regex("\"version\"\\s*:\\s*\"[^\"]*\""), "\"version\":\"1.0.0\""))
    val scanned = InstalledSets(root).scan()
    assertTrue(scanned.single { it.id == "steel" } is InstalledPackage.Broken)
    assertEquals("1.0.0", PreinstalledSets.versionsOf(scanned)["steel"])

    val outcomes = seeder.seed("2", archives) { PreinstalledSets.versionsOf(InstalledSets(root).scan()) }

    assertTrue(outcomes.single { it.name == "steel.zip" } is PreinstalledSets.Outcome.Installed)
    assertTrue(InstalledSets(root).scan().single { it.id == "steel" } is InstalledPackage.Ready)
  }

  @Test
  fun `a newer copy the player installed is never downgraded, and counts as seen`() {
    val players = mapOf("steel" to "9.0.0")

    val outcomes = seeder.seed("1", archives) { players }

    assertTrue(outcomes.single { it.name == "steel.zip" } is PreinstalledSets.Outcome.Kept)
    assertFalse("steel was installed over the player's", File(root, "steel").exists())
    assertTrue(record.readLines().contains("steel=9.0.0"))
  }

  @Test
  fun `an archive the app does not carry, or one that is not a set, installs nothing`() {
    val outcomes =
      seeder.seed(
        "1",
        listOf(
          PreinstalledSets.Archive("missing.zip") { null },
          PreinstalledSets.Archive("broken.zip") { "not a zip".encodeToByteArray() },
        ),
      ) { onDisk() }

    assertTrue(outcomes.all { it is PreinstalledSets.Outcome.Failed })
    assertTrue(onDisk().isEmpty())
  }

  @Test
  fun `a damaged line in the record costs that line and no more`() {
    record.writeText("garbage\n=1.0.0\nsteel=1.0.1\n")

    seeder.seed("1", archives) { onDisk() }

    assertFalse("steel was recorded, so it counts as removed", "steel" in onDisk())
    assertTrue("green-resin" in onDisk())
    assertTrue(record.readLines().contains("${PreinstalledSets.STAMP_KEY}=1"))
  }

  @Test
  fun `a version is newer by major, minor and patch, and an unreadable one never is`() {
    assertTrue(PreinstalledSets.isNewer("1.0.1", "1.0.0"))
    assertTrue(PreinstalledSets.isNewer("1.10.0", "1.9.9"))
    assertTrue(PreinstalledSets.isNewer("2.0.0-beta", "1.9.0"))
    assertFalse(PreinstalledSets.isNewer("1.0.0", "1.0.0"))
    assertFalse(PreinstalledSets.isNewer("1.0.0", "1.0.1"))
    assertFalse(PreinstalledSets.isNewer("one", "1.0.0"))
    assertFalse(PreinstalledSets.isNewer("1.0.0", "1.0"))
    assertFalse(PreinstalledSets.isNewer("1.x.0", "1.0.0"))
  }

  private companion object {
    val NAMES = listOf("green-resin.zip", "halloween.zip", "marble.zip", "red-glass.zip", "steel.zip")

    fun repositoryRoot(): File =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "sample-sets").isDirectory }
        ?: error("no repository root above ${File("").absolutePath}")
  }
}
