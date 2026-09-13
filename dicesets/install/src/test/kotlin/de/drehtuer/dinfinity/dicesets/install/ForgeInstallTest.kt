package de.drehtuer.dinfinity.dicesets.install

import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Installing from a forge, end to end, against a real server
 * (`docs/dice-sets.md`, "Installing from a URL or file" and "Updates").
 *
 * What is being tested is the thing the two halves only do together: the
 * commit a ref resolved to ends up written beside the package, so that "check
 * for updates" has something to compare. And, more importantly, that a forge
 * which will not answer costs the update check and nothing else — the set
 * still installs, because the archive's own SHA-256 is what made it
 * reproducible in the first place.
 *
 * The forge itself is a [Commits] stub rather than a server: whether a reply
 * yields a commit is `RefResolverTest`'s question, and asking it again here
 * would only be asking it through more layers.
 */
class ForgeInstallTest {
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
  private val client =
    OkHttpClient
      .Builder()
      .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
      .build()

  private val server =
    MockWebServer().also {
      it.useHttps(serverCertificates.sslSocketFactory())
      it.start()
    }

  private val temporary: File = Files.createTempDirectory("dinfinity-forge").toFile()
  private val root = File(temporary, "dicesets")

  private fun installerThatIsTold(commit: String?) =
    PackageInstaller(root, PackageFetcher(client), SafeExtractor()) { commit }

  @After
  fun close() {
    server.close()
    temporary.deleteRecursively()
  }

  @Test
  fun `a set installed from a forge records the commit it came from`() {
    enqueueArchive()

    val installed = installerThatIsTold(COMMIT).installFrom(source())

    assertTrue("expected an install, got $installed", installed is PackageInstaller.Result.Installed)
    val meta = File((installed as PackageInstaller.Result.Installed).folder, ".meta.json").readText()
    assertTrue(meta, meta.contains("\"commit\": \"$COMMIT\""))
    assertTrue("the archive's own checksum is still what makes it reproducible", meta.contains("\"sha256\""))
  }

  @Test
  fun `a forge that will not say which commit it is still installs the set`() {
    // The commit answers "is there something newer". Not having it costs the
    // update check, not the package.
    enqueueArchive()

    val installed = installerThatIsTold(null).installFrom(source())

    assertTrue("expected an install, got $installed", installed is PackageInstaller.Result.Installed)
    val meta = File((installed as PackageInstaller.Result.Installed).folder, ".meta.json").readText()
    assertFalse("a commit nobody knows should not be written down", meta.contains("\"commit\""))
  }

  private fun source(): InstallSource =
    InstallSource(
      kind = InstallSource.Kind.GitHub,
      archiveUrl = "${server.url("/tarball/main")}",
      reference = "main",
      commitUrl = "${server.url("/commits/main")}",
    )

  private fun enqueueArchive() {
    val bytes = Archives.wellFormed(temporary).readBytes()
    server.enqueue(
      MockResponse
        .Builder()
        .body(
          object : MockResponseBody {
            override val contentLength: Long = bytes.size.toLong()

            override fun writeTo(sink: BufferedSink) {
              sink.write(bytes)
            }
          },
        ).build(),
    )
  }

  private companion object {
    const val COMMIT = "9f2b1c4d5e6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c"
  }
}
