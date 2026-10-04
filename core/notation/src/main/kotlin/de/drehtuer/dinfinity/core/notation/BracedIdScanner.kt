package de.drehtuer.dinfinity.core.notation

/**
 * Scans the `{…}` of a braced die: `{skull-d6}`, `{brass:skull-d6}`,
 * `{skull:d6}` (`docs/dice-notation.md`, "A set's own dice").
 *
 * The braces are the whole point. A die id and a modifier are made of the same
 * characters, so `brass:skull-d6kh1` has no reading the parser could find
 * without asking the installed sets where the id stops
 * (`docs/architecture.md`, decision 31). Between braces the `}` says where it
 * stops, so an id is lexed here **without consulting any set**: this checks
 * only that what is written is shaped like an id. Whether a set has it is
 * [DieResolver]'s question, asked when the formula is planned.
 */
internal class BracedIdScanner(
  private val cursor: Cursor,
) {
  /** `braced := "{" (slug ":")? slug "}"`, from the `{` onwards. */
  fun scan(): BracedId {
    val open = cursor.position
    cursor.advance()
    val first = cursor.scanSlug()
    val second = if (cursor.match(':')) cursor.scanSlug() else null
    close(open, second)
    checkShape(first, second, open)
    return if (second == null) BracedId(setRef = null, dieId = first) else BracedId(first, second)
  }

  /** Steps over the `}`, or says why there is none where one belongs. */
  private fun close(
    open: Int,
    second: String?,
  ) {
    if (cursor.match('}')) return
    if (cursor.text.indexOf('}', open) < 0) {
      throw failure(NotationErrorCode.UnclosedBrace, "this '{' has no matching '}'", open..open)
    }
    // There is a `}` further on, so this is a character that does not belong
    // in an id — and the blame goes on it, not on the braces: `{Skull}` is one
    // wrong letter.
    val char = cursor.peek()
    val message =
      if (char == ':' && second != null) {
        "a braced die names at most one set, as in {brass:skull-d6}"
      } else {
        "'$char' cannot be part of a die id, which is lower-case letters, digits and '-'"
      }
    throw failure(NotationErrorCode.BadDieId, message, cursor.here())
  }

  /** Refuses braces that closed around something that is not a die. */
  private fun checkShape(
    first: String,
    second: String?,
    open: Int,
  ) {
    val problem =
      when {
        second == null && first.isEmpty() ->
          NotationErrorCode.EmptyBraces to "the braces are empty; put a die id in them, for example {skull-d6}"

        first.isEmpty() ->
          NotationErrorCode.BadDieId to "a set id goes in front of the ':', for example {brass:$second}"

        second?.isEmpty() == true ->
          NotationErrorCode.BadDieId to "a die id goes after the ':', for example {$first:d6}"

        maxOf(first.length, second?.length ?: 0) > NotationLimits.MAX_ID_LENGTH ->
          NotationErrorCode.BadDieId to "a die or set id is at most ${NotationLimits.MAX_ID_LENGTH} characters"

        else -> null
      } ?: return
    throw failure(problem.first, problem.second, cursor.rangeFrom(open))
  }
}

/**
 * What was between the braces.
 *
 * @param setRef the set written in front of the `:`, or `null` for the set
 *   plain notation resolves against.
 * @param dieId the die's own id, exactly as the set file spells it.
 */
internal data class BracedId(
  val setRef: String?,
  val dieId: String,
)
