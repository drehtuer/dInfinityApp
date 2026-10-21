package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
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
import org.junit.Assert.assertFalse
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
  fun `a roll that gives up is a roll without an answer, not a crash`() {
    // A hundred d4 did this on a phone every time: the dice never stop, the
    // loop gives up at the cap, and the frame that noticed asked for an
    // outcome that does not exist — `the roll has not finished yet`, thrown
    // off the roll thread, which no caller can catch.
    liveOver(FakeWorld(DICE, neverSettling())).use { live ->
      repeat(SettleRule.HARD_CAP_STEPS + FRAMES_PER_SECOND) { live.advance(1.0 / FRAMES_PER_SECOND) }

      assertTrue("the roll never gave up, so this proves nothing", live.stalled)
      assertFalse("a roll that gave up reported an answer anyway", live.running)
      assertNull("a roll whose dice never stopped invented a result", live.outcome)
      assertEquals("every die was still moving, so every die is unsettled", DICE, live.unsettled.size)
    }
  }

  @Test
  fun `and run straight through it hands back nothing rather than requiring one`() {
    // The same fault on the other path: power-saving mode runs the roll to the
    // end in one go, and `running` goes false on a stall exactly as it does on
    // an answer.
    liveOver(FakeWorld(DICE, neverSettling())).use { live ->
      assertNull("a roll that gave up was made to produce an outcome", live.runToEnd { false })
      assertTrue(live.stalled)
    }
  }

  @Test
  fun `a roll stepped from a clock comes to what the same roll run straight through came to`() {
    // The whole point, stated once: two worlds, one seed, two ways of asking.
    val world = FakeWorld(DICE, awkward())
    val straight = liveOver(world).use { it.runToEnd() }

    val watched = FakeWorld(DICE, awkward())
    val fromAClock = liveOver(watched).use { it.pumpAt(FRAMES_PER_SECOND) }

    assertEquals(straight, fromAClock)
    assertEquals("the two worlds were not even stepped the same number of times", world.steps, watched.steps)
    // And the throw has to be worth comparing: a roll where nothing awkward
    // happened would agree with itself no matter what this class did. This one
    // reads some dice and leaves one cocked for the player's shake.
    val reported = requireNotNull(straight)
    assertTrue("no die was ever read", reported.faces.isNotEmpty())
    assertTrue("no die was left for a shake", reported.unread.isNotEmpty())
    // And neither way of asking touched the world for it.
    assertTrue("a die was thrown again with no hand on it", world.respawns.isEmpty() && watched.respawns.isEmpty())
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
  fun `the overlay's snapshot carries the steps the clock dropped`() {
    // The loop under the roll never sees the clock, so a snapshot built by it
    // alone would always say nought — and the first throw of a session is the
    // one this number was put on the overlay to catch (`docs/TODO.md`, 5.6).
    val world = FakeWorld(DICE, tumblingThenSettling())
    liveOver(world, clock = FrameClock(maxStepsPerFrame = CATCH_UP_CAP)).use { live ->
      assertEquals(0, live.diagnostics.droppedSteps)

      live.advance(1.0)

      assertEquals(SettleRule.STEPS_PER_SECOND - CATCH_UP_CAP, live.diagnostics.droppedSteps)
      assertEquals(live.stepsTaken, live.diagnostics.steps)
    }
  }

  @Test
  fun `a die nobody can read ends the roll where it lies, and the picture with it`() {
    // A die that came to rest cocked used to be picked up and thrown again by
    // the roll itself, in the middle of the frames. Now the roll is over the
    // moment the table is still: the cocked die is reported for the player's
    // shake, and the last frame is the table exactly as the die came to rest
    // on it — nothing gliding, nothing lifted (decision 70).
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(cocked) }
    val watcher = HeadlessRenderer()
    liveOver(world, watcher, dice = listOf(StandardDice.d6)).use { live ->
      while (live.running) live.advance(SettleRule.TIMESTEP_SECONDS)

      val outcome = requireNotNull(live.outcome)
      assertEquals("the cocked die was not handed back", listOf(0), outcome.unread)
      assertEquals("the roll threw a die again by itself", 0, outcome.rethrows)
      assertEquals("the cocked die was not drawn where it lay", 1, live.frame().current.size)
      assertTrue("the picture the player waits over was never handed to the renderer", watcher.finished)
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
  fun `a roll says whether a hand is throwing it, which is what decides the pace`() {
    // The boundary between the part of a roll the player is driving and the
    // part they are watching. A driven roll gets every frame whole; a watched
    // one is paced so the dice can be seen to land
    // (`de.drehtuer.dinfinity.simulation.api.RollPace`).
    val world = FakeWorld(DICE, tumblingThenSettling())
    liveOver(world).use { live ->
      assertFalse("a tap-to-roll throw has no hand on it", live.driven)

      live.shake(ShakeSample(live.stepsTaken, Vector3(5_000.0, 0.0, 0.0), DOWN))
      assertTrue("the hand did not reach the roll", live.driven)

      repeat(ShakeDriver.HOLD_STEPS + 1) { live.advance(SettleRule.TIMESTEP_SECONDS) }
      assertFalse("the hand was let go and the roll is still driven", live.driven)

      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)
      assertFalse("a roll that is over is being driven by nobody", live.driven)
    }
  }

  @Test
  fun `a shake at a roll that has already landed does not make it driven again`() {
    // Nothing touches a die that has come to rest, and a hand is not an
    // exception — so a sample that arrives late cannot put the roll back into
    // slow motion either.
    val world = FakeWorld(DICE, tumblingThenSettling())
    liveOver(world).use { live ->
      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)

      live.shake(ShakeSample(live.stepsTaken, Vector3(5_000.0, 0.0, 0.0), DOWN))

      assertFalse("a settled roll answered a hand", live.driven)
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
  fun `a die that has been counted is still in the frame, and so is one nobody could read`() {
    // A throw takes nothing off its own table. The dice it read and the die it
    // could not are all where they came to rest, which is the picture the
    // player is asked to shake over; the read ones leave when that shake
    // throws the other, in a picture of its own
    // (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked dice").
    val world = FakeWorld(DICE, awkward())
    val live = liveOver(world)
    val atTheStart = live.frame().current.size

    val outcome = live.use { it.runToEnd() }

    assertEquals("every die was in the first frame", DICE, atTheStart)
    assertTrue("no die was ever counted, so this proves nothing", requireNotNull(outcome).faces.isNotEmpty())
    assertEquals("a die was taken out of the picture", DICE, live.frame().current.size)
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
  fun `the running total and the impacts are the loop's own, passed on as they are`() {
    // The roll screen follows these every frame instead of the dice. A live
    // roll that kept a copy of its own would drift from the loop that decides
    // them, and the total on screen would disagree with the result it becomes.
    val world = FakeWorld(DICE, awkward())
    val spec = spec(List(DICE) { StandardDice.d6 })
    val loop = RollLoop(spec, world, ShakeDriver(emptyList()))
    LiveRoll(spec, world, loop, HeadlessRenderer(), FrameClock()).use { live ->
      live.advance(1.0 / FRAMES_PER_SECOND)
      assertEquals("a die was counted while the dice were still tumbling", emptyMap<Int, Int>(), live.countedSoFar)

      while (live.running) live.advance(1.0 / FRAMES_PER_SECOND)

      val outcome = requireNotNull(live.outcome)
      assertTrue("no die was read, so this proves nothing", outcome.faces.isNotEmpty())
      assertEquals(outcome.faces, live.countedSoFar)
      assertEquals(loop.impacts, live.impacts)
    }
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
    return LiveRoll(spec, world, RollLoop(spec, world, ShakeDriver(emptyList())), renderer, clock)
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
   * A throw with something awkward in it: the dice tumble, one settles onto
   * another for a while, and one finishes cocked and is left for the player's
   * shake. A roll with nothing awkward in it would prove far less.
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

  /** Dice that never stop, which is what a roll that gives up is made of. */
  private fun neverSettling(): FakeWorld.States = FakeWorld.States { _, _, _ -> FakeWorld.tumbling(cocked) }

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
