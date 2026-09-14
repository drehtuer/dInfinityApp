package de.drehtuer.dinfinity.core.collection

/**
 * What a collection may contain (`docs/dice-notation.md`, "Export and import").
 *
 * Every one of these is checked before anything is written to the database,
 * because a file from a stranger is not a file to find the limits of by
 * hitting them.
 */
object CollectionLimits {
  /** The format version this app writes, and the only one it reads. */
  const val FORMAT: Int = 1

  /** Rolls per collection. */
  const val MAX_ROLLS: Int = 500

  /** Groups per collection. */
  const val MAX_GROUPS: Int = 50

  /** Bytes per file. Bigger ones are rejected without being parsed. */
  const val MAX_BYTES: Int = 1024 * 1024

  /**
   * Characters in a name, the collection's or a group's or a roll's.
   *
   * Not in `docs/dice-notation.md`'s table, because it is not a limit anybody
   * writing a collection will meet — it is a limit on what a file can do to a
   * screen. A name of a hundred thousand characters is not a name.
   */
  const val MAX_NAME: Int = 100

  /**
   * Characters in an icon.
   *
   * An emoji, which may be several code points — a flag, a skin tone, a
   * profession — but never a paragraph. Icons are restricted to emoji or names
   * from the built-in icon pack, and never image files: collections travel as
   * JSON and carry no binaries.
   */
  const val MAX_ICON: Int = 16

  /** Characters in a formula. The parser's own limits do the rest. */
  const val MAX_FORMULA: Int = 500
}
