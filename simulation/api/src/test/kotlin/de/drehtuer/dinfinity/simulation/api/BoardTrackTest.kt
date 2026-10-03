package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A board's drop, played back (`docs/physics-and-rendering.md`, "The dice
 * waiting to be thrown").
 *
 * What matters is that playback is arithmetic over the recording — the same
 * moment asked twice is the same answer — that a die carried over to the next
 * board leaves with the speed it had, and that a die nothing touched is drawn
 * exactly where it stood.
 */
class BoardTrackTest {
  private val step = SettleRule.TIMESTEP_SECONDS

  @Test
  fun `an empty board has no dice, no steps and nothing to play`() {
    val empty = BoardTrack.EMPTY

    assertEquals(0, empty.dice)
    assertEquals(0, empty.steps)
    assertEquals(0.0, empty.endsAt)
    assertTrue(empty.ended(0.0))
    assertEquals(0, empty.stepAt(1.0))
    assertTrue(empty.finalPoses.isEmpty())
  }

  @Test
  fun `the recording starts where the dice were let go`() {
    val track = falling(steps = 3)

    assertEquals(START, track.poseAt(0, 0).position)
    assertEquals(listOf(4), track.indices)
    assertEquals(4, track.steps)
    assertEquals(3 * step, track.endsAt, TOLERANCE)
  }

  @Test
  fun `a moment between two steps is drawn between them`() {
    val track = falling(steps = 3)

    assertEquals(1, track.stepAt(1.5 * step))
    assertEquals(0.5, track.fractionAt(1.5 * step), TOLERANCE)
    val between = track.poseAt(0, 1.5 * step).position
    assertEquals(START.z - 1.5 * DROP_PER_STEP, between.z, FLOAT_TOLERANCE)
  }

  @Test
  fun `a moment before the drop is its start and one after it is its end`() {
    val track = falling(steps = 3)

    assertEquals(0, track.stepAt(-1.0))
    assertEquals(track.poseAt(0, 0), track.poseAt(0, -1.0))
    assertEquals(3, track.stepAt(10.0))
    assertEquals(0.0, track.fractionAt(10.0))
    assertEquals(track.finalPoses.single(), track.poseAt(0, 10.0))
    assertTrue(track.ended(10.0))
    assertFalse(track.ended(0.0))
  }

  @Test
  fun `asking for a die or a step that was not recorded is refused`() {
    val track = falling(steps = 3)

    assertFailsWith<IllegalArgumentException> { track.poseAt(1, 0) }
    assertFailsWith<IllegalArgumentException> { track.poseAt(0, 4) }
  }

  @Test
  fun `a die falling is going as fast as it fell`() {
    val track = falling(steps = 3)

    val moving = track.velocitiesAt(0, 0.5 * step)

    assertEquals(-DROP_PER_STEP / step, moving.linear.z, FLOAT_TOLERANCE / step)
    assertEquals(0.0, moving.angular.length, TOLERANCE)
  }

  @Test
  fun `a die turning is spinning about the axis it turned about, at the rate it turned`() {
    val turn = TURN_PER_STEP
    val track =
      record(steps = 3) { k ->
        BoardPose(START, Quaternion.about(Vector3.Up, turn * k))
      }

    val spin = track.velocitiesAt(0, step).angular

    assertEquals(turn / step, spin.z, SPIN_TOLERANCE)
    assertEquals(0.0, spin.x, SPIN_TOLERANCE)
  }

  @Test
  fun `a turn is read the short way round, whichever sign the solver wrote it with`() {
    // `q` and `-q` are the same turn. A solver can hand back either, and read
    // naively the second is a die spinning nearly a whole turn the other way.
    val turn = TURN_PER_STEP
    val track =
      record(steps = 2) { k ->
        val q = Quaternion.about(Vector3.Up, turn * k)
        BoardPose(START, if (k == 1) -q else q)
      }

    assertEquals(turn / step, track.velocitiesAt(0, 0.0).angular.z, SPIN_TOLERANCE)
  }

  @Test
  fun `a drop that is over is going nowhere`() {
    val track = falling(steps = 3)

    assertEquals(Velocities.None, track.velocitiesAt(0, 1.0))
  }

  @Test
  fun `a die carried over leaves with the speed it had`() {
    val track = falling(steps = 3)

    val carried = track.placementAt(0, step)

    assertEquals(track.poseAt(0, step).position, carried.position)
    assertEquals(-DROP_PER_STEP / step, carried.linearVelocity.z, FLOAT_TOLERANCE / step)
  }

