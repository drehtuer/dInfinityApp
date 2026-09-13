package de.drehtuer.dinfinity.core.model

/**
 * What a throw came to: the total, and every die that produced it
 * (`docs/dice-notation.md`, "Evaluation").
 *
 * The result sheet shows the total large, then each group's subtotal and its
 * individual dice, then the modifiers, so nobody has to add anything up. Dice
 * that were dropped are kept in the breakdown and struck through rather than
 * hidden — a player wants to see the 1 that `4d6dl1` threw away.
 *
 * The seed is not here. Determinism is for tests and bug reports, not a
 * feature: the app's history has no replay and never shows a seed
 * (`docs/architecture.md`, decision 13).
 *
 * @param total what the formula evaluated to with these dice.
 * @param rounding the rounding this total was computed under. The sheet can
 *   recompute under another one from the same dice, which is why the dice are
 *   carried rather than just the total.
 * @param groups one entry per [PlannedGroup], in formula order.
 * @param rethrows how many dice were picked up and thrown again because they
 *   came to rest cocked or stacked. Visible, honest, and counted — it is the
 *   last rung of the ladder in `docs/physics-and-rendering.md`, and a number
 *   that climbs is a physics bug.
 * @param forcedSettles how many dice were still moving when the 12-second cap
 *   fired. Any value but zero is an anomaly, logged as one.
 * @param rolledAtEpochMs when the throw finished, for history and statistics.
 */
data class RollResult(
  val formula: String,
  val label: String? = null,
  val total: Long,
  val rounding: Rounding = Rounding.Default,
  val groups: List<RolledGroup> = emptyList(),
  val rethrows: Int = 0,
  val forcedSettles: Int = 0,
  val rolledAtEpochMs: Long = 0L,
) {
  /** Every die that landed, in throw order, dropped ones included. */
  val dice: List<RolledDie> get() = groups.flatMap(RolledGroup::dice)

  /** True when any die showed its highest face — what the sheet paints in the accent. */
  val hasNaturalMax: Boolean get() = dice.any { it.kept && it.naturalMax }
}

/**
 * One group of the breakdown: the dice of a single `dice` node and what they
 * came to together.
 *
 * @param setId the set the dice actually came from, named in the breakdown.
 * @param requestedSetId the set the formula asked for; when it differs, the
 *   breakdown says the roll fell back.
 * @param subtotal the group's contribution after keep/drop, reroll and `min`,
 *   before the formula's own arithmetic.
 */
data class RolledGroup(
  val id: Int,
  val notation: String,
  val setId: String,
  val requestedSetId: String,
  val dice: List<RolledDie> = emptyList(),
  val subtotal: Long = 0L,
) {
  /** True when the breakdown has to say which set actually supplied these dice. */
  val fellBack: Boolean get() = setId != requestedSetId

  /** The dice that counted towards [subtotal]. */
  val kept: List<RolledDie> get() = dice.filter(RolledDie::kept)
}

/**
 * One die as it landed.
 *
 * @param instanceIndex the [DieInstance.index] this came from, so a tap on a
 *   die in the tray can highlight its line in the breakdown.
 * @param value the face value read off the die — the physics result, never a
 *   number chosen anywhere else (`docs/architecture.md`, goal 1).
 * @param label what was printed on the face that landed up, for dice whose
 *   faces carry symbols rather than their value.
 * @param naturalMax the die showed its highest face. Statistics count these,
 *   and the sheet paints them in the accent.
 * @param naturalMin the die showed its lowest face.
 * @param notes anything the breakdown has to say about this die beyond its
 *   value; see [DieNote].
 */
data class RolledDie(
  val instanceIndex: Int,
  val dieId: String,
  val value: Int,
  val label: String = value.toString(),
  val naturalMax: Boolean = false,
  val naturalMin: Boolean = false,
  val notes: Set<DieNote> = emptySet(),
) {
  /** False for a dropped die, which the sheet shows struck through. */
  val kept: Boolean get() = DieNote.Dropped !in notes
}

/** Why a die in the breakdown is not simply "it rolled this". */
enum class DieNote {
  /** Removed by `kh`, `kl`, `dh` or `dl`. Shown, struck through. */
  Dropped,

  /** This die replaced one that `r n` rerolled. */
  Rerolled,

  /** This die was added by an explosion, in a later throw into the same tray. */
  FromExplosion,

  /** `min n` raised this die's value. The face that landed is still shown. */
  ClampedToMin,

  /** The tens half of a d100 pair; its value is already multiplied by ten. */
  PercentileTens,

  /** The units half of a d100 pair. */
  PercentileUnits,

  /**
   * This die exploded but the chain had reached the depth limit, so no further
   * die was thrown (`docs/dice-notation.md`, "Limits").
   */
  ExplosionLimitReached,
}
