package de.drehtuer.dinfinity.simulation.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The fairness run's counting, on the JVM: what `FairnessTest` makes of the
 * faces a device threw, and of the throws that never landed.
 */
class FaceTallyTest {
  @Test
  fun `a perfectly even die has no chi-squared and no face off its share`() {
    val tally = FaceTally(SIX)
    repeat(SIX) { face -> repeat(TEN) { tally.read(face) } }

    assertEquals(60L, tally.read)
    assertEquals(0.0, tally.chiSquared, EPSILON)
    assertEquals(0.0, tally.worstFaceOff, EPSILON)
    assertFalse(tally.missesAFace)
    assertEquals(List(SIX) { 10L }, tally.counts)
  }

  @Test
  fun `a skewed coin is measured by the textbook arithmetic`() {
    // 60 heads and 40 tails: expected 50 each, so (10² + 10²) / 50 = 4, and
    // the worst face is ten points off its half.
    val tally = FaceTally(2)
    repeat(60) { tally.read(0) }
    repeat(40) { tally.read(1) }

    assertEquals(4.0, tally.chiSquared, EPSILON)
    assertEquals(0.1, tally.worstFaceOff, EPSILON)
  }

  @Test
  fun `a give-up is counted as thrown and judged as nothing`() {
    // The point of the class: a throw that ran out the backstop is a figure,
    // not a crash and not a face.
    val tally = FaceTally(2)
    tally.read(0)
    tally.read(1)
    tally.gaveUp()

    assertEquals(1L, tally.giveUps)
    assertEquals(2L, tally.read)
    assertEquals(3L, tally.thrown)
    assertEquals(0.0, tally.chiSquared, EPSILON, "a give-up was counted as a face")
  }

  @Test
  fun `the quick run may have one give-up and not two`() {
    val tally = FaceTally(2)
    repeat(QUICK) { tally.read(it % 2) }
    tally.gaveUp()
    assertEquals(1L, tally.giveUpsAllowed)
    assertFalse(tally.tooManyGiveUps)

    tally.gaveUp()
    assertTrue(tally.tooManyGiveUps)
  }

  @Test
  fun `the full run may have one give-up in ten thousand`() {
    val tally = FaceTally(2)
    repeat(FULL - TEN) { tally.read(it % 2) }
    repeat(TEN) { tally.gaveUp() }

    assertEquals(10L, tally.giveUpsAllowed)
    assertFalse(tally.tooManyGiveUps, "ten in a hundred thousand is on the bar, not over it")

    tally.gaveUp()
    assertTrue(tally.tooManyGiveUps)
  }

  @Test
  fun `a run that read nothing reports nothing rather than dividing by it`() {
    val tally = FaceTally(SIX)
    assertEquals(0.0, tally.chiSquared, EPSILON)
    assertEquals(0.0, tally.worstFaceOff, EPSILON)
    assertFalse(tally.missesAFace, "no throws is not a missing face")

    tally.gaveUp()
    assertEquals(0.0, tally.chiSquared, EPSILON)
    assertFalse(tally.missesAFace)
  }

  @Test
  fun `a face that never came up is a broken die`() {
    val tally = FaceTally(SIX)
    repeat(TEN) { tally.read(0) }
    assertTrue(tally.missesAFace)
  }

  @Test
  fun `a face the die does not have, or a die of one face, is a bug`() {
    val tally = FaceTally(SIX)
    assertFailsWith<IllegalArgumentException> { tally.read(SIX) }
    assertFailsWith<IllegalArgumentException> { tally.read(-1) }
    assertFailsWith<IllegalArgumentException> { FaceTally(1) }
  }

  private companion object {
    const val SIX = 6
    const val TEN = 10
    const val QUICK = 200
    const val FULL = 100_000
    const val EPSILON = 1e-9
  }
}
