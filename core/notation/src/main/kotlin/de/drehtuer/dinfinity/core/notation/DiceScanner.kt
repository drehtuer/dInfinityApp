package de.drehtuer.dinfinity.core.notation

/**
 * Scans one `dice` node: `3d6`, `d%`, `brass:2d20kh1`, `8d6!`.
 *
 * It is where the two counting limits of `docs/dice-notation.md` are enforced,
 * because it is the only place that knows how many dice a group actually puts
 * on the table. A `d%` counts as the *two* d10s it really is, so `500d%`
 * reaches the thousand-dice limit at the same point `1000d6` does.
 */
internal class DiceScanner(
  private val cursor: Cursor,
) {
  private var nodes = 0
  private var diceInFormula = 0

  /** True when a dice token starts [ahead] characters on: a `d` with sides after it. */
  fun isDiceStart(ahead: Int = 0): Boolean {
    val d = cursor.peekAt(ahead)
    if (d != 'd' && d != 'D') return false
    val next = cursor.peekAt(ahead + 1) ?: return false
    return next.isDigit() || next == '%' || next == 'f' || next == 'F'
  }

  /** `dice := (setref ":")? count? "d" sides modifier*`, from the `d` onwards. */
  fun scan(
    setRef: String?,
    count: Long?,
    start: Int,
  ): DiceNode {
    val countRange = cursor.rangeFrom(start)
    cursor.advance()
    val sides = sides()
    val modifiers = ModifierScanner(cursor).scan()
    val node =
      DiceNode(
        id = nodes++,
        setRef = setRef,
        count = countOf(count, countRange),
        sides = sides,
        modifiers = modifiers,
        range = cursor.rangeFrom(start),
      )
    countDice(node)
    return node
  }

  /** A run of digits as a number, refusing one too long to be one. */
  fun literal(
    digits: String,
    range: IntRange,
  ): Long {
    val value = digits.toLongOrNull()
    if (value == null || value > NotationLimits.MAX_LITERAL) {
      throw failure(
        NotationErrorCode.NumberOutOfRange,
        "$digits is larger than the largest number a formula may contain (${NotationLimits.MAX_LITERAL})",
        range,
      )
    }
    return value
  }

  /** `sides := integer | "%" | "F"` */
  private fun sides(): Sides {
    val start = cursor.position
    return when {
      cursor.match('%') -> Sides.Percentile
      cursor.matchWord("f") -> Sides.Fudge
      cursor.peek()?.isDigit() == true -> numericSides(start)
      else ->
        throw failure(
          NotationErrorCode.MissingSides,
          "a 'd' has to be followed by a number of sides, '%' or 'F'",
          cursor.here(),
        )
    }
  }

  private fun numericSides(start: Int): Sides {
    val digits = cursor.scanDigits()
    val value = literal(digits, cursor.rangeFrom(start))
    if (value < 1 || value > NotationLimits.MAX_SIDES) {
      throw failure(
        NotationErrorCode.SidesOutOfRange,
        "a die has between 1 and ${NotationLimits.MAX_SIDES} sides, not $digits",
        cursor.rangeFrom(start),
      )
    }
    // A d100 is always the tens-and-units pair, never a set's hundred-face die
    // (docs/dice-notation.md, "d100 and d%").
    return if (value == PERCENTILE_SIDES) Sides.Percentile else Sides.Numeric(value.toInt())
  }

  private fun countOf(
    count: Long?,
    range: IntRange,
  ): Int {
    val dice = count ?: 1L
    if (dice < 1 || dice > NotationLimits.MAX_DICE_PER_FORMULA) {
      throw failure(
        NotationErrorCode.GroupTooLarge,
        "a group rolls between 1 and ${NotationLimits.MAX_DICE_PER_FORMULA} dice, not $dice",
        range,
      )
    }
    return dice.toInt()
  }

  private fun countDice(node: DiceNode) {
    diceInFormula += node.count * if (node.sides is Sides.Percentile) 2 else 1
    if (diceInFormula > NotationLimits.MAX_DICE_PER_FORMULA) {
      throw failure(
        NotationErrorCode.FormulaTooLarge,
        "a formula rolls at most ${NotationLimits.MAX_DICE_PER_FORMULA} dice; this one reaches $diceInFormula",
        node.range,
      )
    }
  }

  private companion object {
    /** `d100` and `d%` mean the same pair of d10s. */
    const val PERCENTILE_SIDES = 100L
  }
}
