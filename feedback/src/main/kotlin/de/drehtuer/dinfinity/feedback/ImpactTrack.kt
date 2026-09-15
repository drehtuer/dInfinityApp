package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.simulation.api.Impact

/**
 * One impact to play, and how long from now
 * (`docs/physics-and-rendering.md`, "Haptics and sound").
 *
 * @param atMillis milliseconds from the moment the list was handed over. Zero
 *   for everything a watched tray plays, because a frame's worth of impacts
 *   has already happened.
 */
data class Cue(
  val atMillis: Long,
  val impact: Impact,
) {
  init {
    require(atMillis >= 0) { "a cue is played now or later, not $atMillis ms ago" }
  }
}

/**
 * Lays a roll's impacts out in wall time.
 *
 * **This is the whole of the difference between the two modes, and it is one
 * parameter.** A watched tray hands over the impacts of the frame it has just
 * stepped and asks for them now; power-saving mode hands over the whole throw
 * once the dice have stopped and asks for it across about a second, because
 * there were no frames to pace it with (`docs/physics-and-rendering.md`,
 * "Power-saving mode"). Both go through [cues], both come out as the same kind
 * of list, and there is no second player to keep in step with the first.
 *
 * It also thins. A hundred dice landing together are a hundred impacts inside
 * a few steps, and a phone can neither vibrate nor speak a hundred times in
 * that window — it would be one long buzz and one smeared noise. So at most one
 * cue survives per [GAP_MILLIS], and the one that survives is the hardest of
 * them: the sound of a roll is its loudest moments, not its average.
 */
object ImpactTrack {
  /**
   * How close together two cues may be.
   *
   * Forty-five milliseconds, which is about as fast as a hand feels two taps
   * as two rather than as a rattle, and about as fast as two short impact
   * sounds stay separate. It also bounds the work: a second of playback is at
   * most twenty-two cues however many dice were thrown.
   */
  const val GAP_MILLIS: Long = 45

  private const val MILLIS_PER_SECOND = 1_000.0

  /**
   * [impacts] laid out over [overSeconds] of wall time, thinned to one cue per
   * [GAP_MILLIS].
   *
   * Zero — or anything not positive — means *now*: every cue lands at zero and
   * the thinning leaves the hardest one. That is what a frame's worth of
   * impacts is, and it is why a watched tray needs no schedule of its own.
   *
   * The impacts are expected in step order, which is the order a roll records
   * them in. The first impact anchors the layout, so a batch that starts at
   * step 900 is played from the moment it arrives rather than fifteen hundred
   * steps in.
   */
  fun cues(
    impacts: List<Impact>,
    overSeconds: Double = 0.0,
  ): List<Cue> {
    if (impacts.isEmpty()) return emptyList()
    val first = impacts.first().stepIndex
    val span = impacts.last().stepIndex - first
    return thinned(impacts.map { Cue(atMillis = at(it.stepIndex - first, span, overSeconds), impact = it) })
  }

  /**
   * Where a step sits in the playback, in milliseconds.
   *
   * With no time to spread over, everything is now. With a span of nothing —
   * every impact on the same step, which is a handful of dice landing together
   * — everything is also now, because there is nothing to spread them along.
   */
  private fun at(
    step: Int,
    span: Int,
    overSeconds: Double,
  ): Long =
    when {
      overSeconds <= 0.0 || span <= 0 -> 0L
      else -> (overSeconds * MILLIS_PER_SECOND * step / span).toLong()
    }

  /**
   * At most one cue per [GAP_MILLIS], keeping the hardest of each window.
   *
   * The kept cue takes the *earlier* moment rather than its own: what is being
   * decided is which impact a moment sounds like, and moving the moment would
   * let a burst of dice drift later than the frame it happened on.
   */
  private fun thinned(cues: List<Cue>): List<Cue> {
    val kept = ArrayList<Cue>(cues.size)
    cues.forEach { cue ->
      val last = kept.lastOrNull()
      when {
        last == null || cue.atMillis - last.atMillis >= GAP_MILLIS -> kept += cue
        cue.impact.strength > last.impact.strength -> kept[kept.lastIndex] = cue.copy(atMillis = last.atMillis)
        else -> Unit
      }
    }
    return kept
  }
}
