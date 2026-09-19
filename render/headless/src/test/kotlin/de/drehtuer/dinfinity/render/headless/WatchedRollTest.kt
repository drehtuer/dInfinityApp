package de.drehtuer.dinfinity.render.headless

import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * What a roll answers when it has not been asked to say otherwise
 * (`docs/physics-and-rendering.md`).
 *
 * [WatchedRoll] carries defaults so that a fake in a test — or a roll that
 * genuinely keeps none of this — does not have to write out five answers that
 * are all "nothing". The defaults are still behaviour: the tray reads
 * [WatchedRoll.driven] every frame to decide how much of it the roll is worth,
 * and a default that answered the wrong way would pace a roll a hand was
 * throwing.
 */
class WatchedRollTest {
  @Test
  fun `a roll nobody is shaking is not being driven, so it is paced`() {
    // False rather than true is the safe way round: a roll wrongly called
    // driven runs at real time, which is the fault this whole mechanism was
    // written to fix, and a roll wrongly called watched is only slow.
    assertFalse(Plainest.driven)
  }

  @Test
  fun `a roll that keeps nothing says so rather than making something up`() {
    assertFalse(Plainest.stalled, "a roll that simply finished was reported as given up")
    assertEquals(emptyList(), Plainest.unsettled)
    assertEquals(emptyMap(), Plainest.countedSoFar)
    assertEquals(RollDiagnostics.NONE, Plainest.diagnostics)
  }

  private companion object {
    /** A frame of no dice at all, which is what a roll that keeps none shows. */
    val EMPTY = RenderFrame(previous = emptyList(), current = emptyList())
  }

  /** Every member the interface insists on, and not one it offers. */
  private object Plainest : WatchedRoll {
    override val running: Boolean = false
    override val outcome: SimulationOutcome? = null
    override val drivenBy: List<ShakeSample> = emptyList()
    override val impacts: List<Impact> = emptyList()

    override fun advance(elapsedSeconds: Double): RenderFrame = EMPTY

    override fun shake(sample: ShakeSample) = Unit

    override fun close() = Unit
  }
}
