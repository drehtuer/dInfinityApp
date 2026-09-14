package de.drehtuer.dinfinity

import android.content.ContentResolver
import android.net.Uri
import de.drehtuer.dinfinity.core.collection.CollectionLimits

/**
 * Reading a file somebody chose, without trusting how big it is
 * (`docs/dice-notation.md`, "Export and import").
 *
 * A content URI is a handle to something another application controls. Its
 * size is not knowable in advance — the provider may report one figure and
 * hand over another, or stream forever — so it is read **bounded**, one byte
 * past the limit and no further. That one extra byte is what tells the
 * difference between a file exactly at the limit and one that is over it.
 *
 * In `app/` because a content URI is the application's business: `ContentResolver`
 * is reached through a context, and a presenter in `feature/saved` takes text.
 */
object CollectionFileReading {
  /** What reading a chosen file came to. */
  sealed interface Result {
    data class Read(
      val text: String,
    ) : Result

    /** It could not be opened, or there was more of it than a collection may be. */
    data class Failed(
      val why: String,
    ) : Result
  }

  /** Reads [uri] as text, refusing anything over [CollectionLimits.MAX_BYTES]. */
  fun read(
    resolver: ContentResolver,
    uri: Uri,
  ): Result =
    runCatching {
      resolver.openInputStream(uri).use { stream ->
        if (stream == null) return Result.Failed("nothing answered at that file")
        val bytes = stream.readNBytes(CollectionLimits.MAX_BYTES + 1)
        if (bytes.size > CollectionLimits.MAX_BYTES) {
          Result.Failed("that file is larger than ${CollectionLimits.MAX_BYTES} bytes")
        } else {
          Result.Read(bytes.toString(Charsets.UTF_8))
        }
      }
    }.getOrElse { failure -> Result.Failed(failure.message ?: "it could not be read") }
}
