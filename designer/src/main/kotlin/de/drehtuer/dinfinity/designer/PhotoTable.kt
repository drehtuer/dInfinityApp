package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.TableLight
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.dicesets.format.DiceSetLimits

/**
 * One photograph kept as a table look: what it is called, and its bytes.
 *
 * The bytes are already through [PhotoScaling] — nothing downstream of here
 * decides how big a picture is.
 */
data class TablePhoto(
  val id: String,
  val name: String,
  val image: ByteArray,
) {
  /**
   * Compared by what it *is*, not by where its array happens to live.
   *
   * A `data class` with a `ByteArray` in it gets identity equality for free
   * and it is always the wrong answer: two readings of the same photo off the
   * same disk would come back unequal.
   */
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is TablePhoto && id == other.id && name == other.name && image.contentEquals(other.image))

  override fun hashCode(): Int = (id.hashCode() * HASH + name.hashCode()) * HASH + image.contentHashCode()

  private companion object {
    const val HASH = 31
  }
}

/**
 * What a photograph becomes once it is a `[[table]]` in the personal package
 * (`docs/tables.md`, "Your own photo").
 *
 * Plain arithmetic and plain strings, so every decision about a photo table —
 * its id, where its file goes, how it tiles, how many may be kept — is a thing
 * a JVM test can assert on. Nothing here writes anything.
 */
object PhotoTable {
  /** The folder a table's texture lives in, inside the package. */
  const val FOLDER: String = "tables"

  /** Lossy, small, and one of the two kinds the validator can read a header of. */
  const val EXTENSION: String = "webp"

  /** What every photo table's id begins with, so it cannot collide with a drawn one. */
  const val PREFIX: String = "photo-"

  /** The fallback id, for a name with nothing in it a slug can use. */
  const val UNNAMED: String = "photo"

  /**
   * How long a player's own name for a table may be.
   *
   * The same length an id may be ([DiceSetLimits.TABLE_ID_LENGTH]), because
   * the id is made out of the name and a name that cannot fit in one is a name
   * the list would truncate anyway. It is a limit on the player's own typing
   * rather than on a stranger's file, which is why it is here and not in
   * `DiceSetLimits`.
   */
  const val MAX_NAME_LENGTH: Int = 40

  /**
   * How many photos the personal package keeps.
   *
   * Derived rather than chosen: [DiceSetLimits.MAX_PACKAGE_TEXTURE_BYTES]
   * divided by [DiceSetLimits.MAX_TEXTURE_BYTES] is how many textures a
   * package could hold if every one of them were the largest a texture may be.
   * A real photo is a small fraction of that, so the cap almost never bites —
   * what it does is make "the package got too big to validate" a thing that
   * cannot happen from photos alone.
   *
   * The seventh is **refused rather than making room**, which is the opposite
   * of what [DraftStore] does with drafts, and deliberately: a drawing nobody
   * has opened for months is a fair thing to drop, and the table somebody is
   * playing on tonight is not.
   */
  val MAX_PHOTOS: Int = (DiceSetLimits.MAX_PACKAGE_TEXTURE_BYTES / DiceSetLimits.MAX_TEXTURE_BYTES).toInt()

  /** Where the photo with this [id] goes inside the package. */
  fun texturePathOf(id: String): String = "$FOLDER/$id.$EXTENSION"

  /**
   * An id for a table called [name], not one of [taken].
   *
   * The name is slugged the way every other id in the format is spelled —
   * lower-case letters, digits and hyphens — and prefixed, so a photo called
   * "Oak" cannot land on a drawn set's `oak`. A name that slugs to nothing at
   * all (`"漢字"`, `"!!!"`) still gets an id, because refusing somebody's photo
   * over the alphabet they named it in would be a poor reason.
   */
  fun idOf(
    name: String,
    taken: Set<String>,
  ): String {
    val stem = PREFIX + slug(name).ifEmpty { UNNAMED }
    val base = stem.take(DiceSetLimits.TABLE_ID_LENGTH.last).trimEnd('-')
    if (base !in taken) return base
    return generateSequence(2) { it + 1 }
      .map { number -> numbered(base, number) }
      .first { it !in taken }
  }