  @Test
  fun `a die the settle rule calls still is carried over still`() {
    // A hair's breadth of solver drift is not a die moving, and starting it
    // with that drift would cost it the promise that it stays exactly where it
    // is.
    val creep = SettleRule.REST_SPEED_MM_PER_SECOND * step / 2
    val track =
      record(steps = 3, moving = true) { k ->
        BoardPose(START.copy(x = START.x + creep * k), Quaternion.Identity)
      }

    val carried = track.placementAt(0, step)

    assertEquals(Vector3.Zero, carried.linearVelocity)
    assertEquals(Vector3.Zero, carried.angularVelocity)
  }

  @Test
  fun `a die that started still and was barely nudged is recorded exactly where it stood`() {
    val nudge = BoardTrack.STILL_DRIFT_MM / 2
    val track =
      record(steps = 5) { k ->
        BoardPose(START.copy(z = START.z + if (k > 0) nudge else 0.0), Quaternion.Identity)
      }

    (0 until track.steps).forEach { k -> assertEquals(START, track.poseAt(0, k).position, "step $k moved") }
  }

  @Test
  fun `and one barely turned is too`() {
    val turned = Quaternion.about(Vector3.Up, BoardTrack.STILL_TURN_RADIANS / 2)
    val track =
      record(steps = 5) { k ->
        BoardPose(START, if (k > 0) turned else Quaternion.Identity)
      }

    (0 until track.steps).forEach { k -> assertEquals(Quaternion.Identity, track.poseAt(0, k).orientation) }
  }

  @Test
  fun `a die that was really knocked is recorded as it moved`() {
    val knock = BoardTrack.STILL_DRIFT_MM * 10
    val track =
      record(steps = 5) { k ->
        BoardPose(START.copy(x = START.x + knock * k), Quaternion.Identity)
      }

    assertEquals(
      START.x + knock * 5,
      track.finalPoses
        .single()
        .position.x,
      FLOAT_TOLERANCE,
    )
  }

  @Test
  fun `and so is one really turned`() {
    val track =
      record(steps = 5) { k ->
        BoardPose(START, Quaternion.about(Vector3.Up, TURN_PER_STEP * k))
      }

    assertNotEquals(Quaternion.Identity, track.finalPoses.single().orientation)
  }

  @Test
  fun `a die that was let go moving is never held still, however little it moved`() {
    val nudge = BoardTrack.STILL_DRIFT_MM / 2
    val track =
      record(steps = 2, moving = true) { k ->
        BoardPose(START.copy(z = START.z + nudge * k), Quaternion.Identity)
      }

    assertNotEquals(START, track.finalPoses.single().position)
  }

  @Test
  fun `a recording grows past its first allocation`() {
    val track = falling(steps = LONG)

    assertEquals(LONG + 1, track.steps)
    assertEquals(
      START.z - LONG * DROP_PER_STEP,
      track.finalPoses
        .single()
        .position.z,
      FLOAT_TOLERANCE * LONG,
    )
  }

  @Test
  fun `a step of the wrong number of dice is refused`() {
    val recorder = BoardTrack.Recorder(listOf(body(Vector3.Zero)))

    assertFailsWith<IllegalArgumentException> { recorder.record(emptyList()) }
  }

  @Test
  fun `two recordings of the same drop are the same track`() {
    assertEquals(falling(steps = 3), falling(steps = 3))
    assertEquals(falling(steps = 3).hashCode(), falling(steps = 3).hashCode())
    assertNotEquals(falling(steps = 3), falling(steps = 4))
    assertNotEquals<Any>(falling(steps = 3), "a track")
  }

  @Test
  fun `the angle between two turns is how far apart they are`() {
    val quarter = Quaternion.about(Vector3.Up, PI / 2)

    assertEquals(PI / 2, BoardTrack.angleBetween(Quaternion.Identity, quarter), TOLERANCE)
    assertEquals(PI / 2, BoardTrack.angleBetween(Quaternion.Identity, -quarter), TOLERANCE)
    assertEquals(0.0, BoardTrack.angleBetween(quarter, quarter), TOLERANCE)
  }

