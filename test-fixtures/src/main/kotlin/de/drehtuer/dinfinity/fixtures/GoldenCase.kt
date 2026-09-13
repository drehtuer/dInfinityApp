package de.drehtuer.dinfinity.fixtures

/**
 * One case of the golden determinism suite: an input, and what it came to.
 *
 * The suite is what makes "the same seed gives the same roll on every device
 * and every ABI" a check rather than a hope
 * (`docs/physics-and-rendering.md`, "Timestep and determinism"). The inputs
 * are chosen by hand to cover the notation and the table capacity rule; the
 * [expected] half is recorded from a device run and is never edited by hand.
 *
 * @param seed the roll's seed.
 * @param formula as typed, resolved against the standard set.
 * @param input how the throw is started.
 * @param expected what a recorded run produced, or null for a case that has
 *   not been recorded yet — which the suite reports as a failure rather than
 *   skipping, because a suite that goes quiet when its fixture empties is not
 *   a suite.
 */
data class GoldenCase(
  val seed: Long,
  val formula: String,
  val input: GoldenInput = GoldenInput.Tap,
  val expected: GoldenOutcome? = null,
)

/** How a golden throw is started (`docs/physics-and-rendering.md`). */
enum class GoldenInput(
  val id: String,
) {
  /** A drop from the hand: no phone motion at all. */
  Tap("tap"),

  /** The recorded swing in [GoldenShake]. */
  Shake("shake"),
  ;

  companion object {
    /** The input written as [id], or an error naming what was written. */
    fun of(id: String): GoldenInput =
      entries.firstOrNull { it.id == id } ?: error("'$id' is not a golden input; expected tap or shake")
  }
}

/**
 * What one golden case produced.
 *
 * @param scale what the capacity rule shrank the dice to (`docs/tables.md`).
 * @param spawn digest of everything handed to the engine before it stepped:
 *   the dice, their hulls, their placements and the gravity of every step.
 *   It is the part of the chain a JVM test can reach, which is why it is a
 *   column of its own rather than folded into the outcome.
 * @param faces the face index each die came to rest on, in throw order.
 * @param steps fixed steps to rest.
 * @param corrections dice nudged while still moving (rung 2).
 * @param rethrows dice picked up and thrown again (rung 3).
 */
data class GoldenOutcome(
  val scale: Double,
  val spawn: String,
  val faces: List<Int>,
  val steps: Int,
  val corrections: Int,
  val rethrows: Int,
)
