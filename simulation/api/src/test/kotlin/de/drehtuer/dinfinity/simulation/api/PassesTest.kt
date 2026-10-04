package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
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
 * One throw and the throws of its unread dice (`docs/physics-and-rendering.md`,
 * "Avoiding stacked and cocked dice"; `docs/architecture.md`, decision 70).
 *
 * What is tested is the arithmetic the screen and the harness share: which die
 * of the first throw each die of a later pass is, the throw a shake makes for
 * the dice nobody could read, and the one outcome all of it comes to.
 */
class PassesTest {
  private val table = TableLook(id = "plain", name = "Plain")
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `a throw that read every die is finished on its first pass`() {
    val passes = Passes(spec(3))

    passes.landed(SimulationOutcome(faces = mapOf(0 to 1, 1 to 2, 2 to 3), steps = 100, restingAt = resting(3)))

    assertTrue(passes.complete)
    assertEquals(emptyList<Int>(), passes.waiting)
    val outcome = passes.outcome()
    assertEquals(mapOf(0 to 1, 1 to 2, 2 to 3), outcome.faces)
    assertEquals(1, outcome.passes)
    assertEquals(0, outcome.rethrows)
    assertEquals(setOf(0, 1, 2), outcome.restingAt.keys, "the dice of a finished throw stay on the table")
  }

  @Test
  fun `dice nobody could read wait for a hand, and nothing is thrown for them`() {
    val passes = Passes(spec(3))

    passes.landed(SimulationOutcome(faces = mapOf(0 to 4, 2 to 5), unread = listOf(1), stackedAtRest = 1))

    assertFalse(passes.complete)
    assertFalse(passes.airborne, "a throw that stopped is still in the air")
    assertEquals(listOf(1), passes.waiting)
    assertEquals(mapOf(0 to 4, 2 to 5), passes.read)
    assertFailsWith<IllegalStateException> { passes.outcome() }
  }

  @Test
  fun `the shake throws only those dice, among the dice that were down before the throw`() {
    val down =
      listOf(DieAtRest(StandardDice.d20, "builtin", RestingPlace(Vector3(10.0, 0.0, 8.0), Quaternion.Identity)))
    val first = spec(3).copy(among = down)
    val passes = Passes(first)
    passes.landed(SimulationOutcome(faces = mapOf(0 to 4), unread = listOf(1, 2), restingAt = resting(1)))
    val hand = listOf(ShakeSample(0, Vector3(1_000.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0)))

    val again = passes.next(hand)

    assertEquals(listOf(StandardDice.d6, StandardDice.d4), again.dice.map(DieInstance::die))
    assertEquals(listOf(0, 1), again.dice.map(DieInstance::index), "a pass numbers its own dice from nought")
    // The dice this throw read are lifted by the throw of the rest, so they
    // are not among; the dice that were down before it began still are.
    assertEquals(down, again.among)
    assertEquals(hand, again.shake)
    assertEquals(first.dieScale, again.dieScale)
    assertEquals(Seeds.again(first.seed, 1), again.seed)
    assertTrue(passes.airborne)
    assertEquals(emptyList<Int>(), passes.waiting)
  }

  @Test
  fun `a face read on a later pass is filed under the die it belongs to`() {
    val passes = Passes(spec(3))
    passes.landed(SimulationOutcome(faces = mapOf(0 to 4), unread = listOf(1, 2)))
    passes.next()

    // Its own positions: 0 is the first throw's die 1, and 1 is its die 2.
    passes.landed(SimulationOutcome(faces = mapOf(1 to 7), unread = listOf(0), steps = 50))
    assertEquals(listOf(1), passes.waiting)
    passes.next()
    passes.landed(SimulationOutcome(faces = mapOf(0 to 2), steps = 30, restingAt = resting(1)))

    val outcome = passes.outcome()
    assertEquals(mapOf(0 to 4, 1 to 2, 2 to 7), outcome.faces)
    assertEquals(3, outcome.rethrows, "three dice were thrown again, one of them twice")
    assertEquals(3, outcome.passes)
    assertEquals(80, outcome.steps)
    assertEquals(setOf(1), outcome.restingAt.keys, "only the last pass's die is still on the table")
  }

  @Test
  fun `each pass is seeded on its own, and the same way every time`() {
    fun seeds(): List<Long> {
      val passes = Passes(spec(1))
      return List(3) {
        passes.landed(SimulationOutcome(faces = emptyMap(), unread = listOf(0)))
        passes.next().seed
      }
    }

    val once = seeds()
    assertEquals(once, seeds(), "a throw did not replay its re-throws to themselves")
    assertEquals(3, once.toSet().size)
  }

