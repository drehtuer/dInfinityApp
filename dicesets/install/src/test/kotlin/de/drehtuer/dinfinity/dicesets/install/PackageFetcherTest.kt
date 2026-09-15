package de.drehtuer.dinfinity.dicesets.install

import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Downloading from a stranger (`docs/dice-sets.md`; `SECURITY.md`).
 *
 * Against a real HTTPS server rather than a stubbed client. The things worth
 * asserting here — that a redirect to plain `http` is refused, that a server
 * lying about its `Content-Length` does not get to write a gigabyte, that a
 * connection dropped halfway leaves nothing — only happen at a socket, and a
 * test that had to exempt itself from the `https`-only rule would not be
 * testing that rule.
 */
class PackageFetcherTest {
  private val certificate =
    HeldCertificate
      .Builder()
      .addSubjectAlternativeName("localhost")
      .build()
  private val serverCertificates =
    HandshakeCertificates
      .Builder()
      .heldCertificate(certificate)
      .build()
  private val clientCertificates =
    HandshakeCertificates
      .Builder()
      .addTrustedCertificate(certificate.certificate)
      .build()

  private val server =
    MockWebServer().also {
      it.useHttps(serverCertificates.sslSocketFactory())
      it.start()
    }

  private val into: File = Files.createTempDirectory("dinfinity-fetch").toFile()

  private val fetcher =
    PackageFetcher(
      OkHttpClient
        .Builder()
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .build(),
    )

  @After
  fun close() {
    server.close()
    into.deleteRecursively()
  }

  @Test
  fun `plain http is refused without a request being made`() {
    val result = failed(fetcher.fetch("http://example.org/brass.zip", into))
    assertTrue(result.reason, result.reason.contains("https"))
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `a download is identified by what arrived`() {
    server.enqueue(MockResponse(body = "hello"))
    val result = downloaded(fetcher.fetch(url(), into))
    assertEquals(5L, result.bytes)
    // The SHA-256 of "hello", which is what goes into .meta.json.
    assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result.sha256)
    assertEquals("hello", result.file.readText())
  }

  @Test
  fun `an error from the server is a failure, not an empty package`() {
    server.enqueue(MockResponse(code = 404))
    assertTrue(failed(fetcher.fetch(url(), into)).reason.contains("404"))
  }

  @Test
  fun `a redirect within https is followed`() {
    server.enqueue(redirect(url("/moved")))
    server.enqueue(MockResponse(body = "hello"))
    assertEquals("hello", downloaded(fetcher.fetch(url(), into)).file.readText())
  }

  @Test
  fun `a redirect to plain http is refused rather than followed`() {
    server.enqueue(redirect("http://example.org/x"))
    val result = failed(fetcher.fetch(url(), into))
    assertTrue(result.reason, result.reason.contains("https"))
  }

  @Test
  fun `too many redirects is a failure rather than a loop`() {
    repeat(InstallLimits.MAX_REDIRECTS + 2) { server.enqueue(redirect(url())) }
    assertTrue(failed(fetcher.fetch(url(), into)).reason.contains("redirects"))
  }

