package de.drehtuer.dinfinity.dicesets.install

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.Severity
import de.drehtuer.dinfinity.dicesets.format.ValidationCode
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import java.io.File

/**
 * One folder in `dicesets/`, as it is *now* rather than as it was when it
 * installed (`docs/dice-sets.md`, "Runtime isolation").
 *
 * The distinction is the reason this type has two cases. A package that passed
 * the validator once is not thereby valid for ever: storage corrupts, a backup
 * restores half a folder, and an app upgrade may validate more strictly than
 * the one that let the package in. So what is on disk is checked again every
 * time it is read, and a package that no longer passes is a [Broken] with the
 * report rather than an absence.
 */
sealed interface InstalledPackage {
  /**
   * The folder's name, which is how everything else addresses this package.
   *
   * Not the id inside the file. The two agree when the app installed it — the
   * installer names the folder after the validated set — and where they
   * disagree it is the folder that notation resolved against, so it is the
   * folder that is authoritative and the disagreement that is the problem.
   */
  val id: String

  /** Where it lives. Every file reference resolves against this and may not escape it. */
  val folder: File

  /** Where it came from, as far as the app recorded. [PackageMeta.Unknown] when it did not. */
  val meta: PackageMeta

  /** The package validates, and this is what it defines. */
  data class Ready(
    override val id: String,
    override val folder: File,
    override val meta: PackageMeta,
    val set: DiceSet,
    val warnings: List<ValidationMessage>,
  ) : InstalledPackage

  /**
   * The package does not validate, and this is everything wrong with it.
   *
   * **The folder is kept.** An update is the way out of this state, and an
   * update needs somewhere to update from — and the report is worth more to
   * the author than the disk space is to anybody (design `6b`).
   */
  data class Broken(
    override val id: String,
    override val folder: File,
    override val meta: PackageMeta,
    val report: List<ValidationMessage>,
  ) : InstalledPackage
}

/**
 * What is installed, read off the disk (`docs/dice-sets.md`).
 *
 * The app's `dicesets/` folder is a list of packages and nothing else, so this
 * is the one thing that needs saying about it: what is in there, and does it
 * still hold up. Whether a package is *enabled* is not asked here — that is
 * the player's opinion, it lives in the database, and a scanner that mixed the
 * two would make "what is on disk" impossible to answer on its own.
 *
 * There is no Android in it, so it is tested as a plain JVM unit test over a
 * temporary folder: the only Android thing about `dicesets/` is *which*
 * directory it is, and that is `:app`'s to say (`docs/architecture.md`,
 * "Storage layout").
 *
 * @param root the app's `dicesets/` folder.
 * @param validate how a package is checked. Injected only so that a test can
 *   make it fail the way a filesystem does — see [read], which has to answer
 *   even when reading the folder throws, and cannot be asked to by any
 *   arrangement of files.
 */
class InstalledSets(
  private val root: File,
  private val validate: (PackageFiles) -> ValidationResult = DiceSetValidator::validate,
) {
  /**
   * Every package in the folder, in a stable order.
   *
   * Sorted by id rather than by whatever order the filesystem hands back,
   * because a list that reshuffles itself between two readings of the same
   * unchanged disk is a list nobody can use in a UI.
   *
   * A `dicesets/` folder that does not exist yet is not an error: it is a
   * fresh install, and the answer is that nothing is installed.
   */
  fun scan(): List<InstalledPackage> =
    root
      .listFiles()
      .orEmpty()
      .filter { it.isDirectory && !it.name.startsWith(".") }
      .map(::read)
      .sortedBy { it.id }

  /**
   * One folder, validated.
   *
   * Everything here that can be wrong is wrong on a device somebody owns, so
   * none of it throws: an unreadable folder is a [InstalledPackage.Broken]
   * with a line saying so, which is a thing the screen can show and the player
   * can act on.
   */
  fun read(folder: File): InstalledPackage {
    val id = folder.name
    val meta = metaIn(folder)
    return when (val validated = runCatching { validate(PackageFiles.of(folder)) }.getOrNull()) {
      null -> broken(id, folder, meta, listOf(unreadable(id)))
      is ValidationResult.Rejected -> broken(id, folder, meta, validated.messages)
      is ValidationResult.Valid -> named(id, folder, meta, validated)
    }
  }

  /**
   * The one package called [id], or null when nothing is installed under that
   * name.
   *
   * Matched against what is actually in the folder rather than joined onto it,
   * for the reason [remove] gives: an id reaches this from a screen, having
   * come from a folder name that came from an archive, and a `..` or a
   * separator must not be able to point it anywhere else.
   */
  fun find(id: String): InstalledPackage? =
    root
      .listFiles()
      .orEmpty()
      .firstOrNull { it.isDirectory && it.name == id }
      ?.let(::read)

  /**
   * Takes a package off the disk (`docs/architecture.md`: uninstall deletes
   * the folder and the registry row — this is the folder half).
   *
   * **Only ever a direct child of [root], named exactly.** An id arrives here
   * from a screen, and a screen's idea of an id came from a folder name that
   * came from an archive: the one thing that must not be possible is for
   * `..`, a separator or an absolute path to turn a remove into a recursive
   * delete of somewhere else. So the id is matched against what is actually in
   * the folder rather than joined onto it.
   *
   * @return true when there is no longer a package under [id], which includes
   *   there never having been one. The caller's next question is the same
   *   either way.
   */
  fun remove(id: String): Boolean {
    val folder = root.listFiles().orEmpty().firstOrNull { it.isDirectory && it.name == id } ?: return true
    return folder.deleteRecursively()
  }

  /**
   * A valid package, if the set inside it is the one this folder claims to be.
   *
   * A folder called `brass` holding a set that calls itself something else is
   * not a working package: notation resolves `brass:d6` by folder, so the die
   * it found would come from a set the player never named. The app cannot have
   * produced it — the installer names the folder after the validated id — so
   * it is either a hand-edited package or a tampered one, and the honest answer
   * to both is the same.
   */
  private fun named(
    id: String,
    folder: File,
    meta: PackageMeta,
    validated: ValidationResult.Valid,
  ): InstalledPackage =
    if (validated.set.id == id) {
      InstalledPackage.Ready(id, folder, meta, validated.set, validated.warnings)
    } else {
      broken(id, folder, meta, listOf(misnamed(id, validated.set.id)))
    }

  private fun broken(
    id: String,
    folder: File,
    meta: PackageMeta,
    report: List<ValidationMessage>,
  ): InstalledPackage = InstalledPackage.Broken(id, folder, meta, report)

  private fun metaIn(folder: File): PackageMeta =
    runCatching { File(folder, PackageMeta.FILE_NAME).readText() }
      .map(PackageMeta::read)
      .getOrDefault(PackageMeta.Unknown)

  private fun unreadable(id: String) =
    ValidationMessage(
      severity = Severity.Error,
      code = ValidationCode.MissingFile,
      text = "the folder '$id' could not be read",
      file = id,
    )

  private fun misnamed(
    id: String,
    claimed: String,
  ) = ValidationMessage(
    severity = Severity.Error,
    code = ValidationCode.BadSlug,
    text = "the folder is '$id' but the set inside it calls itself '$claimed'",
    file = "$id/${DiceSetValidator.DICE_SET_FILE}",
  )
}
