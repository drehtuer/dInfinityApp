package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.Seeds
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.random.Random

/**
 * The five impact sounds, made rather than shipped
 * (`docs/tables.md`, "Table looks"; `docs/physics-and-rendering.md`).
 *
 * A package names one of these and never brings audio with it, because an audio
 * file is large and every decoder is an attack surface. The app therefore has
 * to have the five, and there are two ways to have them: ship them as assets
 * and decode them, or generate them. This generates them — sixteen-bit mono
 * PCM, written straight into an `AudioTrack` — which means **no decoder takes
 * part at all**, on our own files or anybody's. That is the same argument the
 * format makes, carried one step further than it had to be.
 *
 * A die hitting a table is a short burst that dies away: some ringing, at a
 * pitch the surface decides, and some noise, in a mix the surface also decides.
 * Felt is almost all noise and gone in a twentieth of a second; glass is almost
 * all ring and hangs on. Four numbers per preset say the whole of it.
 *
 * It is deterministic, and that is not incidental: the noise comes from a
 * [Seeds]-stirred stream keyed by the preset, so a table sounds the same on
 * every launch and on every phone, and a test can assert bytes rather than
 * describe a hope.
 */
object ImpactWaveform {
  /**
   * The rate the samples are made at.
   *
   * 22,050 Hz, half of CD: an impact sound is a click and a low ring, and
   * everything in it that matters is under 8 kHz. It halves the memory a
   * handful of static `AudioTrack` buffers hold for nothing anyone can hear.
   */
  const val SAMPLE_RATE_HZ: Int = 22_050

  /** How long the longest of them may run, in milliseconds. */
  const val LONGEST_MILLIS: Int = 220

  /** How far the envelope is followed down before the tail is cut off. */
  private const val DECAY_TAILS = 4.0

  /** How loud the loudest sample of a waveform is, against full scale. */
  private const val HEADROOM = 0.9

  private const val PARTIAL_LEVEL = 0.45
  private const val TWO_PI = 2.0 * Math.PI

  /**
   * What one surface sounds like.
   *
   * @param hertz the pitch it rings at for a standard die.
   * @param decaySeconds how fast it dies away, as the time constant of an
   *   exponential — felt is a fifth of wood, and glass is four times it.
   * @param noise how much of it is noise rather than ring, `0` to `1`.
   * @param partial the second partial's pitch, as a multiple of [hertz]. Not a
   *   harmonic: a struck plate or block rings at ratios that are not whole
   *   numbers, which is most of what tells one from a tone generator.
   */
  private data class Timbre(
    val hertz: Double,
    val decaySeconds: Double,
    val noise: Double,
    val partial: Double,
  )

  private val TIMBRES: Map<TableSound, Timbre> =
    mapOf(
      TableSound.Felt to Timbre(hertz = 180.0, decaySeconds = 0.018, noise = 0.85, partial = 1.7),
      TableSound.Wood to Timbre(hertz = 520.0, decaySeconds = 0.038, noise = 0.35, partial = 2.7),
      TableSound.Glass to Timbre(hertz = 2_200.0, decaySeconds = 0.075, noise = 0.10, partial = 3.1),
      TableSound.Stone to Timbre(hertz = 700.0, decaySeconds = 0.022, noise = 0.55, partial = 2.1),
      TableSound.Plastic to Timbre(hertz = 1_100.0, decaySeconds = 0.030, noise = 0.30, partial = 2.4),
    )

  /**
   * One impact on [sound], as sixteen-bit mono PCM at [SAMPLE_RATE_HZ].
   *
   * Made fresh each call rather than cached here: a cache in an object outlives
   * every screen that wanted it, and the thing that actually needs one is the
   * speaker, which holds the buffer it wrote into the audio device anyway.
   */
  fun of(sound: TableSound): ShortArray {
    val timbre = TIMBRES.getValue(sound)
    val noise = Random(Seeds.stir(sound.ordinal.toLong()))
    val samples = lengthOf(timbre)
    val raw = DoubleArray(samples)
    var loudest = 0.0
    for (index in 0 until samples) {
      val seconds = index.toDouble() / SAMPLE_RATE_HZ
      val envelope = exp(-seconds / timbre.decaySeconds)
      val ring =
        Exact.sin(TWO_PI * timbre.hertz * seconds) +
          PARTIAL_LEVEL * Exact.sin(TWO_PI * timbre.hertz * timbre.partial * seconds)
      val hiss = noise.nextDouble(-1.0, 1.0)
      val value = envelope * ((1.0 - timbre.noise) * ring / (1.0 + PARTIAL_LEVEL) + timbre.noise * hiss)
      raw[index] = value
      loudest = maxOf(loudest, abs(value))
    }
    // Normalised rather than trusted: the mix of a ring and a noise stream has
    // no peak anybody can predict, and a waveform that clips is the one sound
    // a dice app must not make.
    val scale = if (loudest > 0.0) HEADROOM * Short.MAX_VALUE / loudest else 0.0
    return ShortArray(samples) { (raw[it] * scale).toInt().toShort() }
  }

  /**
   * How many samples a preset is worth: four time constants of its envelope,
   * which is down to under two percent, and never past [LONGEST_MILLIS].
   */
  private fun lengthOf(timbre: Timbre): Int {
    val wanted = ceil(timbre.decaySeconds * DECAY_TAILS * SAMPLE_RATE_HZ).toInt()
    val cap = LONGEST_MILLIS * SAMPLE_RATE_HZ / MILLIS_PER_SECOND
    return wanted.coerceIn(1, cap)
  }

  private const val MILLIS_PER_SECOND = 1_000
}
