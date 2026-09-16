package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Writing the personal package, and the state that must not exist
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The rule the type is named for is that **a half-written package is never on
 * disk**: files go into a staging folder and are swapped in, and what was
 * installed goes back if the swap does not happen. These tests are about the
 * swap, because every way it can go wrong ends with somebody's dice either
 * being there or not.
 */
class PackageFolderTest {
  @get:Rule
  val temp: TemporaryFolder = TemporaryFolder()

  @Test
  fun `a package is written where a reading will find it`() {
    val folder = PackageFolder(temp.root, "mine")

    assertTrue(folder.install(mapOf("diceset.toml" to "id = 'mine'".toByteArray())))

    assertEquals("id = 'mine'", File(temp.root, "mine/diceset.toml").readText())
  }

  @Test
  fun `a second install replaces the first, and leaves no working folders behind`() {
    val folder = PackageFolder(temp.root, "mine")
    folder.install(mapOf("a.toml" to "first".toByteArray()))

    assertTrue(folder.install(mapOf("b.toml" to "second".toByteArray())))

    assertFalse("the replaced package is still there", File(temp.root, "mine/a.toml").exists())
    assertEquals("second", File(temp.root, "mine/b.toml").readText())
    assertEquals(
      "a working folder was left where a scan would read it",
      listOf("mine"),
      temp.root
        .listFiles()
        .orEmpty()
        .map(File::getName),
    )
  }

  @Test
  fun `the folders it works in are hidden, so a reading mid-swap sees no half package`() {
    // Everything in `dicesets/` is scanned as a package. A staging folder that
    // did not begin with a dot would be read as one, and it is by definition
    // half written.
    val folder = PackageFolder(temp.root, "mine")
    folder.install(mapOf("diceset.toml" to "x".toByteArray()))

    assertTrue(
      temp.root
        .listFiles()
        .orEmpty()
        .filterNot { it.name == "mine" }
        .all { it.name.startsWith(".") },
    )
  }

  @Test
  fun `a package that cannot be written leaves the one already installed alone`() {
    val folder = PackageFolder(temp.root, "mine")
    folder.install(mapOf("diceset.toml" to "the original".toByteArray()))

    // A path with a file where a directory has to be: the write throws part
    // way through, which is the case staging exists for.
    assertFalse(folder.install(mapOf("a" to "x".toByteArray(), "a/b.toml" to "y".toByteArray())))

    assertEquals("the original", File(temp.root, "mine/diceset.toml").readText())
  }

  @Test
  fun `a rollback that had to copy still leaves nothing for the next install to destroy`() {
    // The bug this is about: `install` clears its holding folder before it
    // writes anything, so a package a failed rollback left only in there is a
    // package the *next* install deletes — and it is the only copy of what the
    // player had. The rollback therefore copies when it cannot move, and what
    // this asserts is the state that has to hold afterwards: the package is
    // back where it belongs and the holding folder is empty.
    val folder = PackageFolder(temp.root, "mine")
    folder.install(mapOf("diceset.toml" to "the original".toByteArray()))

    assertFalse(folder.install(mapOf("a" to "x".toByteArray(), "a/b.toml" to "y".toByteArray())))

    assertEquals("the original", File(temp.root, "mine/diceset.toml").readText())
    assertFalse(
      "the player's package was left only in the holding folder",
      File(temp.root, ".mine.previous").exists(),
    )

    // And the proof that it matters: another install, which clears that folder
    // first, still finds the package where it should be.
    assertTrue(folder.install(mapOf("diceset.toml" to "the new one".toByteArray())))
    assertEquals("the new one", File(temp.root, "mine/diceset.toml").readText())
  }

  @Test
  fun `removing takes the folder off the disk`() {
    val folder = PackageFolder(temp.root, "mine")
    folder.install(mapOf("diceset.toml" to "x".toByteArray()))

    folder.remove()

    assertFalse(folder.folder.exists())
  }

  @Test
  fun `removing a package that was never installed is not an error`() {
    PackageFolder(temp.root, "mine").remove()
  }
}
