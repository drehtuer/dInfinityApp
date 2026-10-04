package de.drehtuer.dinfinity.simulation.api

/**
 * How much of a frame's real time a roll is worth, which is not always all of
 * it (`docs/physics-and-rendering.md`, "The simulation clock").
 *
 * Twenty d20 thrown into a tray the size of a phone stop in about eight tenths
 * of a second, and that is the physics being right rather than the physics
 * being hurried: measured on the reference device, friction moves the figure
 * from 0.73 s to 0.85 s across the whole range a die may plausibly have, and
 * the high end of it also pushes the dice into one another. There is no
 * setting of the solver that gives a player two seconds of tumbling, because a
 * hand does not put two seconds of tumbling into a fistful of dice.
 *
 * So the roll is not slowed down. **The same roll is shown over more
 * wall-clock time**: the same seed, the same fixed steps in the same order,
 * the same faces, the same [SimulationOutcome] — asked for at a rate that lets
 * a player watch them land instead of seeing them arrive. Nothing here reaches
 * the solver; it decides *when* a step is taken and never what the step is
 * ([FrameClock]).
 *
 * The one thing it must not slow is **a hand throwing these dice**. While the
 * phone is being shaken the player is driving the roll and not watching it,
 * and dice that answer a hand a beat late are the one failure this whole
 * mechanism could cause. So the pace is [WATCHED] only while nothing is
 * driving, and exactly `1.0` the moment something is — including a second
 * shake at dice that are still going ([ShakeDriver.stillShaking] is the
 * question, and it is the same question the roll asks before it is allowed to
 * end).
 */
object RollPace {
  /**
   * How much of a second of real time a roll nobody is driving is worth.
   *
   * **This is the number to change after judging it on a phone**, and it is
   * the only one: there is no second factor, no ramp and no per-phase
   * exception to reason about beside it. One means the roll runs at real time,
   * which is what it did before there was a pace at all.
   *
   * Four tenths, after the owner judged half on the Pixel 10a: with the
   * livelier tumble (60–120 rad/s, restitution 0.55) the spin read right but
   * the dice still crossed the tray a little too fast. It turns the measured
   * 0.96 s median for 20d20 into 2.4 s and the ninety-ninth, 1.76 s, into
   * 4.4 s — the dice are seen to travel and turn down onto a face, rather than
   * having finished by the time the eye reaches them. A single die stops well
   * inside the median, so `1d20` still answers in about two seconds. It is
   * also a factor a person can hold in their head while judging it:
   * everything takes two and a half times as long, including the wait for an
   * answer. Half was the first guess and is what this replaced.
   *
   * It is flat, deliberately, rather than easing from full speed into slow
   * motion as the dice settle. Two reasons. A pace that changes while the dice
   * are in view is indistinguishable from a phone dropping frames — the exact
   * fault [FrameClock.droppedSteps] exists to catch — so a roll that eased
   * would make its own smoothness unmeasurable. And the only discontinuity a
   * flat factor has is at the moment the hand lets go, which is a moment the
   * player caused and at which the dice's motion changes character anyway: the
   * tray stops hauling them about and there is nothing but gravity left. Slow
   * motion arriving there reads as the app taking over, not as a stutter.
   */
  const val WATCHED: Double = 0.4

  /**
   * How long a roll is worth watching, in simulated seconds.
   *
   * **Past this the pace goes back to one, and the reason is `100d4`.** The
   * twelve-second cap counts *simulated* time, so pacing does not change when
   * a roll gives up — only how long somebody waits to be told. At a flat
   * four tenths that would turn the cap into thirty seconds of staring at dice
   * that were never going to stop, on the one formula that already reaches it and that
   * a device session already reported as stuck.
   *
   * Three seconds is chosen against the measurements rather than picked: the
   * median 20d20 settles in 0.96 s and the ninety-ninth in 1.76 s, so a roll
   * that is behaving is paced from the first step to the last and never meets
   * this at all. What meets it is a roll that is not landing — and a roll
   * that is not landing is being *waited for* rather than watched, which is
   * the moment slow motion stops being a courtesy.
   *
   * It costs one discontinuity, three seconds in, on rolls that were going
   * wrong anyway; the alternative was making every ordinary roll worse to
   * spare the rare one.
   */
  const val WATCHED_SECONDS: Double = 3.0

  /** The same, in the fixed steps the loop actually counts. */
  val WATCHED_STEPS: Int = (WATCHED_SECONDS * SettleRule.STEPS_PER_SECOND).toInt()

  /**
   * The fewest dice a roll needs for its tail to be quickened ([paceFor]).
   *
   * Below this the last die tumbling *is* the roll — `1d20`, `3d6` — and it is
   * watched at [WATCHED] to the end, which is the pace the owner chose.
   */
  const val TAIL_FROM_DICE: Int = 10

  /**
   * While at least this share of a big roll's dice is still unread, the roll is
   * watched at [WATCHED].
   */
  const val TAIL_STARTS: Double = 0.25

  /** At this share or less still unread, a big roll is shown at real speed. */
  const val TAIL_ENDS: Double = 0.10

  /**
   * What [elapsedSeconds] of a real frame is worth to the roll.
   *
   * @param driven whether a hand is throwing these dice at this moment. True
   *   gives the frame back whole, because a shake is answered now or it is not
   *   answered.
   * @param stepsTaken how far the roll has got, in fixed steps. Past
   *   [WATCHED_STEPS] the frame is given back whole as well.
   * @param stillMoving how many of the roll's dice have not been read yet.
   * @param dice how many dice the roll threw.
   */
  fun secondsFor(
    elapsedSeconds: Double,
    driven: Boolean,
    stepsTaken: Int = 0,
    stillMoving: Int = 0,
    dice: Int = 0,
  ): Double = elapsedSeconds * paceFor(driven, stepsTaken, stillMoving, dice)

  /**
   * The share of real time a roll is shown at, from [WATCHED] up to `1.0`.
   *
   * **A big roll's tail is quickened.** A hundred d4 settle in a median 1.29 s
   * of simulated time but their slowest one in a hundred takes 3.09 s, and at
   * [WATCHED] that is 7.7 s on the screen of a few dice rocking while ninety-odd
   * lie still (Pixel 10a, 200 throws). So once most of a roll of at least
   * [TAIL_FROM_DICE] dice is read, the pace climbs with the share that is: it
   * is [WATCHED] while [TAIL_STARTS] or more is still moving, real speed at
   * [TAIL_ENDS] or less, and in between a straight line. It never passes real
   * speed — the last dice still move the way dice move — and like the rest of
   * this object it decides only *when* steps are taken, never what they are,
   * so the faces are the same at any pace (`docs/physics-and-rendering.md`,
   * "The simulation clock").
   */
  fun paceFor(
    driven: Boolean,
    stepsTaken: Int = 0,
    stillMoving: Int = 0,
    dice: Int = 0,
  ): Double {
    if (driven || stepsTaken >= WATCHED_STEPS) return 1.0
    if (dice < TAIL_FROM_DICE) return WATCHED
    val moving = stillMoving.toDouble() / dice
    return when {
      moving >= TAIL_STARTS -> WATCHED
      moving <= TAIL_ENDS -> 1.0
      else -> WATCHED + (1.0 - WATCHED) * (TAIL_STARTS - moving) / (TAIL_STARTS - TAIL_ENDS)
    }
  }
}
