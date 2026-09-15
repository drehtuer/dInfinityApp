package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the developer toggle keeps, and what a replay is
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Both halves are plain Kotlin over a `ThrowSpec` and a `SimulationOutcome`,
 * so both are decided here rather than on a phone.
 */
class AnomalyTest {
  private val table = TableLook(id = "plain", name = "Plain")
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `a clean roll is no anomaly, which is every roll on a build that works`() {
    assertNull(Anomaly.of(spec(), clean(), atEpochMs = 1L))
  }

  @Test
  fun `a forced settle is an anomaly, and carries the seed that reproduces it`() {
    val anomaly = requireNotNull(Anomaly.of(spec(seed = 42L), forced(2), atEpochMs = 99L))

    assertEquals(42L, anomaly.seed)
    assertEquals(99L, anomaly.atEpochMs)
    // The whole throw, not only its seed: that is what replays the roll, and
    // it is the reason an anomaly is worth writing down at all.
    assertEquals(spec(seed = 42L), anomaly.thrown)
    assertEquals(SettleRule.HARD_CAP_STEPS, anomaly.outcome.steps)
    assertEquals(2, anomaly.forcedSettles)
    assertEquals(0, anomaly.postRestCorrections)
    assertEquals(3, anomaly.diceCount)
    assertFalse(anomaly.invisibleHand)
    assertTrue(anomaly.what.contains("force-settled"))
  }

  @Test
  fun `a die touched after it stopped is named first, because it is the worse of the two`() {
    // A roll can do both at once. "A die was moved after it had stopped" is not
    // a detail beside "a roll ran out of time" — it is the accusation this app
    // cannot answer (`.claude/CLAUDE.md`).
    val both =
      requireNotNull(
        Anomaly.of(spec(), SimulationOutcome(faces = faces(), forcedSettles = 1, postRestCorrections = 1), 0L),
      )

    assertTrue(both.invisibleHand)
    assertTrue(both.what.contains("after coming to rest"))
  }

  @Test
  fun `a log keeps the last throw whether or not it went wrong`() {
    val notes = DeveloperNotes()
    val thrown = spec(seed = 5L)

    notes.landed(thrown, clean(), atEpochMs = 1L)

    // Nothing to report, and everything to replay: the last throw is what both
    // replay buttons act on, and a clean roll is the ordinary case.
    assertTrue(notes.anomalies.isEmpty())
    assertEquals(thrown, notes.lastThrow)
    assertEquals(clean().faces, notes.lastOutcome?.faces)
  }

  @Test
  fun `a log keeps the last anomalies and drops the oldest`() {
    val notes = DeveloperNotes(capacity = 2)

    (1..3).forEach { seed -> notes.landed(spec(seed = seed.toLong()), forced(1), atEpochMs = seed.toLong()) }

    // Bounded, because the thing it is watching for is a physics bug and a bug
    // that fires on every roll must not become a heap.
    assertEquals(listOf(2L, 3L), notes.anomalies.map(Anomaly::seed))
  }

  @Test
  fun `a log of nothing holds nothing`() {
    assertFailsWith<IllegalArgumentException> { DeveloperNotes(capacity = 0) }
  }

  @Test
  fun `clearing takes the last throw with it, so there is nothing left to replay`() {
    val notes = DeveloperNotes()
    notes.landed(spec(), forced(1), atEpochMs = 1L)

    notes.clear()

    assertTrue(notes.anomalies.isEmpty())
    assertNull(notes.lastThrow)
    assertNull(notes.lastOutcome)
  }

  @Test
  fun `the log every install has keeps nothing at all`() {
    val none = DeveloperLog.NONE

    none.landed(spec(), forced(1), atEpochMs = 1L)
    none.clear()

    assertTrue(none.anomalies.isEmpty())
    assertNull(none.lastThrow)
    assertNull(none.lastOutcome)
  }

  @Test
  fun `replaying a throw again is the same throw, seed and shake and all`() {
    val shaken = spec(seed = 11L).copy(shake = listOf(moment()))

    assertEquals(shaken, Replay.again(shaken))
  }

  @Test
  fun `replaying from a seed keeps everything else, because a seed is not a throw`() {
    val shaken = spec(seed = 11L).copy(shake = listOf(moment()))

    val other = Replay.withSeed(shaken, seed = 12L)

    assertEquals(12L, other.seed)
    assertEquals(shaken.dice, other.dice)
    assertEquals(shaken.table, other.table)
    assertEquals(shaken.geometry, other.geometry)
    assertEquals(shaken.dieScale, other.dieScale)
    // The shake above all: a throw replayed without the hand that made it is a
    // different throw (`docs/physics-and-rendering.md`, "Shake input").
    assertEquals(shaken.shake, other.shake)
  }

  @Test
  fun `a seed is a number and nothing else`() {
    assertEquals(42L, Replay.seedOf("42"))
    assertEquals(-42L, Replay.seedOf("-42"))
    assertEquals(42L, Replay.seedOf("  42 "))
    assertNull(Replay.seedOf(""))
    assertNull(Replay.seedOf("4 2"))
    assertNull(Replay.seedOf("forty-two"))
    // Past what a Long holds, which is not a seed this app ever produced.
    assertNull(Replay.seedOf("99999999999999999999"))
  }

  @Test
  fun `an empty log says so in words a person can read`() {
    assertEquals(AnomalyReport.NOTHING, AnomalyReport.text(emptyList()))
  }

  @Test
  fun `a report is one line per anomaly, with the seed on it`() {
    val anomaly = requireNotNull(Anomaly.of(spec(seed = 7L), forced(1), atEpochMs = 3L))

    val text = AnomalyReport.text(listOf(anomaly, anomaly))

    assertEquals(2, text.lines().size)
    assertTrue(text.contains("seed=7"))
    assertTrue(text.contains("forced=1"))
    assertTrue(text.contains("postRest=0"))
    assertTrue(text.contains("dice=3"))
  }

  private fun moment(): ShakeSample =
    ShakeSample(stepIndex = 0, accelerationMmPerSecond2 = Vector3.Zero, gravity = Vector3.Zero)

  private fun faces(): Map<Int, Int> = mapOf(0 to 1, 1 to 2, 2 to 3)

  private fun clean(): SimulationOutcome = SimulationOutcome(faces = faces(), steps = 200)

  private fun forced(count: Int): SimulationOutcome =
    SimulationOutcome(faces = faces(), steps = SettleRule.HARD_CAP_STEPS, forcedSettles = count)

  private fun spec(seed: Long = 1L): ThrowSpec =
    ThrowSpec(
      dice =
        List(3) {
          DieInstance(
            index = it,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = StandardDice.d20,
          )
        },
      geometry = geometry,
      table = table,
      seed = seed,
    )
}
