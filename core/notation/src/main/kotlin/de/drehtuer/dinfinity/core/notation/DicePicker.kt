package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.DiceSet

/**
 * Dice added and taken away by tapping rather than by typing
 * (`design/dInfinity.dc.html`, option 1h).
 *
 * The picker owns no model of its own. **It edits the formula text**, and
 * everything downstream — the parser, the plan, the graph, the breakdown, the
 * statistics — sees a formula that could equally have been typed
 * (`docs/architecture.md`, decision 31). A tap that built a parallel
 * representation would be a second way for a roll to exist, and the first
 * disagreement between the two would be a total nobody could account for.
 *
 * Every edit is a **splice into the text as written**, not a re-print of the
 * parse tree. A player who typed `4d6dl1 + (1d8 + 2) * 2 [Fireball]` and then
 * taps a d20 gets their formula back with `+ 1d20` in it, spelled the way they
 * spelled it. Only the spacing around the top-level `+` and `-` is normalised,
 * because that is the part being cut into.
 */
object DicePicker {
  /**
   * The dice [set] offers the picker row, in the order they are shown.
   *
   * Only the standard dice, and that is a real limit rather than an oversight:
   * plain notation names `dN`, `d%` and `dF` and nothing else, so a set's own
   * `skull-d6` has no spelling a formula could carry
   * (`docs/architecture.md`, decision 31). A row that added a die the formula
   * cannot name would be a row whose taps disappear.
   *
   * @param setRef the `setref:` every tap writes, or `null` for the set plain
   *   notation already resolves against.
   */
  fun offeredBy(
    set: DiceSet,
    setRef: String? = null,
  ): List<PickableDie> =
    DiceSet.StandardDieIds
      .filter { set.die(it) != null }
      .mapNotNull { standard(it)?.copy(setRef = setRef) }

  /**
   * How many of each of [dice] the formula in [text] asks for.
   *
   * Counted from the **top-level sum only, and only where a group is added
   * rather than subtracted and carries no modifiers**. `2d6 + 1d20` shows a 2
   * on the d6; `4d6dl1` shows nothing, because the badge is a count the picker
   * can also take away, and taking one die out of `4d6dl1` would silently
   * change what the modifier drops.
   *
   * A formula that does not parse has no counts at all. There is nothing
   * dishonest to show and nothing to add to.
   */
  fun counts(
    text: String,
    dice: List<PickableDie>,
  ): Map<PickableDie, Int> {
    val formula = FormulaParser.parseOrNull(text) ?: return emptyMap()
    val terms = topLevel(text, formula.root)
    return dice.associateWith { die ->
      terms.sumOf { term -> if (term.adds(die)) (term.node as DiceNode).count else 0 }
    }
  }

  /**
   * [text] with one more [die] in it.
   *
   * Three cases, in order: a group of that die already in the top-level sum is
   * counted up; otherwise a new group is written in front of the first plain
   * number, so that `3d6 - 4` becomes `3d6 + 1d20 - 4` and the modifier stays
   * where a modifier belongs — at the end; otherwise it goes on the end.
   *
   * A formula that does not parse is handed back untouched. Nothing is ever
   * thrown away: a tap on a formula this cannot read into a sum is an addition
   * to it, never a replacement of it.
   */
  fun add(
    text: String,
    die: PickableDie,
  ): String {
    if (text.isBlank()) return die.notation(count = 1)
    val formula = FormulaParser.parseOrNull(text) ?: return text
    val terms = topLevel(text, formula.root)
    val already = terms.firstOrNull { it.adds(die) }?.node as DiceNode?
    return if (already == null) inserted(text, terms, die) else respelled(text, already, already.count + 1)
  }

  /**
   * [text] with one fewer [die] in it, and the group gone when it was the last
   * one.
   *
   * Only ever takes from a group the badge counted, so a long press can never
   * reach into `4d6dl1`, into a subtraction, or inside brackets. A press with
   * nothing to remove does nothing rather than removing something else.
   */
  fun remove(
    text: String,
    die: PickableDie,
  ): String {
    val formula = FormulaParser.parseOrNull(text) ?: return text
    val terms = topLevel(text, formula.root)
    val at = terms.indexOfFirst { it.adds(die) }
    if (at < 0) return text
    val node = terms[at].node as DiceNode
    return if (node.count > 1) respelled(text, node, node.count - 1) else dropped(text, terms, at)
  }
}

/** `d2`, `d4`, … `d%`, `dF` — the standard die [id] as the picker offers it. */
private fun standard(id: String): PickableDie? =
  when {
    id == PERCENTILE_HALF -> PickableDie("d%", Sides.Percentile)
    id == DieResolver.FUDGE_DIE_ID -> PickableDie("dF", Sides.Fudge)
    // `d10` is offered as itself; the tens die is what turns it into `d%`.
    id.startsWith('d') -> id.drop(1).toIntOrNull()?.let { PickableDie(id, Sides.Numeric(it)) }
    else -> null
  }

/** A top-level term of the sum, and whether it is added or subtracted. */
private class Term(
  val sign: Int,
  val node: FormulaNode,
) {
  /** True when this term is exactly "some of [die]", added, with nothing done to it. */
  fun adds(die: PickableDie): Boolean =
    sign > 0 &&
      node is DiceNode &&
      node.modifiers.isEmpty() &&
      node.sides == die.sides &&
      node.setRef == die.setRef
}

