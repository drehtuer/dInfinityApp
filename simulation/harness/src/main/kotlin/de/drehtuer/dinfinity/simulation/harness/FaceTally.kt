package de.drehtuer.dinfinity.simulation.harness

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * How often each face of one die came up over a fairness run, and how many
 * throws never came up at all (`docs/physics-and-rendering.md`, "Are the dice
 * fair").
 *
 * **A throw that gives up is counted, not crashed on.** A die that is still
 * moving at the twelve-second backstop has no face, and the app says so rather
 * than making one up (`SettleRule.HARD_CAP_SECONDS`). A fairness run of a
 * hundred thousand throws used to stop dead on the first one — a coin, once in
 * a hundred thousand — and took every figure it had gathered with it. Now it
 * is a figure of its own: [giveUps], held to [giveUpsAllowed].
 *
 * The faces are judged over the throws that were read. A give-up is not a
 * face, and pretending it was one — any one — would be the made-up number the
 * backstop exists to refuse. At one in a hundred thousand it cannot move a
 * chi-squared either way; at a rate that could, [tooManyGiveUps] has already
 * failed the run.
 *
 * Plain Kotlin and on the JVM, because none of it is physics: the device
 * throws, and every number derived from what it threw is derived here.
 *
 * @param faceCount how many faces the die has.
 */
class FaceTally(
  val faceCount: Int,
) {
  init {
    require(faceCount >= 2) { "a die has at least two faces, not $faceCount" }
  }

  private val faces = LongArray(faceCount)

  /** Throws that ran out the backstop and were never read. */
  var giveUps: Long = 0
    private set

  /** One throw that came to rest showing [face]. */
  fun read(face: Int) {
    require(face in 0 until faceCount) { "a die of $faceCount faces has no face $face" }
    faces[face]++
  }

  /** One throw that never came to rest, and so has no face. */
  fun gaveUp() {
    giveUps++
  }

  /** How often each face came up, by face index. */
  val counts: List<Long> get() = faces.toList()

  /** Throws that were read. What [chiSquared] and [worstFaceOff] are over. */
  val read: Long get() = faces.sum()

  /** Every throw made, read or not. */
  val thrown: Long get() = read + giveUps

  /** Pearson's chi-squared against a fair die, over the throws that were read; nought when none were. */
  val chiSquared: Double
    get() {
      if (read == 0L) return 0.0
      val expected = read.toDouble() / faceCount
      return faces.sumOf { count -> (count - expected) * (count - expected) / expected }
    }

  /** How far the worst face is from its fair share, as a share (0.01 is one percent). */
  val worstFaceOff: Double
    get() {
      if (read == 0L) return 0.0
      return faces.maxOf { abs(it / read.toDouble() - 1.0 / faceCount) }
    }

  /** True when some face never came up although throws were read. */
  val missesAFace: Boolean get() = read > 0 && faces.any { it == 0L }

  /**
   * How many give-ups the run may have and still pass: [GIVE_UP_SHARE] of the
   * throws, and never fewer than one.
   *
   * The floor of one is for the quick run. Two hundred throws a shape at the
   * measured rate meet a give-up somewhere in the suite about once in sixty
   * runs, and a suite that fails that often for a reason it already knows is a
   * suite people stop reading.
   */
  val giveUpsAllowed: Long get() = max(1L, floor(thrown * GIVE_UP_SHARE).toLong())

  /** True when more throws gave up than [giveUpsAllowed]. */
  val tooManyGiveUps: Boolean get() = giveUps > giveUpsAllowed

  companion object {
    /**
     * The share of throws allowed to give up: one in ten thousand.
     *
     * Over three times the worst the Pixel 10a has shown — three coins in a
     * hundred thousand, then one (`docs/TODO.md`, Step 5.2) — so a run that
     * passes says the backstop is still a backstop, and one that fails says it
     * has become part of the roll.
     */
    const val GIVE_UP_SHARE: Double = 1e-4
  }
}
