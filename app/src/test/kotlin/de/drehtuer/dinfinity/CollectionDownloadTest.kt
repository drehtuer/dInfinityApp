package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.feature.saved.Fetched
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The app's one outward request (`docs/dice-notation.md`, "Export and import";
 * `SECURITY.md`).
 *
 * Against a real HTTPS server rather than a stubbed client, for the reason
 * `PackageFetcherTest` gives: the things worth asserting are about what a
 * *server* can do, and a test that faked the server would not be testing them.
 */
class CollectionDownloadTest {
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

  private val cache: File = Files.createTempDirectory("dinfinity-collection").toFile()

  private val download =
    CollectionDownload(
      cacheDir = cache,
      fetcher =
        PackageFetcher(
          OkHttpClient
            .Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build(),
        ),
    )

  @After
  fun close() {
    server.close()
    cache.deleteRecursively()
  }

  @Test
  fun `what the server sent is what comes back`() =
    runTest {
      server.enqueue(MockResponse.Builder().body(THORIN).build())

      val fetched = download.fetch(url())

      assertEquals(THORIN, (fetched as Fetched.Text).text)
    }

  @Test
  fun `plain http never leaves the phone`() =
    runTest {
      // No response is enqueued, so a request that was made would hang rather
      // than pass: the refusal has to happen before the socket.
      val fetched = download.fetch("http://example.org/thorin.json")

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("https"))
    }

  @Test
  fun `a collection is capped at the megabyte the reader would refuse anyway`() =
    runTest {
      // Not the sixty-four an archive gets. A server cannot spend somebody's
      // data allowance proving that it should not have.
      server.enqueue(MockResponse.Builder().body("x".repeat(CollectionLimits.MAX_BYTES + KIB)).build())

      val fetched = download.fetch(url())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("larger than"))
    }

  @Test
  fun `nothing of a download is left in the cache, refused or not`() =
    runTest {
      // Somebody else's data, in a directory nobody empties. The collection it
      // held is in the database by the time anybody would want it again.
      server.enqueue(MockResponse.Builder().body(THORIN).build())
      download.fetch(url())
      server.enqueue(MockResponse.Builder().code(NOT_FOUND).build())
      download.fetch(url())

      val left = cache.walkTopDown().filter { it.isFile }.toList()
      assertEquals("a download was left behind: $left", emptyList<File>(), left)
    }

  @Test
  fun `a server that answers with an error is a failure rather than an empty collection`() =
    runTest {
      server.enqueue(MockResponse.Builder().code(NOT_FOUND).build())

      val fetched = download.fetch(url())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("$NOT_FOUND"))
    }

  private fun url(): String = server.url("/thorin.json").toString()

  private companion object {
    const val KIB = 1024
    const val NOT_FOUND = 404

    val THORIN =
      """
      {
        "format": 1,
        "name": "Thorin",
        "groups": [{ "id": "thorin", "name": "Thorin", "icon": "x", "parent": null }],
        "rolls": [{ "group": "thorin", "name": "Longsword", "formula": "1d20 + 7" }]
      }
      """.trimIndent()
  }
}
