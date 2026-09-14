package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.Severity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Reading what is installed (`docs/dice-sets.md`, "Runtime isolation").
 *
 * The thing being tested is that this is a reading of the disk *now*, not a
 * memory of an install. Packages are therefore put in place by the installer
 * where that is what the test is about, and written by hand where the test is
 * about a folder the installer would never have produced — which is the whole
 * point, because those are the folders that arrive by backup restore, by a
 * text editor, or by somebody trying it on.
 */
class InstalledSetsTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-installed").toFile()
  private val root = File(temporary, "dicesets")
  private val installed = InstalledSets(root)

  @After
  fun clean() {
    temporary.walkBottomUp().forEach { it.setWritable(true) }
    temporary.deleteRecursively()
  }

  @Test
  fun `a folder that does not exist yet is an empty list, not a failure`() {
    // A fresh install has never installed anything, and that is not a problem
    // to report.
    assertEquals(emptyList<InstalledPackage>(), installed.scan())
  }

  @Test
  fun `an installed package is found, and is ready`() {
    PackageInstaller(root).installFrom(Archives.wellFormed(temporary))

    val found = installed.scan().single()

    assertTrue("a package the installer accepted did not read back as ready", found is InstalledPackage.Ready)
    assertEquals("fixture-set", found.id)
    assertEquals("fixture-set", (found as InstalledPackage.Ready).set.id)
    assertEquals(File(root, "fixture-set"), found.folder)
  }

  @Test
  fun `where it came from is read back with it`() {
    PackageInstaller(root).installFrom(Archives.wellFormed(temporary), from = "https://example.invalid/brass.zip")

    val found = installed.scan().single()

    assertEquals("https://example.invalid/brass.zip", found.meta.source)
    assertEquals("1.0.0", found.meta.version)
  }

  @Test
  fun `a package that no longer validates is broken, and keeps its folder`() {
    // The case this type exists for. A set that installed once is not valid
    // for ever: this is what a half-restored backup looks like.
    PackageInstaller(root).installFrom(Archives.wellFormed(temporary))
    val folder = File(root, "fixture-set")
    File(folder, DiceSetValidator.DICE_SET_FILE).writeText("format = 1\n\n[set]\nid = \"fixture-set\"\n")

    val found = installed.scan().single()

    assertTrue("a broken package was dropped rather than reported", found is InstalledPackage.Broken)
    assertTrue("nothing was wrong with it", (found as InstalledPackage.Broken).report.isNotEmpty())
    assertEquals("a broken package pointed somewhere else", folder, found.folder)
    assertTrue("the folder was taken away, so there is nothing to update", found.folder.isDirectory)
    assertEquals("where it came from was forgotten when it broke", "1.0.0", found.meta.version)
  }

  @Test
  fun `a folder whose set calls itself something else is refused`() {
    // Notation resolves by folder, so a folder named `brass` holding a set
    // that calls itself `copper` would hand out dice from a set the player
    // never named. The installer cannot produce this; a text editor can.
    write("brass", Archives.MINIMAL_TOML)

    val found = installed.scan().single()

    assertTrue(found is InstalledPackage.Broken)
    val report = (found as InstalledPackage.Broken).report
    assertTrue(
      "the report did not say which two names disagree: $report",
      report.single().text.contains("brass") && report.single().text.contains("fixture-set"),
    )
  }

  @Test
  fun `an empty folder is broken rather than invisible`() {
    // Silently skipping it would leave the player with a set they can see in
    // the file manager and not in the app.
    File(root, "hollow").mkdirs()

    val found = installed.scan().single()

    assertTrue(found is InstalledPackage.Broken)
    assertEquals("hollow", found.id)
  }

  @Test
  fun `a meta nobody can read costs the provenance and not the package`() {
    // The set still works. Refusing to list it because the app cannot
    // remember where it came from would lose a working package over a detail
    // printed in grey.
    PackageInstaller(root).installFrom(Archives.wellFormed(temporary))
    File(root, "fixture-set/${PackageMeta.FILE_NAME}").writeText("{ this is not json")

    val found = installed.scan().single()

    assertTrue(found is InstalledPackage.Ready)
    assertEquals(PackageMeta.Unknown, found.meta)
  }

  @Test
  fun `hidden folders are not packages`() {
    // `.replacing` leftovers and whatever else a filesystem leaves lying
    // around are not sets, and listing them would be alarming.
    File(root, ".fixture-set.replacing").mkdirs()
    PackageInstaller(root).installFrom(Archives.wellFormed(temporary))

    assertEquals(listOf("fixture-set"), installed.scan().map { it.id })
  }

  @Test
  fun `loose files beside the packages are ignored`() {
    root.mkdirs()
    File(root, "notes.txt").writeText("not a package")
    PackageInstaller(root).installFrom(Archives.wellFormed(temporary))

    assertEquals(listOf("fixture-set"), installed.scan().map { it.id })
  }

  @Test
  fun `the order is the same every time, whatever the filesystem says`() {
    // A list that reshuffles between two readings of an unchanged disk is a
    // list nobody can put on a screen.
    listOf("copper", "brass", "amber").forEach { id -> write(id, Archives.MINIMAL_TOML.replace("fixture-set", id)) }

    assertEquals(listOf("amber", "brass", "copper"), installed.scan().map { it.id })
    assertEquals(installed.scan().map { it.id }, installed.scan().map { it.id })
  }

  @Test
  fun `a warning does not stop a package being ready`() {
    // A clamped physics value is a warning, and a set with one still rolls.
    write("fixture-set", Archives.MINIMAL_TOML + "\n[physics]\nrestitution = 9.0\n")

    val found = installed.scan().single()

    assertTrue("a warning was treated as a rejection", found is InstalledPackage.Ready)
    assertTrue(
      "the warning was not carried with it",
      (found as InstalledPackage.Ready).warnings.all { it.severity == Severity.Warning },
    )
  }

  @Test
  fun `a folder that cannot be read at all is broken, not a crash`() {
    // No arrangement of files produces this — it is an IO error part way
    // through reading a folder — so the only way to ask for it is to make the
    // validator fail the way the filesystem would. What matters is that the
    // scan answers at all: one unreadable folder must not cost the player the
    // list of everything else they have installed.
    write("fixture-set", Archives.MINIMAL_TOML)
    val failing = InstalledSets(root) { error("the disk gave up") }

    val found = failing.scan().single()

    assertTrue(found is InstalledPackage.Broken)
    assertTrue(
      "the report did not name the folder: $found",
      (found as InstalledPackage.Broken)
        .report
        .single()
        .text
        .contains("fixture-set"),
    )
  }

  private fun write(
    id: String,
    toml: String,
  ) {
    val folder = File(root, id).apply { mkdirs() }
    File(folder, DiceSetValidator.DICE_SET_FILE).writeText(toml)
  }
}
