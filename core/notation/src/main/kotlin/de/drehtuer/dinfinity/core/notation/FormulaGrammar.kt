package de.drehtuer.dinfinity.core.notation

/**
 * The grammar of `docs/dice-notation.md`, as recursive descent over a
 * [Cursor].
 *
 * Every production hands back a node carrying the character range it came
 * from, because that range is the whole point of the error messages: the field
 * underlines the `1d7` rather than the line (design option 9c).
 */
internal class FormulaGrammar(
  private val cursor: Cursor,
) {
  private val dice = DiceScanner(cursor)
  private var depth = 0

  /** `formula := expr label?`, with nothing allowed after it. */
  fun formula(): Formula {
    cursor.skipWhitespace()
    if (cursor.atEnd()) {
      throw failure(NotationErrorCode.Empty, "type a formula, for example 3d6 + 2", 0..0)
    }
    val root = expression()
    cursor.skipWhitespace()
    val label = if (cursor.peek() == '[') label() else null
    cursor.skipWhitespace()
    if (!cursor.atEnd()) throw trailing(cursor)
    return Formula(text = cursor.text, root = root, label = label)
  }

  /** `expr := term (("+" | "-") term)*` */
  private fun expression(): FormulaNode = chain(::term, BinaryOperator.Plus, BinaryOperator.Minus)

  /** `term := factor (("*" | "/") factor)*` */
  private fun term(): FormulaNode = chain(::factor, BinaryOperator.Times, BinaryOperator.Divide)

  private fun chain(
    operand: () -> FormulaNode,
    vararg operators: BinaryOperator,
  ): FormulaNode {
    var left = operand()
    while (true) {
      cursor.skipWhitespace()
      val operator = operators.firstOrNull { it.symbol == cursor.peek() } ?: break
      cursor.advance()
      val right = operand()
      left = BinaryNode(operator, left, right, left.range.first..right.range.last)
    }
    return left
  }

  /** `factor := ("-")? atom` */
  private fun factor(): FormulaNode {
    cursor.skipWhitespace()
    val start = cursor.position
    if (!cursor.match('-')) return atom()
    val operand = atom()
    return NegateNode(operand, start..operand.range.last)
  }

  /** `atom := dice | integer | "(" expr ")"` */
  private fun atom(): FormulaNode {
    cursor.skipWhitespace()
    val start = cursor.position
    val char = cursor.peek() ?: throw endOfFormula(cursor)
    return when {
      char == '(' -> parenthesised()
      dice.isDiceStart() -> dice.scan(setRef = null, count = null, start = start)
      char.isDigit() -> numberOrDice(start)
      char in 'a'..'z' -> setReferencedDice(start)
      else -> throw notAValue(cursor, char)
    }
  }

  private fun parenthesised(): FormulaNode {
    val start = cursor.position
    cursor.advance()
    depth++
    if (depth > NotationLimits.MAX_PARENTHESIS_DEPTH) {
      throw failure(
        NotationErrorCode.ParenthesesTooDeep,
        "brackets are nested more than ${NotationLimits.MAX_PARENTHESIS_DEPTH} deep",
        cursor.rangeFrom(start),
      )
    }
    val inner = expression()
    cursor.skipWhitespace()
    if (!cursor.match(')')) {
      throw failure(NotationErrorCode.UnclosedParenthesis, "this '(' has no matching ')'", start..start)
    }
    depth--
    return inner
  }

  /** A number, unless a `d` follows it with no space, which makes it a count. */
  private fun numberOrDice(start: Int): FormulaNode {
    val digits = cursor.scanDigits()
    val value = dice.literal(digits, cursor.rangeFrom(start))
    if (!dice.isDiceStart()) return NumberNode(value, cursor.rangeFrom(start))
    return dice.scan(setRef = null, count = value, start = start)
  }

  /** `brass:1d20` — a group taken from a named set rather than the default one. */
  private fun setReferencedDice(start: Int): FormulaNode {
    val identifier = cursor.scanIdentifier()
    if (!cursor.match(':')) {
      throw failure(
        NotationErrorCode.UnexpectedCharacter,
        "'$identifier' is not a number, a die or a bracket",
        cursor.rangeFrom(start),
      )
    }
    val countStart = cursor.position
    val digits = cursor.scanDigits()
    val count = if (digits.isEmpty()) null else dice.literal(digits, cursor.rangeFrom(countStart))
    if (!dice.isDiceStart()) {
      throw failure(
        NotationErrorCode.MissingSides,
        "'$identifier:' has to be followed by a die, for example $identifier:1d20",
        cursor.rangeFrom(start),
      )
    }
    return dice.scan(setRef = identifier, count = count, start = start)
  }

  /** `label := "[" text "]"` */
  private fun label(): String? {
    val start = cursor.position
    val close = cursor.text.indexOf(']', cursor.position + 1)
    if (close < 0) {
      throw failure(NotationErrorCode.UnclosedLabel, "this '[' has no matching ']'", start..start)
    }
    val text = cursor.text.substring(start + 1, close).trim()
    cursor.advance(close - start + 1)
    if (text.length > NotationLimits.MAX_LABEL_LENGTH) {
      throw failure(
        NotationErrorCode.LabelTooLong,
        "a label is at most ${NotationLimits.MAX_LABEL_LENGTH} characters",
        cursor.rangeFrom(start),
      )
    }
    return text.ifEmpty { null }
  }
}

private fun endOfFormula(cursor: Cursor): ParseFailure =
  failure(
    NotationErrorCode.UnexpectedEnd,
    "the formula ends where a value was expected",
    cursor.here(),
  )

private fun notAValue(
  cursor: Cursor,
  char: Char,
): ParseFailure =
  if (char == ')') {
    failure(NotationErrorCode.UnmatchedParenthesis, "this ')' has no matching '('", cursor.here())
  } else {
    failure(NotationErrorCode.UnexpectedCharacter, "'$char' cannot start a value here", cursor.here())
  }

private fun trailing(cursor: Cursor): ParseFailure {
  val rest = cursor.text.substring(cursor.position)
  val range = cursor.position until cursor.text.length
  return if (rest.startsWith(")")) {
    failure(NotationErrorCode.UnmatchedParenthesis, "this ')' has no matching '('", range)
  } else {
    failure(NotationErrorCode.UnexpectedCharacter, "'${rest.trim()}' is left over at the end of the formula", range)
  }
}
