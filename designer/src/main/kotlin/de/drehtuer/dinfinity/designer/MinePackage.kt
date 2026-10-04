package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator

/**
 * The drawings on this phone, as a dice set anybody could install
 * (`docs/face-designer.md`, "Export details"; `docs/dice-sets.md`).
 *
 * Nothing about the package is special. It is a `diceset.toml` and a folder of
 * atlases, exactly like one somebody wrote by hand, and it goes through
 * `DiceSetValidator` before it is written anywhere or offered to anybody — the
 * app's own output is not a privileged path, for the same reason the bundled
 * set is not one. A package that does not validate is a bug caught here rather
 * than an install failure on somebody else's phone.
 *
 * Building it is pure: drawings and a licence in, a map of paths to bytes out.
 * The only thing in it that needs a device is [AtlasPainter], and a painter
 * that answers null merely costs a die its artwork rather than costing the
 * package its validity.
 */
object MinePackage {
  /** The set id, which is also the folder name it installs under. */
  const val ID: String = DiceSet.PERSONAL_ID

  /** What it is called on the sets screen (design `8c`). */
  const val NAME: String = "My dice"

  /**
   * The version the personal package declares.
   *
   * A fixed one, because nothing compares it: a package installed from a
   * folder on this phone has no source to check for updates against
   * (`docs/dice-sets.md`, "Updates"), and a number that went up every time
   * somebody drew a line would be a version nobody could mean anything by.
   * Whoever publishes the zip is free to edit it — that is what the file is
   * for.
   */
  const val VERSION: String = "1.0.0"

  /** What the details screen shows under the name. */
  const val DESCRIPTION: String = "Dice drawn in the face designer, and tables made from photos, on this phone."

  /**
   * What the details screen shows under the name of a personal set somebody
   * made and named in the face designer — every one of them but "My dice",
   * which is also where the photo tables go (`docs/architecture.md`,
   * decision 79).
   */
  const val NAMED_DESCRIPTION: String = "Dice drawn in the face designer on this phone."

  /** What the zip is called when it is handed to another application. */
  const val FILE_NAME: String = "my-dice.zip"

  /**
   * What the zip of the personal set [id] is called.
   *
   * "My dice" keeps the name it has always gone out under, so a file somebody
   * already shared and the next one they share are the same file; any other
   * personal set goes out under its id, which is a slug and therefore already
   * a file name.
   */
  fun fileNameOf(id: String): String = if (id == ID) FILE_NAME else "$id.zip"

  /** What the details screen says about the personal set [id]. */
  fun descriptionOf(id: String): String = if (id == ID) DESCRIPTION else NAMED_DESCRIPTION

  /** The media type it travels as. */
  const val MEDIA_TYPE: String = "application/zip"

  /**
   * The files of the package [drawings] make, under [license].
   *
   * @param license what goes in the `license` field — a [SetLicense]'s id once
   *   somebody has chosen, and [SetLicense.UNSPECIFIED] until they have. The
   *   *choice* is gated at the share (design `8c`); what is written to disk
   *   before that is honest rather than blank.
   * @param author the name written into the file, or null to leave the field
   *   out. A field that said "You" would be worse than no field at all on
   *   somebody else's phone.
   * @param photos the photographs somebody has made tables of
   *   (`docs/tables.md`, "Your own photo"). Each becomes a `[[table]]` entry
   *   and its picture, and they are last in the parameter list because they
   *   arrived last — a package of nothing but photos is as ordinary as a
   *   package of nothing but dice.
   * @param physical what the dice are made of, written as the `[defaults]`
   *   table every die of the package inherits. It is the one material the
   *   package *does* declare, because it is the one somebody set on purpose
   *   (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
   *   them").
   * @param id which personal set this is — [ID] for "My dice", and the slug a
   *   named one was given otherwise (`docs/architecture.md`, decision 79).
   * @param name what the set is called on the sets screen.
   */
  @Suppress("LongParameterList")
  fun of(
    drawings: List<Draft>,
    license: String,
    author: String?,
    painter: AtlasPainter,
    photos: List<TablePhoto> = emptyList(),
    physical: DieMaterial = DieMaterial(),
    id: String = ID,
    name: String = NAME,
  ): Map<String, ByteArray> {
    val files = mutableMapOf<String, ByteArray>()
    val drawn =
      drawings
        .filterNot(Draft::blank)
        .distinctBy { it.die.id }
    val dice = drawn.map { draft -> withArtwork(draft, painter, files, physical) }
    val finishes = drawn.mapNotNull { draft -> finishOf(draft)?.let { draft.die.id to it } }.toMap()
    val tables =
      photos.distinctBy(TablePhoto::id).map { photo ->
        files[PhotoTable.texturePathOf(photo.id)] = photo.image
        PhotoTable.lookOf(photo.id, photo.name)
      }
    val set =
      DiceSet(
        id = id,
        name = name,
        version = VERSION,
        author = author,
        license = license,
        description = descriptionOf(id),
        dice = dice,
        tables = tables,
      )
    return files + (DiceSetValidator.DICE_SET_FILE to DiceSetToml.write(set, physical, finishes).encodeToByteArray())
  }

  /**
   * The die [draft] was drawn on, pointed at the atlas that was drawn for it.
   *
   * A die whose atlas could not be painted keeps no `texture` line, so it
   * falls back to its printed labels — which is what a die with no artwork is
   * supposed to look like, and a great deal better than a set that refuses to
   * install because one bitmap would not allocate.
   *
   * The colour is deliberately not carried across. A cell is transparent
   * where nobody drew, so the colour under the drawing is the *installing*
   * set's to decide, and the same drawing is meant to work on a black die and
   * on a white one (`docs/face-designer.md`, "Export details").
   *
   * **The finish is** ([finishOf]): what the die is made of and how round it
   * is, laid over the package's own [physical] material. That is what the
   * designer's Material menu and Edges control show, and what **Roll it**
   * throws (`docs/face-designer.md`, "Material and edges").
   */
  private fun withArtwork(
    draft: Draft,
    painter: AtlasPainter,
    files: MutableMap<String, ByteArray>,
    physical: DieMaterial,
  ): Die {
    val die = draft.die
    val png = Atlas.plan(draft)?.let(painter::png)
    val path = DiceSetToml.texturePathOf(die.id)
    if (png != null) files[path] = png
    return Die(
      id = die.id,
      shape = die.shape,
      faces = die.faces,
      read = die.read,
      texturePath = if (png == null) null else path,
      material = finishOf(draft)?.on(physical) ?: physical,
    )
  }

  /**
   * The finish [draft]'s die goes into the package with, or null for none of
   * its own — the package's `[defaults]` then speak for it.
   *
   * What somebody chose, if they chose. Otherwise what the die was copied as,
   * **when that is anything but the standard one**: a die copied from a set of
   * metal dice is a metal die in the designer's menu, and stays one when it is
   * rolled. A die that was standard to begin with carries nothing, so the
   * details screen's translucency stepper goes on reaching it.
   */
  internal fun finishOf(draft: Draft): DieFinish? =
    draft.finish ?: DieFinish.of(draft.die.material).takeUnless { it == DieFinish.STANDARD }
}
