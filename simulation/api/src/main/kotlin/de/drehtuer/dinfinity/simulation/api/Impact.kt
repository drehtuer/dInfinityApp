package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.TableSound
import kotlin.math.abs

/**
 * One moment where a die hit something hard enough to be worth hearing
 * (`docs/physics-and-rendering.md`, "Impacts").
 *
 * It is a *reading* of the roll and never an input to it. Nothing here reaches
 * the solver, nothing here is consulted by the correction ladder, and the same
 * seed comes to the same faces whether anybody is listening or not — which
 * `ImpactRecorderTest` asserts rather than assumes.
 *
 * It carries everything a player of it needs, so that nothing downstream has to
 * go back to the throw to find out what hit what: which step, which die, what
 * it struck, how hard, and how big the die was. Pitch tracks the last two
 * (`docs/TODO.md`, Step 5.6).
 *
 * @param stepIndex which fixed 1/120 s step this happened on. Simulated time,
 *   so a recorded roll replays its impacts at the same moments on every device.
 * @param dieIndex which die, in throw order.
 * @param struck what it hit.
 * @param speedChangeMmPerSecond what is left of the die's change in speed once
 *   the step's own gravity is accounted for — the impulse per unit of mass, and
 *   the only measure of "how hard" the bridge can give without a wider wire
 *   format ([ImpactRule]).
 * @param dieSizeMm how wide the die is, at the scale the capacity rule threw it
 *   (`docs/tables.md`). A small die rings higher.
 */
data class Impact(
  val stepIndex: Int,
  val dieIndex: Int,
  val struck: Struck,
  val speedChangeMmPerSecond: Double,
  val dieSizeMm: Double,
) {
  init {
    require(stepIndex >= 0) { "a step is counted from zero, not $stepIndex" }
    require(dieIndex >= 0) { "a die is numbered from zero, not $dieIndex" }
    require(speedChangeMmPerSecond >= 0.0) { "an impact cannot be $speedChangeMmPerSecond hard" }
    require(dieSizeMm > 0.0) { "a die is wider than nothing, not $dieSizeMm mm" }
  }

  /** How hard, from 0 at the quietest worth reporting to 1 at the loudest. */
  val strength: Double get() = ImpactRule.strengthOf(speedChangeMmPerSecond)

  companion object {
    /**
     * How many impacts one throw's record can hold.
     *
     * A hundred dice, each landing and tumbling for twelve seconds, produce a
     * few hundred; this is an order of magnitude above the worst case measured
     * and exists so that a physics bug cannot turn a record into a memory leak.
     * It is a cap on the *record*, not on the roll: impacts past it are dropped
     * and the dice carry on exactly as they were.
     */
    const val MAX_RECORDED: Int = 4_096
  }
}

/**
 * What a die hit.
 *
 * Three, because they are the three surfaces the tray has and they sound
 * different. Which one it was is read off the contact flags the bridge already
 * reports, so nothing was added to the wire format for it ([ImpactRule.struckBy]).
 */
enum class Struck {
  /** The tray floor, or its invisible ceiling. */
  Floor,

  /** A wall. */
  Wall,

  /** Another die. */
  Die,
}

/**
 * When a change in a die's speed is an impact, and how hard.
 *
 * All of it is arithmetic over two numbers the bridge already reports — the
 * speed before and after a step — so it is decided in Kotlin and tested on a
 * JVM, like everything else about a roll (`docs/architecture.md`, decision 40).
 * Nothing was added to the JNI wire format: a velocity *vector* per die per
 * step would be three more floats across the boundary a hundred and forty
 * thousand times a roll, to say something the scalar already says.
 *
 * The rule is subtraction. Between one step and the next, gravity alone can
 * change a free die's speed by `|g| × 1/120 s` and no more, and friction on a
 * sliding die takes away less than that again. So the part of a change that
 * gravity does *not* explain is the part something else did — which is what an
 * impact is. That is also why a die sliding and a die at rest report nothing
 * (`docs/TODO.md`, Step 5.6: "haptics fire on real impacts only").
 */
object ImpactRule {
  /**
   * How much of the step's gravity a change in speed is forgiven.
   *
   * Twice, rather than once, because a shake can reverse which way the force
   * points between one step and the next: a die falling through that reversal
   * changes speed by twice the allowance without touching anything. Under
   * ordinary gravity the allowance is 163 mm/s, which no landing comes close
   * to; under a four-gravity shake it rises to about 830 and only real slams
   * are reported — which during a shake that hard is the right answer anyway.
   */
  const val GRAVITY_ALLOWANCE: Double = 2.0