  @Test
  fun `a board stood without a drop puts a moving die on the felt below it, square on`() {
    val released = Placement(START, Quaternion.about(Vector3.Up, 1.0), Vector3(30.0, 0.0, -50.0), Vector3.Up)
    val request = boardOf(BoardBody(4, StandardDice.d6, 1.0, released))

    val track = BoardTrack.standing(request)

    val pose = track.finalPoses.single()
    assertEquals(START.x, pose.position.x, FLOAT_TOLERANCE)
    assertEquals(START.y, pose.position.y, FLOAT_TOLERANCE)
    assertEquals(ClearSpace.radiusOf(StandardDice.d6, 1.0), pose.position.z, FLOAT_TOLERANCE)
    assertEquals(Quaternion.Identity, pose.orientation)
    assertEquals(listOf(4), track.indices)
    assertTrue(track.ended(0.0), "a still board was played as a fall")
  }

  @Test
  fun `and leaves a die that was already standing exactly as it stood`() {
    val turned = Quaternion.about(Vector3.Up, 1.0)
    val standing = Placement(START, turned, Vector3.Zero, Vector3.Zero)
    val request = boardOf(BoardBody(0, StandardDice.d6, 1.0, standing))

    val pose = BoardTrack.standing(request).finalPoses.single()

    assertEquals(START, pose.position)
    assertEquals(turned.w, pose.orientation.w, FLOAT_TOLERANCE)
    assertEquals(turned.z, pose.orientation.z, FLOAT_TOLERANCE)
  }

  @Test
  fun `dice let go over the same spot are stood apart, not inside each other`() {
    // Every die being added is let go over one spot, so standing each one
    // straight below where it was would stand them all in one place.
    val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    val standing = Placement(Vector3(0.0, 0.0, radius), Quaternion.Identity, Vector3.Zero, Vector3.Zero)
    val overTheSpot = Placement(Vector3(0.0, 0.0, HIGH), Quaternion.Identity, Vector3(0.0, 0.0, -1.0), Vector3.Up)
    val request =
      BoardRequest(
        1,
        TableGeometry.referenceDevice(),
        TABLE,
        listOf(
          BoardBody(0, StandardDice.d6, 1.0, overTheSpot, dropStep = 0),
          BoardBody(1, StandardDice.d6, 1.0, standing),
          BoardBody(2, StandardDice.d6, 1.0, overTheSpot, dropStep = LATER),
        ),
      )

    val track = BoardTrack.standing(request)

    val at = track.finalPoses.map { it.position }
    assertEquals(standing.position, at[1], "the die already standing was moved")
    for (a in at.indices) {
      for (b in a + 1 until at.size) {
        assertTrue((at[a] - at[b]).length >= 2 * radius, "dice $a and $b were stood inside each other")
      }
      assertEquals(radius, at[a].z, FLOAT_TOLERANCE)
    }
    assertTrue((0 until track.dice).all { track.inPlay(it, 0) }, "a still board left a die out")
  }

  @Test
  fun `dice too big for any clear point are still stood, below where they were`() {
    // Past anything the capacity rule accepts: nothing is clear anywhere, and
    // a still picture with two dice in one place is better than none.
    val moving = Placement(START, Quaternion.Identity, Vector3(0.0, 0.0, -1.0), Vector3.Up)
    val huge = BoardBody(0, StandardDice.d6, HUGE, moving, dropStep = 0)
    val request = BoardRequest(1, TableGeometry.referenceDevice(), TABLE, listOf(huge, huge.copy(index = 1)))

    val track = BoardTrack.standing(request)

    track.finalPoses.forEach { pose ->
      assertEquals(START.x, pose.position.x, FLOAT_TOLERANCE)
      assertEquals(START.y, pose.position.y, FLOAT_TOLERANCE)
    }
  }

  @Test
  fun `tracks of different dice, or of different poses, are different tracks`() {
    val one = BoardTrack.Recorder(listOf(body(START))).finish()
    val other = BoardTrack.Recorder(listOf(body(START).copy(index = 1))).finish()
    val elsewhere = BoardTrack.Recorder(listOf(body(START.copy(x = -START.x)))).finish()

    assertNotEquals(one, other)
    assertNotEquals(one, elsewhere)
  }

  @Test
  fun `a die let go later is not in play until its step`() {
    val now = BoardBody(0, StandardDice.d6, 1.0, Placement(START, Quaternion.Identity, Vector3.Up, Vector3.Zero), 0)
    val later = now.copy(index = 1, dropStep = LATER)
    val recorder = BoardTrack.Recorder(listOf(now, later))
    repeat(LATER) { recorder.record(List(2) { BoardPose(START, Quaternion.Identity) }) }

    val track = recorder.finish()

    assertTrue(track.inPlay(0, 0))
    assertFalse(track.inPlay(1, LATER - 1))
    assertTrue(track.inPlay(1, LATER))
  }

