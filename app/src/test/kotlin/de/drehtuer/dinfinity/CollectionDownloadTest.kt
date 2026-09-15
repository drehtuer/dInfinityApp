package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.collection.CollectionLimits
import de.drehtuer.dinfinity.dicesets.install.InstallSource
import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.feature.saved.Fetched
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
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
 *
 * Both ways in are here, because they are one way in: a link to a file and a
 * link to a git repository are the same downloader under the same cap, and the
 * second half of this class is mostly about the archive in the middle being
 * given no more licence than a dice set's is.
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

  private val fetcher =
    PackageFetcher(
      OkHttpClient
        .Builder()
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .build(),
    )

  private val download = CollectionDownload(cacheDir = cache, fetcher = fetcher)

  @After
  fun close() {
    server.close()
    cache.deleteRecursively()
  }

  @Test
  fun `what the server sent is what comes back`() =
    runTest {
      server.enqueue(MockResponse.Builder().body(Repositories.THORIN).build())

      val fetched = download.fetch(url())

      assertEquals(Repositories.THORIN, (fetched as Fetched.Text).text)
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
      server.enqueue(MockResponse.Builder().body(Repositories.THORIN).build())
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

  @Test
  fun `a collection in a git repository comes back the way a file does`() =
    runTest {
      // The whole point of the repository half: what reaches the reader is
      // text, exactly as if the same bytes had been pasted as a link.
      serve(Repositories.wellFormed())

      val fetched = download.fetch(repository())

      assertEquals(Repositories.THORIN, (fetched as Fetched.Text).text)
    }

  @Test
  fun `a forge link is fetched as the tarball it resolves to, not as itself`() =
    runTest {
      // Which repository a link means is `InstallSource`'s to say — the same
      // answer a dice set's link gets. Nothing here re-derives it; this asks
      // only that the answer is what gets fetched, since the pasted URL itself
      // would not reach this server at all.
      serve(Repositories.wellFormed())
      val asked = mutableListOf<String>()
      val forge =
        CollectionDownload(
          cacheDir = cache,
          fetcher = fetcher,
          sources = { url ->
            asked += url
            InstallSource(
              kind = InstallSource.Kind.GitHub,
              archiveUrl = repository(),
              reference = "main",
              commitUrl = "https://api.github.test/repos/ada/monsters/commits/main",
            )
          },
        )

      val fetched = forge.fetch(GITHUB)

      assertEquals(listOf(GITHUB), asked)
      assertEquals(Repositories.THORIN, (fetched as Fetched.Text).text)
    }

  @Test
  fun `a repository with no collection in it says what one is called`() =
    runTest {
      serve(Repositories.tarGz(mapOf("README.md" to "stat blocks", "stats.json" to "{}")))

      val fetched = download.fetch(repository())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains(".dinfinity.json"))
    }

  @Test
  fun `a repository with two collections in it refuses rather than choosing`() =
    runTest {
      serve(
        Repositories.tarGz(
          mapOf(
            "goblins.dinfinity.json" to Repositories.THORIN,
            "dragons.dinfinity.json" to Repositories.THORIN,
          ),
        ),
      )

      val fetched = download.fetch(repository())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("goblins.dinfinity.json"))
      assertTrue(fetched.reason, fetched.reason.contains("dragons.dinfinity.json"))
    }

  @Test
  fun `a repository is capped at the same megabyte a collection is`() =
    runTest {
      // The cap is on the bytes that arrive, so it is reached long before
      // anything is unpacked — which is the only place it could be reached
      // safely.
      server.enqueue(MockResponse.Builder().body("x".repeat(CollectionLimits.MAX_BYTES + KIB)).build())

      val fetched = download.fetch(repository())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("larger than"))
    }

  @Test
  fun `a repository that expands to more than it weighs is refused as it unpacks`() =
    runTest {
      // Zeroes compress to almost nothing, which is the whole trick. The
      // refusal has to come at the megabyte it becomes obvious.
      serve(Repositories.bytes(mapOf("monsters-x/big.dinfinity.json" to Repositories.bomb(megabytes = 8))))

      val fetched = download.fetch(repository())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("expands to"))
      assertEquals("something was unpacked anyway", emptyList<File>(), filesIn(cache))
    }

  @Test
  fun `a repository whose entry climbs out of the folder writes nothing anywhere`() =
    runTest {
      // The oldest archive attack there is, arriving by the newest door.
      serve(Repositories.bytes(mapOf("../../escaped.dinfinity.json" to Repositories.THORIN.encodeToByteArray())))

      val fetched = download.fetch(repository())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("not a path inside the package"))
      assertEquals("something was written into the cache", emptyList<File>(), filesIn(cache))
      assertTrue("a file escaped the cache", !File(cache.parentFile, "escaped.dinfinity.json").exists())
    }

  @Test
  fun `a reference the forge does not have is its own refusal`() =
    runTest {
      // A branch or tag that does not exist is a 404 from the forge, which is
      // the forge's answer and not something to guess at.
      server.enqueue(MockResponse.Builder().code(NOT_FOUND).build())

      val fetched = download.fetch(repository())

      assertTrue((fetched as Fetched.Failed).reason, fetched.reason.contains("$NOT_FOUND"))
    }

  @Test
  fun `nothing of a repository is left in the cache either, refused or not`() =
    runTest {
      serve(Repositories.wellFormed())
      download.fetch(repository())
      serve(Repositories.tarGz(mapOf("README.md" to "nothing to see")))
      download.fetch(repository())

      val left = cache.walkTopDown().filter { it.isFile }.toList()
      assertEquals("an unpacked repository was left behind: $left", emptyList<File>(), left)
    }

  private fun url(): String = server.url("/thorin.json").toString()

  /** A link the installer recognises as an archive, which is what a forge hands out. */
  private fun repository(): String = server.url("/ada/monsters/archive/HEAD.tar.gz").toString()

  private fun serve(body: Buffer) {
    server.enqueue(MockResponse.Builder().body(body).build())
  }

  private fun filesIn(directory: File): List<File> = directory.walkTopDown().filter { it.isFile }.toList()

  private companion object {
    const val KIB = 1024
    const val NOT_FOUND = 404

    /** A link somebody would actually paste, which never reaches a test server. */
    const val GITHUB = "https://github.com/ada/monsters"
  }
}
