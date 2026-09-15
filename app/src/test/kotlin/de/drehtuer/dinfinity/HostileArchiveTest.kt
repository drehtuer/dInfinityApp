package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.feature.sets.FetchedPackage
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A hostile archive, all the way from a server to the disk
 * (`docs/TODO.md`, Step 4.4; `SECURITY.md`).
 *
 * Every layer refuses these on its own and is tested where it lives:
 * `SafeExtractorTest` for paths that climb out, symbolic links, entry counts
 * and zip bombs; `PackageInstallerTest` for an install that leaves nothing
 * behind; `dicesets/format` for a set file that lies about itself. What none of
 * those can say is whether the layers are actually *joined up* now that an
 * archive can arrive over the network, which is what this asks.
 *
 * It is deliberately end to end and deliberately short. The interesting
 * assertion is not that the install failed — every layer would say that — but
 * that **nothing was written anywhere**: not into the sets folder, and not left
 * in the cache the download used.
 */
class HostileArchiveTest {
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

  private val cache: File = Files.createTempDirectory("dinfinity-hostile-cache").toFile()
  private val sets: File = Files.createTempDirectory("dinfinity-hostile-sets").toFile()

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
    sets.deleteRecursively()
  }

  @Test
  fun `an archive that climbs out of its folder is refused, and writes nothing anywhere`() =
    runTest {
      // The oldest archive attack there is, arriving the newest way.
      serve(zipOf("../../escaped.toml" to "id = \"escaped\""))

      val result = install()

      assertTrue("a zip-slip archive installed: $result", result is PackageInstaller.Result.Failed)
      assertEquals("something was written into the sets folder", emptyList<File>(), filesIn(sets))
      assertEquals("the download was left in the cache", emptyList<File>(), filesIn(cache))
      assertTrue("a file escaped the sets folder", !File(sets.parentFile, "escaped.toml").exists())
    }

  @Test
  fun `an archive with nothing in it that is a set is refused the same way`() =
    runTest {
      serve(zipOf("readme.md" to "nothing to see"))

      val result = install()

      assertTrue("an archive with no set file installed: $result", result is PackageInstaller.Result.Failed)
      assertEquals("something was written into the sets folder", emptyList<File>(), filesIn(sets))
      assertEquals("the download was left in the cache", emptyList<File>(), filesIn(cache))
    }

  @Test
  fun `bytes that are not an archive at all are refused before anything is unpacked`() =
    runTest {
      serve(Buffer().writeUtf8("this is not a zip, whatever the link said"))

      val result = install()

      assertTrue("a file that is not an archive installed: $result", result is PackageInstaller.Result.Failed)
      assertEquals(emptyList<File>(), filesIn(sets))
      assertEquals(emptyList<File>(), filesIn(cache))
    }

  /** Downloads what the server is holding and puts it through the real installer. */
  private suspend fun install(): PackageInstaller.Result {
    val fetched = download.fetch(server.url("/hostile.zip").toString())
    val archive = (fetched as FetchedPackage.Archive).file
    return try {
      PackageInstaller(sets).installFrom(archive)
    } finally {
      archive.delete()
    }
  }

  private fun serve(body: Buffer) {
    server.enqueue(MockResponse.Builder().body(body).build())
  }

  private fun filesIn(directory: File): List<File> = directory.walkTopDown().filter { it.isFile }.toList()

  /** A zip holding exactly these entries, built in memory. */
  private fun zipOf(vararg entries: Pair<String, String>): Buffer {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      entries.forEach { (name, content) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
      }
    }
    return Buffer().write(bytes.toByteArray())
  }
}