  /** Below this, in mm/s, nothing is reported: it is a die settling, not a hit. */
  const val QUIETEST_MM_PER_SECOND: Double = 120.0

  /** At and above this it is as hard as an impact gets, for the purpose of playing it. */
  const val LOUDEST_MM_PER_SECOND: Double = 1_500.0

  /**
   * How long one die is left alone after an impact, in steps — a twentieth of
   * a second.
   *
   * A landing is several steps of contact, and reporting each of them would
   * turn one die hitting the table into a burst. Long enough to be one event,
   * short enough that a die bouncing twice is two.
   */
  const val QUIET_STEPS: Int = 6

  /**
   * The part of a die's change in speed that gravity does not account for, in
   * mm/s. Negative or zero for a die falling, sliding or standing still.
   */
  fun unexplained(
    beforeMmPerSecond: Double,
    afterMmPerSecond: Double,
    gravityMmPerSecond2: Double,
  ): Double =
    abs(afterMmPerSecond - beforeMmPerSecond) -
      GRAVITY_ALLOWANCE * abs(gravityMmPerSecond2) * SettleRule.TIMESTEP_SECONDS

  /** True when that much unexplained change is worth reporting as an impact. */
  fun isImpact(unexplainedMmPerSecond: Double): Boolean = unexplainedMmPerSecond >= QUIETEST_MM_PER_SECOND

  /** Where between the quietest and the loudest this sits, `0` to `1`. */
  fun strengthOf(speedChangeMmPerSecond: Double): Double =
    ((speedChangeMmPerSecond - QUIETEST_MM_PER_SECOND) / (LOUDEST_MM_PER_SECOND - QUIETEST_MM_PER_SECOND))
      .coerceIn(0.0, 1.0)

  /**
   * What the die hit, from the contacts the bridge reports.
   *
   * A die that is touching nothing the tray owns and has just lost speed hit
   * the only other thing there is, which is another die. That is why [Struck.Die]
   * is both the first answer and the last.
   */
  fun struckBy(
    touchingFloor: Boolean,
    touchingWall: Boolean,
    supportedByDie: Boolean,
  ): Struck =
    when {
      supportedByDie -> Struck.Die
      touchingWall -> Struck.Wall
      touchingFloor -> Struck.Floor
      else -> Struck.Die
    }
}

/**
 * Something that plays a roll's impacts — the listening counterpart of
 * `Renderer` (`docs/physics-and-rendering.md`, "Haptics and sound").
 *
 * The same sentence holds as for drawing: nothing here returns anything the
 * simulation could act on, so hearing a roll cannot change it. There is one
 * implementation that plays and one that does nothing, and power-saving mode
 * uses the same one normal mode does — the difference is the clock, not the
 * player.
 */
interface Impacts {
  /**
   * Which table these impacts happen on, so the right sound set is ready
   * before the first one arrives (`docs/tables.md`, "Table looks").
   *
   * Said when the tray is told about its table, and again whenever the look
   * changes. A package names one of five presets rather than shipping audio.
   */
  fun on(sound: TableSound)

  /**
   * Plays [impacts], with the whole list laid out over [overSeconds] of wall
   * time.
   *
   * **One player, two clocks.** Zero means "now", which is what a frame's
   * worth of impacts on a watched tray already is. A whole finished roll is
   * handed over with [REPLAY_SECONDS], which is power-saving mode: there are
   * no frames to pace it, so the impacts the dice actually made are replayed
   * over about a second rather than in real time
   * (`docs/physics-and-rendering.md`, "Power-saving mode").
   */
  fun play(
    impacts: List<Impact>,
    overSeconds: Double,
  )

  companion object {
    /**
     * How long a finished roll's impacts are played back over when there were
     * no frames to play them on.
     *
     * About a second: long enough to read as a handful of dice landing rather
     * than one noise, short enough that the sound has finished before anybody
     * has read the total.
     */
    const val REPLAY_SECONDS: Double = 1.0

    /** Plays nothing, and is what a tray has until something is wired to it. */
    val NONE: Impacts =
      object : Impacts {
        override fun on(sound: TableSound) = Unit

        override fun play(
          impacts: List<Impact>,
          overSeconds: Double,
        ) = Unit
      }
  }
}
