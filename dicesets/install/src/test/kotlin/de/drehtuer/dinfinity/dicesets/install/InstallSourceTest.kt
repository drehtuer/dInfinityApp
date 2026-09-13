package de.drehtuer.dinfinity.dicesets.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which URLs the app will fetch from, and what it fetches
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The forge cases exist so a user can paste the URL they are looking at. They
 * are **not** what makes an install safe: an unknown host is treated as a
 * plain archive and goes through exactly the same extraction and the same
 * validator.
 */
class InstallSourceTest {
  @Test
  fun `a github repository is fetched as a tarball`() {
    val source = InstallSource.of("https://github.com/ada/brass-and-bone")!!
    assertEquals(InstallSource.Kind.GitHub, source.kind)
    assertEquals("https://api.github.com/repos/ada/brass-and-bone/tarball/HEAD", source.archiveUrl)
    assertNull(source.subfolder)
  }

  @Test
  fun `a github tag is fetched as that tag`() {
    val source = InstallSource.of("https://github.com/ada/brass-and-bone/tree/v1.2.0")!!
    assertEquals("v1.2.0", source.reference)
    assertEquals("https://api.github.com/repos/ada/brass-and-bone/tarball/v1.2.0", source.archiveUrl)
  }

  @Test
  fun `a github folder installs from that folder`() {
    val source = InstallSource.of("https://github.com/ada/brass-and-bone/tree/main/sets/skulls")!!
    assertEquals("main", source.reference)
    assertEquals("sets/skulls", source.subfolder)
  }

  @Test
  fun `a gitlab project is fetched through the gitlab api`() {
    val source = InstallSource.of("https://gitlab.com/ada/brass-and-bone/-/tree/main")!!
    assertEquals(InstallSource.Kind.GitLab, source.kind)
    assertEquals(
      "https://gitlab.com/api/v4/projects/ada%2Fbrass-and-bone/repository/archive.tar.gz?sha=main",
      source.archiveUrl,
    )
  }

  @Test
  fun `a self-hosted gitlab is fetched from its own host`() {
    val source = InstallSource.of("https://git.example.org/team/dice/-/tree/main")!!
    assertEquals(InstallSource.Kind.GitLab, source.kind)
    assertEquals(
      "https://git.example.org/api/v4/projects/team%2Fdice/repository/archive.tar.gz?sha=main",
      source.archiveUrl,
    )
  }

  @Test
  fun `a gitlab subgroup keeps its whole path`() {
    val source = InstallSource.of("https://gitlab.com/group/subgroup/dice/-/tree/main")!!
    assertEquals(
      "https://gitlab.com/api/v4/projects/group%2Fsubgroup%2Fdice/repository/archive.tar.gz?sha=main",
      source.archiveUrl,
    )
  }

  @Test
  fun `codeberg is a gitea`() {
    val source = InstallSource.of("https://codeberg.org/ada/brass-and-bone")!!
    assertEquals(InstallSource.Kind.Gitea, source.kind)
    assertEquals("https://codeberg.org/api/v1/repos/ada/brass-and-bone/archive/HEAD.tar.gz", source.archiveUrl)
  }

  @Test
  fun `any https archive is fetched as it is`() {
    listOf(
      "https://example.org/dice/brass.zip",
      "https://example.org/dice/brass.tar.gz",
      "https://example.org/dice/brass.tgz",
    ).forEach { url ->
      val source = InstallSource.of(url)!!
      assertEquals(url, InstallSource.Kind.Archive, source.kind)
      assertEquals(url, source.archiveUrl)
    }
  }

  @Test
  fun `an unknown host that is not an archive is refused before anything is downloaded`() {
    assertNull(InstallSource.of("https://example.org/dice/"))
    assertNull(InstallSource.of("https://example.org/dice/index.html"))
  }

  @Test
  fun `plain http is refused, whatever it points at`() {
    assertNull(InstallSource.of("http://example.org/dice/brass.zip"))
    assertNull(InstallSource.of("http://github.com/ada/brass-and-bone"))
  }

  @Test
  fun `anything that is not a link at all is refused`() {
    listOf("", "   ", "not a url", "file:///etc/passwd", "javascript:alert(1)").forEach { url ->
      assertNull(url, InstallSource.of(url))
    }
  }

  @Test
  fun `a github url with no repository in it is refused`() {
    assertNull(InstallSource.of("https://github.com/ada"))
    assertNull(InstallSource.of("https://github.com/"))
  }

  @Test
  fun `the archive suffixes are the ones the document names`() {
    assertEquals(listOf(".zip", ".tar.gz", ".tgz"), InstallLimits.ARCHIVE_SUFFIXES)
  }

  @Test
  fun `the published limits are these`() {
    assertEquals(64L shl 20, InstallLimits.MAX_DOWNLOAD_BYTES)
    assertEquals(64L shl 20, InstallLimits.MAX_EXTRACTED_BYTES)
    assertEquals(500, InstallLimits.MAX_ENTRIES)
    assertEquals(5, InstallLimits.MAX_REDIRECTS)
    assertEquals(60L, InstallLimits.TIMEOUT.inWholeSeconds)
    assertEquals("https", InstallLimits.SCHEME)
  }
}
