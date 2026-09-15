package de.drehtuer.dinfinity.dicesets.install

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Which folder inside an unpacked archive counts as the package
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * Tested against folders rather than through an extraction, because that is
 * what it is: the rule lifted out of `SafeExtractor` so that something which
 * is not a dice set can be unpacked by the same hardened loop
 * (`docs/architecture.md`, "Importing").
 */
class PackageRootTest {
  private val unpacked: File = Files.createTempDirectory("dinfinity-root").toFile()

  @After
  fun clean() {
    unpacked.deleteRecursively()
  }

  @Test
  fun `the folder holding the set file is the package`() {
    val folder = File(unpacked, "brass").also { it.mkdirs() }
    File(folder, "diceset.toml").writeText("format = 1")

    val found = PackageRoot.DiceSet.of(unpacked, subfolder = null)

    assertEquals(folder, (found as PackageRoot.Found.Folder).folder)
  }

  @Test
  fun `a forge's wrapper folder is seen through`() {
    // Every forge wraps a repository one folder deep in `repo-<sha>/`, so the
    // root of the archive is never the root of the package.
    val folder = File(unpacked, "brass-and-bone-abc123").also { it.mkdirs() }
    File(folder, "diceset.toml").writeText("format = 1")

    val found = PackageRoot.DiceSet.of(unpacked, subfolder = null)

    assertEquals("brass-and-bone-abc123", (found as PackageRoot.Found.Folder).folder.name)
  }

  @Test
  fun `a subfolder the URL named narrows it to that one`() {
    val skulls = File(unpacked, "repo-abc/sets/skulls").also { it.mkdirs() }
    val bones = File(unpacked, "repo-abc/sets/bones").also { it.mkdirs() }
    File(skulls, "diceset.toml").writeText("format = 1")
    File(bones, "diceset.toml").writeText("format = 1")

    val found = PackageRoot.DiceSet.of(unpacked, subfolder = "sets/skulls")

    assertEquals("skulls", (found as PackageRoot.Found.Folder).folder.name)
  }

  @Test
  fun `a leading slash on the subfolder is somebody being careless, not wrong`() {
    val skulls = File(unpacked, "repo-abc/sets/skulls").also { it.mkdirs() }
    File(skulls, "diceset.toml").writeText("format = 1")

    val found = PackageRoot.DiceSet.of(unpacked, subfolder = "/sets/skulls/")

    assertEquals("skulls", (found as PackageRoot.Found.Folder).folder.name)
  }

  @Test
  fun `an archive with no set file in it says so, naming the file it wanted`() {
    File(unpacked, "README.md").writeText("nothing to see")

    val found = PackageRoot.DiceSet.of(unpacked, subfolder = null)

    val missing = found as PackageRoot.Found.Missing
    assertEquals(RejectionReason.NotInTheArchive, missing.reason)
    assertTrue(missing.detail, missing.detail.contains("diceset.toml"))
  }

  @Test
  fun `a subfolder that is not in the archive is not found`() {
    val folder = File(unpacked, "repo-abc").also { it.mkdirs() }
    File(folder, "diceset.toml").writeText("format = 1")

    val found = PackageRoot.DiceSet.of(unpacked, subfolder = "sets/nothing")

    assertTrue("expected a refusal, got $found", found is PackageRoot.Found.Missing)
  }
}
