package de.drehtuer.dinfinity.core.notation

/**
 * A position in a formula, with the small scanning moves the grammar is built
 * out of.
 *
 * It is separate from the grammar because the two answer different questions —
 * "what character is here" against "what may appear here" — and because the
 * character-level rules are where the awkward parts of the notation live:
 * whitespace is ignored *between* tokens but not inside one, so `3 d 6` is
 * three tokens and an error, while `3d6 + 1d20` is fine
 * (`docs/dice-notation.md`).
 */
internal class Cursor(
  val text: String,
) {
  var position: Int = 0
    private set

  /** True when there is nothing left but the end of the formula. */
  fun atEnd(): Boolean = position >= text.length

  /** The character here, or `null` at the end. */
  fun peek(): Char? = text.getOrNull(position)

  /** The character [ahead] characters from here, or `null` past the end. */
  fun peekAt(ahead: Int): Char? = text.getOrNull(position + ahead)

  /** Steps over [count] characters. */
  fun advance(count: Int = 1) {
    position = minOf(position + count, text.length)
  }

  /** Steps over whitespace, which never means anything between tokens. */
  fun skipWhitespace() {
    while (peek()?.isWhitespace() == true) advance()
  }

  /** Steps over [char] and says so, or stays put and says no. */
  fun match(char: Char): Boolean = (peek() == char).also { if (it) advance() }

  /**
   * Steps over [word] and says so, ignoring case. Notation is
   * case-insensitive except for set ids, so `KH1` and `kh1` are one thing.
   */
  fun matchWord(word: String): Boolean =
    text.regionMatches(position, word, 0, word.length, ignoreCase = true).also {
      if (it) advance(word.length)
    }

  /** The run of digits here as text, empty when there is none. */
  fun scanDigits(): String {
    val start = position
    while (peek()?.isDigit() == true) advance()
    return text.substring(start, position)
  }

  /** The `[a-z][a-z0-9_-]*` here as text, empty when there is none. */
  fun scanIdentifier(): String {
    val start = position
    if (peek()?.isLowerCaseLetter() != true) return ""
    advance()
    while (peek()?.isIdentifierPart() == true) advance()
    return text.substring(start, position)
  }
}

/** The range from [start] to the cursor, for an error or a node. */
internal fun Cursor.rangeFrom(start: Int): IntRange = start until maxOf(position, start + 1)

/** The single position the cursor is on, clamped inside the text, for an error. */
internal fun Cursor.here(): IntRange = minOf(position, maxOf(text.length - 1, 0)).let { it..it }

private fun Char.isLowerCaseLetter(): Boolean = this in 'a'..'z'

private fun Char.isIdentifierPart(): Boolean = this in 'a'..'z' || this in '0'..'9' || this == '_' || this == '-'

/** Thrown by the grammar and caught by [FormulaParser]; never escapes the module. */
internal class ParseFailure(
  val error: NotationError,
) : RuntimeException(error.message, null, false, false)

/** Builds the failure the grammar throws, so every site reads the same. */
internal fun failure(
  code: NotationErrorCode,
  message: String,
  range: IntRange,
  suggestion: String? = null,
): ParseFailure = ParseFailure(NotationError(code, message, range, suggestion))
