package de.drehtuer.dinfinity.dicesets.install

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Asking a server what it says about an archive
 * (`docs/dice-sets.md`, "Updates").
 *
 * Against a real HTTPS server, for the reason `PackageFetcherTest` gives: what
 * is worth asserting is about what a *server* can do, and a faked one would
 * not be testing it.
 */
class ArchiveStampsTest {
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

  private val stamps =
    ArchiveStamps(
      OkHttpClient
        .Builder()
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .build(),
    )

  @After
  fun close() {
    server.close()
  }

  @Test
  fun `it reports what the server says about the file, verbatim`() {
    // Nothing is parsed out of either: an `ETag` is opaque by definition —
    // quotes and all — and the date beside it is a header to compare rather
    // than a time to reason about.
    server.enqueue(
      MockResponse
        .Builder()
        .setHeader("ETag", QUOTED)
        .setHeader("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT")
        .build(),
    )

    val result = stamps.of(url()) as ArchiveStamps.Result.Stamped

    assertEquals(QUOTED, result.etag)
    assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", result.lastModified)
  }

  @Test
  fun `a lookup with nowhere to point still builds`() {
    // The default: a real client, made when nothing hands one in. Building it
    // asks nothing of anybody.
    assertNotNull(ArchiveStamps())
  }

  @Test
  fun `it asks without downloading the archive`() {
    // The whole point: nothing is pulled to find out that nothing changed.
    server.enqueue(MockResponse.Builder().setHeader("ETag", "\"v1\"").build())

    stamps.of(url())

    assertEquals("HEAD", server.takeRequest().method)
  }

  @Test
  fun `either header on its own is enough`() {
    server.enqueue(MockResponse.Builder().setHeader("ETag", "\"v1\"").build())

    assertEquals(ArchiveStamps.Result.Stamped("\"v1\"", null), stamps.of(url()))
  }

  @Test
  fun `a server that says neither has told us nothing`() {
    server.enqueue(MockResponse.Builder().build())

    assertEquals(ArchiveStamps.Result.Silent, stamps.of(url()))
  }

  @Test
  fun `a server that answers with an error has told us nothing either`() {
    server.enqueue(
      MockResponse
        .Builder()
        .code(NOT_FOUND)
        .setHeader("ETag", "\"v1\"")
        .build(),
    )

    assertEquals(ArchiveStamps.Result.Silent, stamps.of(url()))
  }

  @Test
  fun `plain http is never asked`() {
    // Nothing is enqueued, so a request that was made would hang rather than
    // fail: the refusal has to happen before the socket.
    assertEquals(ArchiveStamps.Result.Silent, stamps.of("http://example.test/brass.zip"))
  }

  @Test
  fun `a server that cannot be reached has told us nothing`() {
    server.close()

    assertEquals(ArchiveStamps.Result.Silent, stamps.of(url()))
  }

  private fun url(): String = server.url("/brass.zip").toString()

  private companion object {
    const val NOT_FOUND = 404

    /** A real `ETag`, quotes and all — which is what makes it worth not parsing. */
    const val QUOTED = "\"v1\""
  }
}
