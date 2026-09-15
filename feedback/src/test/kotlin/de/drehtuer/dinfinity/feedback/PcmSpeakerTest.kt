package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.core.model.TableSound
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The audio device, on a device Robolectric is pretending to be.
 *
 * Whether the dice *sound* like dice is a judgement with a phone in it
 * (`docs/TODO.md`, Step 5.6). What is worth asserting here is the shape of the
 * thing around that judgement: every preset can be readied and played, a sound
 * that was never readied readies itself, more impacts than there are voices go
 * round the pool rather than failing, and closing twice is closing once.
 *
 * A test that throws nothing is the assertion: every one of these is a path
 * where the wrong lifecycle raises `IllegalStateException` out of `AudioTrack`.
 * Robolectric's audio device hands back tracks that never initialise, which is
 * exactly the phone this has to survive — so what these prove is the degrading,
 * and the playing itself is proven on hardware.
 */
@RunWith(RobolectricTestRunner::class)
class PcmSpeakerTest {
  @Test
  fun `every preset can be readied and played`() {
    PcmSpeaker().use { speaker ->
      TableSound.entries.forEach { sound ->
        speaker.prepare(sound)
        speaker.play(sound, Voice(pitch = 1.0, volume = 1.0))
      }
    }
  }

  @Test
  fun `a sound that was never readied readies itself`() {
    PcmSpeaker().use { it.play(TableSound.Glass, Voice(pitch = 1.0, volume = 0.5)) }
  }

  @Test
  fun `readying the same sound twice costs nothing`() {
    PcmSpeaker().use { speaker ->
      speaker.prepare(TableSound.Wood)
      speaker.prepare(TableSound.Wood)
      speaker.play(TableSound.Wood, Voice(pitch = 1.0, volume = 1.0))
    }
  }

  @Test
  fun `more impacts than there are voices go round the pool`() {
    PcmSpeaker(voices = 2).use { speaker ->
      repeat(IMPACTS) { speaker.play(TableSound.Felt, Voice(pitch = 1.0, volume = 0.5)) }
    }
  }

  @Test
  fun `the whole playable range of pitches and volumes is accepted`() {
    PcmSpeaker().use { speaker ->
      listOf(ImpactVoice.SLOWEST_PITCH, 1.0, ImpactVoice.FASTEST_PITCH).forEach { pitch ->
        speaker.play(TableSound.Stone, Voice(pitch = pitch, volume = ImpactVoice.QUIETEST_VOLUME))
        speaker.play(TableSound.Stone, Voice(pitch = pitch, volume = 1.0))
      }
    }
  }

  @Test
  fun `a closed speaker says nothing rather than failing`() {
    val speaker = PcmSpeaker()
    speaker.prepare(TableSound.Plastic)
    speaker.close()

    speaker.prepare(TableSound.Wood)
    speaker.play(TableSound.Plastic, Voice(pitch = 1.0, volume = 1.0))
    speaker.close()
  }

  private companion object {
    const val IMPACTS = 7
  }
}
