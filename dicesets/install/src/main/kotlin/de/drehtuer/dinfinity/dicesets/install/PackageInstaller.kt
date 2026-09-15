package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import java.io.File

/**
 * Installs a package, or leaves nothing behind
 * (`docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The order is the whole design. Fetch into a temporary folder, extract into
 * another, validate *there*, and only then move the folder into place. At no
 * point is anything half-installed: a failure anywhere deletes the temporary
 * work and the app is exactly as it was. There is no partially installed
 * state, which is rule 2 of `docs/dice-sets.md` and is why it is expressed as
 * a sequence rather than as a promise.
 *
 * @param root the app's `dicesets/` folder (`docs/architecture.md`).
 */
class PackageInstaller(
  private val root: File,
  private val fetcher: PackageFetcher = PackageFetcher(),
  private val extractor: SafeExtractor = SafeExtractor(),
  private val commits: Commits = Commits.fromForges(),
) {
  /** What an install came to. */
  sealed interface Result {
    /**
     * The package is installed.
     *
     * @param replaced true when a package with this id was already there and
     *   has been swapped for this one.
     */
    data class Installed(
      val set: DiceSet,
      val folder: File,
      val warnings: List<ValidationMessage> = emptyList(),
      val replaced: Boolean = false,
    ) : Result

    /** The package did not install, and nothing was written. */
    data class Failed(
      val reason: String,
      val report: List<ValidationMessage> = emptyList(),
    ) : Result
  }

  /** Installs from [url], which may be a forge, an archive, or nothing the app fetches. */
  fun installFrom(url: String): Result {
    val source = InstallSource.of(url) ?: return Result.Failed("'$url' is not a link this app installs from")
    return installFrom(source, url)
  }

  /**
   * The same, from a source already recognised.
   *
   * @param from what to record as where this came from, which is the URL the
   *   user actually pasted rather than the API endpoint it was turned into.
   */
  fun installFrom(
    source: InstallSource,
    from: String = source.archiveUrl,
  ): Result {
    val workspace = temporaryFolder()
    return try {
      when (val downloaded = fetcher.fetch(source.archiveUrl, workspace)) {
        is PackageFetcher.Result.Failed -> Result.Failed(downloaded.reason)
        // Nothing here asks to be cancelled — this path takes no `cancelled`
        // — so reaching it would mean the fetcher had stopped for a reason it
        // was never given. Said rather than swallowed.
        PackageFetcher.Result.Cancelled -> Result.Failed("the download was stopped")
        is PackageFetcher.Result.Downloaded -> {
          val commit = commits.of(source)
          install(
            archive = downloaded.file,
            workspace = workspace,
            subfolder = source.subfolder,
            // A forge that cannot be asked does not stop an install. The
            // archive's own SHA-256 is what makes it reproducible; the commit
            // is what the update check in 4.4 needs to tell one HEAD from
            // another, and not having it costs that and nothing else
            // (`docs/dice-sets.md`, "Updates").
            identity =
              PackageMeta(
                source = from,
                sha256 = downloaded.sha256,
                commit = commit,
                // Only kept for an archive: a set from a forge is told apart
                // by its commit, and two answers to one question are two
                // things to keep in step.
                etag = downloaded.etag.takeIf { commit == null },
                lastModified = downloaded.lastModified.takeIf { commit == null },
              ),
          )
        }
      }
    } finally {
      workspace.deleteRecursively()
    }
  }

  /** Installs from an archive already on the device, picked with the file picker. */
  fun installFrom(
    archive: File,
    from: String = archive.name,
  ): Result {
    val workspace = temporaryFolder()
    return try {
      install(archive, workspace, subfolder = null, identity = PackageMeta(source = from))
    } finally {
      workspace.deleteRecursively()
    }
  }

  private fun install(
    archive: File,
    workspace: File,
    subfolder: String?,
    identity: PackageMeta,
  ): Result =
    when (val extracted = extractor.extract(archive, workspace, subfolder)) {
      is ExtractionResult.Refused -> Result.Failed("${extracted.reason}: ${extracted.detail}")
      is ExtractionResult.Extracted -> validateAndMove(extracted.root, identity)
    }

  private fun validateAndMove(
    folder: File,
    identity: PackageMeta,
  ): Result =
    when (val validated = DiceSetValidator.validate(PackageFiles.of(folder))) {
      is ValidationResult.Rejected ->
        Result.Failed("the package did not pass validation", validated.messages)
      is ValidationResult.Valid -> move(folder, validated, identity)
    }

  /**
   * Moves whatever is installed under this id out of the way, so that a
   * failure from here on leaves the old package rather than neither.
   *
   * Whether the move worked is the whole question. Taken for granted, a
   * rename that quietly failed would leave the old folder sitting where the
   * new one is about to be written, and the promise this method's name makes
   * would be broken in the one case it exists for.
   *
   * A folder left over from an install that was interrupted is cleared first.
   * Without that, one crash at the wrong moment would block every future
   * install of that set, with a message about a package nobody can see.
   */
  private fun movedAside(
    destination: File,
    id: String,
  ): Aside {
    if (!destination.exists()) return Aside.Moved(null)
    val aside = File(root, "$id$REPLACING_SUFFIX")
    return when {
      aside.exists() && !aside.deleteRecursively() ->
        Aside.Stuck("an earlier install of '$id' left a folder behind that cannot be removed")
      !destination.renameTo(aside) ->
        Aside.Stuck("the copy of '$id' already installed could not be moved aside")
      else -> Aside.Moved(aside)
    }
  }

  /**
   * Puts the old package back after a failed replacement, and says what
   * actually happened.
   *
   * The one thing this must not do is promise that the old package is safe
   * when putting it back is what failed. A user who is told their set is
   * untouched will not go looking for it.
   *
   * Not private, because the case worth being sure about — the restore itself
   * failing — needs a filesystem that refuses a rename, and arranging that
   * around a whole install is harder than arranging it around this.
   */
  internal fun restored(
    previous: File?,
    destination: File,
  ): String =
    when {
      previous == null -> "the package could not be moved into place"
      previous.renameTo(destination) ->
        "the package could not be moved into place; the one already installed is untouched"
      else ->
        "the package could not be moved into place, and the copy already installed could not be put back; " +
          "it is in '${previous.name}'"
    }

  /** What became of the package that was already installed under this id. */
  private sealed interface Aside {
    /** Out of the way, or there was nothing there. */
    data class Moved(
      val folder: File?,
    ) : Aside

    /** It could not be moved, so the install stops before writing anything. */
    data class Stuck(
      val reason: String,
    ) : Aside
  }

  /**
   * Moves the validated folder into place, replacing whatever was there.
   *
   * The old folder is moved aside first and deleted afterwards, so a failure
   * halfway leaves the *old* package installed rather than neither.
   */
  private fun move(
    folder: File,
    validated: ValidationResult.Valid,
    identity: PackageMeta,
  ): Result {
    val destination = File(root, validated.set.id)
    root.mkdirs()
    val previous =
      when (val aside = movedAside(destination, validated.set.id)) {
        is Aside.Stuck -> return Result.Failed(aside.reason)
        is Aside.Moved -> aside.folder
      }
    if (!folder.renameTo(destination) && !folder.copyRecursivelyTo(destination)) {
      return Result.Failed(restored(previous, destination))
    }
    previous?.deleteRecursively()
    File(destination, META_FILE).writeText(identity.beside(validated.set))
    return Result.Installed(
      set = validated.set,
      folder = destination,
      warnings = validated.warnings,
      replaced = previous != null,
    )
  }

  private fun temporaryFolder(): File =
    File(root.parentFile ?: root, "install-${System.nanoTime()}").also { it.mkdirs() }

  private companion object {
    /** Where the source, the checksum and the install time live (`docs/architecture.md`). */
    const val META_FILE = PackageMeta.FILE_NAME

    /** Where a package being replaced waits until the new one is in place. */
    const val REPLACING_SUFFIX = ".replacing"

    /** A rename across filesystems fails; a copy is the fallback, not the plan. */
    fun File.copyRecursivelyTo(destination: File): Boolean =
      runCatching { copyRecursively(destination, overwrite = true) }.getOrDefault(false)
  }
}

/**
 * This note, finished off with what the package turned out to be.
 *
 * The version and the install time are the installer's to fill in rather than
 * the caller's: one is read out of the package that just validated, and the
 * other is *now* by definition.
 *
 * Written through [PackageMeta] rather than assembled as text, and the
 * escaping is the reason: a source string is whatever the player pasted, and
 * building the JSON by hand meant handling backslashes and quotes and nothing
 * else — so a URL with a control character in it wrote a file that could not
 * be read back.
 */
private fun PackageMeta.beside(set: DiceSet): String =
  copy(version = set.version, installedAtEpochMs = System.currentTimeMillis()).asJson()
