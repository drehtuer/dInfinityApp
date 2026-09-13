package de.drehtuer.dinfinity.dicesets.install

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Asks a forge which commit a branch or tag is at
 * (`docs/dice-sets.md`, "Updates").
 *
 * This is what lets "check for updates" tell one `HEAD` from another. The
 * install itself does not need it: the SHA-256 of the archive that arrived is
 * what makes an install reproducible, and it is recorded whatever this says.
 * What a checksum cannot do is answer "is there something newer", because a
 * tarball built twice from the same commit need not be byte-identical — so a
 * commit is asked for as well, and a failure to get one is not a failure to
 * install.
 *
 * **The answer is not trusted.** A forge is a stranger like any other host, so
 * what comes back is checked for being a commit hash and nothing else is read
 * out of the response. The reply is parsed as a tree and its fields taken by
 * hand, the same way a dice set's TOML is (`docs/architecture.md`, decision
 * 33); no deserializer ever sees it.
 */
class RefResolver(
  client: OkHttpClient? = null,
) {
  private val client: OkHttpClient =
    (client ?: OkHttpClient())
      .newBuilder()
      // Same-scheme redirects are followed; a redirect from https to http is
      // not. That is the one hop that matters, and OkHttp can refuse it
      // itself — this is a small JSON GET, not the 64 MiB download that has to
      // count its own bytes.
      .followSslRedirects(false)
      .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .build()

  /** What asking came to. */
  sealed interface Result {
    /** @param sha the full commit hash, as the forge reported it. */
    data class Resolved(
      val sha: String,
    ) : Result

    /** There is nothing to ask: a plain archive has no commits to tell apart. */
    data object NotAForge : Result

    /**
     * The forge could not be asked, or did not answer with a commit.
     *
     * An install carries on regardless. Not knowing the commit costs the
     * update check, not the set.
     */
    data class Failed(
      val reason: String,
    ) : Result
  }

  /** Which commit [source]'s reference is at now. */
  fun resolve(source: InstallSource): Result {
    val url = source.commitUrl ?: return Result.NotAForge
    if (!url.startsWith("${InstallLimits.SCHEME}://", ignoreCase = true)) {
      return Result.Failed("'$url' is not an ${InstallLimits.SCHEME} link")
    }
    return runCatching { ask(url, source.kind) }
      .getOrElse { Result.Failed(it.message ?: "the forge could not be reached") }
  }

  private fun ask(
    url: String,
    kind: InstallSource.Kind,
  ): Result {
    val request =
      Request
        .Builder()
        .url(url)
        .header("Accept", JSON)
        .build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        return Result.Failed("the forge answered ${response.code} for the reference")
      }
      // Peeked rather than read: it stops at the cap instead of demanding
      // exactly that many bytes, and a commit is a few hundred of them.
      val body = response.peekBody(MAX_REPLY_BYTES).string()
      val sha = shaIn(body, kind) ?: return Result.Failed("the forge did not say which commit that is")
      return if (looksLikeACommit(sha)) {
        Result.Resolved(sha)
      } else {
        Result.Failed("'${sha.take(SHOWN)}' is not a commit hash")
      }
    }
  }

  /**
   * The commit in a reply, per forge.
   *
   * Three forges, three shapes, and no way around knowing all three: GitHub
   * answers with a commit whose hash is `sha`, GitLab with one whose hash is
   * `id`, and Gitea with a *list* of commits because its endpoint is "the log
   * from here".
   */
  private fun shaIn(
    body: String,
    kind: InstallSource.Kind,
  ): String? =
    when (kind) {
      InstallSource.Kind.GitHub -> JSONObject(body).optString("sha").ifEmpty { null }
      InstallSource.Kind.GitLab -> JSONObject(body).optString("id").ifEmpty { null }
      InstallSource.Kind.Gitea ->
        JSONArray(body)
          .takeIf { it.length() > 0 }
          ?.optJSONObject(0)
          ?.optString("sha")
          ?.ifEmpty { null }
      InstallSource.Kind.Archive -> null
    }

  /** Forty hex digits, or sixty-four once a forge has moved to SHA-256. */
  private fun looksLikeACommit(sha: String): Boolean =
    sha.length in setOf(SHA1_LENGTH, SHA256_LENGTH) && sha.all { it in "0123456789abcdefABCDEF" }

  private companion object {
    const val JSON = "application/json"

    /** A commit is a few hundred bytes of JSON. This is room to spare. */
    const val MAX_REPLY_BYTES = 256L shl 10

    /** Far shorter than a download: this is one small request to an API. */
    const val TIMEOUT_SECONDS = 15L

    const val SHA1_LENGTH = 40
    const val SHA256_LENGTH = 64

    /** How much of a bad answer is quoted back. Enough to recognise, not to fill a screen. */
    const val SHOWN = 16
  }
}

/**
 * Where the installer gets a commit from.
 *
 * A seam, and a narrow one on purpose: the installer's job is fetch, extract,
 * validate, move, and none of that is the internet. It asks for a commit and
 * is handed one or not, which is also what lets its own tests say "the forge
 * said no" without a forge.
 */
fun interface Commits {
  /** Which commit [source]'s reference is at, or null when there is none to be had. */
  fun of(source: InstallSource): String?

  companion object {
    /** The real one: ask the forge, and shrug if it will not say. */
    fun fromForges(resolver: RefResolver = RefResolver()): Commits =
      Commits { source -> (resolver.resolve(source) as? RefResolver.Result.Resolved)?.sha }
  }
}
