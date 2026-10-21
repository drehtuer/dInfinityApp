package de.drehtuer.dinfinity.simulation.api

import kotlin.math.abs

/**
 * How the hand let the dice go: which way across the tray, and how fast
 * (`docs/physics-and-rendering.md`, "The hand lets go"; `docs/architecture.md`,
 * decision 105).
 *
 * The dice are shaken in the tray, in sight, as they always were; when the
 * hand stops, it tosses them the way its last stroke went. Everything after
 * that is the solver's — flight, landing, bounces, the tumble — with nothing
 * but gravity on the dice. Before the toss, a shake's last stroke braked the
 * dice into a wall or each other and they stopped almost as the hand did.
 *
 * It says nothing about a face. It decides how hard and which way the dice
 * are thrown, the way a hand does, and the physics decides the rest.
 *
 * @param direction which way the dice are thrown, a unit vector along the
 *   floor; null when the hand moved no way in particular, and the throw's own
 *   seed then picks one.
 * @param speedMmPerSecond how fast they leave the hand along it.
 */
data class Release(
  val direction: Vector3?,
  val speedMmPerSecond: Double,
) {
  init {
    require(speedMmPerSecond.isFinite() && speedMmPerSecond >= 0.0) { "a throw of $speedMmPerSecond mm/s" }
    require(direction == null || (abs(direction.length - 1.0) < UNIT && direction.z == 0.0)) {
      "a throw's direction is a unit vector along the floor, not $direction"
    }
  }

  companion object {
    /**
     * The release [samples] come to: the hand's last stroke, read as the throw.
     *
     * **Integrated, then the last stroke's fastest moment.** The samples are
     * the phone's acceleration in the tray's frame, so its velocity is their
     * sum over time. A phone that is picked up still, shaken and stopped
     * starts and ends at rest, so any drift the sum picked up is taken out as
     * a straight line from the first moment to the last. What is left swings
     * to and fro with the strokes, and the throw is the **last** of them: in
     * the [LAST_STROKE_SECONDS] seconds before the hand went quiet, the last
     * run of moments moving at least [LOBE_SHARE] of the fastest there, and
     * the fastest moment of that run. Not simply the fastest moment — a
     * window long enough to hold one stroke whole holds part of the one
     * before it, going the other way, and a throw goes the way the hand was
     * going when it let go.
     *
     * The speed is that of the hand times [GAIN], held to
     * [LEAST_MM_PER_SECOND]..[MOST_MM_PER_SECOND]: a gentle shake is still a
     * throw, and a wild one is not a throw past the tray's walls. A hand that
     * moved no way in particular — a shake straight at the glass, or no
     * samples at all — throws at the least speed in no direction of its own.
     */
    fun of(samples: List<ShakeSample>): Release {
      val moments = samples.sortedBy { it.stepIndex }
      if (moments.size < 2) return Release(direction = null, speedMmPerSecond = LEAST_MM_PER_SECOND)
      val velocities = velocitiesOf(moments)
      val end = lastActive(moments)
      val from = moments[end].stepIndex - LAST_STROKE_SECONDS * STEPS_PER_SECOND
      val window = (0..end).filter { moments[it].stepIndex >= from }
      val fastest = lastStroke(window.map { velocities[it] })
      val speed = fastest.length
      if (speed < AIMLESS_MM_PER_SECOND) return Release(direction = null, speedMmPerSecond = LEAST_MM_PER_SECOND)
      return Release(
        direction = fastest * (1.0 / speed),
        speedMmPerSecond = (speed * GAIN).coerceIn(LEAST_MM_PER_SECOND, MOST_MM_PER_SECOND),
      )
    }

    /**
     * The hand's velocity along the floor at each moment, drift taken out.
     *
     * Each sample stands for the time until the next one (the last for the
     * time since the one before), because the sensor's samples are about 50 Hz
     * and land on uneven steps.
     */
    private fun velocitiesOf(moments: List<ShakeSample>): List<Vector3> {
      var x = 0.0
      var y = 0.0
      val raw =
        moments.indices.map { at ->
          val next = if (at + 1 < moments.size) moments[at + 1] else moments[at]
          val previous = if (at + 1 < moments.size) moments[at] else moments[at - 1]
          val seconds = (next.stepIndex - previous.stepIndex).toDouble() / STEPS_PER_SECOND
          x += moments[at].accelerationMmPerSecond2.x * seconds
          y += moments[at].accelerationMmPerSecond2.y * seconds
          Vector3(x, y, 0.0)
        }
      val first = moments.first().stepIndex
      val span = (moments.last().stepIndex - first).toDouble().coerceAtLeast(1.0)
      val drift = raw.last()
      return raw.mapIndexed { at, velocity ->
        velocity - drift * ((moments[at].stepIndex - first) / span)
      }
    }

    /** The fastest moment of the last stroke among [velocities], in order. */
    private fun lastStroke(velocities: List<Vector3>): Vector3 {
      val top = velocities.maxOfOrNull { it.length } ?: return Vector3.Zero
      val last = velocities.indexOfLast { it.length >= top * LOBE_SHARE }
      var first = last
      while (first > 0 && velocities[first - 1].length >= top * LOBE_SHARE) first--
      return velocities.subList(first, last + 1).maxBy { it.length }
    }

    /** The last moment the hand was still shaking rather than coming to a stop. */
    private fun lastActive(moments: List<ShakeSample>): Int {
      val active = moments.indexOfLast { it.accelerationMmPerSecond2.length >= ACTIVE_MM_PER_SECOND2 }
      return if (active >= 0) active else moments.lastIndex
    }

    /** The simulation's steps per second, which is what a sample's step counts in. */
    private const val STEPS_PER_SECOND: Int = 120

    /**
     * How long before the hand went quiet its last stroke is looked for. A
     * shake is three to five strokes a second, so this holds about one whole
     * stroke.
     */
    const val LAST_STROKE_SECONDS: Double = 0.35

    /**
     * How hard a moment has to be to count as shaking — the shake detector's
     * own start threshold (`ShakeThresholds`, 0.6 g), so the end of the shake
     * here is where the detector heard one.
     */
    const val ACTIVE_MM_PER_SECOND2: Double = 6_000.0

    /**
     * How fast, against the fastest moment near the end, the hand has to be
     * moving for a moment to be part of a stroke rather than the turn between
     * two.
     */
    const val LOBE_SHARE: Double = 0.6

    /** Under this the hand moved no way in particular. */
    const val AIMLESS_MM_PER_SECOND: Double = 80.0

    /**
     * How much faster the dice leave than the hand was moving: a wrist flicks
     * a throw on top of the arm's stroke. A first guess, to be set from the
     * owner's recorded throws (`docs/TODO.md`, Step 5.8).
     */
    const val GAIN: Double = 1.5

    /** The gentlest throw: still across the tray rather than dropped where the hand was. */
    const val LEAST_MM_PER_SECOND: Double = 600.0

    /** The hardest throw: about what a hand puts into dice at a table. */
    const val MOST_MM_PER_SECOND: Double = 1_600.0

    private const val UNIT: Double = 1e-6
  }
}
