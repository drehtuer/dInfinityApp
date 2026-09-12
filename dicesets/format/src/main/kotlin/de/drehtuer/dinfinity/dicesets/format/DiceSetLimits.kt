package de.drehtuer.dinfinity.dicesets.format

/**
 * The numbers a package is held to (`docs/dice-sets.md`, `docs/tables.md`).
 *
 * They are published in the documents, so a change here is a change there in
 * the same pull request (`.claude/CLAUDE.md`). They are also the first line of
 * defence: everything in a package was written by a stranger, and a limit that
 * is checked before anything is allocated is worth more than one checked after
 * (`SECURITY.md`).
 */
object DiceSetLimits {
  private const val BYTES_PER_MIB: Long = 1024L * 1024L

  /** The schema version this app understands. A package newer than this is refused. */
  const val CURRENT_FORMAT: Int = 1

  /** A set file is text; a megabyte of it is already far more than anyone writes. */
  const val MAX_TOML_MIB: Int = 1

  const val MAX_TOML_BYTES: Long = MAX_TOML_MIB * BYTES_PER_MIB

  /**
   * How long a *set* id may be. It becomes a folder name on the device, which
   * is why it has a floor: a one-character directory in `dicesets/` is a
   * mistake waiting to be made.
   */
  val SET_ID_LENGTH: IntRange = 3..40

  /**
   * How long a *die* id may be.
   *
   * Shorter than a set's, because `d2`, `d4` and `d6` are all two characters
   * and are the ids plain notation resolves. A die id names something inside a
   * package rather than a folder on disk.
   */
  val DIE_ID_LENGTH: IntRange = 1..40

  /** The same again for a table id, which also names something inside a package. */
  val TABLE_ID_LENGTH: IntRange = 1..40

  /** How much text fits on a face before it stops being legible on a phone. */
  const val MAX_LABEL_LENGTH: Int = 4

  /** Textures: no side longer than this. Read from the header, before decoding. */
  const val MAX_TEXTURE_PIXELS: Int = 2048

  /** One texture file. */
  const val MAX_TEXTURE_MIB: Int = 4

  const val MAX_TEXTURE_BYTES: Long = MAX_TEXTURE_MIB * BYTES_PER_MIB

  /** Every texture in one package together. */
  const val MAX_PACKAGE_TEXTURE_MIB: Int = 24

  const val MAX_PACKAGE_TEXTURE_BYTES: Long = MAX_PACKAGE_TEXTURE_MIB * BYTES_PER_MIB

  /** How often a table texture may repeat before it is moiré rather than felt. */
  val TILING: IntRange = 1..32

  /** What a package may reference. Anything else is not extracted and not read. */
  val ALLOWED_EXTENSIONS: Set<String> = setOf("toml", "png", "webp", "obj", "md", "txt")

  /** What a `texture`, `floor_texture` or `wall_texture` may point at. */
  val IMAGE_EXTENSIONS: Set<String> = setOf("png", "webp")

  /** A `homepage` is shown as text and opened only on a tap; never over plain http. */
  const val HOMEPAGE_SCHEME: String = "https"
}
