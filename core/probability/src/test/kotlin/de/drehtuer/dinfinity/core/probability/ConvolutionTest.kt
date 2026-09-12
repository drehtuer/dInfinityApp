package de.drehtuer.dinfinity.core.probability

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The two ways of adding two distributions have to give the same answer, or
 * the graph would change shape at whatever size the implementation switches
 * over — which is exactly the kind of bug nobody finds by looking.
 */
class ConvolutionTest {
  private val d6 = Pmf.uniformOver((1..6).toList())

  @Test
  fun `adding two dice is the triangle`() {
    val sum = Convolution.of(d6, d6)
    assertEquals(2..12, sum.support)
    assertEquals(6.0 / 36, sum.probabilityOf(7), 1e-15)
  }

  @Test
  fun `repeating is the same as adding one at a time`() {
    (1..12).forEach { count ->
      val repeated = Convolution.repeated(d6, count)
      val oneByOne = (2..count).fold(d6) { sum, _ -> Convolution.of(sum, d6) }
      assertTrue(repeated.approximates(oneByOne, 1e-14), "${count}d6")
    }
  }

  @Test
  fun `the transform agrees with the direct method well past the threshold`() {
    // 300 d20 is ninety thousand multiply-adds per step, far above the
    // threshold, so this is the transform against a hand-rolled convolution.
    val d20 = Pmf.uniformOver((1..20).toList())
    val big = Convolution.repeated(d20, 60)
    val direct = (2..60).fold(d20) { sum, _ -> directly(sum, d20) }
    val worst = (big.min..big.max).maxOf { abs(big.probabilityOf(it) - direct.probabilityOf(it)) }
    assertTrue(worst < 1e-15, "the two methods differ by $worst")
  }

  @Test
  fun `the transform leaves no negative probabilities behind`() {
    val wide = Convolution.repeated(Pmf.uniformOver((1..20).toList()), 100)
    assertTrue(wide.probabilities().all { it >= 0.0 })
    assertTrue(abs(wide.total() - 1.0) < 1e-12)
  }

  @Test
  fun `adding one die to nothing is that die`() {
    assertTrue(Convolution.repeated(d6, 1).approximates(d6))
  }

  @Test
  fun `a sum of no dice is not a sum`() {
    assertFailsWith<IllegalArgumentException> { Convolution.repeated(d6, 0) }
  }

  @Test
  fun `the transform needs a power-of-two length`() {
    assertFailsWith<IllegalArgumentException> { Fft.transform(DoubleArray(3), DoubleArray(3)) }
  }

  @Test
  fun `a transform and its inverse give back what went in`() {
    val real = DoubleArray(8) { it.toDouble() }
    val imaginary = DoubleArray(8)
    val original = real.copyOf()
    Fft.transform(real, imaginary)
    Fft.transform(real, imaginary, inverse = true)
    original.indices.forEach { assertEquals(original[it], real[it], 1e-12) }
  }

  @Test
  fun `lengths round up to the next power of two`() {
    assertEquals(1, Fft.lengthFor(1))
    assertEquals(8, Fft.lengthFor(5))
    assertEquals(8, Fft.lengthFor(8))
    assertEquals(16, Fft.lengthFor(9))
  }

  /** The plain double loop, written out here so the test does not use the code it is testing. */
  private fun directly(
    a: Pmf,
    b: Pmf,
  ): Pmf {
    val left = a.probabilities()
    val right = b.probabilities()
    val out = DoubleArray(left.size + right.size - 1)
    left.forEachIndexed { i, x -> right.forEachIndexed { j, y -> out[i + j] += x * y } }
    return Pmf.of(a.offset + b.offset, out)
  }
}
