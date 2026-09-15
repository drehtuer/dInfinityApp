package de.drehtuer.dinfinity.dicesets.install

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Asking a server what it says about an archive, without downloading it
 * (`docs/dice-sets.md`, "Updates").
 *
 * The counterpart to [RefResolver] for the sources that have no commits to
 * tell apart. A plain `.zip` on somebody's web server has an `ETag` and a
 * `Last-Modified` and nothing else, and those are what an install recorded.
 *
 * **A `HEAD`, so nothing is downloaded to find out that nothing changed.** The
 * whole point of the check is to avoid pulling sixty megabytes to compare them
 * with sixty megabytes already on disk.
 *
 * The headers come back verbatim and nothing is parsed out of them. An `ETag`
 * is an opaque string by definition, and the `Last-Modified` beside it is a
 * header to compare rather than a time to reason about — deciding one date is
 * *later* than another would read a meaning into a server's clock that nobody
 * promised.
 *
 * `https` only, like everything else that leaves the phone (`SECURITY.md`). A
 * redirect is followed, because a `HEAD` carries no body to be stolen — but
 * not one that leaves `https`, because the URL it lands on is one the app
 * would then be treating as the set's source.
 */
class ArchiveStamps(
  client: OkHttpClient? = null,
) {
  private val client: OkHttpClient =
    (client ?: OkHttpClient())
      .newBuilder()
      .followSslRedirects(false)
      .callTimeout(InstallLimits.TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
      .build()

  /** What a server said about a file. */
  sealed interface Result {
    /**
     * What it says now.
     *
     * Either may be absent; both absent is [Silent] rather than this.
     */
    data class Stamped(
      val etag: String?,
      val lastModified: String?,
    ) : Result

    /** It could not be asked, or it said nothing that could be compared. */
    data object Silent : Result
  }

  /** What [url]'s server says about the file now. */
  fun of(url: String): Result {
    if (!url.startsWith("${InstallLimits.SCHEME}://", ignoreCase = true)) return Result.Silent
    return runCatching { ask(url) }.getOrDefault(Result.Silent)
  }

  private fun ask(url: String): Result {
    client
      .newCall(
        Request
          .Builder()
          .url(url)
          .head()
          .build(),
      ).execute()
      .use { response ->
        val etag = response.header("ETag")
        val lastModified = response.header("Last-Modified")
        val nothing = !response.isSuccessful || (etag == null && lastModified == null)
        return if (nothing) Result.Silent else Result.Stamped(etag = etag, lastModified = lastModified)
      }
  }
}
