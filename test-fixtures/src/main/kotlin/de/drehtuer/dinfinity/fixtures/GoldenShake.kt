package de.drehtuer.dinfinity.fixtures

/**
 * The shake the golden suite throws with: one recorded swing of a phone, the
 * same every time (`docs/physics-and-rendering.md`, "Shake input").
 *
 * It is generated rather than captured because a capture is a few hundred
 * lines of sensor noise that nobody can read and nobody can reason about, and
 * because what the suite has to pin is that the *driver* turns a given motion
 * into a given roll — not that a particular hand once moved a particular way.
 * Whether the thresholds match a real hand is a question for a phone and a
 * person (`docs/TODO.md`, Step 5.6).
 *
 * The wave is triangular: acceleration ramps up and back down through each
 * swing and reverses for the next, which is roughly what a hand shaking dice
 * does and, more to the point, is built from integer arithmetic alone. No
 * trigonometry means nothing here can be a bit out on somebody else's
 * runtime, which is the same concern `simulation/api`'s `Exact` answers for
 * the spawn (`docs/architecture.md`, decision 43).
 */
object GoldenShake {
  /** Steps in one swing: up, over, back. */
  const val SWING_STEPS: Int = 16

  /** How many swings the shake lasts. */
  const val SWINGS: Int = 6

  /** The whole shake, in fixed steps — 0.8 s at 120 Hz. */
  const val STEPS: Int = SWING_STEPS * SWINGS

  /** How hard the phone is pulled along its long axis, in mm/s² (about 1.2 g). */
  const val PEAK_MM_PER_SECOND2: Double = 12_000.0

  /** And across it, so the dice are not driven up and down one line. */
  const val CROSS_PEAK_MM_PER_SECOND2: Double = 4_000.0

  /** The recorded swing, one sample per simulation step from 0. */
  fun samples(): List<GoldenShakeSample> =
    List(STEPS) { step ->
      GoldenShakeSample(
        stepIndex = step,
        accelerationMmPerSecond2 =
          GoldenVector(
            x = wave(step) * PEAK_MM_PER_SECOND2,
            // A quarter-swing out of phase, so the two axes peak at different
            // moments and the motion is a shake rather than a shove.
            y = wave(step + SWING_STEPS / 2) * CROSS_PEAK_MM_PER_SECOND2,
            z = 0.0,
          ),
        // Held flat, screen up, for the length of the shake. Turning the phone
        // as well is a separate question and belongs to the device tier.
        gravity = GoldenVector(0.0, 0.0, -1.0),
      )
    }

  /**
   * The triangle at [step]: 0 at the start of a swing, ±1 at its middle, back
   * to 0 at its end, reversing sign each swing.
   */
  private fun wave(step: Int): Double {
    val half = SWING_STEPS / 2
    val into = step % SWING_STEPS
    val ramp = if (into <= half) into.toDouble() / half else (SWING_STEPS - into).toDouble() / half
    val sign = if ((step / SWING_STEPS) % 2 == 0) 1.0 else -1.0
    return sign * ramp
  }
}

/** One recorded moment of [GoldenShake]. */
data class GoldenShakeSample(
  val stepIndex: Int,
  val accelerationMmPerSecond2: GoldenVector,
  val gravity: GoldenVector,
)

/**
 * Three numbers, so this module can describe motion without depending on
 * `:simulation:api` — which depends on tests that depend on this.
 */
data class GoldenVector(
  val x: Double,
  val y: Double,
  val z: Double,
)
