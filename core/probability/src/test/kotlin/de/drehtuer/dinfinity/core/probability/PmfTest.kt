package de.drehtuer.dinfinity.core.probability

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PmfTest {
  private val d6 = Pmf.uniformOver((1..6).toList())

  @Test
  fun `a die is uniform over its faces`() {
    assertEquals(1..6, d6.support)
    (1..6).forEach { assertEquals(1.0 / 6, d6.probabilityOf(it), 1e-15) }
  }

  @Test
  fun `a value outside the support is impossible, not an error`() {
    assertEquals(0.0, d6.probabilityOf(0))
    assertEquals(0.0, d6.probabilityOf(7))
  }

  @Test
  fun `the mass is one`() {
    assertTrue(abs(d6.total() - 1.0) <= 1e-12, "${d6.total()}")
  }

  @Test
  fun `a d6 averages three and a half`() {
    assertEquals(3.5, d6.mean, 1e-12)
  }

  @Test
  fun `a d6 has the variance a uniform over six values has`() {
    assertEquals(35.0 / 12, d6.variance, 1e-12)
    assertEquals(kotlin.math.sqrt(35.0 / 12), d6.standardDeviation, 1e-12)
  }

  @Test
  fun `at least and at most are the questions a game rule asks`() {
    assertEquals(1.0 / 3, d6.atLeast(5), 1e-12)
    assertEquals(0.5, d6.atMost(3), 1e-12)
    assertEquals(1.0, d6.atLeast(1), 1e-12)
    assertEquals(1.0, d6.atMost(6), 1e-12)
  }

  @Test
  fun `asking beyond either end gives nothing, or everything`() {
    assertEquals(0.0, d6.atLeast(7))
    assertEquals(0.0, d6.atMost(0))
    assertEquals(1.0, d6.atLeast(-5), 1e-12)
  }

  @Test
  fun `a cube labelled one two one two one two is a d2, with no special case`() {
    val d2 = Pmf.uniformOver(listOf(1, 2, 1, 2, 1, 2))
    assertEquals(1..2, d2.support)
    assertEquals(0.5, d2.probabilityOf(1), 1e-12)
    assertEquals(1.5, d2.mean, 1e-12)
  }

  @Test
  fun `a fudge die averages nothing at all`() {
    val fudge = Pmf.uniformOver(listOf(-1, -1, 0, 0, 1, 1))
    assertEquals(0.0, fudge.mean, 1e-12)
    assertEquals(-1..1, fudge.support)
  }

  @Test
  fun `a certain value is a distribution of one`() {
    val four = Pmf.certain(4)
    assertEquals(4..4, four.support)
    assertEquals(1.0, four.probabilityOf(4))
    assertEquals(4.0, four.mean)
    assertEquals(0.0, four.variance)
  }

  @Test
  fun `a gap in the middle is kept, because the chart should show it`() {
    val doubled = PmfArithmetic.scale(d6, 2)
    assertEquals(2..12, doubled.support)
    assertEquals(0.0, doubled.probabilityOf(3))
    assertEquals(1.0 / 6, doubled.probabilityOf(4), 1e-12)
    assertEquals(11, doubled.outcomes().size)
  }

  @Test
  fun `zeroes at either end are trimmed away`() {
    val padded = Pmf.of(offset = 0, weights = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0))
    assertEquals(2..3, padded.support)
    assertEquals(0.5, padded.probabilityOf(2), 1e-12)
  }

  @Test
  fun `weights are normalised, so a chain of steps cannot drift`() {
    val unnormalised = Pmf.of(offset = 1, weights = doubleArrayOf(2.0, 2.0))
    assertEquals(0.5, unnormalised.probabilityOf(1), 1e-12)
    assertEquals(1.0, unnormalised.total(), 1e-12)
  }

  @Test
  fun `a distribution with nothing possible in it is a bug, not a distribution`() {
    assertFailsWith<IllegalArgumentException> { Pmf.of(0, doubleArrayOf(0.0, 0.0)) }
    assertFailsWith<IllegalArgumentException> { Pmf.uniformOver(emptyList()) }
  }

  @Test
  fun `outcomes are the bars of the chart, lowest first`() {
    assertEquals((1..6).toList(), d6.outcomes().map(Outcome::value))
    assertTrue(d6.outcomes().all { abs(it.probability - 1.0 / 6) < 1e-12 })
  }

  @Test
  fun `two distributions can be compared for practical purposes`() {
    assertTrue(d6.approximates(Pmf.uniformOver((1..6).toList())))
    assertTrue(!d6.approximates(Pmf.uniformOver((1..8).toList())))
  }

  @Test
  fun `a distribution says what it is when printed`() {
    assertTrue("1..6" in d6.toString(), d6.toString())
    assertTrue("3.5" in d6.toString(), d6.toString())
  }

  @Test
  fun `the raw probabilities can be walked, and are a copy`() {
    val probabilities = d6.probabilities()
    probabilities[0] = 99.0
    assertEquals(1.0 / 6, d6.probabilityOf(1), 1e-12)
    assertEquals(6, probabilities.size)
  }

  @Test
  fun `an outcome is a value and its probability`() {
    val outcome = Outcome(value = 7, probability = 0.25)
    assertEquals(7, outcome.value)
    assertEquals(0.25, outcome.probability)
    assertEquals(outcome, Outcome(7, 0.25))
  }
}
