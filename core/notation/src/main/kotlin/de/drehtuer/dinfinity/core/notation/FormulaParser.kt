package de.drehtuer.dinfinity.core.notation

/**
 * Turns the text a player typed into a [Formula], or into one error that
 * points at the characters to blame (`docs/dice-notation.md`).
 *
 * Parsing resolves nothing: it does not know which dice sets are installed and
 * cannot tell whether `d7` exists. That is `RollPlanner`'s job. Keeping the two
 * apart is what lets the formula field validate as it is typed, on a thread
 * that has never heard of storage.
 */
object FormulaParser {
  /** A `d` between a count and its sides with whitespace in the way: `3 d 6`. */
  private val SPACED_DICE = Regex("(?<=[0-9])\\s+[dD]\\s*(?=[0-9%fF])|(?<=[0-9])[dD]\\s+(?=[0-9%fF])")

  /**
   * [text] as a formula, or the first thing wrong with it.
   *
   * One error, not a list. A formula is a single line and the field shows a
   * single squiggle; a cascade of errors caused by the first one would be
   * noise rather than help.
   */
  fun parse(text: String): ParseResult =
    try {
      ParseResult.Parsed(FormulaGrammar(Cursor(text)).formula())
    } catch (failure: ParseFailure) {
      ParseResult.Failed(despaced(text, failure.error) ?: failure.error)
    }

  /** The formula, or `null` when [text] does not parse. */
  fun parseOrNull(text: String): Formula? = (parse(text) as? ParseResult.Parsed)?.formula

  /**
   * Dice written with spaces in them are the one mistake worth guessing at:
   * `3 d 6` is unambiguous, and offering `3d6` is kinder than explaining that
   * whitespace is ignored between tokens but not inside one
   * (`docs/dice-notation.md`, "Error messages").
   */
  private fun despaced(
    text: String,
    original: NotationError,
  ): NotationError? {
    val fixed = text.replace(SPACED_DICE, "d")
    if (fixed == text || parse(fixed) !is ParseResult.Parsed) return null
    return NotationError(
      code = NotationErrorCode.SpacedDice,
      message = "dice are written without spaces in them",
      range = original.range,
      suggestion = fixed,
    )
  }
}

/** What [FormulaParser.parse] came to. */
sealed interface ParseResult {
  /** The formula parsed. It may still fail to resolve against the installed sets. */
  data class Parsed(
    val formula: Formula,
  ) : ParseResult

  /** The formula did not parse, and this is why. */
  data class Failed(
    val error: NotationError,
  ) : ParseResult
}