  @Test
  fun `what the pass in the air has counted is added under the right dice`() {
    val passes = Passes(spec(3))
    passes.landed(SimulationOutcome(faces = mapOf(1 to 3), unread = listOf(0, 2)))
    passes.next()

    assertEquals(mapOf(1 to 3, 2 to 6), passes.readSoFar(mapOf(1 to 6)))
    assertEquals(mapOf(1 to 3), passes.readSoFar(mapOf(5 to 1)), "a die the pass did not throw was filed anyway")
  }

  @Test
  fun `a pass that gave up leaves the dice that never stopped waiting, like unread ones`() {
    val passes = Passes(spec(4))

    passes.gaveUp(read = mapOf(0 to 1, 3 to 2, 9 to 9), unsettled = listOf(1, 2, 9))

    assertEquals(0, passes.landed, "a pass that gave up landed")
    assertEquals(1, passes.pass)
    assertEquals(listOf(1, 2), passes.waiting)
    assertEquals(mapOf(0 to 1, 3 to 2), passes.read)
    assertEquals(listOf(StandardDice.d6, StandardDice.d4), passes.next().dice.map(DieInstance::die))
    assertEquals(2, passes.pass)
  }

  @Test
  fun `the figures are the throw's, added up the way each one means`() {
    val passes = Passes(spec(2))
    passes.landed(
      SimulationOutcome(
        faces = mapOf(0 to 1),
        unread = listOf(1),
        steps = 200,
        corrections = 1,
        forcedSettles = 1,
        postRestCorrections = 1,
        stackedAtRest = 1,
        deepestDiePenetrationMm = 0.5,
        medianTurnsAfterLanding = 1.5,
      ),
    )
    passes.next()
    passes.landed(SimulationOutcome(faces = mapOf(0 to 2), steps = 100, deepestDiePenetrationMm = 0.2))

    val outcome = passes.outcome()
    assertEquals(300, outcome.steps)
    assertEquals(1, outcome.corrections)
    assertEquals(1, outcome.forcedSettles)
    assertEquals(1, outcome.postRestCorrections)
    assertEquals(0, outcome.stackedAtRest, "a die standing on another was thrown again, not left there")
    assertEquals(0.5, outcome.deepestDiePenetrationMm)
    assertEquals(1.5, outcome.medianTurnsAfterLanding, "the first pass threw every die")
  }

  @Test
  fun `a die a pass never threw is not filed anywhere`() {
    val passes = Passes(spec(1))

    passes.landed(SimulationOutcome(faces = mapOf(0 to 1, 3 to 2), unread = listOf(4), restingAt = resting(4)))

    assertTrue(passes.complete)
    assertEquals(mapOf(0 to 1), passes.outcome().faces)
    assertEquals(setOf(0), passes.outcome().restingAt.keys)
  }

  @Test
  fun `nothing lands that was not thrown, and nothing is thrown that is not waiting`() {
    val passes = Passes(spec(1))
    passes.landed(SimulationOutcome(faces = mapOf(0 to 1)))

    assertFailsWith<IllegalStateException> { passes.landed(SimulationOutcome(faces = mapOf(0 to 1))) }
    assertFailsWith<IllegalStateException> { passes.gaveUp(emptyMap(), listOf(0)) }
    assertFailsWith<IllegalStateException> { passes.next() }
  }

  @Test
  fun `a scripted hand throws the unread dice at once until every one is read`() {
    val thrown = mutableListOf<ThrowSpec>()

    val outcome =
      Passes.scripted(spec(2)) { pass ->
        thrown += pass
        if (thrown.size == 1) {
          SimulationOutcome(faces = mapOf(0 to 3), unread = listOf(1))
        } else {
          SimulationOutcome(faces = mapOf(0 to 5))
        }
      }

    assertEquals(mapOf(0 to 3, 1 to 5), outcome?.faces)
    assertEquals(emptyList<ShakeSample>(), thrown.last().shake, "the scripted hand shook")
    assertEquals(listOf(StandardDice.d6), thrown.last().dice.map(DieInstance::die))
  }

  @Test
  fun `a scripted hand gives up with the pass that gave up, or when a die never comes good`() {
    assertNull(Passes.scripted(spec(1)) { null })

    var passes = 0
    val never =
      Passes.scripted(spec(1)) {
        passes++
        SimulationOutcome(faces = emptyMap(), unread = listOf(0))
      }

    assertNull(never)
    assertEquals(Passes.MOST_UNWATCHED, passes)
  }

  private fun resting(count: Int): Map<Int, RestingPlace> =
    (0 until count).associateWith { RestingPlace(Vector3(it.toDouble(), 0.0, 8.0), Quaternion.Identity) }

  private fun spec(count: Int): ThrowSpec =
    ThrowSpec(
      dice =
        listOf<Die>(StandardDice.d20, StandardDice.d6, StandardDice.d4, StandardDice.d20).take(count).mapIndexed {
          index,
          die,
          ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = table,
      seed = 23L,
      dieScale = 0.8,
    )
}
