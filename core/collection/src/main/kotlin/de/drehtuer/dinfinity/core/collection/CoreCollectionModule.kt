package de.drehtuer.dinfinity.core.collection

/**
 * Marks `:core:collection` as present and wired into the build.
 *
 * The module holds the saved-roll collection format: read, written and
 * validated (`docs/dice-notation.md`, "Export and import"). Pure Kotlin with
 * no Android in it, because deciding whether a file from a stranger is sound
 * needs no device — and because every rule in here deserves a test that runs
 * in milliseconds.
 *
 * Its shape is the import rule made structural. [CollectionReader] turns a
 * file into either a collection that is known to be sound or a list of reasons
 * it is not, and it writes nothing; whatever imports it has no judgement left
 * to make. That is how "an import can never damage what is already there"
 * stops being something every screen has to remember.
 */
object CoreCollectionModule {
  /** This module's Gradle path. */
  const val PATH: String = ":core:collection"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":core:model", ":core:notation")
}
