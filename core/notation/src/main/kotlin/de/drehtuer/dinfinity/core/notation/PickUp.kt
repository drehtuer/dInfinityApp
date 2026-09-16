package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup

/**
 * Which dice of a throw a hand may pick up and throw again
 * (`docs/physics-and-rendering.md`, "Picking a die up and throwing it again").
 *
 * A player reaching into the tray is not the invisible hand the app forbids.
 * The die is not shoved, it is thrown; everybody watching sees it happen; and
 * the number that comes back is the physics' exactly as the first one was. What
 * a hand must not be able to do is not about the die it picks up at all — it is
 * about the dice already lying beside it.
 *
 * **A die that another die was thrown because of is spent.** `8d6!` threw a
 * seventh die because the sixth came up six; `4d6r1` threw a fifth because the
 * second came up one. Those dice are in the tray, their faces have been read
 * and they are in the statistics. Throwing the die that called for them again
 * leaves the roll holding a die the formula no longer asks for, and the only
 * two ways out of that are to take a die off a table nobody threw it off, or to
 * keep a die whose reason has gone. Both are the app moving dice behind the
 * player's back, which is the one thing it does not do.
 *
 * So the offer stops at the **group**, not at the die: a group that can chain
 * — one carrying `!` or `r n` — offers nothing, and every other group offers
 * all of its dice. Judging it per die would mean deciding, from a flat list of
 * dice, which of them a chain hangs off; the chains are real inside
 * [GroupRoller] and are flattened away by the time anything outside can look,
 * and a rule that re-derived them from the notes would be a second description
 * of the same structure, quietly wrong the first time a `4d6r1dl1` dropped a
 * chain that had already been rerolled. Conservative on purpose: a die this
 * refuses is a die a player throws again by pressing **Roll**, and a die it
 * wrongly allowed would be a roll the app had rearranged.
 *
 * It is a question about the **formula** rather than about the physics. Every
 * die in a finished throw is at rest; what differs between them is whether the
 * notation still has something to say. That is why it lives beside
 * [GroupRoller], which is what built the chains this refuses to guess at.
 */
object PickUp {
  /**
   * Every die of [result] a hand may throw again, by [RolledDie.instanceIndex].
   *
   * Empty is an ordinary answer, not a failure: `8d6!` offers nothing, and so
   * does a result whose groups no longer match [formula] — which is what a
   * result left on screen while the field was typed over looks like.
   */
  fun from(
    formula: Formula,
    result: RollResult,
  ): Set<Int> {
    val asked = formula.diceNodes.associateBy(DiceNode::id)
    return result.groups.flatMapTo(mutableSetOf()) { group ->
      asked[group.id]?.let { node -> inThe(group, node) }.orEmpty()
    }
  }

  /**
   * The dice of one group, given the part of the formula that asked for them.
   *
   * [asked] and [group] are matched by [DiceNode.id], which is the same number
   * as the group's — the node's position in source order, which is what lets a
   * die in the tray be traced back to the part of the formula that wanted it.
   */
  fun inThe(
    group: RolledGroup,
    asked: DiceNode,
  ): Set<Int> =
    if (chains(asked)) {
      emptySet()
    } else {
      group.dice.mapTo(mutableSetOf(), RolledDie::instanceIndex)
    }

  /**
   * Whether a die of [node] can have had another thrown because of it.
   *
   * The two modifiers that add dice, and no others. `kh`/`dl` only choose
   * between dice that were all thrown anyway — a die it struck through is
   * precisely the die a player wants to throw again, and which die is dropped
   * is arithmetic over the faces, redone from whatever faces there are. `min n`
   * changes what a die counts as and never what is on the table.
   */
  fun chains(node: DiceNode): Boolean = node.explodes || node.modifiers.any { it is DiceModifier.Reroll }
}
