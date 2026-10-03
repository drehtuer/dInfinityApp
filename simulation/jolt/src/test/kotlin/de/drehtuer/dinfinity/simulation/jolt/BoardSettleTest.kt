package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.BoardBody
import de.drehtuer.dinfinity.simulation.api.BoardDrops
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.Placement
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A board's drop, run against a world the test controls
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * The rules here are what the board *does not* do as much as what it does:
 * it never nudges a die, never re-throws one and never takes one off — it is
 * dice falling and nothing else — and the world it opens is closed whatever
 * happens.
 */
class BoardSettleTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)

  @Test
  fun `every die is put in the world where the board says, with its catalogue hull`() {
    val world = FakeWorld(TWO) { _, _, _ -> FakeWorld.settled() }
    val request = request(TWO)

    settleOn(world, request)

    assertEquals(request.bodies.map { it.placement }, world.spawned)
    assertEquals(request.bodies.map { ShapeGeometry.hullOf(it.die, it.dieScale) }, world.hulls)
  }

  @Test
  fun `gravity is set once and is the throw's gravity`() {
    val world = FakeWorld(TWO) { _, _, _ -> FakeWorld.settled() }

    settleOn(world, request(TWO))

    assertEquals(listOf(ShakeDriver.DEFAULT_GRAVITY), world.gravities)
  }

  @Test
  fun `the drop stops being recorded once every die has been still long enough`() {
    val world = FakeWorld(TWO) { step, _, _ -> if (step < MOVING_STEPS) FakeWorld.tumbling() else FakeWorld.settled() }

    val track = settleOn(world, request(TWO))

    assertEquals(MOVING_STEPS + SettleRule.REST_STEPS, world.steps)
    assertEquals("the release pose and one per step", world.steps + 1, track.steps)
  }

  @Test
  fun `a board that never stops is recorded up to the cap and no further`() {
    val world = FakeWorld(TWO) { _, _, _ -> FakeWorld.tumbling() }

    val track = settleOn(world, request(TWO))

    assertEquals(MOST_BOARD_STEPS, world.steps)
    assertEquals(MOST_BOARD_STEPS + 1, track.steps)
  }

  @Test
  fun `the cap is three seconds of steps`() {
    assertEquals(3.0, MOST_BOARD_STEPS * SettleRule.TIMESTEP_SECONDS, 1e-9)
  }

  @Test
  fun `what is recorded is where the world says the dice are`() {
    val world = FakeWorld(TWO) { _, _, _ -> FakeWorld.settled(orientation = TURNED) }

    val track = settleOn(world, request(TWO))

    track.finalPoses.forEach { pose ->
      assertEquals(FakeWorld.settled().position, pose.position)
      assertEquals(TURNED.w, pose.orientation.w, FLOAT_TOLERANCE)
      assertEquals(TURNED.z, pose.orientation.z, FLOAT_TOLERANCE)
    }
    assertEquals(listOf(0, 1), track.indices)
  }

  @Test
  fun `nothing on the board is nudged, re-thrown or taken off`() {
    // Even a die resting on another and slowing in the watching window — the
    // case a roll would nudge — is left alone: the board is not a roll, and
    // a cocked die on it stays until the shake throws everything.
    val world =
      FakeWorld(TWO) { step, _, _ ->
        if (step < MOVING_STEPS) FakeWorld.settling(supportedByDie = true) else FakeWorld.settled()
      }

    settleOn(world, request(TWO))

    assertTrue(world.biases.isEmpty())
    assertTrue(world.respawns.isEmpty())
    assertTrue(world.removed.isEmpty())
  }

  @Test
  fun `a die let go at once is put in the world over the spot, and the ones after it are parked out of play`() {
    val world = FakeWorld(THREE) { _, _, _ -> FakeWorld.settled() }
    val request = dropping(THREE)

    settleOn(world, request)

    assertEquals(request.bodies[0].placement, world.spawned[0])
    val spacing = 2 * radius + 2 * ClearSpace.CLEARANCE_MM
    assertEquals(parked(1, spacing), world.spawned[1])
    assertEquals(parked(2, spacing), world.spawned[2])
    assertTrue("a parked die was in the tray", world.spawned.drop(1).all { it.position.z <= -PARKED_BELOW_MM })
  }

  @Test
  fun `no two parked dice touch`() {
    val spacing = 2 * radius + 2 * ClearSpace.CLEARANCE_MM

    assertTrue((parked(1, spacing).position - parked(2, spacing).position).length > 2 * radius)
  }

  @Test
  fun `each die after the first is let go once, at its own step, over the spot`() {
    val world = FakeWorld(THREE) { _, _, _ -> FakeWorld.settled() }
    val request = dropping(THREE)

    settleOn(world, request)

    val interval = BoardDrops.DROP_INTERVAL_STEPS
    assertEquals(listOf(interval to 1, 2 * interval to 2), world.respawns)
    assertEquals(listOf(request.bodies[1].placement, request.bodies[2].placement), world.respawnPlacements)
  }

  @Test
  fun `a die waiting its turn is recorded where it is meant to start and is not in play`() {
    val world = FakeWorld(THREE) { _, _, _ -> FakeWorld.settled() }
    val request = dropping(THREE)

    val track = settleOn(world, request)

    val interval = BoardDrops.DROP_INTERVAL_STEPS
    assertFalse(track.inPlay(2, 2 * interval - 1))
    assertTrue(track.inPlay(2, 2 * interval))
    val meant =
      request.bodies[2]
        .placement.position
        .z
    val waiting = track.poseAt(2, 0).position
    val letGo = track.poseAt(2, 2 * interval).position
    assertEquals(meant, waiting.z, FLOAT_TOLERANCE)
    assertEquals(meant, letGo.z, FLOAT_TOLERANCE)
    assertEquals(FakeWorld.settled().position, track.poseAt(2, 2 * interval + 1).position)
  }

  @Test
  fun `the drop is not over before the last die is let go, and the cap counts from there`() {
    // Everything in play is still long before the last die's turn: the board
    // goes on until it has been let go and has stopped.
    val still = FakeWorld(THREE) { _, _, _ -> FakeWorld.settled() }
    val lastDrop = 2 * BoardDrops.DROP_INTERVAL_STEPS

    settleOn(still, dropping(THREE))

    assertEquals(lastDrop + SettleRule.REST_STEPS, still.steps)

    val restless = FakeWorld(THREE) { _, _, _ -> FakeWorld.tumbling() }

    settleOn(restless, dropping(THREE))

    assertEquals(lastDrop + MOST_BOARD_STEPS, restless.steps)
  }

  @Test
  fun `a die let go over one still falling under the spot starts above it`() {
    val meant = dropping(TWO).bodies[1].placement
    val underTheSpot = meant.position.copy(z = meant.position.z - radius)
    val world =
      FakeWorld(TWO) { _, index, _ ->
        if (index == 0) FakeWorld.tumbling().copy(position = underTheSpot) else FakeWorld.settled()
      }

    settleOn(world, dropping(TWO))

    val start = world.respawnPlacements.single().position
    assertTrue("it started inside the other", (start - underTheSpot).length >= 2 * radius + ClearSpace.CLEARANCE_MM)
    assertEquals(meant.position.x, start.x, 0.0)
    assertEquals(meant.position.y, start.y, 0.0)
  }

  @Test
  fun `a board is settled in a world of its own that is closed afterwards`() {
    val world = FakeWorld(TWO) { _, _, _ -> FakeWorld.settled() }
    var asked: Triple<TableGeometry, TableLook, Int>? = null
    val settler =
      JoltBoardSettler { g, t, n ->
        asked = Triple(g, t, n)
        world
      }

    val track = settler.settle(request(TWO))

    assertEquals(Triple(geometry, table, TWO), asked)
    assertTrue("the board's world was left open", world.closed)
    assertEquals(TWO, track.dice)
  }

  @Test
  fun `and closed when the drop goes wrong`() {
    val world = FakeWorld(TWO) { _, _, _ -> error("the solver fell over") }
    val settler = JoltBoardSettler { _, _, _ -> world }

    assertThrows(IllegalStateException::class.java) { settler.settle(request(TWO)) }
    assertTrue("a world that failed was left open", world.closed)
  }

  @Test
  fun `a bridge that will not open is a failure, not a board drawn some other way`() {
    val settler = JoltBoardSettler { _, _, _ -> null }

    val failure = assertThrows(IllegalStateException::class.java) { settler.settle(request(TWO)) }
    assertTrue(failure.message.orEmpty().contains("would not open"))
  }

  @Test
  fun `a board with no dice opens no world at all`() {
    var opened = false
    val settler =
      JoltBoardSettler { _, _, _ ->
        opened = true
        null
      }

    val track = settler.settle(request(0))

    assertSame(BoardTrack.EMPTY, track)
    assertFalse(opened)
  }

  /** A board of [dice] new d6, as the picker asks for it: let go one after another over the spot. */
  private fun dropping(dice: Int): BoardRequest =
    BoardDrops.request(
      number = 1,
      spec =
        ThrowSpec(
          dice =
            List(dice) {
              DieInstance(index = it, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = StandardDice.d6)
            },
          geometry = geometry,
          table = table,
          seed = 0L,
          dieScale = 1.0,
        ),
      kept = emptyMap(),
    )

  private fun request(dice: Int): BoardRequest =
    BoardRequest(
      number = 1,
      geometry = geometry,
      table = table,
      bodies =
        List(dice) { index ->
          BoardBody(
            index = index,
            die = StandardDice.d6,
            dieScale = 1.0,
            placement =
              Placement(
                position = Vector3(index * SPACING_MM, 0.0, HEIGHT_MM),
                rotation = Quaternion.Identity,
                linearVelocity = Vector3(0.0, 0.0, -SPEED),
                angularVelocity = Vector3.Up,
              ),
          )
        },
    )

  private companion object {
    const val TWO = 2
    const val THREE = 3
    const val MOVING_STEPS = 12
    const val SPACING_MM = 40.0
    const val HEIGHT_MM = 80.0
    const val SPEED = 100.0
    const val FLOAT_TOLERANCE = 1e-6
    val TURNED: Quaternion = Quaternion.about(Vector3.Up, 0.5)
  }
}
