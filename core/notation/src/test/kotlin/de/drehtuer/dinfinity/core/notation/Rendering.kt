package de.drehtuer.dinfinity.core.notation

/**
 * A parsed formula written back out in fully bracketed form, so a test can say
 * what it expects the *structure* to be in one line.
 *
 * `3d6 + 1d20 - 4` renders as `((3d6 + 1d20) - 4)`, which is a statement about
 * associativity; `2 + 3 * 4` renders as `(2 + (3 * 4))`, which is a statement
 * about precedence. Neither is visible in a test that only checks the total.
 */
internal fun render(node: FormulaNode): String =
  when (node) {
    is NumberNode -> node.value.toString()
    is NegateNode -> "-${render(node.operand)}"
    is BinaryNode -> "(${render(node.left)} ${node.operator.symbol} ${render(node.right)})"
    is DiceNode -> renderDice(node)
  }

private fun renderDice(node: DiceNode): String =
  buildString {
    node.setRef?.let { append("$it:") }
    append(node.count)
    append('d')
    append(
      when (node.sides) {
        is Sides.Numeric -> node.sides.value.toString()
        Sides.Percentile -> "%"
        Sides.Fudge -> "F"
      },
    )
    node.modifiers.forEach { append(renderModifier(it)) }
  }

private fun renderModifier(modifier: DiceModifier): String =
  when (modifier) {
    is DiceModifier.KeepHighest -> "kh${modifier.n}"
    is DiceModifier.KeepLowest -> "kl${modifier.n}"
    is DiceModifier.DropHighest -> "dh${modifier.n}"
    is DiceModifier.DropLowest -> "dl${modifier.n}"
    is DiceModifier.Explode -> "!"
    is DiceModifier.Reroll -> "r${modifier.threshold}"
    is DiceModifier.Minimum -> "min${modifier.value}"
  }

/** The formula [text] must parse; the test fails loudly if it does not. */
internal fun parsed(text: String): Formula =
  when (val result = FormulaParser.parse(text)) {
    is ParseResult.Parsed -> result.formula
    is ParseResult.Failed -> error("'$text' should parse, but: ${result.error.code} ${result.error.message}")
  }

/** The error [text] must produce; the test fails loudly if it parses. */
internal fun refused(text: String): NotationError =
  when (val result = FormulaParser.parse(text)) {
    is ParseResult.Failed -> result.error
    is ParseResult.Parsed -> error("'$text' should not parse, but it did as ${render(result.formula.root)}")
  }
