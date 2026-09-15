package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.simulation.api.Impact

/**
 * How one impact is played: how fast the sample runs and how loud it is.
 *
 * @param pitch a multiplier on the sample's own rate, `1` for a standard die.
 * @param volume `0` to `1`.
 */
data class Voice(
  val pitch: Double,
  val volume: Double,
)

/**
 * Turns an impact into a [Voice] — the "pitch tracks impulse and die size" half
 * of Step 5.6 (`docs/TODO.md`).
 *
 * Both halves are physical rather than decorative. A small solid rings higher
 * than a big one of the same stuff, in inverse proportion to its size, so a
 * shrunk die in a crowded tray comes up brighter and a 25 mm d20 comes down —
 * which is what a handful of real dice sounds like. And a harder knock excites
 * more of the high partials of anything it hits, so strength lifts the pitch a
 * little as well as the volume.
 *
 * It is arithmetic over two numbers on the impact, so it is tested on a JVM and
 * nothing that plays a sound has an opinion about it.
 */
object ImpactVoice {
  /** The size a die's own pitch is measured from: a standard 16 mm d6. */
  const val REFERENCE_SIZE_MM: Double = 16.0

  /**
   * The range a sample may be played at.
   *
   * An octave either way, which is what `AudioTrack` will resample to and as
   * far as a short impact sound stays recognisable: faster than that and felt
   * becomes a click, slower and glass becomes a thud.
   */
  const val SLOWEST_PITCH: Double = 0.5

  /** And the other end of it. */
  const val FASTEST_PITCH: Double = 2.0

  /** How much of the pitch a hard knock is worth, at most. */
  const val BRIGHTNESS: Double = 0.2

  /** The quietest an impact worth playing is played, so it is still heard. */
  const val QUIETEST_VOLUME: Double = 0.25

  /** How [impact] is played. */
  fun of(impact: Impact): Voice =
    Voice(
      pitch = pitchOf(impact.dieSizeMm, impact.strength),
      volume = QUIETEST_VOLUME + (1.0 - QUIETEST_VOLUME) * impact.strength,
    )

  /** A small die rings higher, and a hard knock brighter. */
  fun pitchOf(
    dieSizeMm: Double,
    strength: Double,
  ): Double =
    (REFERENCE_SIZE_MM / dieSizeMm * (1.0 + BRIGHTNESS * strength))
      .coerceIn(SLOWEST_PITCH, FASTEST_PITCH)
}

/**
 * One haptic tick: how long the actuator is driven and how hard.
 *
 * @param milliseconds how long.
 * @param amplitude `1` to `255`, which is what `VibrationEffect.createOneShot`
 *   takes on a phone with amplitude control.
 */
data class Tick(
  val milliseconds: Long,
  val amplitude: Int,
) {
  init {
    require(milliseconds > 0) { "a tick lasts some time, not $milliseconds ms" }
    require(amplitude in HapticTick.FAINTEST..HapticTick.STRONGEST) { "$amplitude is not an amplitude" }
  }
}

/**
 * Turns an impact into a tick — "haptics fire on real impacts only"
 * (`docs/TODO.md`, Step 5.6).
 *
 * The "only" is not enforced here and could not be: by the time an impact
 * exists it *is* a real one, because a die sliding and a die at rest change
 * speed by less than the step's own gravity explains and are never reported
 * (`ImpactRule`). What is decided here is how a real one feels — short, because
 * a die landing is an event rather than a state, and harder for a harder knock.
 */
object HapticTick {
  /** The shortest the actuator is driven for, in milliseconds. */
  const val SHORTEST_MILLIS: Long = 8

  /** And the longest, for the hardest impact there is. A tick, never a buzz. */
  const val LONGEST_MILLIS: Long = 22

  /** The faintest amplitude worth asking for; below this nothing is felt. */
  const val FAINTEST: Int = 70

  /** And the strongest the API takes. */
  const val STRONGEST: Int = 255

  /** How [impact] feels. */
  fun of(impact: Impact): Tick = of(impact.strength)

  /** How an impact of this strength feels, `0` to `1`. */
  fun of(strength: Double): Tick {
    val share = strength.coerceIn(0.0, 1.0)
    return Tick(
      milliseconds = SHORTEST_MILLIS + ((LONGEST_MILLIS - SHORTEST_MILLIS) * share).toLong(),
      amplitude = FAINTEST + ((STRONGEST - FAINTEST) * share).toInt(),
    )
  }
}
