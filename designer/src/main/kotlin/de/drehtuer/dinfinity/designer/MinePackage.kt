package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
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
  const val DESCRIPTION: String = "Dice drawn in the face designer on this phone."

  /** What the zip is called when it is handed to another application. */
  const val FILE_NAME: String = "my-dice.zip"

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
   */
  fun of(
    drawings: List<Draft>,
    license: String,
    author: String?,
    painter: AtlasPainter,
  ): Map<String, ByteArray> {
    val atlases = mutableMapOf<String, ByteArray>()
    val dice =
      drawings
        .filterNot(Draft::blank)
        .distinctBy { it.die.id }
        .map { draft -> withArtwork(draft, painter, atlases) }
    val set =
      DiceSet(
        id = ID,
        name = NAME,
        version = VERSION,
        author = author,
        license = license,
        description = DESCRIPTION,
        dice = dice,
      )
    return atlases + (DiceSetValidator.DICE_SET_FILE to DiceSetToml.write(set).encodeToByteArray())
  }

  /**
   * The die [draft] was drawn on, pointed at the atlas that was drawn for it.
   *
   * A die whose atlas could not be painted keeps no `texture` line, so it
   * falls back to its printed labels — which is what a die with no artwork is
   * supposed to look like, and a great deal better than a set that refuses to
   * install because one bitmap would not allocate.
   *
   * The material is deliberately not carried across. A cell is transparent
   * where nobody drew, so the colour under the drawing is the *installing*
   * set's to decide, and the same drawing is meant to work on a black die and
   * on a white one (`docs/face-designer.md`, "Export details").
   */
  private fun withArtwork(
    draft: Draft,
    painter: AtlasPainter,
    atlases: MutableMap<String, ByteArray>,
  ): Die {
    val die = draft.die
    val png = Atlas.plan(draft)?.let(painter::png)
    val path = DiceSetToml.texturePathOf(die.id)
    if (png != null) atlases[path] = png
    return Die(
      id = die.id,
      shape = die.shape,
      faces = die.faces,
      read = die.read,
      texturePath = if (png == null) null else path,
    )
  }
}
