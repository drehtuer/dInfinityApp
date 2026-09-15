package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.dicesets.install.InstallSource
import de.drehtuer.dinfinity.dicesets.install.RefResolver
import de.drehtuer.dinfinity.feature.sets.LatestCommit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asking a forge which commit a set's ref is at now
 * (`docs/dice-sets.md`, "Updates"; design `9h`).
 *
 * The third and last thing the app asks a server, beside [PackageDownload] and
 * [CollectionDownload], and the only one that reads a *reply* rather than a
 * file. So the rule [RefResolver] keeps matters more than the code here: what
 * comes back is checked for being a commit hash and nothing else is read out of
 * it, parsed as a tree with every field taken by hand, with no deserializer
 * anywhere near it.
 *
 * It lives in `:app` for the reason its two siblings do — an HTTP client is the
 * platform's, and the presenter takes this as a function.
 *
 * Every answer other than a commit is [LatestCommit.Unknown], deliberately.
 * A source that is not a forge, a forge that is down, a reply that is not a
 * commit: to somebody looking at a list of sets these are one sentence —
 * *nothing can be said about this set* — and telling them apart would be three
 * ways of saying it.
 */
class CommitLookup(
  /**
   * Asking one forge, as a function.
   *
   * [RefResolver] reaches a real host and only recognises real forges, so a
   * test cannot stand a server in front of it — the seam is here instead, and
   * what is tested here is the mapping: which answers become a commit and
   * which become "nothing can be said".
   */
  private val resolve: (InstallSource) -> RefResolver.Result = RefResolver()::resolve,
) {
  /** What [source] is at now, or that nothing can be said. */
  suspend fun latest(source: String): LatestCommit =
    withContext(Dispatchers.IO) {
      val known = InstallSource.of(source) ?: return@withContext LatestCommit.Unknown
      when (val result = resolve(known)) {
        is RefResolver.Result.Resolved -> LatestCommit.At(result.sha)
        RefResolver.Result.NotAForge -> LatestCommit.Unknown
        is RefResolver.Result.Failed -> LatestCommit.Unknown
      }
    }
}