  @Test
  fun `a download bigger than the cap is refused, and the part of it deleted`() {
    server.enqueue(MockResponse.Builder().body(zeroes(InstallLimits.MAX_DOWNLOAD_BYTES + KIB)).build())
    val result = failed(fetcher.fetch(url(), into))
    assertTrue(result.reason, result.reason.contains("MiB"))
    assertTrue("a refused download was left on disk", into.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun `a caller can ask for a smaller cap than an archive gets`() {
    // A saved-roll collection is a page of JSON the reader refuses above a
    // megabyte anyway, so downloading sixty-four to refuse one would be a
    // stranger deciding how much of somebody's data allowance to spend
    // (`CollectionDownload`).
    val small = 4L * KIB
    server.enqueue(MockResponse.Builder().body(zeroes(small + KIB)).build())

    val result = failed(fetcher.fetch(url(), into, maxBytes = small))

    assertTrue(result.reason, result.reason.contains("larger than"))
    assertTrue("a refused download was left on disk", into.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun `a download inside the smaller cap still arrives`() {
    // The other side of it: the cap is a bound, not a rejection of everything.
    val small = 4L * KIB
    server.enqueue(MockResponse.Builder().body(zeroes(small - KIB)).build())

    val result = fetcher.fetch(url(), into, maxBytes = small)

    assertTrue(result.toString(), result is PackageFetcher.Result.Downloaded)
  }

  @Test
  fun `a server that never says how much it is sending is capped by what arrives`() {
    // The cap counts bytes read, which is the only number that is true. A
    // declared Content-Length is not: OkHttp stops at a *small* declared one,
    // so the case that actually needs the cap is the one with no length at
    // all — a chunked response that just keeps coming.
    server.enqueue(MockResponse.Builder().body(endless()).build())
    val result = failed(fetcher.fetch(url(), into))
    assertTrue(result.reason, result.reason.contains("MiB"))
    assertTrue("a refused download was left on disk", into.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun `a server that hangs up mid-download leaves nothing, not half a package`() {
    server.enqueue(
      MockResponse
        .Builder()
        .body("partial")
        .onResponseBody(SocketEffect.CloseSocket())
        .build(),
    )
    fetcher.fetch(url(), into)
    assertTrue("half a download was left on disk", into.listFiles().orEmpty().isEmpty())
  }

  private fun url(path: String = "/brass.tar.gz"): String = server.url(path).toString()

  private fun redirect(to: String): MockResponse =
    MockResponse
      .Builder()
      .code(FOUND)
      .addHeader("Location", to)
      .build()

  /** A body that never declares a length and keeps going past the cap. */
  private fun endless(): MockResponseBody =
    object : MockResponseBody {
      override val contentLength: Long = -1

      override fun writeTo(sink: BufferedSink) {
        val chunk = ByteArray(CHUNK)
        var written = 0L
        // A little past the cap: enough to be refused, not enough to be slow.
        while (written < InstallLimits.MAX_DOWNLOAD_BYTES + CHUNK) {
          sink.write(chunk)
          written += CHUNK
        }
      }
    }

  @Test
  fun `it says how far it has got as the bytes arrive`() {
    // What a bar is drawn from (`design/dInfinity.dc.html`, option `9i`).
    server.enqueue(MockResponse.Builder().body(zeroes(BIG)).build())
    val seen = mutableListOf<PackageFetcher.Progress>()

    val result = fetcher.fetch(url(), into, onProgress = seen::add)

    assertEquals(BIG, downloaded(result).bytes)
    assertTrue("nothing was reported for a $BIG byte download", seen.size > 1)
    assertEquals("the last report was not the whole file", BIG, seen.last().bytes)
    assertEquals("the reports went backwards", seen.map { it.bytes }.sorted(), seen.map { it.bytes })
  }

  @Test
  fun `and how far there is to go, when the server says`() {
    server.enqueue(MockResponse.Builder().body(zeroes(BIG)).build())
    val seen = mutableListOf<PackageFetcher.Progress>()

    fetcher.fetch(url(), into, onProgress = seen::add)

    assertEquals(BIG, seen.last().total)
    assertEquals(1f, seen.last().fraction)
  }

  @Test
  fun `a download nobody can size has no fraction rather than a wrong one`() {
    // A `Content-Length` is a claim, and a bar drawn from a missing one would
    // be a bar that jumps. The count of bytes is still true.
    val far = PackageFetcher.Progress(bytes = 512, total = null)

    assertEquals(null, far.fraction)
    assertEquals(512L, far.bytes)
  }

  @Test
  fun `a server that lies about the size cannot push the bar past full`() {
    val far = PackageFetcher.Progress(bytes = 900, total = 100)

    assertEquals(1f, far.fraction)
  }

  @Test
  fun `and one that says nothing is coming has no fraction either`() {
    // Zero is a `Content-Length` a server really does send, and dividing by it
    // is the one arithmetic mistake a progress bar can make.
    assertEquals(null, PackageFetcher.Progress(bytes = 0, total = 0).fraction)
    assertEquals(0f, PackageFetcher.Progress(bytes = 0, total = 100).fraction)
  }

  @Test
  fun `stopping it part-way leaves nothing behind`() {
    // The one that matters: a cancelled download must not leave a half-written
    // archive in the cache for the next install to trip over.
    server.enqueue(MockResponse.Builder().body(zeroes(BIG)).build())

    val result = fetcher.fetch(url(), into, cancelled = { true })

    assertTrue("expected a cancellation, got $result", result is PackageFetcher.Result.Cancelled)
    assertEquals("a stopped download left a file behind", emptyList<File>(), into.listFiles().orEmpty().toList())
  }

  @Test
  fun `a download stopped before it starts never opens a socket`() {
    // Nothing is enqueued, so a request that was made would hang rather than
    // fail: the refusal has to happen before the call.
    val result = fetcher.fetch(url(), into, cancelled = { true })

    assertTrue("expected a cancellation, got $result", result is PackageFetcher.Result.Cancelled)
  }

  /** A body of the given size that costs nothing to hold. */
  private fun zeroes(length: Long): MockResponseBody =
    object : MockResponseBody {
      override val contentLength: Long = length

      override fun writeTo(sink: BufferedSink) {
        val chunk = ByteArray(CHUNK)
        var left = length
        while (left > 0) {
          val write = minOf(left, CHUNK.toLong()).toInt()
          sink.write(chunk, 0, write)
          left -= write
        }
      }
    }

  private fun downloaded(result: PackageFetcher.Result): PackageFetcher.Result.Downloaded {
    assertTrue("expected a download, got $result", result is PackageFetcher.Result.Downloaded)
    return result as PackageFetcher.Result.Downloaded
  }

  private fun failed(result: PackageFetcher.Result): PackageFetcher.Result.Failed {
    assertTrue("expected a failure, got $result", result is PackageFetcher.Result.Failed)
    return result as PackageFetcher.Result.Failed
  }

  private companion object {
    const val FOUND = 302
    const val KIB = 1024L
    const val CHUNK = 64 * 1024

    /** Bigger than one buffer, so there is more than one report to make. */
    const val BIG = 200L * 1024
  }
}
