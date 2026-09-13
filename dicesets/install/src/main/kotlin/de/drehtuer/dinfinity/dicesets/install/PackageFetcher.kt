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
    ) : Result

    data class Failed(
      val reason: String,
    ) : Result
  }

  /** Fetches [url] into a new file under [into]. */
  fun fetch(
    url: String,
    into: File,
  ): Result {
    var current = url
    var hops = 0
    while (hops <= InstallLimits.MAX_REDIRECTS) {
      if (!current.startsWith("${InstallLimits.SCHEME}://", ignoreCase = true)) {
        return Result.Failed("'$current' is not an ${InstallLimits.SCHEME} link")
      }
      when (val step = step(current, into)) {
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
      return Step.Done(if (it.isSuccessful) save(it, into) else Result.Failed("the server answered ${it.code}"))
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
  ): Result {
    val target = File(into, "download-${System.nanoTime()}")
    val digest = MessageDigest.getInstance("SHA-256")
    val written =
      runCatching {
        response.body.byteStream().use { input ->
          target.outputStream().use { output -> copyBounded(input, output, digest) }
        }
      }.getOrElse {
        target.delete()
        return Result.Failed((it as? IOException)?.message ?: "the download failed")
      }
    if (written == null) {
      target.delete()
      return Result.Failed("the download is larger than ${InstallLimits.MAX_DOWNLOAD_BYTES shr MIB_SHIFT} MiB")
    }
    return Result.Downloaded(file = target, bytes = written, sha256 = digest.digest().toHex())
  }

  /**
   * Copies until the stream ends, or gives up and returns `null` once more
   * bytes have arrived than are allowed.
   *
   * The count of bytes actually read, never the `Content-Length`: one of those
   * is true and the other is what the server said.
   */
  private fun copyBounded(
    input: java.io.InputStream,
    output: java.io.OutputStream,
    digest: MessageDigest,
  ): Long? {
    val buffer = ByteArray(BUFFER_BYTES)
    var written = 0L
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) return written
      written += read
      if (written > InstallLimits.MAX_DOWNLOAD_BYTES) return null
      digest.update(buffer, 0, read)
      output.write(buffer, 0, read)
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
