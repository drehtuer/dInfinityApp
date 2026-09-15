package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationMessage
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import java.io.File

/** A file on its way out of the app, which is a name and some bytes. */
class PackageFile(
  val name: String,
  val bytes: ByteArray,
)

/** What came of asking for the personal package as a file (design `8c`). */
sealed interface ExportResult {
  /** Here it is. What happens to it next is the share sheet's business. */
  class Ready(
    val file: PackageFile,
  ) : ExportResult

  /**
   * The package the app itself built does not validate.
   *
   * It should be impossible, and it is reported as a report rather than
   * swallowed precisely because of that: a drawing that produces an invalid
   * set is a bug, and the person looking at it can read the lines and say
   * which one.
   */
  data class Rejected(
    val report: List<ValidationMessage>,
  ) : ExportResult

  /** Nothing has been drawn, so there is no set to export. */
  data object Empty : ExportResult
}

/**
 * The personal set, "My dice" (`docs/face-designer.md`, "Flow", step 5;
 * design option `8c`).
 *
 * It is an ordinary installed package in an ordinary folder, built from the
 * drawings on the phone. Everything that reads dice sets therefore reads it
 * without knowing it is special: the sets list shows it, the details screen
 * shows what is in it, notation resolves `mine:d20`, and removing it is the
 * same tap as removing anybody else's package.
 *
 * **It is built, not accumulated.** The drafts are the record; the folder is a
 * view of them. So there is nothing to keep in step by hand and no way for the
 * two to disagree — a drawing deleted is a die gone from the package at the
 * next reading.
 *
 * @param drafts the drawings, which are what the package is made of.
 * @param root the app's `dicesets/` folder.
 * @param painter the one part of this that needs a device.
 * @param dice every die a draft may name, from the installed catalogue. A
 *   draft stores its die by id rather than carrying a copy, so this is how one
 *   is turned back into a die; a draft whose die is not installed is not in
 *   the package, and its file is kept (`docs/face-designer.md`).
 * @param author what to write in the `author` field, or null for none.
 */
class MineSets(
  private val drafts: DraftStore,
  private val root: File,
  private val painter: AtlasPainter,
  private val dice: () -> List<Die>,
  private val author: () -> String? = { null },
) {
  /**
   * The stamp of the drawings the folder was last built from.
   *
   * In memory rather than on disk, so the first reading in a sitting always
   * rebuilds. That is not laziness: a folder left by an older version of the
   * app was written by a different exporter, and the cheapest way to be sure
   * it says what this version would say is to write it again.
   */
  private var builtFrom: Long = UNBUILT

  /**
   * Writes the personal package if the drawings have moved on since it was
   * last written.
   *
   * Called before the sets folder is read, because that is the moment the
   * answer matters and the only moment it is worth paying for: rasterising
   * every drawn face is far too much to do after every stroke, and a package
   * rebuilt when nobody is looking at the list is a package rebuilt for
   * nobody.
   */
  fun bringUpToDate() {
    val stamp = drafts.stamp()
    if (stamp == builtFrom) return
    builtFrom = stamp
    val drawings = drawings()
    if (drawings.isEmpty()) {
      remove()
      return
    }
    // The licence already on disk, so that a choice somebody made survives the
    // next stroke they draw. A rebuild that reset it to "unspecified" would
    // un-answer a question they had answered.
    build(drawings, licenseOnDisk() ?: SetLicense.UNSPECIFIED)
  }

  /**
   * The personal package as a zip, under [license] (design `8c`).
   *
   * The licence is not a decoration on the way out: it is written into the
   * `diceset.toml` inside the archive, and the same files are written back to
   * the installed folder, so what the author chose is what the sets screen
   * says afterwards and what the next person to install it reads.
   *
   * Validated before it is offered, like every other package
   * (`docs/dice-sets.md`, "Validation").
   */
  fun export(license: SetLicense): ExportResult {
    val drawings = drawings()
    if (drawings.isEmpty()) return ExportResult.Empty
    val files = MinePackage.of(drawings, license.id, author(), painter)
    return when (val checked = DiceSetValidator.validate(PackageFiles.of(files))) {
      is ValidationResult.Rejected -> ExportResult.Rejected(checked.messages)
      is ValidationResult.Valid -> {
        // The folder says the same thing as the file that just left, which is
        // what makes the licence stick.
        install(files)
        builtFrom = drafts.stamp()
        ExportResult.Ready(PackageFile(name = MinePackage.FILE_NAME, bytes = PackageZip.of(files)))
      }
    }
  }

  /** Every drawing that has something on it, as a draft on the die it was drawn on. */
  private fun drawings(): List<Draft> {
    val known = dice().associateBy(Die::id)
    return drafts
      .known()
      .mapNotNull { id -> known[id] }
      .map(drafts::load)
      .filterNot(Draft::blank)
      .sortedBy { it.die.id }
  }

  /** The licence the installed package declares, or null when there is no package. */
  private fun licenseOnDisk(): String? =
    (DiceSetValidator.validate(PackageFiles.of(folder())) as? ValidationResult.Valid)?.set?.license

  /** Builds and installs, or leaves what is there alone. */
  private fun build(
    drawings: List<Draft>,
    license: String,
  ) {
    val files = MinePackage.of(drawings, license, author(), painter)
    if (DiceSetValidator.validate(PackageFiles.of(files)) is ValidationResult.Valid) install(files)
  }

  /**
   * Puts [files] where an installed package lives.
   *
   * Into a staging folder first and swapped in afterwards, the way an install
   * does (`docs/dice-sets.md`, "Installing from a URL or file"): a half-written
   * package is exactly the state that must not exist, and the old one is put
   * back if the swap does not happen. The staging and holding folders begin
   * with a dot, so a reading that catches them mid-swap does not mistake
   * either for a package.
   */
  private fun install(files: Map<String, ByteArray>) {
    val staging = File(root, STAGING)
    val previous = File(root, PREVIOUS)
    staging.deleteRecursively()
    previous.deleteRecursively()
    val written =
      runCatching {
        files.forEach { (path, bytes) ->
          val file = File(staging, path)
          file.parentFile?.mkdirs()
          file.writeBytes(bytes)
        }
      }.isSuccess
    if (!written) {
      staging.deleteRecursively()
      return
    }
    swap(staging, previous)
  }

  private fun swap(
    staging: File,
    previous: File,
  ) {
    val folder = folder()
    if (folder.isDirectory && !folder.renameTo(previous)) {
      staging.deleteRecursively()
      return
    }
    if (staging.renameTo(folder)) {
      previous.deleteRecursively()
    } else {
      previous.renameTo(folder)
      staging.deleteRecursively()
    }
  }

  /** Takes the personal package off the disk, which is what no drawings means. */
  private fun remove() {
    folder().deleteRecursively()
  }

  private fun folder(): File = File(root, MinePackage.ID)

  private companion object {
    /** No reading of the drafts can produce this, so the first one always rebuilds. */
    const val UNBUILT = -1L

    const val STAGING = ".mine.writing"
    const val PREVIOUS = ".mine.previous"
  }
}
