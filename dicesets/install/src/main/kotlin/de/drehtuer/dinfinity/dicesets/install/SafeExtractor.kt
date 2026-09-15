package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.dicesets.format.ReferencedFile
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Unpacks an archive somebody else wrote, into a folder it cannot leave
 * (`docs/dice-sets.md`, "Installing from a URL or file"; `SECURITY.md`).
 *
 * This is the app's whole attack surface in one class, so it is worth saying
 * what it refuses and why the order matters.
 *
 * **Streamed, and bounded as it goes.** The entry count and the running total
 * of extracted bytes are checked *during* the read, not after it. An archive
 * that expands to a terabyte has to be refused at the megabyte where that
 * becomes obvious; checking afterwards means the disk is already full.
 *
 * **Nothing but plain files.** A tar entry that declares itself a symbolic
 * link, a hard link or a device node is refused: a link is how an archive
 * reaches outside a folder whose every *path* looks perfectly ordinary, and
 * tar puts that flag in the entry header, where a streaming reader sees it.
 *
 * A zip cannot be checked the same way and does not need to be. Unix modes
 * live in a zip's central directory, at the *end* of the file, which a
 * streaming reader never reads — so `isUnixSymlink` is always false here, and
 * trusting it would be a false comfort. What makes it safe is that this class
 * has no code path that creates a link: an entry is written with
 * `File(destination, safePath).outputStream()` and nothing else. A zip
 * "symlink" therefore extracts as an ordinary little file whose contents are a
 * path, which is exactly as dangerous as a text file. `PackageFiles` then
 * refuses to read anything whose canonical path leaves the folder, so even a
 * link planted by some other means reads as nothing at all.
 *
 * **Paths through the same check a set file's references go through.** There
 * is one rule about what a path inside a package may look like, and it is
 * [ReferencedFile] — reimplementing it here is how the two would come to
 * disagree.
 *
 * **Written into a temporary folder.** Nothing is moved anywhere real until
 * the validator has passed it, and a refusal deletes what it wrote. There is
 * no partially installed state (`docs/dice-sets.md`, rule 2).
 *
 * **What it is unpacking is a parameter, and only that.** A saved-roll
 * collection arrives in an archive too (`docs/dice-notation.md`), under
 * smaller [ArchiveLimits] and looking for a different [PackageRoot]; every
 * refusal above is the same refusal for both, which is the point of there
 * being one extractor. A second one would be a second place for the answer to
 * "is this path safe" to be given, and two answers to that is one too many.
 */
