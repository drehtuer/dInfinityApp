package de.drehtuer.dinfinity.core.probability

import kotlin.math.max

/**
 * Adding two independent quantities: the distribution of the sum.
 *
 * Two implementations of one operation. Below a threshold the direct
 * double loop is faster than setting an FFT up; above it, it is hopeless
 * (`docs/probability.md`). Which one ran is never visible in the answer:
 * [FFT_THRESHOLD] is chosen so both agree to well inside the tolerance the
 * outcome graph is tested against.
 */
internal object Convolution {
  /**
   * Above this many multiply-adds, the direct method gives way to the
   * transform. A few thousand operations is where the constant factor of two
   * forward transforms and one inverse starts to pay for itself.
   */
  const val FFT_THRESHOLD: Long = 20_000L

  /** The distribution of `a + b`, for independent `a` and `b`. */
  fun of(
    a: Pmf,
    b: Pmf,
  ): Pmf {
    val left = a.probabilities()
    val right = b.probabilities()
    // Checked before anything is allocated. A set file may give a d20 faces in
    // the thousands, and a thousand of those reach ten million totals — an
    // array nobody asked for and a phone cannot spare.
    checkedSize(left.size.toLong() + right.size.toLong() - 1)
    val work = left.size.toLong() * right.size.toLong()
    val weights = if (work <= FFT_THRESHOLD) direct(left, right) else viaTransform(left, right)
    return Pmf.of(a.offset + b.offset, weights)
  }

  /** The distribution of `a` added to itself [times] times, by repeated squaring. */
  fun repeated(
    a: Pmf,
    times: Int,
  ): Pmf {
    require(times >= 1) { "a sum of $times dice is not a sum" }
    var result: Pmf? = null
    var power = a
    var remaining = times
    while (remaining > 0) {
      if (remaining and 1 == 1) result = result?.let { of(it, power) } ?: power
      remaining = remaining shr 1
      if (remaining > 0) power = of(power, power)
    }
    return requireNotNull(result)
  }

  private fun direct(
    left: DoubleArray,
    right: DoubleArray,
  ): DoubleArray {
    val out = DoubleArray(left.size + right.size - 1)
    left.forEachIndexed { i, a ->
      if (a != 0.0) right.forEachIndexed { j, b -> out[i + j] += a * b }
    }
    return out
  }

  private fun viaTransform(
    left: DoubleArray,
    right: DoubleArray,
  ): DoubleArray {
    val size = left.size + right.size - 1
    val length = Fft.lengthFor(size)
    val leftReal = left.copyOf(length)
    val leftImaginary = DoubleArray(length)
    val rightReal = right.copyOf(length)
    val rightImaginary = DoubleArray(length)
    Fft.transform(leftReal, leftImaginary)
    Fft.transform(rightReal, rightImaginary)
    for (index in 0 until length) {
      val real = leftReal[index] * rightReal[index] - leftImaginary[index] * rightImaginary[index]
      val imaginary = leftReal[index] * rightImaginary[index] + leftImaginary[index] * rightReal[index]
      leftReal[index] = real
      leftImaginary[index] = imaginary
    }
    Fft.transform(leftReal, leftImaginary, inverse = true)
    // The transform leaves a little numerical dust below zero, which is not a
    // probability; Pmf normalises what is left.
    return DoubleArray(size) { max(0.0, leftReal[it]) }
  }
}
