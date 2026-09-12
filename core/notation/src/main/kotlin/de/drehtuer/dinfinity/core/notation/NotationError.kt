package de.drehtuer.dinfinity.core.notation

/**
 * Something wrong with a formula, pointing at the characters that caused it
 * (`docs/dice-notation.md`, "Error messages").
 *
 * The range is what the field draws its squiggle over and what design option
 * 9c shows underneath. It is always a range of the formula *as typed*, so it
 * survives being handed between the parser, the planner and the screen.
 *
 * @param range the offending characters, as offsets into the formula.
 * @param suggestion a formula to offer instead, when the intent is obvious —
 *   `3 d 6` meant `3d6`, `d7` meant the nearest die the set actually has.
 *   `null` when there is nothing honest to suggest.
 */
data class NotationError(
  val code: NotationErrorCode,
  val message: String,
  val range: IntRange,
  val suggestion: String? = null,
)

/**
 * What kind of thing went wrong.
 *
 * The code exists so tests and screens can match on the failure without
 * matching on English: the message is for the player, the code is for the
 * code.
 */
enum class NotationErrorCode {
  /** The formula is empty, or is nothing but a label. */
  Empty,

  /** A character that cannot start a value, e.g. `3d6 + )`. */
  UnexpectedCharacter,

  /** The formula stops in the middle of something, e.g. `3d6 +`. */
  UnexpectedEnd,

  /** A `(` with no `)`. */
  UnclosedParenthesis,

  /** A `)` with no `(`. */
  UnmatchedParenthesis,

  /** More than [NotationLimits.MAX_PARENTHESIS_DEPTH] levels of brackets. */
  ParenthesesTooDeep,

  /** A `[` with no `]`. */
  UnclosedLabel,

  /** A label longer than [NotationLimits.MAX_LABEL_LENGTH]. */
  LabelTooLong,

  /** Dice written with spaces in them, e.g. `3 d 6`. */
  SpacedDice,

  /** A `d` with nothing usable after it. */
  MissingSides,

  /** `d0`, or sides that are not a number the app can use. */
  SidesOutOfRange,

  /** A number too long to be one, e.g. a forty-digit modifier. */
  NumberOutOfRange,

  /** Fewer than one, or more than [NotationLimits.MAX_DICE_PER_FORMULA], dice in one group. */
  GroupTooLarge,

  /** More than [NotationLimits.MAX_DICE_PER_FORMULA] dice in the whole formula. */
  FormulaTooLarge,

  /** `2d20kh3` — keeping or dropping more dice than the group rolls. */
  KeepDropOutOfRange,

  /** A modifier written twice, e.g. `4d6dl1dl1`. */
  DuplicateModifier,

  /** A `setref:` naming a dice set that is not installed. */
  UnknownSet,

  /** A die the set does not define, e.g. `d7`. */
  UnknownDie,

  /** `/ 0`, which has no answer to round. */
  DivisionByZero,

  /** A formula whose result cannot be guaranteed to fit in 64 bits. */
  ResultTooLarge,

  /** `!` on a die whose every face is its highest, which would never stop. */
  ExplodesForever,
}
