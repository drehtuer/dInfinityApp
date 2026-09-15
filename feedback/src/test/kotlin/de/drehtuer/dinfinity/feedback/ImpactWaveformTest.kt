package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.core.model.TableSound
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The five impact sounds the app makes rather than ships
 * (`docs/tables.md`, "Table looks").
 *
 * They can be tested here precisely because they are generated: a decoded asset
 * would be a file to trust, and this is arithmetic with an answer.
 */
class ImpactWaveformTest {
  @Test
  fun `every preset has a sound`() {
    TableSound.entries.forEach { sound ->
      assertTrue("${sound.id} is silent", ImpactWaveform.of(sound).isNotEmpty())
    }
  }

  @Test
  fun `a table sounds the same every time it is asked`() {
    TableSound.entries.forEach { sound ->
      assertArrayEquals("${sound.id} is not the same twice", ImpactWaveform.of(sound), ImpactWaveform.of(sound))
    }
  }

  @Test
  fun `the five sound different from each other`() {
    val made = TableSound.entries.map { ImpactWaveform.of(it).toList() }

    assertEquals("two presets came out identical", TableSound.entries.size, made.distinct().size)
  }

  @Test
  fun `every one of them dies away`() {
    TableSound.entries.forEach { sound ->
      val samples = ImpactWaveform.of(sound)
      val quarter = samples.size / QUARTERS
      val opening = energyOf(samples, 0, quarter)
      val tail = energyOf(samples, samples.size - quarter, samples.size)

      assertTrue("${sound.id} does not decay: $opening then $tail", opening > tail * DECAY_FACTOR)
    }
  }

  @Test
  fun `nothing clips`() {
    TableSound.entries.forEach { sound ->
      ImpactWaveform.of(sound).forEach { sample ->
        assertTrue("${sound.id} clips", abs(sample.toInt()) <= Short.MAX_VALUE.toInt())
      }
    }
  }

  @Test
  fun `each of them actually uses the range it has`() {
    TableSound.entries.forEach { sound ->
      val loudest = ImpactWaveform.of(sound).maxOf { abs(it.toInt()) }

      assertTrue("${sound.id} is far too quiet at $loudest", loudest > Short.MAX_VALUE * QUIET_LIMIT)
    }
  }

  @Test
  fun `none of them runs longer than the cap`() {
    val cap = ImpactWaveform.LONGEST_MILLIS * ImpactWaveform.SAMPLE_RATE_HZ / MILLIS_PER_SECOND
    TableSound.entries.forEach { sound ->
      assertTrue("${sound.id} is too long", ImpactWaveform.of(sound).size <= cap)
    }
  }

  @Test
  fun `felt is the shortest of them and glass the longest`() {
    val lengths = TableSound.entries.associateWith { ImpactWaveform.of(it).size }

    assertEquals(TableSound.Felt, lengths.minByOrNull { it.value }?.key)
    assertEquals(TableSound.Glass, lengths.maxByOrNull { it.value }?.key)
  }

  private fun energyOf(
    samples: ShortArray,
    from: Int,
    until: Int,
  ): Double {
    var total = 0.0
    for (index in from until until) total += samples[index].toDouble() * samples[index]
    return sqrt(total / (until - from))
  }

  private companion object {
    const val QUARTERS = 4
    const val DECAY_FACTOR = 4.0
    const val QUIET_LIMIT = 0.5
    const val MILLIS_PER_SECOND = 1_000
  }
}
