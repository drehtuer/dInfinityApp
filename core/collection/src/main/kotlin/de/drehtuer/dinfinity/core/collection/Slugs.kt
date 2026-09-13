package de.drehtuer.dinfinity.core.collection

/**
 * The ids a collection uses (`docs/dice-notation.md`, "Export and import").
 *
 * A slug rather than a UUID, because a collection is a file people edit: a
 * community repository of stat blocks is written by hand, and `"group":
 * "thorin"` is something a person can type where a UUID is something a person
 * mistypes. Stable across an export and an import, so a re-import can
 * recognise what it already has.
 */
object Slugs {
  private const val MAX = 64
  private val SHAPE = Regex("[a-z0-9][a-z0-9_-]*")

  /** Whether [text] is an id a collection may use. */
  fun valid(text: String): Boolean = text.length <= MAX && SHAPE.matches(text)

  /**
   * Turns a name into an id, and [taken] into one nothing else has.
   *
   * Used when exporting, because groups in the database are identified by
   * whatever made them — a UUID from the editor, a slug from an earlier
   * import — and a collection should read the same either way. A name that
   * slugs to nothing at all (all emoji, or a language with no ASCII) falls
   * back to [fallback] with a number, because an id is for machines and the
   * name beside it is what a person reads.
   */
  fun of(
    name: String,
    taken: Set<String> = emptySet(),
    fallback: String = "group",
  ): String {
    val base =
      name
        .lowercase()
        .map { if (it.isLetterOrDigit() && it.code < ASCII) it else '-' }
        .joinToString("")
        .trim('-')
        .replace(Regex("-+"), "-")
        .take(MAX)
        .ifEmpty { fallback }
    if (base !in taken) return base
    // A number rather than a random suffix, so exporting the same collection
    // twice gives the same file.
    var n = 2
    while ("$base-$n" in taken) n++
    return "$base-$n"
  }

  /** Above this a character is not something a URL or a hand-typed id should carry. */
  private const val ASCII = 128
}
