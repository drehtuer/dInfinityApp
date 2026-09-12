package de.drehtuer.dinfinity.core.model

/**
 * A formula resolved to the actual dice that will be thrown
 * (`docs/architecture.md`, "Data flow of a roll").
 *
 * `:core:notation` parses a formula, resolves every `dice` node against the
 * installed sets and produces one of these; `:simulation:api` checks it
 * against the table's capacity and turns it into a throw. The plan carries no
 * arithmetic — the formula string is the arithmetic, and it is re-parsed to
 * score the dice once they land, which is also what lets the result sheet
 * recompute a total under a different rounding without touching the dice
 * (`docs/dice-notation.md`, "Division rounding").
 *
 * @param formula the formula as typed, or as assembled by the dice picker.
 *   Picked dice and typed dice produce the same plan, which is why the graph,
 *   the breakdown and the statistics need no separate path for either.
 * @param label the trailing `[…]`, used as the outcome graph's title.
 * @param groups one entry per `dice` node in the formula, in source order.
 */
data class RollPlan(
  val formula: String,
  val label: String? = null,
  val groups: List<PlannedGroup> = emptyList(),
) {
  /** Every die in the throw, in spawn order. One physics throw holds them all. */
  val dice: List<DieInstance> get() = groups.flatMap(PlannedGroup::dice)

  /**
   * How many dice the table has to make room for: the dice thrown plus the
   * dice a *first* explosion could add, since those land in the same tray
   * (`docs/tables.md`, capacity rule).
   */
  val capacityDiceCount: Int
    get() = groups.sumOf { group -> group.dice.size * if (group.explodes) 2 else 1 }

  /** True when some die came from a different set than the formula asked for. */
  val hasFallbacks: Boolean get() = groups.any(PlannedGroup::fellBack)
}

/**
 * One `dice` node of a formula — `3d6`, `2d20kh1`, `brass:1d20` — with the
 * dice it resolved to.
 *
 * @param id the node's index in the formula, `0` upwards. The breakdown
 *   attributes every physical die back to its group with it.
 * @param notation the node as written, shown as the breakdown's group heading.
 * @param explodes whether the node carries `!`, which the capacity rule needs
 *   to know before any body is created.
 */
data class PlannedGroup(
  val id: Int,
  val notation: String,
  val dice: List<DieInstance>,
  val explodes: Boolean = false,
) {
  /** True when this group's dice did not come from the set it asked for. */
  val fellBack: Boolean get() = dice.any(DieInstance::fellBack)
}

/**
 * One physical die in a throw: which die it is, which set it came from, and
 * what part it plays.
 *
 * @param index position in the throw, `0` upwards, matching the order bodies
 *   are spawned and the order [RollResult] reads them back.
 * @param groupId the [PlannedGroup] this die belongs to.
 * @param setId the set the die actually came from.
 * @param requestedSetId the set the formula asked for. Different from [setId]
 *   when the default set lacks this die and the built-in set supplied it
 *   instead — which happens per die, so a set with no d12 still rolls
 *   `1d20 + 1d12` (`docs/dice-notation.md`, "Evaluation").
 * @param role what this die contributes; see [DieRole].
 */
data class DieInstance(
  val index: Int,
  val groupId: Int,
  val setId: String,
  val requestedSetId: String,
  val die: Die,
  val role: DieRole = DieRole.Normal,
) {
  /** True when the breakdown has to say which set actually supplied this die. */
  val fellBack: Boolean get() = setId != requestedSetId
}

/**
 * What a die contributes to its group.
 *
 * A d100 is two d10s, because a hundred-sided ball does not roll honestly in a
 * tray (`docs/architecture.md`, decision 7). The pair is marked here so the
 * evaluator can add them as tens + units, with `00` + `0` reading as 100.
 */
enum class DieRole {
  /** Scores its face value. */
  Normal,

  /** The tens half of a d100 pair. */
  PercentileTens,

  /** The units half of a d100 pair. */
  PercentileUnits,
}