class SafeExtractor(
  private val limits: ArchiveLimits = ArchiveLimits(),
  private val wanted: PackageRoot = PackageRoot.DiceSet,
) {
  /**
   * Extracts [archive] into a new folder under [into].
   *
   * @param subfolder the folder inside the archive to install from, for a URL
   *   that pointed at one (`…/tree/main/sets/skulls`), or `null` to leave it
   *   to [wanted] to find the package wherever it is.
   */
  fun extract(
    archive: File,
    into: File,
    subfolder: String? = null,
  ): ExtractionResult {
    val destination = File(into, "extract-${System.nanoTime()}")
    if (!destination.mkdirs()) {
      return ExtractionResult.Refused(RejectionReason.Unreadable, "could not make a folder to unpack into")
    }
    return try {
      unpack(archive, destination, subfolder)
    } catch (failure: IOException) {
      destination.deleteRecursively()
      ExtractionResult.Refused(RejectionReason.Unreadable, failure.message ?: "the archive could not be read")
    }
  }

  private fun unpack(
    archive: File,
    destination: File,
    subfolder: String?,
  ): ExtractionResult {
    var files = 0
    var skipped = 0
    var bytes = 0L
    var refusal: EntryVerdict.Refused? = null
    openArchive(archive).use { stream ->
      var entry = stream.nextEntry
      while (entry != null && refusal == null) {
        when (val verdict = write(stream, entry, destination, bytes, files)) {
          is EntryVerdict.Refused -> refusal = verdict
          is EntryVerdict.Skipped -> skipped++
          is EntryVerdict.Written -> {
            files++
            bytes += verdict.bytes
          }
        }
        entry = if (refusal == null) stream.nextEntry else null
      }
    }
    val found =
      refusal?.let { PackageRoot.Found.Missing(it.reason, it.detail) }
        ?: notAnArchive(files + skipped)
        ?: wanted.of(destination, subfolder)
    return when (found) {
      is PackageRoot.Found.Missing -> {
        destination.deleteRecursively()
        ExtractionResult.Refused(found.reason, found.detail)
      }
      is PackageRoot.Found.Folder ->
        ExtractionResult.Extracted(root = found.folder, files = files, bytes = bytes, skipped = skipped)
    }
  }

  /**
   * The refusal for bytes that were never an archive, or null when they were.
   *
   * A zip reader handed bytes that are not a zip reports no entries rather
   * than failing, so "nothing at all in it" is how a file that is not an
   * archive arrives here.
   */
  private fun notAnArchive(entries: Int): PackageRoot.Found.Missing? =
    PackageRoot.Found
      .Missing(RejectionReason.Unreadable, "this is not an archive the app can read")
      .takeIf { entries == 0 }

  private fun write(
    stream: ArchiveInputStream<*>,
    entry: ArchiveEntry,
    destination: File,
    bytesSoFar: Long,
    filesSoFar: Int,
  ): EntryVerdict {
    val reference = ReferencedFile.parse(entry.name, limits.allowedExtensions)
    return when {
      entry.isDirectory -> EntryVerdict.Skipped
      linkRefusal(entry) != null -> requireNotNull(linkRefusal(entry))
      // A path that escapes is an attack; an extension that is not on the list
      // is a `.gitignore`. Only one of those is a refusal.
      !ReferencedFile.staysInsidePackage(entry.name) ->
        EntryVerdict.Refused(RejectionReason.PathEscapesPackage, "'${entry.name}' is not a path inside the package")
      reference == null -> EntryVerdict.Skipped
      filesSoFar + 1 > limits.maxEntries ->
        EntryVerdict.Refused(RejectionReason.TooManyEntries, "more than ${limits.maxEntries} files")
      else -> copy(stream, reference.path, destination, bytesSoFar)
    }
  }

  /**
   * Anything a tar entry declares itself to be other than a plain file.
   *
   * Refused by what the archive *says* it is, before anything is opened. See
   * the note on the class about why a zip is not checked here and does not
   * need to be.
   */
  private fun linkRefusal(entry: ArchiveEntry): EntryVerdict.Refused? {
    val link = entry is TarArchiveEntry && (entry.isSymbolicLink || entry.isLink || !entry.isFile)
    return if (link) {
      EntryVerdict.Refused(RejectionReason.NotAPlainFile, "'${entry.name}' is a link or a device, not a file")
    } else {
      null
    }
  }

  private fun copy(
    stream: InputStream,
    path: String,
    destination: File,
    bytesSoFar: Long,
  ): EntryVerdict {
    val target = File(destination, path)
    target.parentFile?.mkdirs()
    val buffer = ByteArray(BUFFER_BYTES)
    var written = 0L
    target.outputStream().use { out ->
      while (true) {
        val read = stream.read(buffer)
        if (read <= 0) break
        // Checked inside the copy, not after it: the whole point of a bomb is
        // that its size is only apparent once it is too late.
        if (bytesSoFar + written + read > limits.maxExtractedBytes) {
          return EntryVerdict.Refused(
            RejectionReason.TooLarge,
            "the archive expands to more than ${limits.maxExtractedBytes shr MIB_SHIFT} MiB",
          )
        }
        out.write(buffer, 0, read)
        written += read
      }
    }
    return EntryVerdict.Written(written)
  }

  private sealed interface EntryVerdict {
    data class Written(
      val bytes: Long,
    ) : EntryVerdict

    data object Skipped : EntryVerdict

    data class Refused(
      val reason: RejectionReason,
      val detail: String,
    ) : EntryVerdict
  }

  private companion object {
    const val BUFFER_BYTES = 8 * 1024
    const val MIB_SHIFT = 20
  }

  /**
   * The archive, as whichever kind it is.
   *
   * Recognised by its own bytes rather than by its name: a file called
   * `set.zip` that is a gzipped tar is a mistake, not an attack, and refusing
   * it would be unhelpful.
   */
  private fun openArchive(archive: File): ArchiveInputStream<*> {
    val head = BufferedInputStream(archive.inputStream(), BUFFER_BYTES)
    head.mark(GZIP_MAGIC.size)
    val magic = ByteArray(GZIP_MAGIC.size)
    val read = head.read(magic)
    head.reset()
    val gzipped = read == GZIP_MAGIC.size && magic.contentEquals(GZIP_MAGIC)
    return if (gzipped) {
      TarArchiveInputStream(GzipCompressorInputStream(head))
    } else {
      ZipArchiveInputStream(head)
    }
  }
}

private val GZIP_MAGIC = byteArrayOf(0x1F, 0x8B.toByte())
