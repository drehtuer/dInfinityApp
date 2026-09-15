package de.drehtuer.dinfinity.feature.settings

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.Anomaly
import de.drehtuer.dinfinity.simulation.api.DeveloperNotes
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The developer screen's state (`docs/physics-and-rendering.md`, "Debug
 * tooling").
 *
 * The replay is a lambda here, which is the point of it being one on the
 * presenter: every rule about what a replay *is* — which spec it runs, which
 * seed it carries, whether the answer is supposed to agree — is decided in
 * Kotlin and settled on a JVM with no engine anywhere near it
 * (`docs/architecture.md`, decision 40).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperPresenterTest {
  /** Every spec a replay actually ran, in order. */
  private val ran = mutableListOf<ThrowSpec>()

  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val d6 = StandardDice.d6

  @Test
  fun `before anything has been thrown there is nothing to replay`() =
    runTest(UnconfinedTestDispatcher()) {
      val presenter = presenter(DeveloperNotes())

      assertEquals(ReplayState.Nothing, presenter.state)
      assertFalse(presenter.canReplay)
      // And pressing it anyway does nothing rather than throwing something.
      presenter.replayLast()
      assertEquals(ReplayState.Nothing, presenter.state)
    }

  @Test
  fun `the last throw is offered with its seed, which is the one place a seed is shown`() =
    runTest(UnconfinedTestDispatcher()) {
      val notes = DeveloperNotes()
      notes.landed(spec(seed = 7L), clean(), atEpochMs = 1L)

      val state = presenter(notes).state as ReplayState.Ready

      assertEquals(7L, state.seed)
      assertEquals(2, state.diceCount)
    }

  @Test
  fun `replaying the last roll runs its own spec and says whether it agreed`() =
    runTest(UnconfinedTestDispatcher()) {
      val notes = DeveloperNotes()
      val thrown = spec(seed = 7L)
      notes.landed(thrown, clean(), atEpochMs = 1L)
      val presenter = presenter(notes, run = ::record)

      presenter.replayLast()

      // The spec that landed, unchanged: the dice, the table, the scale, the
      // seed and the shake (`Replay.again`).
      assertEquals(listOf(thrown), ran)
      val replayed = presenter.state as ReplayState.Replayed
      assertEquals(7L, replayed.seed)
      assertEquals(listOf(3, 4), replayed.faces)
      assertEquals(true, replayed.reproduced)
    }

  @Test
  fun `a replay that came to different faces under the same seed is called out`() =
    runTest(UnconfinedTestDispatcher()) {
      // The most serious bug this app can have: determinism is goal 4, and a
      // seed that does not reproduce its roll is a release blocker.
      val notes = DeveloperNotes()
      notes.landed(spec(seed = 7L), clean(), atEpochMs = 1L)
      val presenter = presenter(notes, run = { SimulationOutcome(faces = mapOf(0 to 1, 1 to 1)) })

      presenter.replayLast()

      assertEquals(false, (presenter.state as ReplayState.Replayed).reproduced)
    }

  @Test
  fun `a replay from a seed keeps the dice and the shake, and is judged against nothing`() =
    runTest(UnconfinedTestDispatcher()) {
      val notes = DeveloperNotes()
      val thrown = spec(seed = 7L)
      notes.landed(thrown, clean(), atEpochMs = 1L)
      val presenter = presenter(notes, run = ::record)

      presenter.typeSeed("12")
      presenter.replayFromSeed()

      assertEquals(12L, ran.single().seed)
      assertEquals(thrown.dice, ran.single().dice)
      // A different seed is not supposed to agree with anything, so there is
      // no verdict to give.
      assertNull((presenter.state as ReplayState.Replayed).reproduced)
    }

  @Test
  fun `a half-typed seed throws nothing rather than throwing something else`() =
    runTest(UnconfinedTestDispatcher()) {
      val notes = DeveloperNotes()
      notes.landed(spec(seed = 7L), clean(), atEpochMs = 1L)
      val presenter = presenter(notes, run = ::record)

      presenter.typeSeed("-")
      presenter.replayFromSeed()

      assertNull(presenter.typedSeed)
      assertTrue(ran.isEmpty())
    }

  @Test
  fun `a replay with no throw behind it does nothing, seed or no seed`() =
    runTest(UnconfinedTestDispatcher()) {
      val presenter = presenter(DeveloperNotes(), run = ::record)

      presenter.typeSeed("12")
      presenter.replayFromSeed()

      assertTrue(ran.isEmpty())
    }

  @Test
  fun `an empty log is the expected state and says so`() =
    runTest(UnconfinedTestDispatcher()) {
      val presenter = presenter(DeveloperNotes())

      assertFalse(presenter.hasAnomalies)
      assertTrue(presenter.report.contains("what a working build looks like"))
    }

  @Test
  fun `an anomaly reaches the screen with its seed on it`() =
    runTest(UnconfinedTestDispatcher()) {
      val notes = DeveloperNotes()
      notes.landed(spec(seed = 7L), forced(), atEpochMs = 1L)

      val presenter = presenter(notes)

      assertTrue(presenter.hasAnomalies)
      assertEquals(listOf(7L), presenter.anomalies.map(Anomaly::seed))
      assertTrue(presenter.report.contains("seed=7"))
    }

  @Test
  fun `clearing empties the log and leaves nothing to replay`() =
    runTest(UnconfinedTestDispatcher()) {
      val notes = DeveloperNotes()
      notes.landed(spec(seed = 7L), forced(), atEpochMs = 1L)
      val presenter = presenter(notes)

      presenter.clear()

      assertFalse(presenter.hasAnomalies)
      assertEquals(ReplayState.Nothing, presenter.state)
      assertFalse(presenter.canReplay)
    }

  /** Records the spec it was asked to run, and reports a clean throw. */
  private fun record(spec: ThrowSpec): SimulationOutcome {
    ran += spec
    return clean()
  }

  private fun kotlinx.coroutines.test.TestScope.presenter(
    log: DeveloperNotes,
    run: (ThrowSpec) -> SimulationOutcome = { clean() },
  ): DeveloperPresenter = DeveloperPresenter(log = log, throwAgain = { run(it) }, scope = this)

  private fun clean(): SimulationOutcome = SimulationOutcome(faces = mapOf(0 to 3, 1 to 4), steps = 120)

  private fun forced(): SimulationOutcome =
    SimulationOutcome(faces = mapOf(0 to 3, 1 to 4), steps = SettleRule.HARD_CAP_STEPS, forcedSettles = 1)

  private fun spec(seed: Long): ThrowSpec =
    ThrowSpec(
      dice =
        List(2) {
          DieInstance(index = it, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = d6)
        },
      geometry = geometry,
      table = table,
      seed = seed,
    )
}
