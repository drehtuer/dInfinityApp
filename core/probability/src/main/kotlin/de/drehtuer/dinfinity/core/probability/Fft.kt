package de.drehtuer.dinfinity.core.probability

import kotlin.math.cos
import kotlin.math.sin

/**
 * An iterative radix-2 fast Fourier transform, in place, over split real and
 * imaginary arrays.
 *
 * It exists for one job: convolving two large distributions in
 * `O(n log n)` instead of `O(n²)`. `docs/probability.md` calls for it above
 * roughly 64 dice, and the difference is real — a thousand d20 have a support
 * twenty thousand wide, which the direct method would walk four hundred
 * million times.
 *
 * Split arrays rather than a complex type because this runs over hundreds of
 * thousands of points and one object per point would cost more than the
 * arithmetic does.
 */
internal object Fft {
  /**
   * Transforms [real] and [imaginary] in place.
   *
   * @param inverse the inverse transform, scaled by `1/n`, so a forward
   *   followed by an inverse gives back what went in.
   */
  fun transform(
    real: DoubleArray,
    imaginary: DoubleArray,
    inverse: Boolean = false,
  ) {
    val n = real.size
    require(n > 0 && n and (n - 1) == 0) { "the transform needs a power-of-two length, not $n" }
    reorder(real, imaginary)
    var span = 2
    while (span <= n) {
      val angle = (if (inverse) TURN else -TURN) / span
      val stepReal = cos(angle)
      val stepImaginary = sin(angle)
      butterflies(real, imaginary, span, stepReal, stepImaginary)
      span = span shl 1
    }
    if (inverse) {
      for (index in 0 until n) {
        real[index] /= n
        imaginary[index] /= n
      }
    }
  }

  /** A full turn in radians: the angle a transform of length one span walks round. */
  private const val TURN = 2.0 * Math.PI

  /** The smallest power of two at least [size]. */
  fun lengthFor(size: Int): Int {
    var length = 1
    while (length < size) length = length shl 1
    return length
  }

  /** Puts the entries in bit-reversed order, which is what makes the passes in-place. */
  private fun reorder(
    real: DoubleArray,
    imaginary: DoubleArray,
  ) {
    val n = real.size
    var target = 0
    for (index in 1 until n) {
      var bit = n shr 1
      while (target and bit != 0) {
        target = target xor bit
        bit = bit shr 1
      }
      target = target or bit
      if (index < target) {
        real[index] = real[target].also { real[target] = real[index] }
        imaginary[index] = imaginary[target].also { imaginary[target] = imaginary[index] }
      }
    }
  }

  private fun butterflies(
    real: DoubleArray,
    imaginary: DoubleArray,
    span: Int,
    stepReal: Double,
    stepImaginary: Double,
  ) {
    val half = span shr 1
    for (start in real.indices step span) {
      var twiddleReal = 1.0
      var twiddleImaginary = 0.0
      for (offset in 0 until half) {
        val low = start + offset
        val high = low + half
        val productReal = real[high] * twiddleReal - imaginary[high] * twiddleImaginary
        val productImaginary = real[high] * twiddleImaginary + imaginary[high] * twiddleReal
        real[high] = real[low] - productReal
        imaginary[high] = imaginary[low] - productImaginary
        real[low] += productReal
        imaginary[low] += productImaginary
        val nextReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
        twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
        twiddleReal = nextReal
      }
    }
  }
}
