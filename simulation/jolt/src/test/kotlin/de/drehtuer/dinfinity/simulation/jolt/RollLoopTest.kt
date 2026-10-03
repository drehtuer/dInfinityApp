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
import org.junit.Assert.assertFalse
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
    // be in, and out of reach for exactly that reason. It is not nudged, not
    // lifted and not thrown again — it is reported, and the player's next
    // shake throws it (decision 70).
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }

    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals("the die nobody could read was not handed back", listOf(0), outcome.unread)
    assertTrue("a settled die was biased", world.biases.isEmpty())
    assertTrue("a settled die was thrown again by the roll itself", world.respawns.isEmpty())
    assertTrue("a settled die was taken off the table", world.removed.isEmpty())
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
    assertTrue("and nothing was left waiting for a shake", outcome.complete)
    assertEquals("and taken off a table nothing else was going to be thrown onto", emptyList<Int>(), world.removed)
  }

  @Test
  fun `a die that cannot be read is left where it lies for a hand to throw`() {
    // It used to be thrown again by the roll, as often as it took. A throw is
    // the player's to make, so the throw ends here and says which die is
    // waiting (`docs/physics-and-rendering.md`, "Avoiding stacked and cocked
    // dice").
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals("the die was read off a die it was standing on", emptyMap<Int, Int>(), outcome.faces)
    assertEquals(listOf(0), outcome.unread)
    assertFalse("a throw with a die nobody could read said it was finished", outcome.complete)
    assertEquals("a loop counted a re-throw it never made", 0, outcome.rethrows)
    assertEquals("the die standing on another was not reported", 1, outcome.stackedAtRest)
    assertEquals("the roll ran on after its dice had stopped", SettleRule.REST_STEPS, outcome.steps)
  }

  @Test
  fun `a die that can be read is counted, and the one it holds up waits for the shake`() {
    // Two dice, one readable and one standing on it. The readable one is read;
    // the other is not, and neither is touched — the heap stays on the table
    // exactly as it fell until the player shakes.
    val world =
      FakeWorld(2) { _, index, _ ->
        if (index == 0) FakeWorld.settled() else FakeWorld.settled(supportedByDie = true)
      }
    val loop = loop(listOf(StandardDice.d6, StandardDice.d6), world)

    val outcome = loop.run()

    assertEquals("the die that could be read was not read", setOf(0), outcome.faces.keys)
    assertEquals("the die standing on it was not handed back", listOf(1), outcome.unread)
    assertEquals("the dice are drawn as read", listOf(true, false), loop.countedOut)
    assertEquals("a die came off the table before anybody shook", emptyList<Int>(), world.removed)
    assertTrue("a die was thrown again by the roll itself", world.respawns.isEmpty())
  }

  @Test
  fun `a roll that settles first time leaves every die where it landed`() {
    // The rule the screen depends on. Reading a die and taking it off the
    // table used to be one act, so a roll that went perfectly cleared itself
    // off the felt and left the player looking at an empty tray with a number
    // floating over it. Nothing comes off the table in a throw at all now.
    val world = FakeWorld(3) { _, _, _ -> FakeWorld.settled() }
    val loop = loop(listOf(StandardDice.d6, StandardDice.d20, StandardDice.d6), world)

    val outcome = loop.run()

    assertEquals("a die was thrown again with nothing wrong with it", 0, outcome.rethrows)
    assertEquals("all three were read", 3, outcome.faces.size)
    assertEquals("a die was taken off a table nothing was going to be thrown onto", emptyList<Int>(), world.removed)
    assertEquals("the dice are drawn as read", listOf(true, true, true), loop.countedOut)
  }

  @Test
  fun `the dice of a throw that left some unread are not offered to the throw that follows`() {
    // The next throw is the unread dice's, and it lifts every die this one
    // read to free the floor they stood on. `restingAt` is what a throw is
    // drawn among and aimed around (`ClearSpace`), so a die about to leave
    // the table must not be in it: drawn back it puts two dice in one place,
    // and counted as floor it hides the room the lift made
    // (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
    // adds").
    val world =
      FakeWorld(2) { _, index, _ ->
        if (index == 0) FakeWorld.settled() else FakeWorld.settled(supportedByDie = true)
      }

    val outcome = loop(listOf(StandardDice.d6, StandardDice.d6), world).run()

    assertEquals("the read die kept no face", setOf(0), outcome.faces.keys)
    assertEquals("a die about to be lifted was handed to the next throw", emptyMap<Int, Any>(), outcome.restingAt)
  }

  @Test
  fun `a die in trouble but already too slow to touch is left alone`() {
    // Below the resting speed but not yet counted as at rest. The watching
    // window has closed even though the tracker is still counting, so this die
    // is past helping, and what it is past is the roll's help: it is a die
    // for the player to throw again.
    val crawling =
      FakeWorld.settled(supportedByDie = true).copy(
        motion = DieMotion(speedMmPerSecond = 1.0, spinRadiansPerSecond = 0.001),
      )
    val world = FakeWorld(1) { _, _, _ -> crawling }

    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertTrue(world.biases.isEmpty())
    assertTrue("the roll threw a die again by itself", world.respawns.isEmpty())
    assertEquals("what nothing may fix is the player's to throw", listOf(0), outcome.unread)
  }

  @Test
  fun `a die that stops cocked is left for a hand, not nudged onto a face`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(cocked) }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(listOf(0), outcome.unread)
    assertTrue("a cocked die was read off the face it was nearest", outcome.faces.isEmpty())
    assertTrue("the roll threw it again by itself", world.respawns.isEmpty())
    assertTrue("a settled die is never biased, cocked or not", world.biases.isEmpty())
    assertTrue(outcome.clean)
  }

  @Test
  fun `a die resting on another is not read even though its face is up`() {
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }
    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals("a stacked die has no table under it, whatever face is up", listOf(0), outcome.unread)
  }

  @Test
  fun `a die nobody can read ends the throw unanswered, rather than holding it open`() {
    // It used to be thrown three times and then **read off the face it was
    // nearest** — a made-up answer to a die that never came good — and later
    // thrown again for as long as it took, which in a run nobody watched meant
    // until the backstop. It ends the throw now, the moment the table is
    // still, and waits for a hand (`SettleRule.HARD_CAP_SECONDS`).
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(cocked) }
    val loop = loop(listOf(StandardDice.d6), world)

    val outcome = loop.run()

    assertFalse("a throw that stopped was given up on", loop.stalled)
    assertEquals(SettleRule.REST_STEPS, world.steps)
    assertEquals(listOf(0), outcome.unread)
  }

  @Test
  fun `a roll that gave up says which dice never settled`() {
    // What the screen needs in order to offer them back: the dice that were
    // read are read, and only the ones still moving are thrown again
    // (`docs/physics-and-rendering.md`).
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
    //
    // The moments are numbered on the *world's* clock, not on the sensor's:
    // the numbers below are deliberately nothing like the steps they arrive
    // for, and the record still says which step each one drove
    // (`ShakeDriver.add`).
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    val loop = loop(listOf(StandardDice.d6), world)
    val hand = List(3) { ShakeSample(SENSOR_NUMBERING + it, Vector3(6_000.0, 0.0, 0.0), DOWN) }

    hand.forEach { moment ->
      loop.shake(moment)
      loop.advance()
    }
    loop.run()

    assertEquals(listOf(0, 1, 2), loop.drivenBy.map(ShakeSample::stepIndex))
    assertEquals(
      hand.map(ShakeSample::accelerationMmPerSecond2),
      loop.drivenBy.map(ShakeSample::accelerationMmPerSecond2),
    )
  }

  @Test
  fun `several readings before a single step are one step's worth of gravity`() {
    // Sensors outrun 120 Hz, and a paced roll steps slower still. A step has
    // one gravity, so what the record keeps is the reading that drove it.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled() }
    val loop = loop(listOf(StandardDice.d6), world)

    List(3) { ShakeSample(SENSOR_NUMBERING + it, Vector3(6_000.0, 0.0, 0.0), DOWN) }.forEach(loop::shake)
    loop.run()

    assertEquals(listOf(0), loop.drivenBy.map(ShakeSample::stepIndex))
  }

  @Test
  fun `a roll is driven while a hand is on it and watched once it lets go`() {
    // The boundary the pace is applied at, and it is the same question the
    // loop asks before it lets a roll end
    // (`de.drehtuer.dinfinity.simulation.api.RollPace`).
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.tumbling() }
    val loop = loop(listOf(StandardDice.d6), world)

    assertFalse("a tap-to-roll throw has no hand on it", loop.driven)

    loop.shake(ShakeSample(SENSOR_NUMBERING, Vector3(6_000.0, 0.0, 0.0), DOWN))
    assertTrue("the hand did not reach the roll", loop.driven)

    repeat(ShakeDriver.HOLD_STEPS + 1) { loop.advance() }
    assertFalse("the hand was let go and the roll is still being driven", loop.driven)
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
  fun `a die that ends standing on another is counted as stacked, and as unread`() {
    // The figure Step 5.5 asks the harness to keep at zero is about the dice a
    // roll *ends* with. A throw that leaves one standing on another has not
    // ended the roll — the player's shake throws it next — so it says so in
    // both places: stacked, because it is, and unread, because that is why.
    val world = FakeWorld(1) { _, _, _ -> FakeWorld.settled(supportedByDie = true) }

    val outcome = loop(listOf(StandardDice.d6), world).run()

    assertEquals(1, outcome.stackedAtRest)
    assertEquals(listOf(0), outcome.unread)
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
    /** A shake that outlasts the settle rule several times over. */
    const val SHAKE_STEPS = 200

    /** And one that outlasts the roll's own cap several times over. */
    const val THIRTY_SECONDS_OF_STEPS = 3_600

    /** Straight down, as the gyroscope reports it: a direction, not a magnitude. */
    val DOWN = Vector3(0.0, 0.0, -1.0)

    /** A step index off the sensor's own clock, unlike any the world will take. */
    const val SENSOR_NUMBERING = 500
    const val TROUBLE_STEPS = 12
  }
}
