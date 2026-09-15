package de.drehtuer.dinfinity.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import de.drehtuer.dinfinity.core.model.TableSound

/**
 * Impact sounds, played as raw PCM (`docs/physics-and-rendering.md`, "Haptics
 * and sound").
 *
 * **Nothing is decoded, here or anywhere.** [ImpactWaveform] makes the samples
 * in plain Kotlin and they go straight into a static `AudioTrack` buffer, so
 * there is no file, no container and no decoder in the path — which is the same
 * reason `docs/tables.md` gives for a package naming a sound rather than
 * shipping one, applied to the app's own five as well as to a stranger's.
 * `SoundPool` was the obvious alternative and needs a file to load from; that
 * would mean writing a WAV into the cache at runtime and handing it back to a
 * decoder, which is a strictly worse version of the same thing.
 *
 * A pool per sound, rather than a pool shared between them. A static
 * `AudioTrack` holds one buffer, so a shared pool would rewrite the samples
 * every time a die-on-die impact followed a die-on-table one — which is most of
 * them. Two sounds are in play at once at most (the table's, and the plastic
 * that dice hitting each other use), so this is six short buffers.
 *
 * It decides nothing: [ImpactVoice] already said how fast and how loud. Like
 * `FilamentStage` it is handles and buffers, and it is the file in this module
 * that needs a device (`.claude/CLAUDE.md`).
 */
class PcmSpeaker(
  private val voices: Int = VOICES,
) : Speaker {
  private val pools = mutableMapOf<TableSound, List<AudioTrack>>()
  private val nextVoice = mutableMapOf<TableSound, Int>()
  private var closed = false

  override fun prepare(sound: TableSound) {
    if (closed || pools.containsKey(sound)) return
    val samples = ImpactWaveform.of(sound)
    pools[sound] = List(voices) { trackOf(samples) }.filterNotNull()
    nextVoice[sound] = 0
  }

  override fun play(
    sound: TableSound,
    voice: Voice,
  ) {
    if (closed) return
    prepare(sound)
    val pool = pools[sound] ?: return
    // An audio device that would not give us a track is a phone that plays no
    // impact sounds, which is a smaller loss than a crash in the middle of a
    // roll. The rest of the app is not told: there is nothing it could do.
    if (pool.isEmpty()) return
    val at = nextVoice.getValue(sound)
    nextVoice[sound] = (at + 1) % pool.size
    val track = pool[at]
    // Round robin rather than "find a free one": a track still playing is the
    // oldest of the pool, and cutting a forty-millisecond tail short to make
    // room for a newer impact is what a real pile of dice does to itself.
    track.pause()
    track.setVolume(voice.volume.toFloat())
    track.playbackRate = (ImpactWaveform.SAMPLE_RATE_HZ * voice.pitch).toInt()
    // The play head is where the last play left it, and a static buffer has to
    // be handed back before it will run again.
    track.reloadStaticData()
    track.play()
  }

  override fun close() {
    if (closed) return
    closed = true
    pools.values.flatten().forEach { track ->
      track.pause()
      track.release()
    }
    pools.clear()
    nextVoice.clear()
  }

  /**
   * One voice, or null when the audio device would not give us one.
   *
   * `AudioTrack` reports that by *being built* and then saying it is
   * uninitialised, and every call on one in that state throws — so it is asked
   * once, here, rather than guarded at each of the four calls that play a
   * sound.
   */
  private fun trackOf(samples: ShortArray): AudioTrack? {
    val track =
      AudioTrack
        .Builder()
        .setAudioAttributes(ATTRIBUTES)
        .setAudioFormat(
          AudioFormat
            .Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(ImpactWaveform.SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        ).setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * BYTES_PER_SAMPLE)
        .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
      track.release()
      return null
    }
    track.write(samples, 0, samples.size)
    return track
  }

  private companion object {
    /**
     * How many of each sound can be in the air at once.
     *
     * Three, against the twenty-two cues a second of playback can hold: the
     * samples are under a tenth of a second each, so three is already more
     * overlap than the rate limit can produce.
     */
    const val VOICES: Int = 3

    const val BYTES_PER_SAMPLE: Int = 2

    /**
     * What this audio is *for*.
     *
     * `USAGE_GAME` with `CONTENT_TYPE_SONIFICATION`: an impact is a sound the
     * app makes about something that happened, so it ducks and is silenced
     * with the phone's game and media volume rather than fighting music or a
     * call.
     */
    val ATTRIBUTES: AudioAttributes =
      AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
  }
}
