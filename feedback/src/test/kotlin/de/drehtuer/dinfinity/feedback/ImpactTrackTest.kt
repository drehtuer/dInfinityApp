package de.drehtuer.dinfinity.feedback

import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.ImpactRule
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.Struck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clock over a list of impacts, which is the whole of the difference
 * between the two modes (`docs/physics-and-rendering.md`, "Haptics and sound").
 */
class ImpactTrackTest {
  @Test
  fun `nothing to play is no cues`() {
    assertEquals(emptyList<Cue>(), ImpactTrack.cues(emptyList()))
    assertEquals(emptyList<Cue>(), ImpactTrack.cues(emptyList(), Impacts.REPLAY_SECONDS))
  }

  @Test
  fun `a frame's worth of impacts is played now`() {
    val cues = ImpactTrack.cues(listOf(impact(step = 300), impact(step = 302, die = 1)))

    assertTrue("a watched tray does not schedule anything", cues.all { it.atMillis == 0L })
  }

  @Test
  fun `a whole throw is spread across the second it is given`() {
    val cues = ImpactTrack.cues(spread(), Impacts.REPLAY_SECONDS)

    assertEquals(0L, cues.first().atMillis)
    assertEquals(MILLIS_PER_SECOND, cues.last().atMillis)
    assertEquals(cues.map(Cue::atMillis).sorted(), cues.map(Cue::atMillis))
  }

  @Test
  fun `the layout is anchored on the first impact rather than on step zero`() {
    // A batch that begins nine hundred steps in still starts at zero: it is
    // being played from the moment it was handed over.
    val late = listOf(impact(step = 900), impact(step = 1_100, die = 1))

    assertEquals(0L, ImpactTrack.cues(late, Impacts.REPLAY_SECONDS).first().atMillis)
  }

  @Test
  fun `impacts all on the same step are played together`() {
    val together = List(THREE) { impact(step = 40, die = it) }

    assertTrue(ImpactTrack.cues(together, Impacts.REPLAY_SECONDS).all { it.atMillis == 0L })
  }

  @Test
  fun `a hundred dice landing at once become one cue rather than a hundred`() {
    val pile = List(HUNDRED) { impact(step = 40, die = it) }

    assertEquals(1, ImpactTrack.cues(pile).size)
  }

  @Test
  fun `the cue that survives a crowded moment is the hardest of them`() {
    val quiet = impact(step = 40, die = 0, speed = 200.0)
    val loud = impact(step = 41, die = 1, speed = 1_400.0)
    val middling = impact(step = 42, die = 2, speed = 700.0)

    assertEquals(loud, ImpactTrack.cues(listOf(quiet, loud, middling)).single().impact)
  }

  @Test
  fun `the hardest impact keeps the earliest moment of its window`() {
    // Two impacts two steps apart, and a third far enough away to give the
    // playback something to spread across: the first two are one moment and
    // the loud one of them is what that moment sounds like.
    val cues =
      ImpactTrack.cues(
        listOf(
          impact(step = 0, speed = 200.0),
          impact(step = 2, die = 1, speed = 1_400.0),
          impact(step = 600, die = 2, speed = 400.0),
        ),
        Impacts.REPLAY_SECONDS,
      )

    assertEquals(2, cues.size)
    assertEquals(0L, cues.first().atMillis)
    assertEquals(1_400.0, cues.first().impact.speedChangeMmPerSecond, 0.0)
  }

  @Test
  fun `cues stay at least the gap apart`() {
    val cues = ImpactTrack.cues(spread(), Impacts.REPLAY_SECONDS)

    cues.zipWithNext { before, after ->
      assertTrue(
        "${after.atMillis} follows ${before.atMillis} too closely",
        after.atMillis - before.atMillis >= ImpactTrack.GAP_MILLIS,
      )
    }
  }

  @Test
  fun `a second of playback holds no more than the rate allows`() {
    val everyStep = (0 until FULL_ROLL_STEPS).map { impact(step = it, die = it % HUNDRED) }
    val cues = ImpactTrack.cues(everyStep, Impacts.REPLAY_SECONDS)

    assertTrue("${cues.size} cues in a second", cues.size <= MILLIS_PER_SECOND / ImpactTrack.GAP_MILLIS + 1)
  }

  @Test
  fun `the two clocks give different layouts of the same list`() {
    val impacts = spread()

    assertNotEquals(ImpactTrack.cues(impacts), ImpactTrack.cues(impacts, Impacts.REPLAY_SECONDS))
  }

  @Test
  fun `a negative clock is the same as no clock at all`() {
    assertEquals(ImpactTrack.cues(spread()), ImpactTrack.cues(spread(), -1.0))
  }

  @Test
  fun `a cue cannot be in the past`() {
    val failed =
      runCatching { Cue(atMillis = -1, impact = impact()) }.exceptionOrNull()

    assertTrue(failed is IllegalArgumentException)
  }

  private fun spread(): List<Impact> = (0 until TEN).map { impact(step = it * SPREAD_STEPS, die = it) }

  private fun impact(
    step: Int = 10,
    die: Int = 0,
    speed: Double = ImpactRule.QUIETEST_MM_PER_SECOND + 300.0,
  ): Impact =
    Impact(
      stepIndex = step,
      dieIndex = die,
      struck = Struck.Floor,
      speedChangeMmPerSecond = speed,
      dieSizeMm = 16.0,
    )

  private companion object {
    const val MILLIS_PER_SECOND = 1_000L
    const val THREE = 3
    const val TEN = 10
    const val HUNDRED = 100
    const val SPREAD_STEPS = 24
    const val FULL_ROLL_STEPS = 600
  }
}
