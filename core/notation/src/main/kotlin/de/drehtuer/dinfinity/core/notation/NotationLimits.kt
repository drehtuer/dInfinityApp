package de.drehtuer.dinfinity.core.notation

/**
 * The bounds a formula is held to (`docs/dice-notation.md`, "Limits").
 *
 * They are here, in one object, because the numbers are published in the
 * documentation and quoted in error messages: if one moves, the document and
 * the messages have to move with it in the same pull request
 * (`.claude/CLAUDE.md`).
 *
 * None of these is about the *table*. A formula may be far too big to roll and
 * still be perfectly legal — `500d6` parses, graphs and is refused only when
 * it reaches the tray (`docs/tables.md`). These limits exist so the parser and
 * the outcome graph stay cheap, not so the physics stays honest.
 */
object NotationLimits {
  /**
   * Dice in the whole formula, and therefore in any one group of it. Bounds
   * the outcome graph's support, which is the only reason it exists.
   *
   * `500d6` is under it and parses, which is the point: it graphs perfectly
   * well and is refused only when it reaches the tray (`docs/tables.md`).
   */
  const val MAX_DICE_PER_FORMULA: Int = 1_000

  /** How many times `!` may re-throw down one chain before it stops. */
  const val MAX_EXPLOSION_DEPTH: Int = 20

  /** Brackets within brackets. Past this, nobody is reading the formula anyway. */
  const val MAX_PARENTHESIS_DEPTH: Int = 8

  /** The largest number that may be written in a formula. */
  const val MAX_LITERAL: Long = 1_000_000_000L

  /** The most sides a `dN` may name. Beyond it no set could define the die. */
  const val MAX_SIDES: Int = 1_000_000

  /** How much text fits in a `[…]` before it stops being a label. */
  const val MAX_LABEL_LENGTH: Int = 60

  /**
   * The magnitude a formula's result must provably stay under.
   *
   * Half of `Long.MAX_VALUE`, so a sum of two values that each pass the bound
   * still cannot overflow when they are added (`docs/dice-notation.md`,
   * "Limits": result magnitude fits in 64-bit).
   */
  const val MAX_RESULT_MAGNITUDE: Long = Long.MAX_VALUE / 2
}