  @Test
  fun `when a die is let go is part of the track`() {
    val now = BoardBody(0, StandardDice.d6, 1.0, Placement(START, Quaternion.Identity, Vector3.Up, Vector3.Zero), 0)

    val first = BoardTrack.Recorder(listOf(now)).finish()
    val later = BoardTrack.Recorder(listOf(now.copy(dropStep = LATER))).finish()

    assertNotEquals(first, later)
    assertNotEquals(first.hashCode(), later.hashCode())
  }

  @Test
  fun `a recording can start from poses other than the ones the dice were meant to start in`() {
    val meant = body(START)
    val lifted = BoardPose(START.copy(z = HIGH), Quaternion.Identity)

    val track = BoardTrack.Recorder(listOf(meant), listOf(lifted)).finish()

    assertEquals(HIGH, track.poseAt(0, 0).position.z, FLOAT_TOLERANCE)
  }

  @Test
  fun `a die cannot be let go before the drop starts`() {
    assertFailsWith<IllegalArgumentException> { body(START).copy(dropStep = -1) }
  }

  @Test
  fun `a drop that works out is the drop`() {
    val request = boardOf(body(START))
    val dropped = falling(steps = 3)
    var told: Exception? = null

    val track = BoardSettler { dropped }.settleOrStand(request) { told = it }

    assertEquals(dropped, track)
    assertEquals(null, told)
  }

  @Test
  fun `a drop that fails stands the board still and says why, rather than throwing`() {
    // The board is worked out on a thread of its own, where an exception
    // would be the end of the app (`docs/physics-and-rendering.md`).
    val request = boardOf(body(START))
    var told: Exception? = null

    val track = BoardSettler { error("the physics bridge would not open") }.settleOrStand(request) { told = it }

    assertEquals(BoardTrack.standing(request), track)
    assertTrue(told is IllegalStateException)
  }

  @Test
  fun `a failure with nobody listening still falls back`() {
    val request = boardOf(body(START))

    assertEquals(BoardTrack.standing(request), BoardSettler { error("no") }.settleOrStand(request))
  }

  @Test
  fun `an error is not a board's to swallow`() {
    val request = boardOf(body(START))

    assertFailsWith<OutOfMemoryError> { BoardSettler { throw OutOfMemoryError() }.settleOrStand(request) }
  }

  /** One die falling straight down at [DROP_PER_STEP] a step, let go moving. */
  private fun falling(steps: Int): BoardTrack =
    record(steps, moving = true) { k ->
      BoardPose(START.copy(z = START.z - DROP_PER_STEP * k), Quaternion.Identity)
    }

  private fun record(
    steps: Int,
    moving: Boolean = false,
    at: (Int) -> BoardPose,
  ): BoardTrack {
    val start = at(0)
    val velocity = if (moving) Vector3(0.0, 0.0, -1.0) else Vector3.Zero
    val recorder =
      BoardTrack.Recorder(
        listOf(
          BoardBody(
            index = 4,
            die = StandardDice.d6,
            dieScale = 1.0,
            placement = Placement(start.position, start.orientation, velocity, Vector3.Zero),
          ),
        ),
      )
    (1..steps).forEach { recorder.record(listOf(at(it))) }
    return recorder.finish()
  }

  private fun boardOf(body: BoardBody): BoardRequest =
    BoardRequest(1, TableGeometry.referenceDevice(), TABLE, listOf(body))

  private fun body(position: Vector3): BoardBody =
    BoardBody(0, StandardDice.d6, 1.0, Placement(position, Quaternion.Identity, Vector3.Zero, Vector3.Zero))

  private companion object {
    val START = Vector3(10.0, -20.0, 80.0)
    val TABLE = TableLook(id = "plain", name = "Plain")
    const val DROP_PER_STEP = 2.0
    const val HIGH = 90.0
    const val HUGE = 30.0
    const val LATER = 12
    const val TURN_PER_STEP = 0.1
    const val LONG = 200
    const val TOLERANCE = 1e-9
    const val FLOAT_TOLERANCE = 1e-4
    const val SPIN_TOLERANCE = 1e-3
  }
}
