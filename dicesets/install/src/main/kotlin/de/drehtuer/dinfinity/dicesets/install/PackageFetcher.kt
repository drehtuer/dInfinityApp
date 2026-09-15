package de.drehtuer.dinfinity.dicesets.install

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads a package, refusing anything that is not what it should be
 * (`docs/dice-sets.md`, "Installing from a URL or file"; `SECURITY.md`).
 *
 * Three rules, all of them about the fact that a stranger controls the other
 * end:
 *
 * - **`https` only, and redirects only to `https`.** A redirect from `https`
 *   to `http` is the oldest downgrade there is, and OkHttp follows it by
 *   default. It is switched off here and the hop is refused.
 * - **Capped as it downloads**, not after. A `Content-Length` is what the
 *   server says; the count of bytes actually read is what is true.
 * - **Identified by what arrived.** The SHA-256 of the bytes is what goes into
 *   `.meta.json`, so an update is diffable and an install is reproducible.
 *
 * It reports how far it has got and can be stopped part-way
 * (`docs/dice-sets.md`, "Updates"). Both are the same loop that counts the
 * bytes: nothing is polled and no thread is interrupted, so a download that
 * stops leaves no half-written file behind.
 */
class PackageFetcher(
  client: OkHttpClient? = null,
) {
  private val client: OkHttpClient =
    (client ?: OkHttpClient())
      .newBuilder()
      // Followed by hand below, so a redirect to plain http can be refused
      // rather than taken.
      .followRedirects(false)
      .followSslRedirects(false)
      .callTimeout(InstallLimits.TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
      .build()

  /**
   * How far a download has got (`docs/dice-sets.md`, "Updates").
   *
   * [bytes] is what has actually arrived and is counted here. [total] is what
   * the server *said* the whole thing is, and may be absent, wrong or a lie —
   * it is for drawing a bar with and for nothing else. The cap is applied to
   * [bytes], never to this.
   */
  data class Progress(
    val bytes: Long,
    val total: Long?,
  ) {
    /** How far along, or null when the server did not say how far there is to go. */
    val fraction: Float?
      get() = total?.takeIf { it > 0L }?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) }
  }

  /** What a download came to. */
  sealed interface Result {
    /**
     * @param sha256 what arrived, not what was asked for. This is the identity
     *   recorded with the install.
     */
    data class Downloaded(
      val file: File,
      val bytes: Long,
      val sha256: String,
      /**
       * What the server said about the file, for an archive that has no
       * commits to tell apart (`docs/dice-sets.md`, "Updates").
       *
       * Passed on verbatim and never parsed: an `ETag` is opaque by
       * definition, and a `Last-Modified` here is a header to compare rather
       * than a time to reason about.
       */
      val etag: String?,
      val lastModified: String?,
    ) : Result

    data class Failed(
      val reason: String,
    ) : Result

    /**
     * Somebody stopped it.
     *
     * Not a [Failed] with a polite message, because it is not a failure and
     * nothing should be said about it: the person who cancelled knows what
     * happened, and a screen that answers a Cancel button with an error is a
     * screen arguing with them.
     */
    data object Cancelled : Result
  }

  /**
   * Fetches [url] into a new file under [into], refusing it once more than
   * [maxBytes] have arrived.
   *
   * The cap is a parameter because what is being downloaded decides it: a dice
   * set is an archive of textures and meshes, and a saved-roll collection is a
   * page of JSON that the reader will refuse above a megabyte anyway
   * (`CollectionLimits.MAX_BYTES`). Downloading sixty-four megabytes to refuse
   * one is a stranger deciding how much of somebody's data allowance to spend.
   */
  fun fetch(
    url: String,
    into: File,
    maxBytes: Long = InstallLimits.MAX_DOWNLOAD_BYTES,
    onProgress: (Progress) -> Unit = {},
    cancelled: () -> Boolean = { false },
  ): Result {
    var current = url
    var hops = 0
    while (hops <= InstallLimits.MAX_REDIRECTS) {
      // Checked before every hop as well as during the copy: a redirect chain
      // is the one part of a download where nothing is arriving to check.
      val refusal =
        when {
          cancelled() -> Result.Cancelled
          !current.startsWith("${InstallLimits.SCHEME}://", ignoreCase = true) ->
            Result.Failed("'$current' is not an ${InstallLimits.SCHEME} link")
          else -> null
        }
      if (refusal != null) return refusal
      when (val step = step(current, into, maxBytes, onProgress, cancelled)) {
        is Step.Done -> return step.result
        is Step.Redirect -> {
          current = step.to
          hops++
        }
      }
    }
    return Result.Failed("more than ${InstallLimits.MAX_REDIRECTS} redirects")
  }

  /** One request: either the answer, or where to go next. */
  private fun step(
    url: String,
    into: File,
    maxBytes: Long,
    onProgress: (Progress) -> Unit,
    cancelled: () -> Boolean,
  ): Step {
    val response =
      runCatching { call(url) }
        .getOrElse { return Step.Done(Result.Failed(it.message ?: "the download failed")) }
    response.use {
      val location = if (it.isRedirect) it.header("Location") else null
      if (location != null) {
        val resolved =
          it.request.url
            .resolve(location)
            ?.toString()
        return resolved?.let(Step::Redirect) ?: Step.Done(Result.Failed("a redirect went nowhere"))
      }
      return Step.Done(
        if (it.isSuccessful) {
          save(it, into, maxBytes, onProgress, cancelled)
        } else {
          Result.Failed("the server answered ${it.code}")
        },
      )
    }
  }

  private sealed interface Step {
    data class Done(
      val result: Result,
    ) : Step

    data class Redirect(
      val to: String,
    ) : Step
  }

  private fun call(url: String): Response = client.newCall(Request.Builder().url(url).build()).execute()

  private fun save(
    response: Response,
    into: File,
    maxBytes: Long,
    onProgress: (Progress) -> Unit,
    cancelled: () -> Boolean,
  ): Result {
    val target = File(into, "download-${System.nanoTime()}")
    val digest = MessageDigest.getInstance("SHA-256")
    // What the server says the whole thing is. Only ever used to draw a bar:
    // a `Content-Length` is a claim, and the cap is applied to what arrives.
    val total = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
    val written =
      runCatching {
        response.body.byteStream().use { input ->
          target.outputStream().use { output ->
            copyBounded(input, output, digest, maxBytes) { bytes ->
              onProgress(Progress(bytes = bytes, total = total))
              cancelled()
            }
          }
        }
      }.getOrElse {
        target.discard()
        return Result.Failed((it as? IOException)?.message ?: "the download failed")
      }
    return when (written) {
      is Copied.Stopped -> {
        target.discard()
        Result.Cancelled
      }
      is Copied.TooBig -> {
        target.discard()
        Result.Failed("the download is larger than the ${maxBytes shr MIB_SHIFT} MiB allowed")
      }
      is Copied.Whole ->
        Result.Downloaded(
          file = target,
          bytes = written.bytes,
          sha256 = digest.digest().toHex(),
          etag = response.header("ETag"),
          lastModified = response.header("Last-Modified"),
        )
    }
  }

  /** How a copy ended. */
  private sealed interface Copied {
    data class Whole(
      val bytes: Long,
    ) : Copied

    data object TooBig : Copied

    data object Stopped : Copied
  }

  /**
   * Throws away a half-written download.
   *
   * Whether the file went is worth acting on rather than ignoring: one that
   * will not delete now is one that sits in the cache for ever, and a refused
   * download is exactly the case where it might be held open a moment longer
   * by the stream that just failed. Marking it for the end of the process is
   * not a guarantee on Android, where the process is killed rather than
   * closed, but it costs nothing and it is better than deciding not to look.
   */
  private fun File.discard() {
    if (!delete()) deleteOnExit()
  }

  /**
   * Copies until the stream ends, the cap is passed or [reached] says to stop.
   *
   * The count of bytes actually read, never the `Content-Length`: one of those
   * is true and the other is what the server said.
   *
   * [reached] is told how much has arrived after every buffer and answers
   * whether to give up. One callback rather than two because they happen at
   * the same moment and for the same reason — somebody watching a bar is
   * somebody who may press Cancel — and because a loop with two hooks in it is
   * a loop with two places to get the order wrong.
   *
   * A buffer at a time, not a byte: a check per byte would cost more than the
   * copy, and sixteen kilobytes is a few milliseconds of even a slow link.
   */
  private fun copyBounded(
    input: java.io.InputStream,
    output: java.io.OutputStream,
    digest: MessageDigest,
    maxBytes: Long,
    reached: (Long) -> Boolean,
  ): Copied {
    val buffer = ByteArray(BUFFER_BYTES)
    var written = 0L
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) return Copied.Whole(written)
      written += read
      if (written > maxBytes) return Copied.TooBig
      digest.update(buffer, 0, read)
      output.write(buffer, 0, read)
      if (reached(written)) return Copied.Stopped
    }
  }

  private companion object {
    const val BUFFER_BYTES = 16 * 1024
    const val MIB_SHIFT = 20
    const val HEX = "0123456789abcdef"
    const val BYTE_MASK = 0xFF
    const val LOW_NIBBLE = 0x0F
    const val NIBBLE_BITS = 4

    fun ByteArray.toHex(): String =
      buildString(size * 2) {
        this@toHex.forEach { byte ->
          val value = byte.toInt() and BYTE_MASK
          append(HEX[value shr NIBBLE_BITS])
          append(HEX[value and LOW_NIBBLE])
        }
      }
  }
}
