package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.FrameClock
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * A roll being watched while it happens (`docs/physics-and-rendering.md`, "The
 * simulation clock").
 *
 * The claim under test is the one the whole roll screen rests on: **watching a
 * roll cannot change it.** However the frames fall — sixty a second, two a
 * second, one enormous one after the app came back from the background — the
 * same seed comes to the same faces, in the same number of steps, with the
 * same corrections. If that were not true, power-saving mode would be a second
 * implementation and the golden determinism suite would be asserting a
 * coincidence.
 */
class LiveRollTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  /** A cube turned 45° about x: no face within 15° of up, so it reads cocked. */
  private val cocked = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 4)

  @Test
  fun `a roll stepped from a clock comes to what the same roll run straight through came to`() {
    // The whole point, stated once: two worlds, one seed, two ways of asking.
    val world = FakeWorld(DICE, awkward())
    val straight = liveOver(world).use { it.runToEnd() }

    val watched = FakeWorld(DICE, awkward())
    val fromAClock = liveOver(watched).use { it.pumpAt(FRAMES_PER_SECOND) }

    assertEquals(straight, fromAClock)
    assertEquals("the two worlds were not even stepped the same number of times", world.steps, watched.steps)
    assertEquals(world.respawns, watched.respawns)
    assertEquals(world.removed, watched.removed)
    // And the throw has to be worth comparing: a roll where nothing awkward
    // happened would agree with itself no matter what this class did.
    assertTrue("no die was ever thrown again", world.respawns.isNotEmpty())
    assertTrue("no die was ever counted and taken off the table", world.removed.isNotEmpty())
  }

  @Test
  fun `frames of wildly different lengths still make the same roll`() {
    // A phone that stutters, throttles and is backgrounded mid-roll is still
    // rolling the same dice: `FrameClock` drops time, never steps.
    val steady = FakeWorld(DICE, awkward())
    val expected = liveOver(steady).use { it.pumpAt(FRAMES_PER_SECOND) }

    val ragged = FakeWorld(DICE, awkward())
    val frames = listOf(0.004, 0.4, 0.008, 0.016, 1.5, 0.001)
    val actual =
      liveOver(ragged).use { live ->
        var at = 0
        while (live.running) {
          live.advance(frames[at++ % frames.size])
        }
        live.outcome
      }

    assertEquals(expected, actual)
    assertEquals(steady.steps, ragged.steps)
  }

  @Test
  fun `a roll run straight through shows no frames at all`() {
    // Power-saving mode's actual claim is not "a cheap renderer" but "nobody
    // is called per step" (`docs/physics-and-rendering.md`).
    val watcher = HeadlessRenderer()
    liveOver(FakeWorld(DICE, awkward()), watcher).use { it.runToEnd() }

    assertEquals(0, watcher.framesShown)
    assertTrue("the last position was never handed over", watcher.finished)
  }

  @Test
  fun `the renderer is shown a frame for every frame the clock was given`() {
    val watcher = HeadlessRenderer()
    val frames =
      liveOver(FakeWorld(DICE, awkward()), watcher).use { live ->
        var count = 0
        while (live.running) {
          live.advance(1.0 / FRAMES_PER_SECOND)
          count++
        }
        count
      }

    // Every frame but the last: the frame the dice stop on is handed over as
    // settled rather than shown, so a renderer can tell "here they are now"
    // from "here they will stay".
    assertEquals(frames - 1, watcher.framesShown)
  }

  @Test
  fun `nothing is shown after the dice have settled, however long the caller keeps asking`() {
    // A frame callback does not stop the moment a roll does. What it must not
    // do is keep telling a renderer the roll is live, or settle it twice.
    val watcher = HeadlessRenderer()
    liveOver(FakeWorld(DICE, awkward()), watcher).use { live ->
      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)
      val shownWhileRolling = watcher.framesShown

      repeat(SPARE_FRAMES) { live.advance(1.0 / FRAMES_PER_SECOND) }

      assertEquals("a settled roll was shown another frame", shownWhileRolling, watcher.framesShown)
      assertTrue(watcher.finished)
    }
  }

  @Test
  fun `a settled roll takes no further steps, whatever it is handed`() {
    // The rule that matters most, one level above the loop: once the roll is
    // over the world is never stepped again, so nothing at all can reach a die
    // that has come to rest (`.claude/CLAUDE.md`).
    val world = FakeWorld(DICE, awkward())
    liveOver(world).use { live ->
      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)
      val steps = world.steps

      repeat(SPARE_FRAMES) { live.advance(1.0) }

      assertEquals("the world was stepped after the roll ended", steps, world.steps)
    }
  }

  @Test
  fun `a frame shorter than a step draws the same two states further along`() {
    // A 240 Hz panel. The dice do not move, but the moment between the last
    // two steps does, which is what makes 120 Hz physics look smooth on a
    // faster screen.
    val world = FakeWorld(DICE, tumblingThenSettling())
    liveOver(world).use { live ->
      live.advance(SettleRule.TIMESTEP_SECONDS)
      val stepped = live.stepsTaken
      val first = live.advance(SettleRule.TIMESTEP_SECONDS / 4)
      val second = live.advance(SettleRule.TIMESTEP_SECONDS / 4)

      assertEquals("a frame shorter than a step stepped the world", stepped, live.stepsTaken)
      assertEquals(first.previous, second.previous)
      assertEquals(first.current, second.current)
      assertTrue("the moment between the steps did not move", second.interpolation > first.interpolation)
    }
  }

  @Test
  fun `a frame that fell far behind is not paid in full`() {
    // Without the cap, one late frame is a hundred and twenty steps of solver
    // inside a frame callback — a freeze the player watches happen.
    val world = FakeWorld(DICE, tumblingThenSettling())
    liveOver(world, clock = FrameClock(maxStepsPerFrame = CATCH_UP_CAP)).use { live ->
      live.advance(1.0)

      assertEquals(CATCH_UP_CAP, live.stepsTaken)
      assertEquals(SettleRule.STEPS_PER_SECOND - CATCH_UP_CAP, live.droppedSteps)
    }
  }

  @Test
  fun `a die thrown again does not glide back to where it started`() {
    // Rung 3 is a die being picked up and thrown, and it has to read as one. A
    // re-throw takes no simulated time, so blending across it would draw the
    // die sliding smoothly through the air back to the spawn point — an
    // invisible hand with an animation on it (`docs/physics-and-rendering.md`,
    // rung 3).
    val world =
      FakeWorld(1) { _, _, rethrows ->
        if (rethrows == 0) FakeWorld.settled(cocked) else FakeWorld.settled()
      }
    liveOver(world, dice = listOf(StandardDice.d6)).use { live ->
      var acrossTheRethrow: RenderFrame? = null
      while (live.running) {
        val before = live.stepsTaken
        val frame = live.advance(SettleRule.TIMESTEP_SECONDS)
        if (live.running && live.stepsTaken == before) acrossTheRethrow = frame
      }

      assertEquals(1, requireNotNull(live.outcome).rethrows)
      val frame = requireNotNull(acrossTheRethrow) { "the re-throw never landed in a frame of its own" }
      assertEquals("the die was drawn moving across a re-throw", frame.previous, frame.current)
    }
  }

  @Test
  fun `a shake that arrives mid-roll reaches the dice`() {
    // The dice are spawned when the shake begins, so most of a shake arrives
    // after the throw has started. It reaches the solver as the one thing a
    // shake changes: which way down is, for one step.
    val world = FakeWorld(DICE, tumblingThenSettling())
    liveOver(world).use { live ->
      live.advance(SettleRule.TIMESTEP_SECONDS)
      val sideways = Vector3(5_000.0, 0.0, 0.0)
      live.shake(ShakeSample(live.stepsTaken, sideways, DOWN))

      live.advance(SettleRule.TIMESTEP_SECONDS)

      assertTrue(
        "the shake never reached the world's gravity",
        world.gravities.last().x < 0.0,
      )
    }
  }

  @Test
  fun `a shake that arrives after the dice have stopped is dropped`() {
    // Nothing touches a die that has come to rest, and a hand is not an
    // exception (`.claude/CLAUDE.md`).
    val world = FakeWorld(DICE, awkward())
    liveOver(world).use { live ->
      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)
      val steps = world.steps
      val gravities = world.gravities.size

      live.shake(ShakeSample(live.stepsTaken, Vector3(9_000.0, 0.0, 0.0), DOWN))
      live.advance(1.0 / FRAMES_PER_SECOND)

      assertEquals("a settled roll was stepped again", steps, world.steps)
      assertEquals("a settled roll's gravity was changed", gravities, world.gravities.size)
    }
  }

  @Test
  fun `a roll that has landed knows the shake that threw it`() {
    // The throw's `ThrowSpec` was empty of shake — the dice are spawned when
    // the shake is confirmed — so this is the only place the record exists, and
    // `spec.copy(shake = drivenBy)` is what would replay the roll.
    val world = FakeWorld(DICE, tumblingThenSettling())
    liveOver(world).use { live ->
      val hand = mutableListOf<ShakeSample>()
      while (live.running && hand.size < A_SHORT_SHAKE) {
        val sample = ShakeSample(live.stepsTaken, Vector3(5_000.0, 0.0, 0.0), DOWN)
        live.shake(sample)
        hand += sample
        live.advance(SettleRule.TIMESTEP_SECONDS)
      }
      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)

      assertEquals(hand, live.drivenBy)
    }
  }

  @Test
  fun `a shake dropped after the dice stopped is not in the record either`() {
    // The record is what drove the roll. A sample the roll refused to apply is
    // a sample that shaped nothing, and a replay including it would be a replay
    // of a different throw.
    val world = FakeWorld(DICE, awkward())
    liveOver(world).use { live ->
      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)

      live.shake(ShakeSample(live.stepsTaken, Vector3(9_000.0, 0.0, 0.0), DOWN))

      assertEquals(emptyList<ShakeSample>(), live.drivenBy)
    }
  }

  @Test
  fun `a die that has been counted is not in the frame any more`() {
    // It is off the table, and the floor it stood on is free for the dice
    // still to be thrown — so a later die may land exactly there. A frame that
    // still carried it would draw two dice in one place, which is a worse
    // thing to watch than the stacking this replaced (`docs/TODO.md`, 5.5).
    val world = FakeWorld(DICE, awkward())
    val live = liveOver(world)
    val atTheStart = live.frame().current.size

    live.use { it.runToEnd() }

    assertEquals("every die was in the first frame", DICE, atTheStart)
    assertTrue("no die was ever counted, so this proves nothing", world.removed.isNotEmpty())
    assertEquals(
      "a die that had been lifted off the table was still being drawn on it",
      DICE - world.removed.size,
      live.frame().current.size,
    )
  }

  @Test
  fun `a frame has the same dice at both ends even as they are counted`() {
    // The two halves are filtered by one list on purpose: a frame spans a step,
    // and one that lost a die from only one end would be asking the renderer
    // to blend between different dice.
    val world = FakeWorld(DICE, awkward())

    liveOver(world).use { live ->
      while (live.running) {
        live.advance(SettleRule.TIMESTEP_SECONDS)
        val frame = live.frame()
        assertEquals("a frame lost a die from one end only", frame.previous.size, frame.current.size)
      }
    }
  }

  @Test
  fun `a roll abandoned in the air takes its samples with it`() {
    // Nothing landed, so there is nothing to record — and the record of a throw
    // that was never made has nowhere to go. It dies with the roll rather than
    // being held for whatever is thrown next.
    val world = FakeWorld(DICE, tumblingThenSettling())
    val live = liveOver(world)
    live.advance(SettleRule.TIMESTEP_SECONDS)
    live.shake(ShakeSample(live.stepsTaken, Vector3(5_000.0, 0.0, 0.0), DOWN))

    live.close()

    assertNull("a roll nobody waited for produced a result", live.outcome)
    assertTrue("the world the samples drove was left open", world.closed)
  }

  @Test
  fun `a roll that has finished is not between two states`() {
    liveOver(FakeWorld(DICE, awkward())).use { live ->
      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)

      assertEquals(1.0, live.frame().interpolation, 0.0)
    }
  }

  @Test
  fun `ending a roll gives the world back and leaves the picture standing`() {
    // A LiveRoll holds native memory, and abandoning one mid-roll — the player
    // left the screen — has to free it just as finishing does.
    //
    // What it must *not* free is the scene. A roll that has landed is still on
    // screen and the player is still reading it, so the picture outlives the
    // simulation that made it; ending it here meant a settled roll vanished
    // the moment anything took the surface away and gave it back, and the
    // screen blanking was enough to do that.
    val world = FakeWorld(DICE, tumblingThenSettling())
    val watcher = HeadlessRenderer()
    liveOver(world, watcher).use { live ->
      live.advance(1.0 / FRAMES_PER_SECOND)
      assertTrue("the roll was over before it had begun", live.running)
    }

    assertTrue("the physics world was left open", world.closed)
    assertTrue("the picture was torn down with the physics world", watcher.running)
  }

  @Test
  fun `the outcome is not there until the dice have stopped`() {
    liveOver(FakeWorld(DICE, tumblingThenSettling())).use { live ->
      live.advance(SettleRule.TIMESTEP_SECONDS)
      assertNull("a roll that is still going reported a result", live.outcome)

      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)
      assertNotNull(live.outcome)
    }
  }

  private fun LiveRoll.pumpAt(rate: Int): SimulationOutcome? {
    while (running) advance(1.0 / rate)
    return outcome
  }

  private fun liveOver(
    world: FakeWorld,
    renderer: Renderer = HeadlessRenderer(),
    dice: List<Die> = List(DICE) { StandardDice.d6 },
    clock: FrameClock = FrameClock(),
  ): LiveRoll {
    val spec = spec(dice)
    val layout = SpawnLayout(geometry, largestRadiusMm(spec), spec.seed)
    return LiveRoll(spec, world, RollLoop(spec, world, layout, ShakeDriver(emptyList())), renderer, clock)
  }

  private fun spec(dice: List<Die>): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = table,
      seed = SEED,
    )

  /**
   * A throw that uses every rung of the ladder: the dice tumble, one settles
   * onto another and takes its one nudge, and one finishes cocked and has to
   * be thrown again. A roll with nothing awkward in it would prove far less.
   */
  private fun awkward(): FakeWorld.States =
    FakeWorld.States { step, index, rethrows ->
      when {
        step < TUMBLE_STEPS -> FakeWorld.tumbling(cocked)
        index == 0 && rethrows == 0 -> FakeWorld.settled(cocked)
        step < TUMBLE_STEPS + TROUBLE_STEPS -> FakeWorld.settling(cocked, supportedByDie = index == 1)
        else -> FakeWorld.settled()
      }
    }

  private fun tumblingThenSettling(): FakeWorld.States =
    FakeWorld.States { step, _, _ ->
      if (step < TUMBLE_STEPS) FakeWorld.tumbling() else FakeWorld.settled()
    }

  private companion object {
    const val SEED = 20_260_913L
    const val FRAMES_PER_SECOND = 60
    const val DICE = 3
    const val TUMBLE_STEPS = 40
    const val TROUBLE_STEPS = 12
    const val SPARE_FRAMES = 5

    /** Long enough to be a shake and short enough to end inside the roll. */
    const val A_SHORT_SHAKE = 10
    const val CATCH_UP_CAP = 4
    val DOWN = Vector3(0.0, 0.0, -1.0)
  }
}
