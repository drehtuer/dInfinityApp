package de.drehtuer.dinfinity.simulation.api

import kotlin.random.Random

/**
 * Where every random number in a roll comes from
 * (`docs/physics-and-rendering.md`, "Timestep and determinism").
 *
 * A roll has one seed and needs several streams from it: where each die starts
 * and how hard it is thrown, the nudge a die in trouble gets, the throw an
 * exploding die adds. Each of those has to be its own stream — a die's numbers
 * must not depend on how many dice were drawn before it — and each has to come
 * back the same way every time the same roll is replayed.
 *
 * **The seed is stirred, and that is the whole of why this exists.** A seed
 * handed to `kotlin.random.Random` becomes an xorwow state by way of sixty-four
 * warm-up steps, and sixty-four steps is not enough to separate two seeds that
 * differ only in their low bits: 200,000 d18 throws seeded `0, 1, 2, …` start
 * in orientations spread *more* evenly than chance allows — χ² of 0.73 against
 * 17 degrees of freedom, where a fair sample sits near 17. Too even is a
 * correlation like any other, and it is the correlation that matters here:
 * a roll and the throws its explosions add used to be seeded `s`, `s + 1`,
 * `s + 2`, so every exploding die was thrown by a stream related to the one
 * that set it off.
 *
 * SplitMix64's finaliser fixes it in three lines and is the standard answer to
 * exactly this — a seed sequence that is a counter. It is a bijection, so two
 * different seeds still give two different streams, and it is pure arithmetic,
 * so the same seed still gives the same roll on every device
 * (`docs/architecture.md`, decision 43).
 */
object Seeds {
  /**
   * The stream for one die's [purpose] within the roll seeded [seed].
   *
   * [dieIndex] goes in so that one die's numbers never depend on how many dice
   * were drawn before it — adding a die to a throw must not silently re-roll
   * the ones already in it.
   */
  fun stream(
    seed: Long,
    dieIndex: Int,
    purpose: Long,
  ): Random = Random(stir(seed xor (dieIndex.toLong() shl DIE_SHIFT) xor purpose))

  /**
   * The seed of the [nth] extra throw a roll needed — an exploding die's
   * next die, or a re-throw.
   *
   * A seed rather than a stream, because what needs it is a whole throw of its
   * own with its own spawn and its own corrections (`docs/dice-notation.md`,
   * "Evaluation").
   */
  fun derived(
    seed: Long,
    nth: Int,
  ): Long = stir(seed xor (nth.toLong() shl EXTRA_SHIFT) xor EXTRA)

  /**
   * SplitMix64's finaliser: a bijection on 64 bits that spreads a change in
   * any one of them across all of them.
   *
   * Published constants, used as published. It is not a generator — there is
   * no state and no sequence — it is the mixing half of one, which is the part
   * that is wanted here.
   */
  fun stir(value: Long): Long {
    var z = value
    z = (z xor (z ushr FIRST_SHIFT)) * FIRST_MIX
    z = (z xor (z ushr SECOND_SHIFT)) * SECOND_MIX
    return z xor (z ushr LAST_SHIFT)
  }

  /**
   * Where a die's spawn numbers come from.
   *
   * "SPAWN" in ASCII: any distinct number would do, and one that reads as a
   * word is one nobody later mistakes for a tuning constant.
   */
  const val SPAWN: Long = 0x53_50_41_57_4E

  /** And a re-thrown die's, with the attempt number added so two attempts differ. */
  const val RETHROW: Long = 0x52_45_54_48_52_4F

  /** And the nudge a die in trouble gets. */
  const val BIAS: Long = 0x42_49_41_53

  /**
   * And the tumble a die dropped onto the board before a throw falls with.
   *
   * **A stream of its own is the whole reason this constant exists.** What it
   * seeds is not a roll and is never read (`FallingIn`), but a purpose it
   * shared with the spawn would make it a roll's business all the same: every
   * die the picker added before a throw would take the numbers the throw was
   * going to be given, and the golden fixture would move under a feature that
   * decides nothing. Separate purpose, separate stream, and the recorded
   * throws are exactly what they were.
   */
  const val WAITING: Long = 0x57_41_49_54

  /** And an extra throw's, which is a seed rather than a stream. */
  private const val EXTRA: Long = 0x45_58_54_52_41

  private const val DIE_SHIFT = 32
  private const val EXTRA_SHIFT = 40

  // SplitMix64's finaliser, as published.
  private const val FIRST_MIX = -0x40a7b892e31b1a47L
  private const val SECOND_MIX = -0x6b2fb644ecceee15L
  private const val FIRST_SHIFT = 30
  private const val SECOND_SHIFT = 27
  private const val LAST_SHIFT = 31
}
