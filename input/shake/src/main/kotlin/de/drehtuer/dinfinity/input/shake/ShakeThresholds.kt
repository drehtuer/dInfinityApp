package de.drehtuer.dinfinity.input.shake

/**
 * When a shake starts, when it stops, and how finely it is recorded
 * (`docs/physics-and-rendering.md`, "Shake input").
 *
 * The numbers are published there, so a change here is a change there in the
 * same pull request (`.claude/CLAUDE.md`).
 */
object ShakeThresholds {
  /**
   * How hard the phone has to move, in millimetres per second squared, before
   * it counts as being shaken rather than carried.
   *
   * About 0.6 g, held for [START_MILLIS]. Both halves matter and the second is
   * the one that does the work: picking a phone up, setting it down or handing
   * it over all cross 0.6 g for a moment, and none of them stays there for a
   * tenth of a second. Shaking dice is one to three g for as long as the arm
   * keeps going.
   *
   * It started at 0.35 g and was raised because ordinary handling was
   * registering. Whether it is now *too* hard to trigger is a question a
   * person answers with a phone in their hand, not a number anyone can derive
   * (`docs/TODO.md`, Step 5.6).
   */
  const val START_MM_PER_SECOND2: Double = 6_000.0

  /** And how gently it has to move again before the shake has stopped. */
  const val STOP_MM_PER_SECOND2: Double = 1_500.0

  /** How long it has to keep moving before a jolt becomes a shake. */
  const val START_MILLIS: Long = 100

  /** And how long it has to be still before a pause becomes the end of one. */
  const val STOP_MILLIS: Long = 400

  /** A shake that goes on longer than this has stopped being an input. */
  const val MAX_SESSION_MILLIS: Long = 30_000

  /**
   * The grid every recorded acceleration is snapped to, in mm/s².
   *
   * Recording raw sensor doubles would make a roll unreproducible from its own
   * record, because the record has to be written, stored and read back; a
   * quantised sample survives that unchanged. The grid is fine enough that
   * nobody could feel the difference and coarse enough that a roll replays
   * exactly (`docs/physics-and-rendering.md`).
   */
  const val ACCELERATION_QUANTUM_MM_PER_SECOND2: Double = 1.0

  /** The same, for the components of the gravity direction. */
  const val DIRECTION_QUANTUM: Double = 1.0 / 4_096

  /** Sensor samples per second to keep, which is the simulation's own rate. */
  const val SAMPLE_RATE_HZ: Int = 120
}
