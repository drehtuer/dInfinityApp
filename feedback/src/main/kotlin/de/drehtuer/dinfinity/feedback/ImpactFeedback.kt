package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.Struck

/** Something that can make the phone tick. */
fun interface Buzzer {
  /** One tick, now. */
  fun tick(tick: Tick)

  companion object {
    /** A phone with no actuator, or a player who turned haptics off. */
    val NONE: Buzzer = Buzzer { }
  }
}

/** Something that can make an impact sound. */
interface Speaker : AutoCloseable {
  /** Get ready to play this table's impacts, before the first one arrives. */
  fun prepare(sound: TableSound)

  /** One impact on [sound], played [voice]'s way, now. */
  fun play(
    sound: TableSound,
    voice: Voice,
  )

  companion object {
    /** Says nothing, which is what sound being off means. */
    val NONE: Speaker =
      object : Speaker {
        override fun prepare(sound: TableSound) = Unit

        override fun play(
          sound: TableSound,
          voice: Voice,
        ) = Unit

        override fun close() = Unit
      }
  }
}

/**
 * Plays a roll's impacts (`docs/physics-and-rendering.md`, "Haptics and
 * sound").
 *
 * **One player, and the clock is a parameter.** A watched tray hands over each
 * frame's impacts with no time to spread them over; power-saving mode hands
 * over the whole finished throw with about a second. Both arrive here, both go
 * through [ImpactTrack], and there is no second implementation to keep in step
 * with the first — which is the same argument `LiveRoll` makes about the two
 * modes one level down (`docs/architecture.md`, decision 48).
 *
 * Every decision in it is plain Kotlin and every effect is behind [Buzzer] or
 * [Speaker], so it is tested on a JVM with a fake of each and a scheduler that
 * runs everything at once.
 *
 * @param haptics whether the phone ticks. Read when the roll screen opens and
 *   not watched, like power saving and the shake: a roll that started buzzing
 *   half way through is not a setting taking effect
 *   (`docs/architecture.md`, decision 16).
 * @param sound whether there is a noise, on the same terms.
 * @param later how a cue is held until its moment. The default plays everything
 *   at once, which is right for a watched tray — every cue it is given is
 *   already at zero — and wrong for the second's worth power-saving hands over,
 *   so the app passes a real one ([AndroidFeedback]).
 * @param stop what to give back besides the speaker, which for the real player
 *   is the thread cues wait on.
 */
class ImpactFeedback(
  private val buzzer: Buzzer = Buzzer.NONE,
  private val speaker: Speaker = Speaker.NONE,
  private val haptics: Boolean = true,
  private val sound: Boolean = true,
  private val later: (Long, () -> Unit) -> Unit = { _, work -> work() },
  private val stop: () -> Unit = {},
) : Impacts,
  AutoCloseable {
  /** Which table's impacts these are, as the tray last said. */
  private var table: TableSound = TableSound.Felt

  /** True when this is going to play anything at all. */
  val plays: Boolean get() = haptics || sound

  override fun on(sound: TableSound) {
    table = sound
    if (this.sound) speaker.prepare(sound)
  }

  override fun play(
    impacts: List<Impact>,
    overSeconds: Double,
  ) {
    if (!plays) return
    ImpactTrack.cues(impacts, overSeconds).forEach { cue ->
      // A cue at zero still goes through the scheduler rather than round it:
      // one path, so a bug in the holding cannot be a bug in only one mode.
      later(cue.atMillis) { fire(cue.impact) }
    }
  }

  override fun close() {
    speaker.close()
    stop()
  }

  private fun fire(impact: Impact) {
    if (haptics) buzzer.tick(HapticTick.of(impact))
    if (sound) speaker.play(soundOf(impact), ImpactVoice.of(impact))
  }

  /**
   * Which sound set an impact belongs to.
   *
   * Dice hitting each other sound like dice rather than like the table they
   * are on, whatever the table is made of — so they take the plastic preset,
   * which is what a set of acrylic dice is. The table decides everything else,
   * which is what a `sound` preset means (`docs/tables.md`).
   */
  private fun soundOf(impact: Impact): TableSound = if (impact.struck == Struck.Die) TableSound.Plastic else table
}