/**
 * The sum [node] is at the top level, left to right.
 *
 * **A bracketed sub-expression is one term, not its own terms.** The parser
 * hands back what is inside the brackets with no mark on it, so the only
 * evidence that `3d6 - (1d4 + 2)` is not a three-term sum is the text, which
 * is where this reads it from. Flattening across a bracket would let a long
 * press pull a die out of a group it cannot be pulled out of, and the sign
 * of everything after it would be wrong.
 */
private fun topLevel(
  text: String,
  node: FormulaNode,
  sign: Int = 1,
  into: MutableList<Term> = mutableListOf(),
): List<Term> {
  val sum = node as? BinaryNode
  if (sum == null || sum.operator !in SUM_OPERATORS || extentOf(text, sum) != sum.range) {
    into += Term(sign, node)
    return into
  }
  topLevel(text, sum.left, sign, into)
  topLevel(text, sum.right, if (sum.operator == BinaryOperator.Minus) -sign else sign, into)
  return into
}

/**
 * [node]'s range widened over the brackets around it.
 *
 * A bracketed expression is returned by the parser as the expression itself,
 * so its range starts after the `(` and ends before the `)`. Anything that
 * cuts text has to know about the brackets it would otherwise leave behind.
 */
private fun extentOf(
  text: String,
  node: FormulaNode,
): IntRange {
  var range = node.range
  while (true) {
    val opens = text.lastIndexOf('(', range.first - 1).takeIf { it >= 0 && blank(text, it + 1, range.first) }
    val closes = text.indexOf(')', range.last + 1).takeIf { it >= 0 && blank(text, range.last + 1, it) }
    if (opens == null || closes == null) return range
    range = opens..closes
  }
}

private fun blank(
  text: String,
  from: Int,
  until: Int,
): Boolean = (from until until).all { text[it].isWhitespace() }

/** Where the `+` or `-` in front of the term starting at [start] is. */
private fun operatorBefore(
  text: String,
  start: Int,
): Int = (start - 1 downTo 0).first { !text[it].isWhitespace() }

/** [text] with [node]'s count written as [count] instead. */
private fun respelled(
  text: String,
  node: DiceNode,
  count: Int,
): String {
  val from = node.range.first + (node.setRef?.let { it.length + 1 } ?: 0)
  val until = (from..node.range.last).firstOrNull { !text[it].isDigit() } ?: node.range.last + 1
  return text.replaceRange(from, until, count.toString())
}

/** [text] with a new group of one [die] written into its top-level sum. */
private fun inserted(
  text: String,
  terms: List<Term>,
  die: PickableDie,
): String {
  val written = die.notation(count = 1)
  val modifier = terms.indexOfFirst { it.node is NumberNode }
  val spliced =
    when {
      // In front of a plain number, so the modifier stays at the end where a
      // modifier belongs: `3d6 - 4` gains `+ 1d20` before the `- 4`.
      modifier > 0 -> {
        val operator = operatorBefore(text, extentOf(text, terms[modifier].node).first)
        text.replaceRange(operator, operator, " + $written ")
      }

      modifier == 0 -> {
        val start = extentOf(text, terms[0].node).first
        text.replaceRange(start, start, "$written + ")
      }

      else -> {
        val end = extentOf(text, terms.last().node).last + 1
        text.replaceRange(end, end, " + $written")
      }
    }
  return tidied(spliced)
}

/** [text] with the term at [at] taken out of the sum, operator and all. */
private fun dropped(
  text: String,
  terms: List<Term>,
  at: Int,
): String {
  val extent = extentOf(text, terms[at].node)
  if (terms.size == 1) return tidied(text.replaceRange(extent.first, extent.last + 1, ""))
  if (at > 0) {
    val operator = operatorBefore(text, extent.first)
    return tidied(text.replaceRange(operator, extent.last + 1, ""))
  }
  // The first term takes the operator after it with it, but only when that
  // operator is a `+`: a `-` belongs to the term it introduces and removing
  // it would turn a subtraction into an addition.
  val next = operatorBefore(text, extentOf(text, terms[1].node).first)
  val until = if (text[next] == '+') next + 1 else extent.last + 1
  return tidied(text.replaceRange(extent.first, until, ""))
}

/**
 * Puts the spacing back to one space where a splice left two or none.
 *
 * Only up to the label: `[a  b]` is a name somebody chose, and re-spacing it
 * would be editing their words rather than their formula.
 */
private fun tidied(text: String): String {
  val label = text.indexOf('[').takeIf { it >= 0 } ?: text.length
  val expression = text.substring(0, label).replace(SPACE_RUN, " ").trim()
  val rest = text.substring(label)
  return when {
    expression.isEmpty() -> ""
    rest.isEmpty() -> expression
    else -> "$expression $rest"
  }
}

private val SUM_OPERATORS = setOf(BinaryOperator.Plus, BinaryOperator.Minus)
private val SPACE_RUN = Regex("\\s+")
private const val PERCENTILE_HALF = "d10-tens"

/**
 * One die on the picker row.
 *
 * @param notation how a formula spells it — `d6`, `d%`, `dF`.
 * @param sides what that spelling parses to, which is what a group in the
 *   formula is matched against. `d100` and `d%` are the same die and so count
 *   towards the same badge.
 * @param setRef the `setref:` a tap writes, or `null` for the default set. A
 *   group written `builtin:1d6` while `builtin` *is* the default is a
 *   different spelling and deliberately does not count: the picker edits what
 *   it can spell, and guessing at which set a player meant is how a tap starts
 *   removing dice it did not put there.
 */
data class PickableDie(
  val notation: String,
  val sides: Sides,
  val setRef: String? = null,
) {
  /** This die as [count] of them, written the way a formula writes it. */
  fun notation(count: Int): String = "${setRef?.let { "$it:" } ?: ""}$count$notation"
}
