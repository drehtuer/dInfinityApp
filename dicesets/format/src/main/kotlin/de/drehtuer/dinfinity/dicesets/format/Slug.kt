package de.drehtuer.dinfinity.dicesets.format

/**
 * The id rule a set, a die and a table all share: lower-case letters, digits
 * and hyphens (`docs/dice-sets.md`).
 *
 * A set's id becomes a folder name on the device, which is the real reason the
 * rule is this narrow. There is nothing in it that a filesystem, a URL or the
 * notation could read as anything but a name.
 */
internal object Slug {
  private val PATTERN = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")

  fun isValid(
    value: String,
    length: IntRange,
  ): Boolean = value.length in length && PATTERN.matches(value)

  /** The rule as a phrase to finish "'x' is not …" with. */
  fun describe(length: IntRange): String =
    "an id of ${length.first} to ${length.last} lower-case letters, digits and hyphens"
}
