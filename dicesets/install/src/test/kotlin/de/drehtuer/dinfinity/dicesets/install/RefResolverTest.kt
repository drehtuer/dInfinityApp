package de.drehtuer.dinfinity.dicesets.install

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Asking a forge which commit a branch is at (`docs/dice-sets.md`, "Updates").
 *
 * Against a real HTTPS server, like every other test that talks to a stranger
 * in this module: the rules worth asserting — https only, an answer that is
 * not a commit refused, a forge that is down costing the update check and
 * nothing else — are about what comes back over a socket.
 *
 * Robolectric because the reply is parsed with the platform's own JSON reader,
 * which is a stub in a plain JVM test.
 */
@RunWith(RobolectricTestRunner::class)
class RefResolverTest {
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

  private val resolver =
    RefResolver(
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
  fun `a plain archive has no commits to tell apart`() {
    val archive = InstallSource.of("https://example.org/dice/brass.zip")

    assertEquals(RefResolver.Result.NotAForge, resolver.resolve(requireNotNull(archive)))
    assertEquals("nothing should have been asked", 0, server.requestCount)
  }

  @Test
  fun `GitHub answers with a commit whose hash is called sha`() {
    server.enqueue(MockResponse(body = """{"sha": "$COMMIT", "commit": {"message": "a set"}}"""))

    val resolved = resolved(source(InstallSource.Kind.GitHub))

    assertEquals(COMMIT, resolved.sha)
  }

  @Test
  fun `GitLab calls the same thing id`() {
    server.enqueue(MockResponse(body = """{"id": "$COMMIT", "title": "a set"}"""))

    assertEquals(COMMIT, resolved(source(InstallSource.Kind.GitLab)).sha)
  }

  @Test
  fun `Gitea answers with a list, because its endpoint is the log from here`() {
    server.enqueue(MockResponse(body = """[{"sha": "$COMMIT"}, {"sha": "$OLDER"}]"""))

    assertEquals("the newest commit is the one at the top", COMMIT, resolved(source(InstallSource.Kind.Gitea)).sha)
  }

  @Test
  fun `a forge with no commits at all has nothing to report`() {
    server.enqueue(MockResponse(body = "[]"))

    assertTrue(failed(source(InstallSource.Kind.Gitea)).reason.contains("did not say"))
  }

  @Test
  fun `a sixty-four character hash is a commit too, for a forge that has moved on`() {
    val sha256 = "a".repeat(64)
    server.enqueue(MockResponse(body = """{"sha": "$sha256"}"""))

    assertEquals(sha256, resolved(source(InstallSource.Kind.GitHub)).sha)
  }

  @Test
  fun `an answer that is not a commit hash is refused rather than recorded`() {
    // A forge is a stranger like any other host. What comes back is checked
    // for being a commit, and nothing else in the reply is read at all.
    server.enqueue(MockResponse(body = """{"sha": "../../etc/passwd"}"""))

    val failure = failed(source(InstallSource.Kind.GitHub))

    assertTrue(failure.reason, failure.reason.contains("not a commit hash"))
  }

  @Test
  fun `a reply that is not JSON is a failure, not a crash`() {
    server.enqueue(MockResponse(body = "<html>signed in?</html>"))

    assertTrue(failed(source(InstallSource.Kind.GitHub)).reason.isNotBlank())
  }

  @Test
  fun `a forge that says no leaves the reason in the message`() {
    server.enqueue(MockResponse(code = 404, body = "no such repository"))

    assertTrue(failed(source(InstallSource.Kind.GitHub)).reason.contains("404"))
  }

  @Test
  fun `plain http is refused without a request being made`() {
    val plain = source(InstallSource.Kind.GitHub).copy(commitUrl = "http://example.org/api/commits/main")

    assertTrue(failed(plain).reason.contains("https"))
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `a forge nobody can reach costs the update check and not the set`() {
    // The archive's own SHA-256 is what makes an install reproducible. This
    // only ever answered "is there something newer", so failing to get it is
    // a refusal to guess rather than a refusal to install.
    server.close()

    assertTrue(failed(source(InstallSource.Kind.GitHub)).reason.isNotBlank())
  }

  @Test
  fun `a kind with no commits of its own reads nothing out of a reply`() {
    // `InstallSource` is a data class anyone can `copy`, so an archive that
    // somehow carries a forge endpoint must not have its reply read as one.
    server.enqueue(MockResponse(body = """{"sha": "$COMMIT"}"""))

    val archive = source(InstallSource.Kind.GitHub).copy(kind = InstallSource.Kind.Archive)

    assertTrue(failed(archive).reason.contains("did not say"))
  }

  @Test
  fun `the installer is handed the commit when the forge gives one`() {
    server.enqueue(MockResponse(body = """{"sha": "$COMMIT"}"""))

    assertEquals(COMMIT, Commits.fromForges(resolver).of(source(InstallSource.Kind.GitHub)))
  }

  @Test
  fun `and is handed nothing when it does not`() {
    server.enqueue(MockResponse(code = 503, body = "later"))

    assertNull(Commits.fromForges(resolver).of(source(InstallSource.Kind.GitHub)))
  }

  private fun source(kind: InstallSource.Kind): InstallSource =
    InstallSource(
      kind = kind,
      archiveUrl = "${server.url("/archive.tar.gz")}",
      reference = "main",
      commitUrl = "${server.url("/commits/main")}",
    )

  private fun resolved(source: InstallSource): RefResolver.Result.Resolved {
    val result = resolver.resolve(source)
    assertTrue("expected a commit, got $result", result is RefResolver.Result.Resolved)
    return result as RefResolver.Result.Resolved
  }

  private fun failed(source: InstallSource): RefResolver.Result.Failed {
    val result = resolver.resolve(source)
    assertTrue("expected a refusal, got $result", result is RefResolver.Result.Failed)
    return result as RefResolver.Result.Failed
  }

  private companion object {
    const val COMMIT = "9f2b1c4d5e6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c"
    const val OLDER = "0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5e4d3c2b1a"
  }
}
