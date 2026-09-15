package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.ImpactRule
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.Struck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one player, with a fake actuator and a fake speaker behind it
 * (`docs/physics-and-rendering.md`, "Haptics and sound").
 */
class ImpactFeedbackTest {
  private val buzzer = FakeBuzzer()
  private val speaker = FakeSpeaker()
  private val held = mutableListOf<Long>()

  @Test
  fun `a frame's impacts tick and sound once each`() {
    player().play(listOf(impact()), NOW)

    assertEquals(1, buzzer.ticks.size)
    assertEquals(1, speaker.played.size)
  }

  @Test
  fun `haptics off leaves the sound alone`() {
    player(haptics = false).play(listOf(impact()), NOW)

    assertTrue(buzzer.ticks.isEmpty())
    assertEquals(1, speaker.played.size)
  }

  @Test
  fun `sound off leaves the haptics alone`() {
    player(sound = false).play(listOf(impact()), NOW)

    assertEquals(1, buzzer.ticks.size)
    assertTrue(speaker.played.isEmpty())
  }

  @Test
  fun `both off plays nothing and schedules nothing`() {
    val silent = player(haptics = false, sound = false)

    assertFalse(silent.plays)
    silent.on(TableSound.Wood)
    silent.play(listOf(impact()), Impacts.REPLAY_SECONDS)

    assertTrue(buzzer.ticks.isEmpty())
    assertTrue(speaker.played.isEmpty())
    assertTrue("a silent player still scheduled work", held.isEmpty())
    assertTrue("a silent player still readied a sound", speaker.prepared.isEmpty())
  }

  @Test
  fun `the table decides what a die hitting it sounds like`() {
    val player = player()
    player.on(TableSound.Glass)
    player.play(listOf(impact(struck = Struck.Floor)), NOW)

    assertEquals(TableSound.Glass, speaker.played.single().first)
  }

  @Test
  fun `a wall is the table too`() {
    val player = player()
    player.on(TableSound.Wood)
    player.play(listOf(impact(struck = Struck.Wall)), NOW)

    assertEquals(TableSound.Wood, speaker.played.single().first)
  }

  @Test
  fun `dice hitting each other sound like dice, whatever the table is`() {
    val player = player()
    player.on(TableSound.Glass)
    player.play(listOf(impact(struck = Struck.Die)), NOW)

    assertEquals(TableSound.Plastic, speaker.played.single().first)
  }

  @Test
  fun `the table's sound is readied before the first impact arrives`() {
    player().on(TableSound.Stone)

    assertEquals(listOf(TableSound.Stone), speaker.prepared)
  }

  @Test
  fun `nothing is readied when there is to be no sound`() {
    player(sound = false).on(TableSound.Stone)

    assertTrue(speaker.prepared.isEmpty())
  }

  @Test
  fun `a whole throw is held across the second it was given`() {
    player().play(spread(), Impacts.REPLAY_SECONDS)

    assertTrue("nothing was held", held.any { it > 0 })
    assertEquals(held, held.sorted())
    assertTrue("held past the second it was given", held.max() <= A_SECOND)
  }

  @Test
  fun `a watched tray holds nothing, because its impacts have happened`() {
    player().play(listOf(impact(), impact(step = 2, die = 1)), NOW)

    assertTrue(held.all { it == 0L })
  }

  @Test
  fun `a harder impact is played louder and higher`() {
    player().play(listOf(impact(speed = 1_400.0, size = SMALL_MM)), NOW)

    val voice = speaker.played.single().second
    assertTrue(voice.volume > ImpactVoice.QUIETEST_VOLUME)
    assertTrue(voice.pitch > 1.0)
  }

  @Test
  fun `a player with nothing behind it takes everything and says nothing`() {
    // The defaults: no actuator, no speaker, and a scheduler that plays
    // everything where it stands. It is what a test of the layer above gets,
    // and it must not be a source of surprises.
    val bare = ImpactFeedback()

    bare.on(TableSound.Felt)
    bare.play(listOf(impact()), NOW)
    bare.play(listOf(impact(), impact(step = 30, die = 1)), Impacts.REPLAY_SECONDS)
    bare.close()

    assertTrue(bare.plays)
  }

  @Test
  fun `the actuator and the speaker that do nothing still take everything`() {
    Buzzer.NONE.tick(HapticTick.of(strength = 1.0))
    Speaker.NONE.prepare(TableSound.Glass)
    Speaker.NONE.play(TableSound.Glass, Voice(pitch = 1.0, volume = 1.0))
    Speaker.NONE.close()
  }

  @Test
  fun `closing gives back the speaker and whatever else was held`() {
    var stopped = false
    ImpactFeedback(buzzer = buzzer, speaker = speaker, stop = { stopped = true }).close()

    assertTrue(speaker.closed)
    assertTrue(stopped)
  }

  @Test
  fun `the same list played two ways reaches the same impacts`() {
    val impacts = spread()
    val live = player()
    live.play(impacts, NOW)
    val liveHeard = speaker.played.size

    val replayed = ImpactFeedbackTest().also { it.player().play(impacts, Impacts.REPLAY_SECONDS) }

    assertTrue("a live frame thinned the whole throw down to nothing", liveHeard >= 1)
    assertTrue("a replay played fewer than a live frame did", replayed.speaker.played.size >= liveHeard)
  }

  private fun player(
    haptics: Boolean = true,
    sound: Boolean = true,
  ): ImpactFeedback =
    ImpactFeedback(
      buzzer = buzzer,
      speaker = speaker,
      haptics = haptics,
      sound = sound,
      later = { millis, work ->
        held += millis
        work()
      },
    )

  private fun spread(): List<Impact> = (0 until TEN).map { impact(step = it * SPREAD_STEPS, die = it) }

  private fun impact(
    step: Int = 0,
    die: Int = 0,
    struck: Struck = Struck.Floor,
    speed: Double = ImpactRule.QUIETEST_MM_PER_SECOND + 300.0,
    size: Double = 16.0,
  ): Impact =
    Impact(
      stepIndex = step,
      dieIndex = die,
      struck = struck,
      speedChangeMmPerSecond = speed,
      dieSizeMm = size,
    )

  private class FakeBuzzer : Buzzer {
    val ticks = mutableListOf<Tick>()

    override fun tick(tick: Tick) {
      ticks += tick
    }
  }

  private class FakeSpeaker : Speaker {
    val prepared = mutableListOf<TableSound>()
    val played = mutableListOf<Pair<TableSound, Voice>>()
    var closed = false
      private set

    override fun prepare(sound: TableSound) {
      prepared += sound
    }

    override fun play(
      sound: TableSound,
      voice: Voice,
    ) {
      played += sound to voice
    }

    override fun close() {
      closed = true
    }
  }

  private companion object {
    const val NOW = 0.0
    const val A_SECOND = 1_000L
    const val TEN = 10
    const val SPREAD_STEPS = 24
    const val SMALL_MM = 10.0
  }
}
