package de.drehtuer.dinfinity.simulation.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * That two rolls which are nearly the same seed are not nearly the same roll
 * (`docs/physics-and-rendering.md`, "Timestep and determinism").
 *
 * This is a **fairness** test rather than a tidiness one, and it is written as
 * one. A die is fair because its solid is isohedral and its starting turn is
 * drawn evenly over every orientation there is — an argument that assumes the
 * draws are independent. Handed straight to `kotlin.random.Random`, seeds that
 * differ only in their low bits are not: sixty-four xorwow warm-up steps do not
 * separate them, and a counter for a seed produces a sequence that is *too*
 * even, which is a correlation like any other.
 *
 * The numbers below are deliberately statistical and deliberately deterministic:
 * they are computed from fixed seeds, so the test either always passes or
 * always fails, and it fails on the real defect rather than on a threshold.
 */
class SeedsTest {
  @Test
  fun `a counter for a seed gives streams that look independent`() {
    // The shape of the original bug. Every roll number in 0..N is turned into a
    // stream, one number is drawn from each, and the spread of those numbers is
    // measured. Independent streams put about a twelfth of the samples in each
    // of twelve buckets, and the spread of a fair sample is chi-squared with
    // eleven degrees of freedom — about eleven, and very rarely under two.
    val counted = spreadOf { roll -> Seeds.stream(roll.toLong(), dieIndex = 0, purpose = Seeds.SPAWN) }

    assertTrue(
      counted in LOOSE_ENOUGH..TIGHT_ENOUGH,
      "consecutive seeds gave a spread of $counted, which is not what independent streams give",
    )
  }

  @Test
  fun `the same is true of every die in a throw`() {
    // The die index is mixed in as well, so the tenth die of one roll is not
    // the ninth die of the next.
    val counted = spreadOf { die -> Seeds.stream(seed = 7L, dieIndex = die, purpose = Seeds.SPAWN) }

    assertTrue(counted in LOOSE_ENOUGH..TIGHT_ENOUGH, "die indexes gave a spread of $counted")
  }

  @Test
  fun `and of the throws an explosion adds`() {
    // The one that reached a player: an exploding die's next throw used to be
    // seeded one more than the throw that set it off.
    val counted = spreadOf { nth -> kotlin.random.Random(Seeds.derived(seed = 11L, nth = nth)) }

    assertTrue(counted in LOOSE_ENOUGH..TIGHT_ENOUGH, "an explosion's throws gave a spread of $counted")
  }

  @Test
  fun `a stirred seed is still the same seed`() {
    // Everything above would also be true of a stir that threw the seed away.
    // A roll has to replay to itself, so the same seed has to give the same
    // stream, every time and on every device (`docs/architecture.md`).
    val once = Seeds.stream(seed = 99L, dieIndex = 3, purpose = Seeds.SPAWN).nextDouble()
    val again = Seeds.stream(seed = 99L, dieIndex = 3, purpose = Seeds.SPAWN).nextDouble()

    assertEquals(once, again)
  }

  @Test
  fun `two different seeds are two different streams`() {
    // `stir` is a bijection, so no two seeds can be folded onto one.
    val seen = (0 until MANY).map { seed -> Seeds.stir(seed.toLong()) }.toSet()

    assertEquals(MANY, seen.size, "two seeds were stirred onto the same stream")
  }

  @Test
  fun `a purpose is a stream of its own`() {
    // A die's spawn and the nudge it might get later must not be the same
    // numbers twice.
    val spawn = Seeds.stream(seed = 5L, dieIndex = 0, purpose = Seeds.SPAWN).nextDouble()
    val bias = Seeds.stream(seed = 5L, dieIndex = 0, purpose = Seeds.BIAS).nextDouble()

    assertNotEquals(spawn, bias)
  }

  @Test
  fun `stirring nothing is not nothing`() {
    // Seed zero is the one a counter starts at and the one a bad mixer leaves
    // alone. SplitMix64's finaliser maps it to zero, which is why the roll
    // seed is combined with a purpose before it is stirred rather than after —
    // `stream` and `derived` both fold something non-zero in.
    assertEquals(0L, Seeds.stir(0L), "the finaliser is published as fixing zero, and this pins that")
    assertNotEquals(0.0, Seeds.stream(seed = 0L, dieIndex = 0, purpose = Seeds.SPAWN).nextDouble())
  }

  /**
   * How evenly [stream] spreads one draw per seed over twelve buckets, as a
   * chi-squared.
   *
   * One number per stream and not a hundred: what is being asked is whether
   * *different* streams are related to each other, which is not something you
   * can see by looking at any one of them for longer.
   */
  private fun spreadOf(stream: (Int) -> kotlin.random.Random): Double {
    val buckets = LongArray(BUCKETS)
    for (roll in 0 until SAMPLES) {
      buckets[(stream(roll).nextDouble() * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)]++
    }
    val expected = SAMPLES.toDouble() / BUCKETS
    return buckets.sumOf { count -> (count - expected) * (count - expected) / expected }
  }

  private companion object {
    const val SAMPLES = 120_000
    const val BUCKETS = 12
    const val MANY = 20_000

    /**
     * How small a spread may be before it is suspiciously even.
     *
     * The bound that matters, and the one the old code broke: eleven degrees
     * of freedom give a chi-squared under 2.0 about one time in a thousand, so
     * a sample that quiet is a sample that was not drawn independently.
     */
    const val LOOSE_ENOUGH = 2.0

    /** And how large before it is not even enough. p = 0.001 at 11 degrees of freedom. */
    const val TIGHT_ENOUGH = 31.264
  }
}
