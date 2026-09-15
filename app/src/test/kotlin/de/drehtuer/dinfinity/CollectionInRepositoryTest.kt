package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.dicesets.install.PackageRoot
import de.drehtuer.dinfinity.dicesets.install.RejectionReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Which file in a repository is the collection
 * (`docs/dice-notation.md`, "Export and import").
 *
 * The rule is one sentence — one collection, at the root, named the way the
 * app names one — and every test here is a way of not following it. They run
 * against folders rather than through a download, because that is the shape of
 * the decision: by the time this is asked, the archive is already unpacked
 * into somewhere it cannot leave.
 */
class CollectionInRepositoryTest {
  private val unpacked: File = Files.createTempDirectory("dinfinity-repository").toFile()

  @After
  fun clean() {
    unpacked.deleteRecursively()
  }

  @Test
  fun `the collection at the root of the repository is the one`() {
    val root = folder("monsters-deadbeef")
    collection(root, "monster-manual.dinfinity.json")

    val found = CollectionInRepository.of(unpacked, subfolder = null)

    assertEquals(root, (found as PackageRoot.Found.Folder).folder)
  }

  @Test
  fun `an archive with no wrapper folder is its own root`() {
    // A forge always wraps; somebody zipping the contents of a directory does
    // not, and that is not a mistake worth refusing.
    collection(unpacked, "monster-manual.dinfinity.json")

    val found = CollectionInRepository.of(unpacked, subfolder = null)

    assertEquals(unpacked, (found as PackageRoot.Found.Folder).folder)
  }

  @Test
  fun `a repository with no collection in it says what one is called`() {
    val root = folder("monsters-deadbeef")
    File(root, "stats.json").writeText("{}")

    val found = CollectionInRepository.of(unpacked, subfolder = null)

    val missing = found as PackageRoot.Found.Missing
    assertEquals(RejectionReason.NotInTheArchive, missing.reason)
    assertTrue(missing.detail, missing.detail.contains(".dinfinity.json"))
  }

  @Test
  fun `a repository with two collections at its root refuses, and names both`() {
    // A link names a repository, not a file. Picking one of two would be
    // picking for somebody.
    val root = folder("monsters-deadbeef")
    collection(root, "goblins.dinfinity.json")
    collection(root, "dragons.dinfinity.json")

    val missing = CollectionInRepository.of(unpacked, subfolder = null) as PackageRoot.Found.Missing

    assertTrue(missing.detail, missing.detail.contains("goblins.dinfinity.json"))
    assertTrue(missing.detail, missing.detail.contains("dragons.dinfinity.json"))
  }

  @Test
  fun `a collection that is not at the root is found, and the refusal says where it is`() {
    // The likeliest mistake anybody will actually make, so it is the one
    // refusal that has to be more than "no".
    val root = folder("monsters-deadbeef")
    collection(File(root, "collections").also { it.mkdirs() }, "goblins.dinfinity.json")

    val missing = CollectionInRepository.of(unpacked, subfolder = null) as PackageRoot.Found.Missing

    assertTrue(missing.detail, missing.detail.contains("collections/goblins.dinfinity.json"))
    assertTrue(missing.detail, missing.detail.contains("root"))
  }

  @Test
  fun `an empty archive is a repository with nothing in it`() {
    val missing = CollectionInRepository.of(unpacked, subfolder = null) as PackageRoot.Found.Missing

    assertEquals(RejectionReason.NotInTheArchive, missing.reason)
  }

  @Test
  fun `a single wrapper folder is seen through, and two folders are not`() {
    // "The root" has to mean something definite. One folder and nothing beside
    // it is a wrapper; anything else is the repository itself.
    assertEquals(unpacked, CollectionInRepository.rootOf(unpacked))
    val wrapper = folder("monsters-deadbeef")
    assertEquals(wrapper, CollectionInRepository.rootOf(unpacked))
    folder("monsters-cafebabe")
    assertEquals(unpacked, CollectionInRepository.rootOf(unpacked))
  }

  @Test
  fun `a lone file at the root is not a wrapper`() {
    collection(unpacked, "monster-manual.dinfinity.json")

    assertEquals(unpacked, CollectionInRepository.rootOf(unpacked))
  }

  @Test
  fun `the collections in a folder are the files named like one, in a stable order`() {
    collection(unpacked, "goblins.dinfinity.json")
    collection(unpacked, "dragons.dinfinity.json")
    File(unpacked, "stats.json").writeText("{}")
    folder("dragons.dinfinity.json.d")

    assertEquals(
      listOf("dragons.dinfinity.json", "goblins.dinfinity.json"),
      CollectionInRepository.collectionsIn(unpacked).map(File::getName),
    )
  }

  @Test
  fun `a repository is unpacked under the same megabyte a collection is`() {
    // Not the sixty-four an archive of textures gets: the app is here for one
    // page of JSON, and a repository that expands to more than the file it
    // carries is asking the phone to unpack a library to read a page.
    assertEquals(CollectionLimits.MAX_BYTES.toLong(), CollectionInRepository.Limits.maxExtractedBytes)
    assertEquals(setOf("json"), CollectionInRepository.Limits.allowedExtensions)
  }

  private fun folder(name: String): File = File(unpacked, name).also { it.mkdirs() }

  private fun collection(
    where: File,
    name: String,
  ): File = File(where, name).also { it.writeText(Repositories.THORIN) }
}
