package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.ImpactRule
import de.drehtuer.dinfinity.simulation.api.Struck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Sound pitch tracks impulse and die size" and "haptics fire on real impacts
 * only" (`docs/TODO.md`, Step 5.6), as far as either can be asserted without a
 * phone.
 */
class ImpactVoiceTest {
  @Test
  fun `a standard die rings at the rate the sample was made for`() {
    assertEquals(1.0, ImpactVoice.pitchOf(ImpactVoice.REFERENCE_SIZE_MM, strength = 0.0), 1e-9)
  }

  @Test
  fun `a smaller die rings higher and a bigger one lower`() {
    val small = ImpactVoice.pitchOf(SMALL_MM, strength = 0.0)
    val standard = ImpactVoice.pitchOf(ImpactVoice.REFERENCE_SIZE_MM, strength = 0.0)
    val large = ImpactVoice.pitchOf(LARGE_MM, strength = 0.0)

    assertTrue("$small is not above $standard", small > standard)
    assertTrue("$large is not below $standard", large < standard)
  }

  @Test
  fun `a harder knock is brighter than a soft one on the same die`() {
    val soft = ImpactVoice.pitchOf(ImpactVoice.REFERENCE_SIZE_MM, strength = 0.0)
    val hard = ImpactVoice.pitchOf(ImpactVoice.REFERENCE_SIZE_MM, strength = 1.0)

    assertTrue("$hard is not above $soft", hard > soft)
    assertEquals(1.0 + ImpactVoice.BRIGHTNESS, hard, 1e-9)
  }

  @Test
  fun `pitch stays inside what a sample can be resampled to`() {
    listOf(TINY_MM, SMALL_MM, ImpactVoice.REFERENCE_SIZE_MM, LARGE_MM, HUGE_MM).forEach { size ->
      listOf(0.0, 0.5, 1.0).forEach { strength ->
        val pitch = ImpactVoice.pitchOf(size, strength)
        val playable = ImpactVoice.SLOWEST_PITCH..ImpactVoice.FASTEST_PITCH
        assertTrue("$pitch is outside the playable range", pitch in playable)
      }
    }
  }

  @Test
  fun `volume runs from the quietest worth hearing to the whole of it`() {
    assertEquals(ImpactVoice.QUIETEST_VOLUME, ImpactVoice.of(impact(ImpactRule.QUIETEST_MM_PER_SECOND)).volume, 1e-9)
    assertEquals(1.0, ImpactVoice.of(impact(ImpactRule.LOUDEST_MM_PER_SECOND)).volume, 1e-9)
  }

  @Test
  fun `a harder impact is louder`() {
    val soft = ImpactVoice.of(impact(300.0)).volume
    val hard = ImpactVoice.of(impact(1_200.0)).volume

    assertTrue("$hard is not above $soft", hard > soft)
  }

  @Test
  fun `the voice of an impact is its size and its strength together`() {
    val voice = ImpactVoice.of(impact(speed = 1_200.0, size = SMALL_MM))

    assertEquals(ImpactVoice.pitchOf(SMALL_MM, impact(1_200.0).strength), voice.pitch, 1e-9)
    assertTrue(voice.volume > ImpactVoice.QUIETEST_VOLUME)
  }

  @Test
  fun `a tick is short, and a harder one is longer and stronger`() {
    val soft = HapticTick.of(0.0)
    val hard = HapticTick.of(1.0)

    assertEquals(HapticTick.SHORTEST_MILLIS, soft.milliseconds)
    assertEquals(HapticTick.LONGEST_MILLIS, hard.milliseconds)
    assertEquals(HapticTick.FAINTEST, soft.amplitude)
    assertEquals(HapticTick.STRONGEST, hard.amplitude)
  }

  @Test
  fun `a tick is a tick rather than a buzz`() {
    assertTrue(HapticTick.LONGEST_MILLIS < A_BUZZ_MILLIS)
  }

  @Test
  fun `a strength outside nought to one is brought back inside it`() {
    assertEquals(HapticTick.of(0.0), HapticTick.of(-5.0))
    assertEquals(HapticTick.of(1.0), HapticTick.of(5.0))
  }

  @Test
  fun `an impact is turned into the tick its strength deserves`() {
    assertEquals(HapticTick.of(impact(1_200.0).strength), HapticTick.of(impact(1_200.0)))
  }

  @Test
  fun `a tick refuses a length or an amplitude that is not one`() {
    assertTrue(runCatching { Tick(milliseconds = 0, amplitude = 100) }.exceptionOrNull() is IllegalArgumentException)
    assertTrue(runCatching { Tick(milliseconds = 5, amplitude = 0) }.exceptionOrNull() is IllegalArgumentException)
  }

  private fun impact(
    speed: Double,
    size: Double = ImpactVoice.REFERENCE_SIZE_MM,
  ): Impact =
    Impact(
      stepIndex = 0,
      dieIndex = 0,
      struck = Struck.Floor,
      speedChangeMmPerSecond = speed,
      dieSizeMm = size,
    )

  private companion object {
    const val TINY_MM = 3.2
    const val SMALL_MM = 10.0
    const val LARGE_MM = 25.0
    const val HUGE_MM = 40.0
    const val A_BUZZ_MILLIS = 50L
  }
}
