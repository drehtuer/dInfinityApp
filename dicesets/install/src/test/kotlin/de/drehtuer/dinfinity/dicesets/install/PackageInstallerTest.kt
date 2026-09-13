package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The install as a whole: fetch, extract, validate, and only then move
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The order is what is being tested. Nothing goes into `dicesets/` until the
 * validator has passed it, a failure anywhere leaves the app exactly as it
 * was, and replacing a package that is already there cannot end with neither
 * of them installed.
 */
class PackageInstallerTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-install").toFile()
  private val root = File(temporary, "dicesets")
  private val installer = PackageInstaller(root)

  @After
  fun clean() {
    // A test that made a folder read-only to force a rename to fail has to
    // hand it back, or the whole temporary tree outlives the run.
    temporary.walkBottomUp().forEach { it.setWritable(true) }
    temporary.deleteRecursively()
  }

  @Test
  fun `a good package installs, under its own id`() {
    val result = installed(installer.installFrom(Archives.wellFormed(temporary)))
    assertEquals("fixture-set", result.set.id)
    assertEquals("fixture-set", result.folder.name)
    assertTrue(File(result.folder, "diceset.toml").isFile)
    assertFalse(result.replaced)
  }

  @Test
  fun `an install records where it came from, beside the package`() {
    val result = installed(installer.installFrom(Archives.wellFormed(temporary), from = "brass.tar.gz"))
    val meta = File(result.folder, ".meta.json")
    assertTrue("nothing recorded the source", meta.isFile)
    assertTrue(meta.readText(), meta.readText().contains("\"source\": \"brass.tar.gz\""))
    assertTrue(meta.readText(), meta.readText().contains("\"version\": \"1.0.0\""))
  }

  @Test
  fun `a package that fails validation leaves nothing behind`() {
    val broken =
      Archives.tarGz(
        temporary,
        mapOf("pkg/diceset.toml" to "format = 1\n\n[set]\nid = \"x\"\n".encodeToByteArray()),
      )
    val result = failed(installer.installFrom(broken))
    assertTrue(result.report.any { it.code == ValidationCode.BadSlug || it.code == ValidationCode.MissingField })
    assertNothingInstalled()
  }

  @Test
  fun `a hostile archive leaves nothing behind either`() {
    val hostile =
      Archives.tarGz(
        temporary,
        mapOf(
          "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "../../etc/passwd" to "root".encodeToByteArray(),
        ),
      )
    failed(installer.installFrom(hostile))
    assertNothingInstalled()
  }

  @Test
  fun `installing over a package replaces it, and says so`() {
    installer.installFrom(Archives.wellFormed(temporary))
    val again = installed(installer.installFrom(Archives.wellFormed(temporary)))
    assertTrue(again.replaced)
    assertEquals(1, root.listFiles()?.size)
  }

  @Test
  fun `a failed replacement leaves the package that was already there`() {
    val first = installed(installer.installFrom(Archives.wellFormed(temporary)))
    val marker = File(first.folder, "README.md").also { it.writeText("the original") }
    val broken = Archives.tarGz(temporary, mapOf("pkg/diceset.toml" to "not toml at all {".encodeToByteArray()))
    failed(installer.installFrom(broken))
    assertTrue("the original package was lost", marker.isFile)
    assertEquals("the original", marker.readText())
  }

  @Test
  fun `an install interrupted last time does not block the next one`() {
    // A crash between moving the old package aside and putting the new one in
    // leaves a `.replacing` folder behind. Left to sit there it would fail
    // every future install of that set, with a message about a folder the
    // user cannot see.
    val first = installed(installer.installFrom(Archives.wellFormed(temporary)))
    File(root, "${first.set.id}.replacing").also { it.mkdirs() }.resolve("junk").writeText("interrupted")

    val again = installed(installer.installFrom(Archives.wellFormed(temporary)))

    assertTrue("the interrupted install was not cleared", again.replaced)
    assertFalse(File(root, "${first.set.id}.replacing").exists())
  }

  @Test
  fun `a package that cannot be moved aside stops before anything is written`() {
    // The promise the installer makes is that a failure halfway leaves the
    // *old* package installed rather than neither. That promise is only kept
    // if moving the old one aside is checked: taken for granted, the new
    // package would be written over a folder that never moved.
    val first = installed(installer.installFrom(Archives.wellFormed(temporary)))
    val marker = File(first.folder, "README.md").also { it.writeText("the original") }
    assertTrue("this test needs a directory it can lock", root.setWritable(false))

    val result = failed(installer.installFrom(Archives.wellFormed(temporary)))

    assertTrue(result.reason, result.reason.contains("moved aside"))
    assertTrue("the original package was lost", marker.isFile)
    assertEquals("the original", marker.readText())
  }

  @Test
  fun `a package that cannot be written at all is refused, with nothing half-installed`() {
    root.mkdirs()
    assertTrue("this test needs a directory it can lock", root.setWritable(false))

    val result = failed(installer.installFrom(Archives.wellFormed(temporary)))

    assertTrue(result.reason, result.reason.contains("could not be moved into place"))
  }

  @Test
  fun `a folder left behind that will not delete stops the install rather than the install stopping it`() {
    val first = installed(installer.installFrom(Archives.wellFormed(temporary)))
    val leftover = File(root, "${first.set.id}.replacing").also { it.mkdirs() }
    leftover.resolve("junk").writeText("interrupted")
    assertTrue("this test needs a directory it can lock", leftover.setWritable(false))

    val result = failed(installer.installFrom(Archives.wellFormed(temporary)))

    assertTrue(result.reason, result.reason.contains("left a folder behind"))
  }

  @Test
  fun `a restored package is reported as untouched, and an unrestorable one is not`() {
    // What the user is told after a failed replacement. Being told the set is
    // untouched when putting it back is what failed would send them looking
    // for a folder that is not there under a name they have never seen.
    val destination = File(temporary, "set").also { it.mkdirs() }
    val aside = File(temporary, "set.replacing").also { it.mkdirs() }

    assertTrue(installer.restored(null, destination).endsWith("moved into place"))
    assertTrue(installer.restored(aside, destination).contains("untouched"))

    val stuck = File(temporary, "locked").also { it.mkdirs() }
    val trapped = File(stuck, "set.replacing").also { it.mkdirs() }
    assertTrue("this test needs a directory it can lock", stuck.setWritable(false))
    val message = installer.restored(trapped, File(stuck, "set"))

    assertTrue(message, message.contains("could not be put back"))
    assertTrue(message, message.contains("set.replacing"))
  }

  @Test
  fun `a link the app does not install from is refused before anything happens`() {
    val result = failed(installer.installFrom("http://example.org/brass.zip"))
    assertTrue(result.reason, result.reason.contains("not a link"))
    assertNothingInstalled()
  }

  @Test
  fun `a warning does not stop an install`() {
    // The fixture set has only a d6, so the validator warns about the rest.
    val result = installed(installer.installFrom(Archives.wellFormed(temporary)))
    assertTrue("the missing standard dice should have been mentioned", result.warnings.isNotEmpty())
  }

  @Test
  fun `the workspace is cleaned up whether the install worked or not`() {
    installer.installFrom(Archives.wellFormed(temporary))
    failed(installer.installFrom("https://example.org/not-an-archive"))
    val leftovers = temporary.listFiles()?.filter { it.isDirectory && it.name.startsWith("install-") }.orEmpty()
    assertTrue("left ${leftovers.map(File::getName)} behind", leftovers.isEmpty())
  }

  private fun installed(result: PackageInstaller.Result): PackageInstaller.Result.Installed {
    assertTrue("expected an install, got $result", result is PackageInstaller.Result.Installed)
    return result as PackageInstaller.Result.Installed
  }

  private fun failed(result: PackageInstaller.Result): PackageInstaller.Result.Failed {
    assertTrue("expected a refusal, got $result", result is PackageInstaller.Result.Failed)
    return result as PackageInstaller.Result.Failed
  }

  private fun assertNothingInstalled() {
    val installed = root.listFiles()?.map(File::getName).orEmpty()
    assertTrue("$installed was installed and should not have been", installed.isEmpty())
  }
}
