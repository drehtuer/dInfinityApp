package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.feature.sets.FetchedPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Fetching a dice set from a stranger (`docs/dice-sets.md`; `SECURITY.md`).
 *
 * Against a real HTTPS server, for the reason `PackageFetcherTest` gives: what
 * is worth asserting is about what a *server* can do, and a faked server would
 * not be testing it.
 */
class PackageDownloadTest {
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

  private val cache: File = Files.createTempDirectory("dinfinity-package").toFile()

  private val download =
    PackageDownload(
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
  fun `the archive arrives on disk with the bytes the server sent`() =
    runTest {
      server.enqueue(MockResponse.Builder().body(ARCHIVE).build())

      val fetched = download.fetch(url())

      assertEquals(ARCHIVE, (fetched as FetchedPackage.Archive).file.readText())
    }

  @Test
  fun `plain http never leaves the phone`() =
    runTest {
      // Nothing is enqueued, so a request that was made would hang rather than
      // fail: the refusal has to happen before the socket.
      val fetched = download.fetch("http://example.org/brass.zip")

      assertTrue((fetched as FetchedPackage.Failed).reason, fetched.reason.contains("https"))
    }

  @Test
  fun `a redirect that would leave https is refused`() =
    runTest {
      server.enqueue(
        MockResponse
          .Builder()
          .code(MOVED)
          .setHeader("Location", "http://example.org/brass.zip")
          .build(),
      )

      val fetched = download.fetch(url())

      assertTrue((fetched as FetchedPackage.Failed).reason, fetched.reason.contains("https"))
    }

  @Test
  fun `a server that answers with an error is a failure rather than an empty archive`() =
    runTest {
      server.enqueue(MockResponse.Builder().code(NOT_FOUND).build())

      val fetched = download.fetch(url())

      assertTrue((fetched as FetchedPackage.Failed).reason, fetched.reason.contains("$NOT_FOUND"))
      assertEquals("a failed download left something behind", emptyList<File>(), files())
    }

  @Test
  fun `packages are kept apart from collections in the cache`() =
    runTest {
      // Two different things a stranger sent, and neither is the other.
      server.enqueue(MockResponse.Builder().body(ARCHIVE).build())

      download.fetch(url())

      assertTrue("the archive is not under packages: ${files()}", files().all { it.parentFile?.name == "packages" })
    }

  @Test
  fun `a link that is not a repository or an archive is refused before a socket opens`() =
    runTest {
      // Nothing is enqueued, so a request that was made would hang. The
      // sentence somebody needs is about the link, not about the network.
      val fetched = download.fetch("https://example.org/dice/brass.txt")

      assertTrue((fetched as FetchedPackage.Failed).reason, fetched.reason.contains("archive"))
    }

  @Test
  fun `a tar dot gz is an archive as much as a zip is`() =
    runTest {
      // The link goes through `InstallSource`, which is what decides that a
      // `.tar.gz` is a package and a `.txt` is not. Which forge URLs it turns
      // into which tarballs is tested where it lives; what is worth asserting
      // here is that this asks it at all, and both sides of its answer.
      server.enqueue(MockResponse.Builder().body(ARCHIVE).build())

      val fetched = download.fetch(server.url("/brass.tar.gz").toString())

      assertEquals(ARCHIVE, (fetched as FetchedPackage.Archive).file.readText())
    }

  @Test
  fun `cancelling the coroutine stops the download and leaves nothing behind`() =
    runTest {
      // A blocking read does not notice a cancelled job on its own, so the
      // fetcher is given `isActive` to ask between buffers
      // (`docs/dice-sets.md`, "Updates"). What must hold whatever the timing
      // is: no half-written archive is left in the cache for the next install
      // to trip over, and nothing is reported as having arrived.
      server.enqueue(MockResponse.Builder().body(ARCHIVE).build())
      val scope = CoroutineScope(Dispatchers.IO)
      val fetching = scope.async { download.fetch(url()) { scope.cancel() } }

      val fetched = runCatching { fetching.await() }

      assertFalse(
        "a stopped download reported an archive: ${fetched.getOrNull()}",
        fetched.getOrNull() is FetchedPackage.Archive,
      )
      assertEquals("a stopped download left something behind", emptyList<File>(), files())
    }

  @Test
  fun `a download that is not stopped reports how far it has got`() {
    // The other side of the same seam: progress arrives from the downloading
    // thread, and whatever draws a bar with it gets itself back to the
    // screen's thread — which is the caller's business, not this one's.
    runTest {
      server.enqueue(MockResponse.Builder().body(ARCHIVE).build())
      val seen = mutableListOf<Long>()

      val fetched = download.fetch(url()) { far -> seen += far.bytes }

      assertTrue("nothing was reported", seen.isNotEmpty())
      assertEquals(ARCHIVE.length.toLong(), seen.last())
      assertTrue("the archive did not arrive", fetched is FetchedPackage.Archive)
    }
  }

  private fun files(): List<File> = cache.walkTopDown().filter { it.isFile }.toList()

  private fun url(): String = server.url("/brass.zip").toString()

  private companion object {
    const val NOT_FOUND = 404
    const val MOVED = 302
    const val ARCHIVE = "not really a zip, but bytes all the same"
  }
}