  /**
   * The look a photo becomes.
   *
   * **Tiling is 1×1, and that is the whole of what a photo table decides about
   * its texture.** Tiling exists so a 512-pixel felt swatch can cover a tray
   * without being a screen-sized image (`docs/tables.md`); a photograph is one
   * picture of one thing, and repeating it eight times across the floor would
   * be a wallpaper rather than the photo somebody chose.
   *
   * The walls keep the neutral grey of the `plain` table and the floor's tint
   * stays white, so the picture is shown as it was taken rather than
   * multiplied by a colour. Sound and light are `felt` and `neutral`: a
   * photograph says nothing about how hard the surface is, and the quietest,
   * least opinionated pair is the honest default. The physics values are the
   * model's own defaults, untouched — a photo changes how the tray looks and
   * nothing about how it rolls.
   */
  fun lookOf(
    id: String,
    name: String,
  ): TableLook =
    TableLook(
      id = id,
      name = name,
      floorTexturePath = texturePathOf(id),
      floorTiling = TableLook.Tiling(acrossShortSide = 1, acrossLongSide = 1),
      floorColorArgb = UNTINTED,
      sound = TableSound.Felt,
      light = TableLight.Neutral,
    )

  /**
   * [text] tidied into something worth showing in a list.
   *
   * Runs of whitespace become one space **first**, so a tab between two words
   * is still a gap between two words; then every remaining control character
   * goes, because a name is written into a TOML file and read back out of it.
   * [DiceSetToml] escapes them either way, but a table with a bell character
   * in the middle of its name is nobody's intention. What is left is trimmed
   * and cut to [MAX_NAME_LENGTH].
   */
  fun nameOf(text: String): String =
    text
      .replace(WHITESPACE, " ")
      .filterNot(Char::isISOControl)
      .trim()
      .take(MAX_NAME_LENGTH)
      .trim()

  /** Whether [text] would name a table, once tidied. */
  fun named(text: String): Boolean = nameOf(text).isNotEmpty()

  /**
   * A name to start the field off with, made out of the file the picker handed
   * back (`design/dInfinity.dc.html`, option `1u`, the upload sheet).
   *
   * The folders go, the extension goes, the separators a camera and a download
   * put in a file name become spaces, and the first letter is lifted — so
   * `oak_table-02.jpg` offers "Oak table 02" rather than a file name. It is a
   * *suggestion*: the field is there to be typed over, and a file called
   * `IMG_20260916.jpg` will suggest something nobody wants to call a table.
   * A name with nothing in it but punctuation suggests nothing at all, which
   * leaves an empty field rather than a table called "-".
   */
  fun suggestionFrom(fileName: String): String {
    val leaf = fileName.substringAfterLast('/').substringAfterLast('\\')
    val stem = if (leaf.count { it == '.' } > 0) leaf.substringBeforeLast('.') else leaf
    val spaced = nameOf(stem.replace(SEPARATORS, " "))
    return spaced.replaceFirstChar(Char::uppercaseChar)
  }

  /** `base-2`, `base-3`, … still inside the id length. */
  private fun numbered(
    base: String,
    number: Int,
  ): String {
    val suffix = "-$number"
    return base.take(DiceSetLimits.TABLE_ID_LENGTH.last - suffix.length).trimEnd('-') + suffix
  }

  private fun slug(name: String): String =
    name
      .lowercase()
      .map { if (it in ALLOWED) it else '-' }
      .joinToString("")
      .replace(RUNS_OF_HYPHEN, "-")
      .trim('-')

  /** White where the floor's own colour would tint the picture. */
  private const val UNTINTED: Int = 0xFFFFFFFF.toInt()

  private val ALLOWED = (('a'..'z') + ('0'..'9')).toSet()
  private val RUNS_OF_HYPHEN = Regex("-+")
  private val SEPARATORS = Regex("[-_.]+")
  private val WHITESPACE = Regex("\\s+")
}
