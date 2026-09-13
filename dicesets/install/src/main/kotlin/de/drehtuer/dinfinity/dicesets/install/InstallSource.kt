package de.drehtuer.dinfinity.dicesets.install

import java.net.URI

/**
 * Where a package is being installed from
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The forge cases exist for convenience — browse to a repository, paste the
 * URL, get a package. **They are not what makes an install safe.** The
 * validator is, and it runs identically whatever this says; an unknown host is
 * treated as a plain archive and goes through exactly the same extraction and
 * the same checks. Recognising GitHub buys a nicer URL to paste and nothing
 * else.
 *
 * @param archiveUrl where the bytes are actually fetched from.
 * @param subfolder the folder inside the archive to install from, for a URL
 *   that pointed at one.
 * @param reference the branch, tag or commit the URL named, or [DEFAULT_REF].
 * @param commitUrl the forge endpoint that says which commit [reference] is
 *   at right now, or null for a plain archive, which has no commits to tell
 *   apart ([RefResolver]).
 */
data class InstallSource(
  val kind: Kind,
  val archiveUrl: String,
  val subfolder: String? = null,
  val reference: String = DEFAULT_REF,
  val commitUrl: String? = null,
) {
  /** Which sort of place this is. */
  enum class Kind {
    GitHub,
    GitLab,
    Gitea,
    Archive,
  }

  companion object {
    /** What a forge is asked for when the URL does not name a branch or tag. */
    const val DEFAULT_REF: String = "HEAD"

    private val GITEA_HOSTS = setOf("codeberg.org")

    /**
     * The source [url] names, or `null` when it is not one the app will fetch.
     *
     * Plain `http` is refused here rather than at the socket, because the
     * refusal a user needs to see is "that is not an https link", not a
     * failure three layers down (`docs/dice-sets.md`).
     */
    fun of(url: String): InstallSource? {
      val uri = runCatching { URI(url.trim()) }.getOrNull()
      val host = uri?.host?.lowercase()
      val https = uri?.scheme.equals(InstallLimits.SCHEME, ignoreCase = true)
      if (uri == null || host == null || !https) return null
      val segments =
        uri.path
          .orEmpty()
          .trim('/')
          .split('/')
          .filter(String::isNotEmpty)
      return when {
        host == "github.com" || host == "www.github.com" -> gitHub(url, segments)
        host == "gitlab.com" || segments.contains("-") -> gitLab(url, uri, segments)
        host in GITEA_HOSTS -> gitea(url, uri, segments)
        else -> archive(url)
      }
    }

    /** A repository URL, optionally `/tree/<ref>` and optionally a folder inside it. */
    private fun gitHub(
      url: String,
      segments: List<String>,
    ): InstallSource? {
      if (segments.size < REPO_SEGMENTS) return null
      val (owner, repository) = segments
      val after = segments.drop(REPO_SEGMENTS)
      val reference = if (after.firstOrNull() == "tree") after.getOrNull(1) ?: DEFAULT_REF else DEFAULT_REF
      val folder = after.drop(if (after.firstOrNull() == "tree") 2 else 0).joinToString("/").ifEmpty { null }
      return InstallSource(
        kind = Kind.GitHub,
        archiveUrl = "https://api.github.com/repos/$owner/$repository/tarball/$reference",
        subfolder = folder,
        reference = reference,
        commitUrl = "https://api.github.com/repos/$owner/$repository/commits/$reference",
      ).takeIf { url.isNotBlank() }
    }

    /** GitLab puts everything after the project under `/-/`, self-hosted included. */
    private fun gitLab(
      url: String,
      uri: URI,
      segments: List<String>,
    ): InstallSource? {
      val dash = segments.indexOf("-")
      val project = (if (dash >= 0) segments.take(dash) else segments).joinToString("/")
      if (project.isEmpty()) return null
      val after = if (dash >= 0) segments.drop(dash + 1) else emptyList()
      val reference = if (after.firstOrNull() == "tree") after.getOrNull(1) ?: DEFAULT_REF else DEFAULT_REF
      val folder = after.drop(if (after.firstOrNull() == "tree") 2 else 0).joinToString("/").ifEmpty { null }
      val encoded = project.replace("/", "%2F")
      return InstallSource(
        kind = Kind.GitLab,
        archiveUrl = "https://${uri.host}/api/v4/projects/$encoded/repository/archive.tar.gz?sha=$reference",
        subfolder = folder,
        reference = reference,
        commitUrl = "https://${uri.host}/api/v4/projects/$encoded/repository/commits/$reference",
      ).takeIf { url.isNotBlank() }
    }

    private fun gitea(
      url: String,
      uri: URI,
      segments: List<String>,
    ): InstallSource? {
      if (segments.size < REPO_SEGMENTS) return null
      val (owner, repository) = segments
      val after = segments.drop(REPO_SEGMENTS)
      val reference = if (after.firstOrNull() == "src") after.getOrNull(2) ?: DEFAULT_REF else DEFAULT_REF
      // Gitea answers "which commit is this ref" with a list rather than one
      // commit, and it has no name for HEAD: asked for a branch called HEAD it
      // returns nothing, where asked for no branch at all it returns the
      // default one, which is what HEAD means.
      val branch = if (reference == DEFAULT_REF) "" else "sha=$reference&"
      return InstallSource(
        kind = Kind.Gitea,
        archiveUrl = "https://${uri.host}/api/v1/repos/$owner/$repository/archive/$reference.tar.gz",
        reference = reference,
        commitUrl = "https://${uri.host}/api/v1/repos/$owner/$repository/commits?${branch}limit=1&stat=false",
      ).takeIf { url.isNotBlank() }
    }

    /**
     * Anything else, which must at least look like an archive.
     *
     * Not because the name proves anything — it does not — but because a URL
     * that is plainly a web page is a mistake worth catching before 64 MiB of
     * HTML is downloaded and refused (`docs/dice-sets.md`, step 1).
     */
    private fun archive(url: String): InstallSource? {
      val path = runCatching { URI(url).path.orEmpty() }.getOrElse { "" }
      if (InstallLimits.ARCHIVE_SUFFIXES.none { path.endsWith(it, ignoreCase = true) }) return null
      return InstallSource(kind = Kind.Archive, archiveUrl = url)
    }

    private const val REPO_SEGMENTS = 2
  }
}
