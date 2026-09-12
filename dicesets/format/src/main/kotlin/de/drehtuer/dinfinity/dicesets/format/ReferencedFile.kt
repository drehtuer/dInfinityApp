package de.drehtuer.dinfinity.dicesets.format

/**
 * A path a set file points at, checked before anything tries to open it.
 *
 * This is the rule that makes a package a sandbox: a reference is relative, it
 * stays inside the package, and it ends in something on the allowlist. Nothing
 * downstream re-derives that — everything that opens a file takes one of these
 * (`docs/dice-sets.md`, "Runtime isolation").
 *
 * Backslashes count as separators too. An archive written on Windows can carry
 * `..\..\etc\passwd`, and a check that only knew about `/` would wave it
 * through to a filesystem that does know about both.
 */
@JvmInline
value class ReferencedFile private constructor(
  val path: String,
) {
  /** The lower-case extension, or the empty string when there is none. */
  val extension: String get() = path.substringAfterLast('.', "").lowercase()

  override fun toString(): String = path

  companion object {
    /** Why a path was refused, or `null` when it was not. */
    fun reasonToRefuse(
      raw: String,
      allowed: Set<String>,
    ): String? =
      when {
        raw.isBlank() -> "a file reference cannot be empty"
        raw.startsWith('/') || raw.startsWith('\\') -> "'$raw' is an absolute path"
        DRIVE_LETTER.matches(raw) -> "'$raw' is an absolute path"
        raw.split('/', '\\').any { it == ".." } -> "'$raw' points outside the package"
        raw.split('/', '\\').any { it.isEmpty() } -> "'$raw' is not a path inside the package"
        raw.substringAfterLast('.', "").lowercase() !in allowed ->
          "'$raw' is not one of ${allowed.sorted().joinToString(", ")}"
        else -> null
      }

    /** [raw] as a reference, or `null` when [reasonToRefuse] has something to say. */
    fun parse(
      raw: String,
      allowed: Set<String> = DiceSetLimits.ALLOWED_EXTENSIONS,
    ): ReferencedFile? = if (reasonToRefuse(raw, allowed) == null) ReferencedFile(raw.replace('\\', '/')) else null

    private val DRIVE_LETTER = Regex("^[A-Za-z]:.*")
  }
}
