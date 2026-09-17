package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.DieMotion
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.ImpactRule
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.Struck
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * What a roll reports about where the dice hit something, tested where it is
 * decided (`docs/physics-and-rendering.md`, "Impacts").
 *
 * The claim worth asserting rather than believing is the last one in the file:
 * **listening changes nothing.** An impact is a reading of a step that has
 * already been taken, so a roll with a recorder on it comes to the same faces,
 * in the same number of steps, with the same corrections, as the same seed with
 * nothing listening at all.
 */
class ImpactRecorderTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun `a die landing is one impact rather than a burst`() {
    val recorder = ImpactRecorder(listOf(SIZE_MM))
    // Falling, then stopped: a die arriving on the table, and then several
    // steps of it lying there in contact.
    recorder.step(0, listOf(moving(1_200.0)), GRAVITY)
    repeat(STEPS_OF_CONTACT) { step -> recorder.step(step + 1, listOf(moving(0.0)), GRAVITY) }

    assertEquals(1, recorder.recorded().size)
    assertEquals(1, recorder.recorded().single().stepIndex)
  }

  @Test
  fun `the same die can be heard again once it has been quiet long enough`() {
    val recorder = ImpactRecorder(listOf(SIZE_MM))
    recorder.step(0, listOf(moving(1_200.0)), GRAVITY)
    recorder.step(1, listOf(moving(0.0)), GRAVITY)
    // Far enough on that the quiet window has closed.
    recorder.step(1 + ImpactRule.QUIET_STEPS, listOf(moving(900.0)), GRAVITY)

    assertEquals(2, recorder.recorded().size)
  }

  @Test
  fun `the first step a die is seen on is not an impact`() {
    val recorder = ImpactRecorder(listOf(SIZE_MM))
    // Spawned already travelling fast: that speed came from the throw, not
    // from hitting anything.
    recorder.step(0, listOf(moving(2_000.0)), GRAVITY)

    assertTrue(recorder.recorded().isEmpty())
  }

  @Test
  fun `a die sliding and a die at rest are never heard`() {
    val sliding = ImpactRecorder(listOf(SIZE_MM))
    // Sliding, and slowing as friction takes it — which can never take more
    // out of a die in one step than gravity's own worth.
    var speed = 300.0
    repeat(SLIDING_STEPS) { step ->
      sliding.step(step, listOf(moving(speed)), GRAVITY)
      speed = (speed - FRICTION_MM_PER_SECOND).coerceAtLeast(0.0)
    }
    assertTrue("a slide was played as an impact", sliding.recorded().isEmpty())

    val still = ImpactRecorder(listOf(SIZE_MM))
    repeat(SLIDING_STEPS) { step -> still.step(step, listOf(moving(0.0)), GRAVITY) }
    assertTrue("a die at rest was played as an impact", still.recorded().isEmpty())
  }

  @Test
  fun `an impact carries the die it belongs to and the size it was thrown at`() {
    val recorder = ImpactRecorder(listOf(SIZE_MM, SMALL_MM))
    recorder.step(0, listOf(moving(0.0), moving(0.0)), GRAVITY)
    recorder.step(1, listOf(moving(0.0), moving(1_000.0)), GRAVITY)

    val impact = recorder.recorded().single()
    assertEquals(1, impact.dieIndex)
    assertEquals(SMALL_MM, impact.dieSizeMm, 0.0)
  }

  @Test
  fun `what a die hit travels with the impact`() {
    val recorder = ImpactRecorder(listOf(SIZE_MM))
    recorder.step(0, listOf(moving(1_000.0, touchingWall = true)), GRAVITY)
    recorder.step(1, listOf(moving(0.0, touchingWall = true)), GRAVITY)

    assertEquals(Struck.Wall, recorder.recorded().single().struck)
  }

  @Test
  fun `a die picked up and thrown again is not heard doing it`() {
    val recorder = ImpactRecorder(listOf(SIZE_MM))
    recorder.step(0, listOf(moving(0.0)), GRAVITY)
    // Rung 3 puts the die back at the spawn point between one step and the
    // next, and the speed it has afterwards is the app's own hand.
    recorder.rethrown(0)
    recorder.step(1, listOf(moving(1_800.0)), GRAVITY)

    assertTrue("the app played a sound for its own invisible hand", recorder.recorded().isEmpty())
  }

  @Test
  fun `a deaf recorder hears nothing whatever happens`() {
    val recorder = ImpactRecorder.deaf(dieCount = 1)
    recorder.step(0, listOf(moving(0.0)), GRAVITY)
    recorder.step(1, listOf(moving(2_000.0)), GRAVITY)
    recorder.rethrown(0)

    assertTrue(recorder.recorded().isEmpty())
  }

  @Test
  fun `the record stops at its cap rather than growing without bound`() {
    val recorder = ImpactRecorder(listOf(SIZE_MM))
    var step = 0
    var fast = false
    // Alternating hard enough to be heard, as often as the quiet window allows,
    // for longer than the record may hold.
    repeat((Impact.MAX_RECORDED + 2) * (ImpactRule.QUIET_STEPS + 1)) {
      recorder.step(step, listOf(moving(if (fast) 1_400.0 else 0.0)), GRAVITY)
      fast = !fast
      step++
    }

    assertEquals(Impact.MAX_RECORDED, recorder.recorded().size)
  }

  @Test
  fun `a roll reports the impacts it produced, in step order`() {
    var speed = 0.0
    val world =
      FakeWorld(1) { step, _, _ ->
        // Bouncing: fast on every fourth step, stopped in between — and then
        // stopped for good, because a die that bounced for ever would be a
        // roll that never ends and this test is about the impacts, not the
        // settle rule.
        speed = if (step < BOUNCING_STEPS && step % BOUNCE_EVERY == 0) 1_400.0 else 0.0
        FakeWorld.settled().copy(motion = DieMotion(speed, 0.0))
      }
    val loop = loop(listOf(StandardDice.d6), world)
    loop.run()

    assertTrue("a bouncing die was silent", loop.impacts.isNotEmpty())
    assertEquals(loop.impacts.map(Impact::stepIndex).sorted(), loop.impacts.map(Impact::stepIndex))
  }

  @Test
  fun `the same seed gives the same roll whether anything is listening or not`() {
    val heard = loop(DICE, bouncing(), ImpactRecorder(DICE.map { SIZE_MM }))
    val deaf = loop(DICE, bouncing(), ImpactRecorder.deaf(DICE.size))

    val withEars = heard.run()
    val without = deaf.run()

    assertEquals(without.faces, withEars.faces)
    assertEquals(without.steps, withEars.steps)
    assertEquals(without.corrections, withEars.corrections)
    assertEquals(without.rethrows, withEars.rethrows)
    assertEquals(without.forcedSettles, withEars.forcedSettles)
    assertEquals(without.postRestCorrections, withEars.postRestCorrections)
    // And the point of the exercise: the one that was listening heard something,
    // so the comparison is between a roll that recorded and a roll that did not
    // rather than between two silent ones.
    assertTrue(heard.impacts.isNotEmpty())
    assertTrue(deaf.impacts.isEmpty())
  }

  @Test
  fun `a roll driven into the corrections still gives the same faces with ears on`() {
    // Cocked and standing on another die: the ladder does everything it can do.
    val cocked = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 4)
    val trouble = { _: Int, _: Int, rethrows: Int ->
      // In trouble until it has been thrown again a few times, and then down
      // clean. A die that never came good would be a roll that never ends.
      if (rethrows < TRIES) FakeWorld.settled(cocked, supportedByDie = true) else FakeWorld.settled()
    }

    val heard = loop(DICE, FakeWorld(DICE.size, trouble), ImpactRecorder(DICE.map { SIZE_MM })).run()
    val deaf = loop(DICE, FakeWorld(DICE.size, trouble), ImpactRecorder.deaf(DICE.size)).run()

    assertEquals(deaf, heard)
    assertNotEquals("this case is meant to exercise the ladder", 0, heard.rethrows)
  }

  private fun bouncing(): FakeWorld =
    FakeWorld(DICE.size) { step, index, _ ->
      val speed = if (step < BOUNCING_STEPS && (step + index) % BOUNCE_EVERY == 0) 1_400.0 else 0.0
      FakeWorld.settled().copy(motion = DieMotion(speed, 0.0))
    }

  private fun moving(
    speedMmPerSecond: Double,
    touchingFloor: Boolean = true,
    touchingWall: Boolean = false,
  ): DieState =
    DieState(
      position = Vector3(0.0, 0.0, 8.0),
      orientation = Quaternion.Identity,
      motion = DieMotion(speedMmPerSecond = speedMmPerSecond, spinRadiansPerSecond = 0.0),
      touchingFloor = touchingFloor,
      touchingWall = touchingWall,
      supportedByDie = false,
    )

  private fun loop(
    dice: List<Die>,
    world: FakeWorld,
    recorder: ImpactRecorder = ImpactRecorder(dice.map { SIZE_MM }),
  ): RollLoop {
    val spec =
      ThrowSpec(
        dice =
          dice.mapIndexed { index, die ->
            DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
          },
        geometry = geometry,
        table = table,
        seed = SEED,
      )
    return RollLoop(
      spec = spec,
      world = world,
      layout = SpawnLayout(geometry, RADIUS_MM, spec.seed),
      shake = ShakeDriver(spec.shake),
      recorder = recorder,
    )
  }

  private companion object {
    /** How long a bouncing die bounces before it stops for good. */
    const val BOUNCING_STEPS = 40

    /** Throws before a die in trouble comes good. */
    const val TRIES = 3

    const val GRAVITY = 9_806.65
    const val SIZE_MM = 16.0
    const val SMALL_MM = 10.0
    const val RADIUS_MM = 8.0
    const val SEED = 4_242L
    const val STEPS_OF_CONTACT = 4
    const val SLIDING_STEPS = 20

    /** What a step of friction can take out of a sliding die, at most. */
    const val FRICTION_MM_PER_SECOND = GRAVITY / SettleRule.STEPS_PER_SECOND
    const val BOUNCE_EVERY = 8
    val DICE = listOf(StandardDice.d6, StandardDice.d20)

    init {
      // The quiet window has to be longer than a landing's contact, or the
      // first test above would be asserting nothing.
      check(ImpactRule.QUIET_STEPS >= STEPS_OF_CONTACT)
      check(SettleRule.REST_STEPS > 0)
    }
  }
}
