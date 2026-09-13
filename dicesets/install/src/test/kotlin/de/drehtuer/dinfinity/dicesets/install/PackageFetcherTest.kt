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
  }
}
