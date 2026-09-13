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
    val workspace = temporaryFolder()
    return try {
      when (val downloaded = fetcher.fetch(source.archiveUrl, workspace)) {
        is PackageFetcher.Result.Failed -> Result.Failed(downloaded.reason)
        is PackageFetcher.Result.Downloaded ->
          install(downloaded.file, workspace, source.subfolder, Identity(url, downloaded.sha256))
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
      install(archive, workspace, subfolder = null, identity = Identity(from, sha256 = null))
    } finally {
      workspace.deleteRecursively()
    }
  }

  private fun install(
    archive: File,
    workspace: File,
    subfolder: String?,
    identity: Identity,
  ): Result =
    when (val extracted = extractor.extract(archive, workspace, subfolder)) {
      is ExtractionResult.Refused -> Result.Failed("${extracted.reason}: ${extracted.detail}")
      is ExtractionResult.Extracted -> validateAndMove(extracted.root, identity)
    }

  private fun validateAndMove(
    folder: File,
    identity: Identity,
  ): Result =
    when (val validated = DiceSetValidator.validate(PackageFiles.of(folder))) {
      is ValidationResult.Rejected ->
        Result.Failed("the package did not pass validation", validated.messages)
      is ValidationResult.Valid -> move(folder, validated, identity)
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
    identity: Identity,
  ): Result {
    val destination = File(root, validated.set.id)
    root.mkdirs()
    val previous = if (destination.exists()) File(root, "${validated.set.id}.replacing") else null
    previous?.let { destination.renameTo(it) }
    if (!folder.renameTo(destination) && !folder.copyRecursivelyTo(destination)) {
      previous?.renameTo(destination)
      return Result.Failed("the package could not be moved into place")
    }
    previous?.deleteRecursively()
    File(destination, META_FILE).writeText(identity.asJson(validated.set))
    return Result.Installed(
      set = validated.set,
      folder = destination,
      warnings = validated.warnings,
      replaced = previous != null,
    )
  }

  private fun temporaryFolder(): File =
    File(root.parentFile ?: root, "install-${System.nanoTime()}").also { it.mkdirs() }

  /** Where a package came from and what arrived, recorded beside it. */
  private data class Identity(
    val source: String,
    val sha256: String?,
  ) {
    fun asJson(set: DiceSet): String =
      buildString {
        append("{\n")
        append("  \"source\": \"${source.escaped()}\",\n")
        sha256?.let { append("  \"sha256\": \"$it\",\n") }
        append("  \"version\": \"${set.version.escaped()}\",\n")
        append("  \"installedAt\": ${System.currentTimeMillis()}\n")
        append("}\n")
      }

    private fun String.escaped(): String = replace("\\", "\\\\").replace("\"", "\\\"")
  }

  private companion object {
    /** Where the source, the checksum and the install time live (`docs/architecture.md`). */
    const val META_FILE = ".meta.json"

    /** A rename across filesystems fails; a copy is the fallback, not the plan. */
    fun File.copyRecursivelyTo(destination: File): Boolean =
      runCatching { copyRecursively(destination, overwrite = true) }.getOrDefault(false)
  }
}
