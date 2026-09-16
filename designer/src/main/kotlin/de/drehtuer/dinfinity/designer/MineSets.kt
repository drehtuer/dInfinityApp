package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.TableLook
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
 * What came of making a table out of a photograph
 * (`docs/tables.md`, "Your own photo").
 *
 * Every refusal is a *reason*, never a silent nothing: the photo was chosen on
 * purpose and the player is owed an answer. [Rejected] carries the validator's
 * own report, because a photo is written into the personal package and the
 * package is then checked exactly as a downloaded one is — so the lines that
 * come back are the lines a failed install would show.
 */
sealed interface PhotoResult {
  /** It is in the package, and this is the look the picker will list. */
  data class Added(
    val look: TableLook,
  ) : PhotoResult

  /** The package the photo would have made does not validate, and here is why. */
  data class Rejected(
    val report: List<ValidationMessage>,
  ) : PhotoResult

  /** [PhotoTable.MAX_PHOTOS] are already kept; one has to go before another fits. */
  data class NoRoom(
    val kept: Int,
  ) : PhotoResult

  /** There is no name to call it by. */
  data object Unnamed : PhotoResult

  /** The disk would not take it, and nothing was left behind. */
  data object NotWritten : PhotoResult
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
 * @param photos the photographs somebody has made tables of. The second record
 *   this package is built from, beside the drafts, and kept apart from the
 *   folder for the same reason they are: the package is a view of both
 *   (`docs/tables.md`, "Your own photo").
 */
@Suppress("LongParameterList")
class MineSets(
  private val drafts: DraftStore,
  private val root: File,
  private val painter: AtlasPainter,
  private val dice: () -> List<Die>,
  private val photos: PhotoStore,
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
   * Where the package goes, and the rule that a half-written one never exists
   * ([PackageFolder]).
   */
  private val into = PackageFolder(root, MinePackage.ID)

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
    val stamp = stamp()
    if (stamp == builtFrom) return
    builtFrom = stamp
    val drawings = drawings()
    val pictures = photos.photos()
    if (drawings.isEmpty() && pictures.isEmpty()) {
      into.remove()
      return
    }
    // The licence already on disk, so that a choice somebody made survives the
    // next stroke they draw. A rebuild that reset it to "unspecified" would
    // un-answer a question they had answered.
    build(drawings, pictures, licenseOnDisk() ?: SetLicense.UNSPECIFIED)
  }

  /**
   * Makes a table out of a photograph that has already been cut down to size
   * ([PhotoScaling]), or says why it could not.
   *
   * The order is the whole of it, and it is the order an install follows. The
   * picture is written to the store, the *whole* personal package is rebuilt
   * from the drawings and every photo including this one, and that package goes
   * through `DiceSetValidator` — the same validator, with no argument to say
   * the app wrote it. Only a package that comes back valid is installed.
   *
   * A package that does not validate takes the photo back out of the store
   * again, so a refusal leaves the phone exactly as it was. That matters more
   * here than anywhere else in this class: the store is the record the package
   * is rebuilt from, so a photo left behind after a refusal would make *every
   * later* rebuild fail, taking the drawn dice down with it.
   */
  fun addPhoto(
    name: String,
    image: ByteArray,
  ): PhotoResult {
    val called = PhotoTable.nameOf(name)
    if (called.isEmpty()) return PhotoResult.Unnamed
    if (!photos.hasRoom()) return PhotoResult.NoRoom(photos.limit)
    val id = PhotoTable.idOf(called, photos.ids())
    val stored = photos.add(TablePhoto(id = id, name = called, image = image))
    return if (stored) installed(id, called) else PhotoResult.NotWritten
  }

  /**
   * Takes a photo table off the phone.
   *
   * The picture goes and the package is rebuilt without it, which is what
   * makes this the same operation as deleting a drawing: there is one record,
   * and the folder follows it.
   */
  fun removePhoto(id: String) {
    if (id !in photos.ids()) return
    photos.forget(id)
    bringUpToDate()
  }

  /** The package [id] would make, validated and installed, or the reason it was not. */
  private fun installed(
    id: String,
    called: String,
  ): PhotoResult {
    val files = files(drawings(), photos.photos(), licenseOnDisk() ?: SetLicense.UNSPECIFIED)
    val checked = DiceSetValidator.validate(PackageFiles.of(files))
    if (checked is ValidationResult.Rejected) {
      photos.forget(id)
      return PhotoResult.Rejected(checked.messages)
    }
    if (!into.install(files)) {
      photos.forget(id)
      return PhotoResult.NotWritten
    }
    builtFrom = stamp()
    return PhotoResult.Added(PhotoTable.lookOf(id, called))
  }

  /**
   * One number over both records.
   *
   * Multiplied rather than added, so that a stroke drawn and a photo deleted
   * in the same moment cannot cancel each other out into "nothing changed".
   */
  private fun stamp(): Long = drafts.stamp() * STAMP_MIX + photos.stamp()

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
    val pictures = photos.photos()
    if (drawings.isEmpty() && pictures.isEmpty()) return ExportResult.Empty
    val files = files(drawings, pictures, license.id)
    return when (val checked = DiceSetValidator.validate(PackageFiles.of(files))) {
      is ValidationResult.Rejected -> ExportResult.Rejected(checked.messages)
      is ValidationResult.Valid -> {
        // The folder says the same thing as the file that just left, which is
        // what makes the licence stick.
        into.install(files)
        builtFrom = stamp()
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
    (DiceSetValidator.validate(PackageFiles.of(into.folder)) as? ValidationResult.Valid)?.set?.license

  /** Builds and installs, or leaves what is there alone. */
  private fun build(
    drawings: List<Draft>,
    pictures: List<TablePhoto>,
    license: String,
  ) {
    val files = files(drawings, pictures, license)
    if (DiceSetValidator.validate(PackageFiles.of(files)) is ValidationResult.Valid) into.install(files)
  }

  /** The package's files, from both records at once. */
  private fun files(
    drawings: List<Draft>,
    pictures: List<TablePhoto>,
    license: String,
  ): Map<String, ByteArray> = MinePackage.of(drawings, license, author(), painter, pictures)

  private companion object {
    /** No reading of the drafts can produce this, so the first one always rebuilds. */
    const val UNBUILT = -1L

    /** An odd multiplier, so two records' changes cannot cancel out. */
    const val STAMP_MIX = 31L
  }
}
