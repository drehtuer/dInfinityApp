package de.drehtuer.dinfinity.dicesets.install

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * One test per way an archive can be hostile
 * (`docs/dice-sets.md`, "Installing from a URL or file"; `SECURITY.md`).
 *
 * The pattern in every refusal case is the same and is the point: the archive
 * is refused **and nothing is left behind**. A partially extracted attack is
 * still an attack.
 */
class SafeExtractorTest {
  private val workspace: File = Files.createTempDirectory("dinfinity-extract").toFile()
  private val extractor = SafeExtractor()

  @After
  fun clean() {
    workspace.deleteRecursively()
  }

  @Test
  fun `an ordinary package extracts`() {
    val result = extracted(extractor.extract(Archives.wellFormed(workspace), workspace))
    assertEquals(1, result.files)
    assertTrue(File(result.root, "diceset.toml").isFile)
  }

  @Test
  fun `a zip extracts as well as a tarball, because a file picker offers both`() {
    val zip = Archives.zip(workspace, mapOf("diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray()))
    extracted(extractor.extract(zip, workspace))
  }

  @Test
  fun `the kind of archive is read from its bytes, not from its name`() {
    val misnamed = Archives.tarGz(workspace, mapOf("diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray()))
    val renamed = File(workspace, "actually-a-tarball.zip")
    misnamed.renameTo(renamed)
    extracted(extractor.extract(renamed, workspace))
  }

  @Test
  fun `a path that climbs out of the package is refused, and nothing is written`() {
    val hostile =
      Archives.tarGz(
        workspace,
        mapOf(
          "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "../../etc/passwd" to "root:x:0:0".encodeToByteArray(),
        ),
      )
    val result = refused(extractor.extract(hostile, workspace))
    assertEquals(RejectionReason.PathEscapesPackage, result.reason)
    assertNothingLeftBehind()
  }

  @Test
  fun `an absolute path is refused`() {
    // Written as a zip, because a tar writer quietly strips the leading slash
    // and the archive would no longer be the hostile one this is about.
    val hostile =
      Archives.zip(
        workspace,
        mapOf(
          "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "/etc/passwd" to "root".encodeToByteArray(),
        ),
      )
    val result = refused(extractor.extract(hostile, workspace))
    assertEquals(RejectionReason.PathEscapesPackage, result.reason)
    assertNothingLeftBehind()
  }

  @Test
  fun `a windows path is refused too, since an archive can come from anywhere`() {
    val hostile =
      Archives.zip(
        workspace,
        mapOf(
          "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "..\\..\\windows\\system32\\evil.toml" to "x".encodeToByteArray(),
        ),
      )
    assertEquals(RejectionReason.PathEscapesPackage, refused(extractor.extract(hostile, workspace)).reason)
  }

  @Test
  fun `a zip symlink extracts as an ordinary file, because nothing here makes links`() {
    // A zip's unix modes live in its central directory, at the end of the
    // file, which a streaming reader never reads — so an entry cannot be
    // recognised as a link at all. It does not need to be: the extractor has
    // no code path that creates one, so a "symlink" entry becomes a small file
    // whose contents are a path, which is exactly as dangerous as a text file.
    val hostile =
      Archives.zip(
        workspace,
        entries =
          mapOf(
            "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
            "pkg/textures/d20.png" to "/etc/shadow".encodeToByteArray(),
          ),
        symlinks = setOf("pkg/textures/d20.png"),
      )
    val result = extracted(extractor.extract(hostile, workspace))
    val written = File(result.root, "textures/d20.png")
    assertTrue("the entry must be a plain file", written.isFile)
    assertTrue("and must not be a link", !Files.isSymbolicLink(written.toPath()))
    assertEquals("/etc/shadow", written.readText())
  }

  @Test
  fun `a symbolic link is refused, however innocent its own name looks`() {
    val hostile =
      Archives.tarGz(
        workspace,
        entries = mapOf("pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray()),
        symlinks = mapOf("pkg/textures/d20.png" to "/etc/shadow"),
      )
    val result = refused(extractor.extract(hostile, workspace))
    assertEquals(RejectionReason.NotAPlainFile, result.reason)
    assertNothingLeftBehind()
  }

  @Test
  fun `an archive with more files than a package may hold is refused`() {
    val many = (1..InstallLimits.MAX_ENTRIES + 1).associate { "pkg/f$it.txt" to "x".encodeToByteArray() }
    val result = refused(extractor.extract(Archives.tarGz(workspace, many), workspace))
    assertEquals(RejectionReason.TooManyEntries, result.reason)
    assertNothingLeftBehind()
  }

  @Test
  fun `an archive of exactly the limit is not refused for its count`() {
    val entries =
      (2..InstallLimits.MAX_ENTRIES).associate { "pkg/f$it.txt" to "x".encodeToByteArray() } +
        mapOf("pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray())
    val result = extracted(extractor.extract(Archives.tarGz(workspace, entries), workspace))
    assertEquals(InstallLimits.MAX_ENTRIES, result.files)
  }

  @Test
  fun `a zip bomb is refused at the megabyte it becomes obvious, not at the terabyte`() {
    // Zeroes compress to almost nothing, which is the whole trick.
    val bomb =
      Archives.tarGz(
        workspace,
        mapOf(
          "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "pkg/big.txt" to Archives.compressible(megabytes = 70),
        ),
      )
    assertTrue("the archive itself has to be small, or this is not a bomb", bomb.length() < ONE_MIB)
    val result = refused(extractor.extract(bomb, workspace))
    assertEquals(RejectionReason.TooLarge, result.reason)
    assertNothingLeftBehind()
  }

  @Test
  fun `a file that is not an archive at all is refused`() {
    val nonsense = File(workspace, "not-an-archive.zip").also { it.writeText("hello") }
    val result = refused(extractor.extract(nonsense, workspace))
    assertEquals(RejectionReason.Unreadable, result.reason)
  }

  @Test
  fun `an archive with no set file in it is refused`() {
    val archive = Archives.tarGz(workspace, mapOf("pkg/README.md" to "hi".encodeToByteArray()))
    val result = refused(extractor.extract(archive, workspace))
    assertEquals(RejectionReason.NotInTheArchive, result.reason)
    assertNothingLeftBehind()
  }

  @Test
  fun `what may be written is the limits' to say, not this class's`() {
    // The lifted piece, exercised as a piece: the same archive, unpacked under
    // an allowlist of one extension, writes one file and skips the other. It
    // is what lets a saved-roll collection come down the same path as a dice
    // set without either of them learning about the other
    // (`docs/dice-notation.md`, "Export and import").
    val archive =
      Archives.tarGz(
        workspace,
        mapOf(
          "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "pkg/README.md" to "hi".encodeToByteArray(),
        ),
      )
    val narrow = SafeExtractor(ArchiveLimits(allowedExtensions = setOf("toml")))

    val result = extracted(narrow.extract(archive, workspace))

    assertEquals(1, result.files)
    assertEquals(1, result.skipped)
    assertTrue("a file off the allowlist reached the disk", !File(result.root, "README.md").exists())
  }

  @Test
  fun `what counts as the package is the root's to say, not this class's`() {
    // A package marked by something other than a dice set file: the extractor
    // does not know what it is unpacking, and does not need to.
    val archive = Archives.tarGz(workspace, mapOf("repo-abc/rolls.txt" to "x".encodeToByteArray()))
    val byTxt =
      SafeExtractor(
        limits = ArchiveLimits(allowedExtensions = setOf("txt")),
        wanted = { destination, _ ->
          destination
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "rolls.txt" }
            ?.parentFile
            ?.let(PackageRoot.Found::Folder)
            ?: PackageRoot.Found.Missing(RejectionReason.NotInTheArchive, "no rolls.txt in the archive")
        },
      )

    assertEquals("repo-abc", extracted(byTxt.extract(archive, workspace)).root.name)
  }

  @Test
  fun `a root that finds nothing is a refusal that leaves nothing behind`() {
    val archive = Archives.tarGz(workspace, mapOf("pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray()))
    val never =
      SafeExtractor(wanted = { _, _ -> PackageRoot.Found.Missing(RejectionReason.NotInTheArchive, "nothing here") })

    val result = refused(never.extract(archive, workspace))

    assertEquals("nothing here", result.detail)
    assertNothingLeftBehind()
  }

  @Test
  fun `a file with an extension nobody asked for is skipped, not refused`() {
    val archive =
      Archives.tarGz(
        workspace,
        mapOf(
          "pkg/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "pkg/.gitignore" to "build/".encodeToByteArray(),
          "pkg/build.sh" to "rm -rf /".encodeToByteArray(),
        ),
      )
    val result = extracted(extractor.extract(archive, workspace))
    assertEquals(1, result.files)
    assertEquals(2, result.skipped)
    assertTrue("a script must never reach the disk", !File(result.root, "build.sh").exists())
  }

  @Test
  fun `the package's own folder is found inside a forge's wrapper`() {
    val result = extracted(extractor.extract(Archives.wellFormed(workspace, prefix = "repo-deadbeef/"), workspace))
    assertEquals("repo-deadbeef", result.root.name)
  }

  @Test
  fun `a package in a subfolder is found when the URL named one`() {
    val archive =
      Archives.tarGz(
        workspace,
        mapOf(
          "repo-abc/sets/skulls/diceset.toml" to Archives.MINIMAL_TOML.encodeToByteArray(),
          "repo-abc/README.md" to "hi".encodeToByteArray(),
        ),
      )
    val result = extracted(extractor.extract(archive, workspace, subfolder = "sets/skulls"))
    assertEquals("skulls", result.root.name)
  }

  @Test
  fun `a subfolder that is not in the archive is not found`() {
    val result = refused(extractor.extract(Archives.wellFormed(workspace), workspace, subfolder = "sets/nothing"))
    assertEquals(RejectionReason.NotInTheArchive, result.reason)
  }

  /** The result, insisting it was an extraction. */
  private fun extracted(result: ExtractionResult): ExtractionResult.Extracted {
    assertTrue("expected an extraction, got $result", result is ExtractionResult.Extracted)
    return result as ExtractionResult.Extracted
  }

  /** The result, insisting it was a refusal. */
  private fun refused(result: ExtractionResult): ExtractionResult.Refused {
    assertTrue("expected a refusal, got $result", result is ExtractionResult.Refused)
    return result as ExtractionResult.Refused
  }

  /** Every refusal has to leave the disk as it found it. */
  private fun assertNothingLeftBehind() {
    val folders = workspace.listFiles()?.filter { it.isDirectory }.orEmpty()
    assertTrue("a refusal left ${folders.map(File::getName)} behind", folders.isEmpty())
  }

  private companion object {
    const val ONE_MIB = 1024L * 1024
  }
}
