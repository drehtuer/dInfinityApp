package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * Turns a recorded shake into the one thing the world is told each step: which
 * way, and how hard, down is (`docs/physics-and-rendering.md`, "Shake input").
 *
 * The tray never moves, and it cannot: the tray *is* the phone's screen, so in
 * the frame the player is looking at it is nailed down. What a shake does is
 * load everything inside it. That is the phone's acceleration applied
 * **inverse**, added to gravity — a die feels a hand yanking the tray sideways
 * as a force throwing it the other way, which is exactly what it is, and it is
 * why dice slam into the walls the way they do in a cupped hand.
 *
 * A tray that moved instead would be a moving wall, and a moving wall is the
 * one thing an engine cannot protect a small body from: continuous collision
 * detection sweeps a fast *die* against the world, never a fast wall against a
 * die. At the speed a hand shakes, a wall crosses more than its own thickness
 * in one step, arrives already inside a die, and the solver pushes that die out
 * of whichever face is nearer — which half the time is the outside. It was
 * written that way first, and the dice escaped the tray.
 *
 * The samples are indexed by simulation step, not by wall-clock moment, which
 * is what lets the same record drive normal mode, where it arrives as it
 * happens, and power-saving mode, where it is replayed as a batch afterwards,
 * and get the same roll out of both.
 *
 * This is a state machine over the step index and nothing else: no clock, no
 * sensor, no engine. That is why it is tested on the JVM.
 */
class ShakeDriver(
  samples: List<ShakeSample>,
) {
  private val byStep: MutableMap<Int, ShakeSample> = samples.associateByTo(mutableMapOf(), ShakeSample::stepIndex)

  private var down: Vector3 = DEFAULT_GRAVITY

  /**
   * Which way down is and how hard, in mm/s²: gravity as the gyroscope has
   * turned it, plus the inverse of whatever the hand is doing.
   */
  var gravity: Vector3 = DEFAULT_GRAVITY
    private set

  /** True when there is no shake at all and this is a tap-to-roll throw. */
  val isStill: Boolean get() = byStep.isEmpty()

  /**
   * Takes one more moment of a shake that is still happening.
   *
   * The dice are spawned when the shake begins, so most of a shake arrives
   * *after* the roll has started and has to reach it as it comes. Each sample
   * carries the step it belongs to, counted from the start of the shake by
   * `ShakeRecorder` — and because the frame clock never runs the simulation
   * faster than real time, the step a sample names is always still ahead of
   * the step the world is on. So a sample is in place before it is needed, and
   * the same record replayed afterwards drives exactly the same steps
   * (`docs/physics-and-rendering.md`, "Shake input").
   *
   * A sample for a step already held replaces it: sensors deliver faster than
   * 120 Hz and a step has one gravity.
   */
  fun add(sample: ShakeSample) {
    byStep[sample.stepIndex] = sample
  }

  /**
   * Takes the sample belonging to [step], if there is one.
   *
   * Call this once per step, before the world is stepped: [gravity] then holds
   * the value for the step about to be taken. A step with no sample keeps the
   * direction the last one left and drops the hand's part of it — a phone that
   * has stopped being sampled has not been put down, but it is no longer being
   * shaken either.
   */
  fun advance(step: Int) {
    val sample = byStep[step]
    if (sample == null) {
      gravity = down
      return
    }
    down = downFrom(sample.gravity)
    gravity = down - capped(sample.accelerationMmPerSecond2)
  }

  /**
   * Harder than [MAX_SHAKE_MM_PER_SECOND2] is not a hand; it is a sensor fault
   * or a phone that has been dropped. Letting it through would fire the dice at
   * the walls faster than any thickness of wall survives.
   */
  private fun capped(acceleration: Vector3): Vector3 {
    val size = acceleration.length
    return if (size <= MAX_SHAKE_MM_PER_SECOND2) {
      acceleration
    } else {
      acceleration * (MAX_SHAKE_MM_PER_SECOND2 / size)
    }
  }

  /**
   * The sample's gravity is a direction — the gyroscope has turned it, but its
   * length is whatever the sensor fusion made of it. Only the direction is
   * meant, so it is given the one magnitude gravity has.
   */
  private fun downFrom(direction: Vector3): Vector3 =
    if (direction.length <= 0.0) DEFAULT_GRAVITY else direction.normalised() * GRAVITY_MM_PER_SECOND2

  companion object {
    /** Standard gravity in the units the tray is measured in. */
    const val GRAVITY_MM_PER_SECOND2: Double = 9_806.65

    /** Down, when nothing has said otherwise. */
    val DEFAULT_GRAVITY: Vector3 = Vector3(0.0, 0.0, -GRAVITY_MM_PER_SECOND2)

    /** About four gravities: harder than anyone shakes a fistful of dice. */
    const val MAX_SHAKE_MM_PER_SECOND2: Double = 40_000.0
  }
}
