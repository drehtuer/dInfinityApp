package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.DieMotion
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * The correction ladder, tested where it is decided rather than where it is
 * carried out (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked
 * dice").
 *
 * Every rule here is one a device run can only sample. A phone can show that
 * ten thousand rolls contained no post-rest correction; it cannot show that
 * none is possible. These can, because the gate is in Kotlin and the JVM can
 * hand the loop a die that has stopped dead in the worst state a die can be in
 * and watch nothing happen to it.
 */
class RollLoopTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  /** A cube turned 45° about x: no face within 15° of up, so it reads cocked. */
  private val cocked = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 4)

  @Test
  fun `a throw of dice that simply settle is read off their faces`() {
    val world = FakeWorld(2) { _, _, _ -> FakeWorld.settled() }
    val outcome = loop(listOf(StandardDice.d6, StandardDice.d20), world).run()

    assertEquals(2, outcome.faces.size)
    assertEquals(0, outcome.corrections)
    assertEquals(0, outcome.rethrows)
    assertTrue("a roll that just settled has nothing to report", outcome.clean)
    // A die is at rest once it has kept still for the documented time, not the
    // moment it stops moving.
    assertEquals(SettleRule.REST_STEPS.toLong(), outcome.steps.toLong())
  }

  @Test
  fun `the outcome says where each die stopped, not only what it says`() {
    // A roll whose formula explodes is not over when its dice stop: the die
    // that follows is dropped into the floor these left clear and drawn among
    // them, and neither is something the screen could work out for itself
    // (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
    // adds").
    val world = FakeWorld(2) { _, _, _ -> FakeWorld.settled() }

    val outcome = loop(listOf(StandardDice.d6, StandardDice.d20), world).run()

    assertEquals(outcome.faces.keys, outcome.restingAt.keys)
    outcome.restingAt.values.forEach { place ->
      assertEquals(FakeWorld.settled().position, place.position)
      assertEquals(FakeWorld.settled().orientation, place.orientation)
    }
  }

  @Test
  fun `nothing touches a die that has come to rest, however wrong it looks`() {
    // Stopped dead and standing on another die: as much trouble as a die can
    // be in, and out of reach for exactly that reason.
    // It is thrown again for as long as it takes, and never touched where it
    // lies — so a run nobody is watching gives up rather than reporting a die
    // standing on another one.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }

    assertThrows(IllegalStateException::class.java) { loop(listOf(StandardDice.d6), world).run() }

    assertTrue("a settled die was biased", world.biases.isEmpty())
  }

  @Test
  fun `a die that passes through trouble and then settles is simply counted`() {
    // What used to happen here was a nudge, a third of a second into the
    // throw, at a die that was going to be fine anyway. Nothing acts mid-flight
    // any more: the roll waits until the dice have stopped and then asks what
    // can be read.
    val world = FakeWorld(1, inTroubleFor(TROUBLE_STEPS))
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertTrue("something reached into the roll", world.biases.isEmpty())
    assertEquals("nothing was corrected, because there is nothing left that could be", 0, outcome.corrections)
    assertEquals(0, outcome.postRestCorrections)
    assertEquals("a die that settled perfectly well was thrown again", 0, outcome.rethrows)
    assertEquals("it was read", 1, outcome.faces.size)
    assertEquals("and taken off a table nothing else was going to be thrown onto", emptyList<Int>(), world.removed)
  }

  @Test
  fun `a die that cannot be read is thrown again until it can`() {
    val world = FakeWorld(1, unreadableUntilThrownAgain())
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertTrue("something reached into the roll", world.biases.isEmpty())
    assertEquals(0, outcome.corrections)
    assertEquals("the die nobody could read was not thrown again", 1, outcome.rethrows)
    assertEquals("and once it could be read it was counted", 1, outcome.faces.size)
    // The re-throw came *before* anything had been read, so there was nothing
    // on the table to make room for and nothing was taken off it. The die that
    // ended the roll is the only die there is, and it stays where it landed.
    assertEquals("the only die in the roll was taken off the table", emptyList<Int>(), world.removed)
    assertEquals(0, outcome.stackedAtRest)
  }

  @Test
  fun `a die that can be read is counted and taken off the table`() {
    // The whole mechanism in one throw: two dice, one readable and one standing
    // on it. The readable one is counted and lifted off — which is what frees
    // the floor — and the other is thrown again onto the room that made.
    val world =
      FakeWorld(2) { _, index, rethrows ->
        if (index == 0 || rethrows > 0) FakeWorld.settled() else FakeWorld.settled(supportedByDie = true)
      }

    val outcome = loop(listOf(StandardDice.d6, StandardDice.d6), world).run()

    assertEquals("the die that could be read was not taken off the table", listOf(0), world.removed.take(1))
    assertTrue("the die standing on another was not thrown again", world.respawns.any { it.second == 1 })
    assertEquals("a die was left standing on another", 0, outcome.stackedAtRest)
    assertEquals(2, outcome.faces.size)
  }

  @Test
  fun `a roll that settles first time leaves every die where it landed`() {
    // The rule the screen depends on. Reading a die and taking it off the
    // table used to be one act, so a roll that went perfectly cleared itself
    // off the felt and left the player looking at an empty tray with a number
    // floating over it. Nothing is thrown again here, so nothing has to make
    // room, so nothing comes off.
    val world = FakeWorld(3) { _, _, _ -> FakeWorld.settled() }
    val loop = loop(listOf(StandardDice.d6, StandardDice.d20, StandardDice.d6), world)

    val outcome = loop.run()

    assertEquals("a die was thrown again with nothing wrong with it", 0, outcome.rethrows)
    assertEquals("all three were read", 3, outcome.faces.size)
    assertEquals("a die was taken off a table nothing was going to be thrown onto", emptyList<Int>(), world.removed)
    assertEquals("the dice are drawn as read", listOf(true, true, true), loop.countedOut)
    assertEquals("and the renderer was told to stop drawing them", listOf(false, false, false), loop.liftedOut)
  }

  @Test
  fun `a die comes off the table only to make room for one being thrown again`() {
    // Two dice: one readable, one standing on it. The second has to be thrown
    // again, and *that* is what lifts the first — the floor it is standing on
    // is the room the re-throw needs.
    val world =
      FakeWorld(2) { _, index, rethrows ->
        if (index == 0 || rethrows > 0) FakeWorld.settled() else FakeWorld.settled(supportedByDie = true)
      }
    val loop = loop(listOf(StandardDice.d6, StandardDice.d6), world)

    loop.run()

    assertEquals("both dice were read", listOf(true, true), loop.countedOut)
    // Only the first. The second was read in the pass that threw nothing
    // again, so it had no reason to come off and stays on the table.
    assertEquals("the wrong dice were lifted off", listOf(true, false), loop.liftedOut)
    assertEquals("the die that made room did not come off", listOf(0), world.removed)
  }

  @Test
  fun `a die counted in an early pass keeps the face it was counted on`() {
    // The reading is taken when the die is lifted off, not at the end of the
    // roll — by then its body has been out of the simulation for several
    // passes, and a result read from a die nobody is simulating any more would
    // be reading whatever the last pass happened to leave behind.
    val world =
      FakeWorld(2) { _, index, rethrows ->
        if (index == 0 || rethrows > 0) FakeWorld.settled() else FakeWorld.settled(supportedByDie = true)
      }

    val outcome = loop(listOf(StandardDice.d6, StandardDice.d6), world).run()

    // One, not two. Die 0 was counted *and* lifted off to make the room die 1
    // was thrown again into, so it is no longer on the table and is not
    // offered to the throw that follows. Its **face** is kept all the same,
    // which is what this test is about.
    assertEquals("a counted die that is still down lost the place it was counted at", 1, outcome.restingAt.size)
    assertTrue("a counted die has no face", outcome.faces.values.all { it >= 0 })
  }

  @Test
  fun `a die lifted off the table is not offered to the throw that follows`() {
    // The same two dice, asked the other question: what is the *next* throw
    // of the chain told is on the table. Die 0 is read and lifted off to make
    // room, and die 1 is thrown again onto the floor that freed — the same
    // spot, because that is where the room was.
    //
    // `restingAt` is what an explosion's throw is drawn among and aimed
    // around (`RollMachine.earnedThrow`, `ClearSpace`). A die that is no
    // longer on the table must not be in it: drawn back it puts two dice in
    // one place, and counted as floor it hides the room it left
    // (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
    // adds").
    val world =
      FakeWorld(2) { _, index, rethrows ->
        if (index == 0 || rethrows > 0) FakeWorld.settled() else FakeWorld.settled(supportedByDie = true)
      }
    val loop = loop(listOf(StandardDice.d6, StandardDice.d6), world)

    val outcome = loop.run()

    assertEquals("both dice were read", 2, outcome.faces.size)
    assertEquals("the wrong dice were lifted off", listOf(true, false), loop.liftedOut)
    assertEquals(
      "a die that had been taken off the table was handed to the next throw",
      setOf(1),
      outcome.restingAt.keys,
    )
  }

  @Test
  fun `a die in trouble but already too slow to touch is left alone`() {
    // Below the resting speed but not yet counted as at rest. The watching
    // window has closed even though the tracker is still counting, so this die
    // is past helping and can only be thrown again.
    val crawling =
      FakeWorld.settled(supportedByDie = true).copy(
        motion = DieMotion(speedMmPerSecond = 1.0, spinRadiansPerSecond = 0.001),
      )
    val world = FakeWorld(1) { _, _, _ -> crawling }

    assertThrows(IllegalStateException::class.java) { loop(listOf(StandardDice.d6), world).run() }

    assertTrue(world.biases.isEmpty())
    assertTrue("what nothing may fix, a re-throw must", world.respawns.isNotEmpty())
  }

  @Test
  fun `a die that stops cocked is thrown again, not nudged onto a face`() {
    val world =
      FakeWorld(1) { _, _, rethrows ->
        if (rethrows == 0) FakeWorld.settled(cocked) else FakeWorld.settled()
      }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(1, outcome.rethrows)
    assertEquals(listOf(0), world.respawns.map { it.second })
    assertTrue("a settled die is never biased, cocked or not", world.biases.isEmpty())
    assertEquals("the re-thrown die reads its new face", 0, outcome.faces.getValue(0))
    assertTrue(outcome.clean)
  }

  @Test
  fun `a die resting on another is thrown again even though it reads fine`() {
    val world = FakeWorld(1) { _, _, rethrows -> FakeWorld.settled(supportedByDie = rethrows == 0) }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(
      "a stacked die has no table under it, whatever face is up",
      1,
      outcome.rethrows,
    )
  }

  @Test
  fun `a die nobody can read is thrown again until the run gives up, and never answered`() {
    // It used to be thrown three times and then **read off the face it was
    // nearest** — a made-up answer to a die that never came good. There is no
    // budget now: it is thrown as often as it takes, and a run nobody is
    // watching stops waiting rather than inventing a number
    // (`SettleRule.HARD_CAP_SECONDS`).
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(cocked) }

    val refused =
      assertThrows(IllegalStateException::class.java) {
        loop(listOf(StandardDice.d6), world).run()
      }

    assertTrue(
      "the failure does not say what went wrong",
      refused.message.orEmpty().contains("had not settled"),
    )
    assertTrue("the die was not thrown again at all", world.respawns.size > OLD_BUDGET)
  }

  @Test
  fun `a roll that gave up says which dice never settled`() {
    // What the screen needs in order to offer them back: the dice that were
    // read are read and off the table, and only the ones still moving are
    // thrown again (`docs/physics-and-rendering.md`).
    val world =
      FakeWorld(2) { _, index, _ ->
        if (index == 0) FakeWorld.settled() else FakeWorld.tumbling()
      }
    val loop = loop(listOf(StandardDice.d6, StandardDice.d6), world)

    @Suppress("ControlFlowWithEmptyBody")
    while (loop.advance()) {
      // Every step is the same step.
    }

    assertTrue("the roll did not give up", loop.stalled)
    assertEquals("the die that settled was offered back too", listOf(1), loop.unsettled)
  }

  @Test
  fun `a roll that gave up has no outcome, because it has no answer`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.tumbling() }
    val loop = loop(listOf(StandardDice.d6), world)

    @Suppress("ControlFlowWithEmptyBody")
    while (loop.advance()) {
      // Every step is the same step.
    }

    assertThrows(IllegalArgumentException::class.java) { loop.outcome() }
  }

  @Test
  fun `a roll that never settles is given up on rather than answered`() {
    // It used to be force-settled at twelve seconds and read off whatever face
    // each die was nearest, which is a made-up answer to a throw that never
    // ended. A run nobody is watching stops waiting instead, and says so.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.tumbling() }

    assertThrows(IllegalStateException::class.java) { loop(listOf(StandardDice.d6), world).run() }

    assertEquals("it kept stepping past the backstop", SettleRule.HARD_CAP_STEPS, world.steps)
  }

  @Test
  fun `the same seed throws a die again the same way, and another seed does not`() {
    val trouble = unreadableUntilThrownAgain()

    val first = FakeWorld(1, trouble).also { loop(listOf(StandardDice.d6), it, seed = 7L).run() }
    val again = FakeWorld(1, trouble).also { loop(listOf(StandardDice.d6), it, seed = 7L).run() }
    val other = FakeWorld(1, trouble).also { loop(listOf(StandardDice.d6), it, seed = 8L).run() }

    assertTrue("nothing was thrown again, so there is nothing to compare", first.respawnPlacements.isNotEmpty())
    assertEquals("a roll has to replay to itself", first.respawnPlacements, again.respawnPlacements)
    assertNotEquals(
      "two seeds that throw a die again identically are one seed",
      first.respawnPlacements,
      other.respawnPlacements,
    )
  }

  @Test
  fun `the shake drives every step, and pushes the dice the way the phone did not`() {
    val shake =
      List(SettleRule.REST_STEPS) {
        ShakeSample(
          stepIndex = it,
          accelerationMmPerSecond2 = Vector3(2_000.0, 0.0, 0.0),
          gravity = Vector3(0.0, 0.0, -1.0),
        )
      }
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    loop(listOf(StandardDice.d6), world, shake = shake).run()

    assertEquals("gravity is set once per step, always", world.steps, world.gravities.size)
    assertTrue(
      "a phone pulled one way has to load the dice the other",
      world.gravities.first().x < 0.0,
    )
  }

  @Test
  fun `a throw with no dice in it is not a roll`() {
    val world = FakeWorld(0) { _, _, _ -> FakeWorld.settled() }
    val outcome = loop(emptyList(), world).run()

    assertEquals(0, outcome.diceCount)
    assertEquals(0, world.steps)
  }

  /** A die nobody can read until it has been picked up and thrown again. */
  private fun unreadableUntilThrownAgain(): FakeWorld.States =
    FakeWorld.States { _, _, rethrows ->
      if (rethrows == 0) FakeWorld.settled(supportedByDie = true) else FakeWorld.settled()
    }

  /** A die stuck on another for [steps] steps, then down and clean. */

  private fun inTroubleFor(steps: Int): FakeWorld.States =
    FakeWorld.States { step, _, rethrows ->
      if (rethrows == 0 && step < steps) {
        FakeWorld.settling(supportedByDie = true)
      } else {
        FakeWorld.settled()
      }
    }

  @Test
  fun `a roll does not end while the phone is still being shaken`() {
    // Dice that look still while the hand is still going are not a roll that is
    // over — they are a roll caught at the top of a swing. Before this, the
    // shake was the signal to tumble and nothing more: the dice settled under
    // it and every later moment of the shake was dropped.
    val shaking = List(SHAKE_STEPS) { ShakeSample(it, Vector3(6_000.0, 0.0, 0.0), DOWN) }
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }

    val outcome = loop(listOf(StandardDice.d6), world, shake = shaking).run()

    assertTrue(
      "the roll ended after ${outcome.steps} steps, with the hand still shaking at $SHAKE_STEPS",
      outcome.steps > SHAKE_STEPS,
    )
  }

  @Test
  fun `a tapped roll still ends the moment its dice are at rest`() {
    // The rule above must not cost a throw that nobody is shaking a single
    // step: there is no hand to wait for.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }

    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(SettleRule.REST_STEPS.toLong(), outcome.steps.toLong())
  }

  @Test
  fun `the record of a throw is what the loop was handed, not what its spec held`() {
    // A shake-driven throw is spawned the moment the shake is confirmed, so its
    // spec goes into the world empty and the moments arrive afterwards. What
    // the roll is reproducible from is this, and nothing above the loop is in a
    // position to collect it (`docs/physics-and-rendering.md`, "Shake input").
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    val loop = loop(listOf(StandardDice.d6), world)
    val hand = List(3) { ShakeSample(it, Vector3(6_000.0, 0.0, 0.0), DOWN) }

    hand.forEach(loop::shake)
    loop.run()

    assertEquals(hand, loop.drivenBy)
  }

  @Test
  fun `a tapped throw has nothing to be reproduced from but its seed`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    val loop = loop(listOf(StandardDice.d6), world)

    loop.run()

    assertEquals(emptyList<ShakeSample>(), loop.drivenBy)
  }

  @Test
  fun `a hand that never stops holds the roll open, and a run nobody watches gives up`() {
    // Thirty seconds of shaking. A hand holds a roll open for as long as it
    // goes, which is the point of a shake — and on screen that is the player's
    // own doing and their own to stop. A headless run has no hand to stop and
    // no screen to leave, so it is the backstop that ends this one.
    val forever = List(THIRTY_SECONDS_OF_STEPS) { ShakeSample(it, Vector3(6_000.0, 0.0, 0.0), DOWN) }
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }

    assertThrows(IllegalStateException::class.java) {
      loop(listOf(StandardDice.d6), world, shake = forever).run()
    }

    assertEquals("the roll stopped before the backstop", SettleRule.HARD_CAP_STEPS, world.steps)
  }

  @Test
  fun `the roll reports how deep dice ever got into each other, because only the engine knows`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    world.deepestDiePenetrationMm = 0.31

    assertEquals(0.31, loop(listOf(StandardDice.d6), world).run().deepestDiePenetrationMm, 0.0)
  }

  @Test
  fun `a die left standing on another is counted where it ended, not where it passed through`() {
    // Standing on another die for the first stretch of the throw and clear of
    // it by the end: an ordinary moment of a roll, and not a stacked die.
    val world =
      FakeWorld(2) { step, index, _ ->
        val stacked = index == 1 && step < TROUBLE_STEPS
        if (stacked) FakeWorld.settling(supportedByDie = true) else FakeWorld.settled()
      }

    assertEquals(0, loop(listOf(StandardDice.d6, StandardDice.d6), world).run().stackedAtRest)
  }

  @Test
  fun `a die that ends standing on another is counted, whatever the ladder tried`() {
    // A die that comes down on another one every time is thrown again for ever
    // rather than being left standing on it — so the run gives up instead of
    // reporting a die at rest on another die, which was the failure Step 5.5
    // asks the harness to count.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }

    assertThrows(IllegalStateException::class.java) { loop(listOf(StandardDice.d6), world).run() }

    assertTrue("the die was left where it fell", world.respawns.size > OLD_BUDGET)
  }

  private fun loop(
    dice: List<Die>,
    world: FakeWorld,
    shake: List<ShakeSample> = emptyList(),
    seed: Long = 42L,
  ): RollLoop {
    val spec = spec(dice, seed).copy(shake = shake)
    return RollLoop(
      spec = spec,
      world = world,
      layout = SpawnLayout(geometry, RADIUS_MM, spec.seed),
      shake = ShakeDriver(spec.shake),
    )
  }

  private fun spec(
    dice: List<Die>,
    seed: Long,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(
            index = index,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = die,
          )
        },
      geometry = geometry,
      table = table,
      seed = seed,
    )

  private companion object {
    /** The three re-throws a die used to be rationed. */
    const val OLD_BUDGET = 3

    const val RADIUS_MM = 8.0

    /** A shake that outlasts the settle rule several times over. */
    const val SHAKE_STEPS = 200

    /** And one that outlasts the roll's own cap several times over. */
    const val THIRTY_SECONDS_OF_STEPS = 3_600

    /** Straight down, as the gyroscope reports it: a direction, not a magnitude. */
    val DOWN = Vector3(0.0, 0.0, -1.0)
    const val TROUBLE_STEPS = 12
  }
}
