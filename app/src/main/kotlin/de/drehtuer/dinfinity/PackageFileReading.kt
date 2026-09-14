package de.drehtuer.dinfinity

import android.content.ContentResolver
import android.net.Uri
import de.drehtuer.dinfinity.dicesets.install.InstallLimits
import java.io.File

/**
 * Copying an archive somebody chose onto disk, without trusting how big it is
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The same rule [CollectionFileReading] follows, for the same reason and with
 * one difference. A content URI is a handle to something another application
 * controls: its size is not knowable in advance, the provider may report one
 * figure and hand over another, and it may stream for ever. So it is copied
 * **bounded**, stopping one byte past the limit — that one byte is what tells a
 * file exactly at the limit from one over it.
 *
 * The difference is that a dice set is an *archive* rather than text, and the
 * installer takes a `File`. So this writes one rather than returning a string,
 * and the caller deletes it: the bytes are a stranger's, the copy is
 * temporary, and it exists only so the installer has something to open twice.
 *
 * In `app/` because a content URI is the application's business —
 * `ContentResolver` is reached through a context, and nothing in
 * `dicesets/install` knows what Android is.
 */
object PackageFileReading {
  /** What copying a chosen file came to. */
  sealed interface Result {
    /** It is on disk, at [file]. The caller deletes it when it is done. */
    data class Copied(
      val file: File,
    ) : Result

    /** It could not be opened, or there was more of it than a package may be. */
    data class Failed(
      val why: String,
    ) : Result
  }

  /**
   * Copies [uri] into [into], refusing anything over
   * [InstallLimits.MAX_DOWNLOAD_BYTES].
   *
   * The same cap a download gets. A file picked off the phone has not crossed
   * the network, but it is no more trustworthy for it: the limit is about what
   * the extractor is willing to open, not about where the bytes came from.
   */
  fun copy(
    resolver: ContentResolver,
    uri: Uri,
    into: File,
  ): Result =
    runCatching {
      into.mkdirs()
      val file = File(into, "chosen-${System.nanoTime()}")
      resolver.openInputStream(uri).use { stream ->
        if (stream == null) return Result.Failed("nothing answered at that file")
        val written = file.outputStream().use { out -> stream.copyBoundedTo(out) }
        if (written > InstallLimits.MAX_DOWNLOAD_BYTES) {
          file.delete()
          Result.Failed("that file is larger than ${InstallLimits.MAX_DOWNLOAD_BYTES} bytes")
        } else {
          Result.Copied(file)
        }
      }
    }.getOrElse { failure -> Result.Failed(failure.message ?: "it could not be read") }

  /**
   * Copies at most one byte past the limit, and says how many that was.
   *
   * Reading the whole thing and checking afterwards is the version of this
   * that a stream with no end wins.
   */
  private fun java.io.InputStream.copyBoundedTo(out: java.io.OutputStream): Long {
    val buffer = ByteArray(BUFFER)
    var total = 0L
    while (total <= InstallLimits.MAX_DOWNLOAD_BYTES) {
      val read = read(buffer)
      if (read < 0) break
      out.write(buffer, 0, read)
      total += read
    }
    return total
  }

  private const val BUFFER = 64 * 1024
}
