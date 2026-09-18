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
  // Filtered on the way in for the same reason [add] filters: a sample naming
  // a step past the twelve-second cap has no step to drive, so it is neither
  // kept nor reported as part of what threw these dice.
  private val byStep: MutableMap<Int, ShakeSample> =
    samples.filter(ShakeSample::drivesAStep).associateByTo(mutableMapOf(), ShakeSample::stepIndex)

  /**
   * Which way down is on the table, which is always straight down.
   *
   * **The virtual table is horizontal, whatever the phone is doing.** The
   * gyroscope's reading is still recorded with every sample — it costs nothing
   * and a decision taken later may want it — but nothing turns the world by it.
   *
   * It was turned by it, and the cost was not subtle. `GravityTracker` starts
   * each shake at "straight down relative to the screen" and integrates
   * gyroscope rates with nothing to re-anchor them, so a vigorous shake could
   * leave down pointing sideways in the tray — and it stayed there for the
   * rest of the roll, because the last sample's direction is the one that
   * sticks. A tray whose down points at a wall is a chute: the dice slide into
   * that wall, pack against it and stop tumbling, which is what a hundred d6
   * heaped into one corner looked like on the phone
   * (`docs/physics-and-rendering.md`).
   */
  private val down: Vector3 = DEFAULT_GRAVITY

  /** The hand's part, held between samples. See [advance]. */
  private var hand: Vector3 = Vector3.Zero
  private var handFromStep: Int = Int.MIN_VALUE

  private var lastSampleStep: Int = byStep.keys.maxOrNull() ?: -1

  /**
   * Which way down is and how hard, in mm/s²: gravity as the gyroscope has
   * turned it, plus the inverse of whatever the hand is doing.
   */
  var gravity: Vector3 = DEFAULT_GRAVITY
    private set

  /** True when there is no shake at all and this is a tap-to-roll throw. */
  val isStill: Boolean get() = byStep.isEmpty()

  /**
   * Every moment that drove this roll, in step order — the record of the
   * throw, as opposed to the `ThrowSpec` it started as.
   *
   * This is the only place the whole of a live shake exists. A shake-driven
   * throw is spawned the instant the shake is confirmed, so its spec goes into
   * the world with an empty `shake` and the moments arrive afterwards, one at
   * a time, through [add]. Reading them back off the driver that consumed them
   * is what lets a finished roll be described as the spec that would replay it
   * — `spec.copy(shake = recorded())` — rather than as a spec that is missing
   * the half of itself that decided the answer
   * (`docs/physics-and-rendering.md`, "Shake input").
   *
   * In step order because a step is the only clock a sample has, and the map
   * it is held in has none. Deduplicated by step for free, because a step has
   * one gravity: what comes out is what went in to the world, not what came
   * off the sensors.
   */
  fun recorded(): List<ShakeSample> = byStep.values.sortedBy(ShakeSample::stepIndex)

  /**
   * True while the hand is still throwing these dice.
   *
   * A roll may not be declared over while this holds, however still the dice
   * look for a moment. A player who is still shaking has not finished throwing,
   * and dice that stopped in their hand would be a roll that ended because the
   * phone happened to be at the top of a swing (`docs/physics-and-rendering.md`,
   * "Shake input").
   *
   * It is a question about the *record*, so it answers the same during a live
   * roll and during a replay of one: a live sample is filed on the step the
   * world is about to take ([add]), and a replayed one on the step it drove
   * the first time, which is the same step.
   */
  fun stillShaking(step: Int): Boolean = !isStill && step <= lastSampleStep + HOLD_STEPS

  /**
   * Takes one more moment of a shake that is still happening, and puts it on
   * the next step the world will take.
   *
   * The dice are spawned when the shake begins, so most of a shake arrives
   * *after* the roll has started and has to reach it as it comes. The step
   * index a live sample carries is `ShakeRecorder`'s, counted in wall-clock
   * milliseconds from the start of the shake — and **that is a different clock
   * from the world's step counter.** They ran together while a watched roll
   * was stepped at real time; they do not once it is paced
   * ([de.drehtuer.dinfinity.simulation.api.RollPace]), and they never quite
   * did while a frame was late enough to drop steps
   * ([de.drehtuer.dinfinity.simulation.api.FrameClock.droppedSteps]).
   *
   * So a live sample is not filed under the step its own clock names. It is
   * filed under [atStep], the step the world is about to take, which is the
   * only meaning "the hand is doing this *now*" can have to a simulation. A
   * sample kept at a step the world passed a second ago drives nothing at all,
   * which is what shaking a phone at tumbling dice used to do
   * (`docs/physics-and-rendering.md`, "Shake input"); one kept at a step the
   * world will not reach for a second arrives a second late, which is what
   * pacing would otherwise have made of a second shake.
   *
   * The sample is **rewritten** to the step it was filed under rather than
   * merely stored there, so [recorded] is a record that replays: the record of
   * a throw is what actually drove it, not what came off the sensors.
   *
   * A sample for a step already held replaces it: sensors deliver faster than
   * 120 Hz and a step has one gravity.
   *
   * A sample past [ShakeSample.MAX_RECORDED] is dropped rather than kept. The
   * roll is given up at the twelve-second cap, so that sample names a step
   * that will never be taken; keeping it would let a hand that goes on shaking
   * grow this map — and the record handed out with the result — for as long as
   * it liked.
   *
   * @param atStep the next step the world will take, which the caller knows
   *   and this does not: a driver has no clock, by design.
   */
  fun add(
    sample: ShakeSample,
    atStep: Int,
  ) {
    val placed = sample.copy(stepIndex = atStep)
    if (!placed.drivesAStep) return
    byStep[atStep] = placed
    lastSampleStep = maxOf(lastSampleStep, atStep)
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
    when {
      sample != null -> {
        hand = capped(sample.accelerationMmPerSecond2)
        handFromStep = step
      }
      // The hand does not stop between sensor readings, so neither does its
      // force. It is let go only once the readings have actually stopped
      // coming, which is what ends a shake rather than what falls between two
      // moments of one.
      step - handFromStep > HOLD_STEPS -> hand = Vector3.Zero
    }
    gravity = down - hand
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

  companion object {
    /** Standard gravity in the units the tray is measured in. */
    const val GRAVITY_MM_PER_SECOND2: Double = 9_806.65

    /** Down, when nothing has said otherwise. */
    val DEFAULT_GRAVITY: Vector3 = Vector3(0.0, 0.0, -GRAVITY_MM_PER_SECOND2)

    /** About four gravities: harder than anyone shakes a fistful of dice. */
    const val MAX_SHAKE_MM_PER_SECOND2: Double = 40_000.0

    /**
     * How many steps the hand's last reading stands for before it is let go.
     *
     * A tenth of a second, which is a long time for a sensor and no time at
     * all for an arm. It has to be at least the gap between readings —
     * `SENSOR_DELAY_GAME` is about 50 Hz against the simulation's 120, so most
     * steps have no reading of their own and would otherwise be handed plain
     * gravity, leaving the dice driven on two steps in five and coasting
     * through the rest.
     */
    const val HOLD_STEPS: Int = 12
  }
}
