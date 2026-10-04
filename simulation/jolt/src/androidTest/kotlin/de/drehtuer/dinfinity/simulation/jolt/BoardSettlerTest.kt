package de.drehtuer.dinfinity.simulation.jolt

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.BoardBody
import de.drehtuer.dinfinity.simulation.api.BoardDrops
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.CapacityVerdict
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.Placement
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The dice waiting to be thrown, dropped onto the board in a real Jolt world
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * What a JVM test cannot say: that a die let go from above actually comes down
 * inside the tray and stops there, that a die nothing touches is left exactly
 * where it stood, that the same board is the same drop, and how long a drop
 * takes to work out — which is how long a tap waits before its die appears.
 */
@RunWith(AndroidJUnit4::class)
class BoardSettlerTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")
  private val settler = JoltBoardSettler()
  private val d6 = Die.standard("d6", DieShape.Cube)

  @Test
  fun droppedDiceComeDownOnTheFloorInsideTheTray() {
    val request = BoardDrops.request(number = 1, spec(EIGHT, scaleFor(EIGHT)), kept = emptyMap())

    val track = settler.settle(request)

    val lastDrop = request.bodies.maxOf { it.firstStep }
    assertTrue("the drop never stopped", track.steps <= lastDrop + MOST_BOARD_STEPS + 1)
    track.finalPoses.forEachIndexed { die, pose ->
      val at = pose.position
      assertTrue("die $die went through the floor: $at", at.z > 0.0)
      assertTrue("die $die is above the walls: $at", at.z < geometry.wallHeightMm)
      assertTrue("die $die is past a long wall: $at", abs(at.x) < geometry.longSideMm / 2)
      assertTrue("die $die is past a short wall: $at", abs(at.y) < geometry.shortSideMm / 2)
    }
  }

  @Test
  fun everyDieOfAFullBoardIsDroppedAndComesDownInsideTheTray() {
    // On the phone the 35th of 40 once found no clear spot and silently went
    // missing. A die the player added must appear: the shake counts it. Forty
    // let go over one spot is also the heap the lift over dice in the way is
    // for.
    val request = BoardDrops.request(number = 1, spec(FORTY, scaleFor(FORTY)), kept = emptyMap())

    val track = settler.settle(request)

    assertEquals("dice were left off the board", FORTY, request.bodies.size)
    assertEquals("dice went missing from the drop", FORTY, track.dice)
    (0 until FORTY).forEach { die -> assertTrue("die $die was never let go", track.inPlay(die, track.steps - 1)) }
    track.finalPoses.forEachIndexed { die, pose ->
      val at = pose.position
      assertTrue("die $die went through the floor: $at", at.z > 0.0)
      assertTrue("die $die is past a long wall: $at", abs(at.x) < geometry.longSideMm / 2)
      assertTrue("die $die is past a short wall: $at", abs(at.y) < geometry.shortSideMm / 2)
    }
  }

  @Test
  fun diceAddedTogetherLeaveTheOneSpotOneAfterAnotherAndNeverStartInsideAnother() {
    // The feature: a handful comes down as a stream from one point, so the
    // eye can follow it. Each die starts over the spot — or straight above it,
    // lifted over a die still in the way — at its own step, clear of every die
    // in play at that moment.
    val request = BoardDrops.request(number = 1, spec(EIGHT, scaleFor(EIGHT)), kept = emptyMap())
    val radius = ClearSpace.radiusOf(d6, scaleFor(EIGHT))

    val track = settler.settle(request)

    request.bodies.forEachIndexed { die, body ->
      val step = body.firstStep
      assertTrue("die $die was in play before its turn", step == 0 || !track.inPlay(die, step - 1))
      val start = track.poseAt(die, step).position
      val across = Vector3(start.x - BoardDrops.DROP_SPOT.x, start.y - BoardDrops.DROP_SPOT.y, 0.0).length
      assertTrue("die $die was let go $across mm off the spot", across <= BoardDrops.SPOT_JITTER_MM + FLOAT_MM)
      (0 until die).forEach { other ->
        val gap = (track.poseAt(other, step).position - start).length
        assertTrue("die $die started inside die $other ($gap mm apart)", gap >= 2 * radius)
      }
    }
    val distinct = request.bodies.map { it.firstStep }.distinct()
    assertEquals("dice were let go together", EIGHT, distinct.size)
  }

  @Test
  fun aDropThatMissesAStandingDieLeavesItExactlyWhereItStood() {
    // The promise the no-shimmer rule keeps: a die nothing touched is drawn
    // where it stood, to the bit, however the solver left it.
    val first = settler.settle(BoardDrops.request(number = 1, spec(1, 1.0), kept = emptyMap()))
    val standing = first.placementAt(0, first.endsAt)
    val radius = ClearSpace.radiusOf(d6, 1.0)
    val farSide = if (standing.position.x > 0) -FAR_MM else FAR_MM
    val dropped =
      Placement(
        position = Vector3(farSide, 0.0, radius + BoardDrops.DROP_HEIGHT_MM),
        rotation = Quaternion.Identity,
        linearVelocity = Vector3.Zero,
        angularVelocity = Vector3.Zero,
      )
    val request =
      BoardRequest(
        number = 2,
        geometry = geometry,
        table = table,
        bodies = listOf(BoardBody(0, d6, 1.0, standing), BoardBody(1, d6, 1.0, dropped, dropStep = 0)),
      )

    val track = settler.settle(request)

    val start = track.poseAt(0, 0)
    (0 until track.steps).forEach { step ->
      assertEquals("the standing die moved at step $step", start, track.poseAt(0, step))
    }
    assertTrue("the dropped die never fell", track.finalPoses[1].position.z < dropped.position.z - radius)
  }

  @Test
  fun theSameBoardIsTheSameDrop() {
    val request = BoardDrops.request(number = SEVEN, spec(EIGHT, scaleFor(EIGHT)), kept = emptyMap())

    assertEquals(settler.settle(request), settler.settle(request))
  }

  @Test
  fun aBoardIsWorkedOutFastEnoughNotToBeNoticed() {
    // Logged for every size, held to a bound for the one that happens most:
    // one die tapped in among a board that already has plenty on it.
    repeat(WARM_UP) { settler.settle(addingOne(TEN)) }
    timed("1 new among 10") { addingOne(TEN) }
    val oneAmongTwenty = timed("1 new among 20") { addingOne(TWENTY) }
    timed("8 new") { BoardDrops.request(1, spec(EIGHT, scaleFor(EIGHT)), emptyMap()) }
    timed("40 new") { BoardDrops.request(1, spec(FORTY, scaleFor(FORTY)), emptyMap()) }

    assertTrue("one die among twenty took $oneAmongTwenty ms", oneAmongTwenty < ONE_AMONG_TWENTY_MS)
  }

  /** A board of [standing] dice that have come to rest, and one more dropped onto it. */
  private fun addingOne(standing: Int): BoardRequest {
    val scale = scaleFor(standing + 1)
    val first = settler.settle(BoardDrops.request(1, spec(standing, scale), emptyMap()))
    val kept = first.indices.withIndex().associate { (die, index) -> index to first.placementAt(die, first.endsAt) }
    return BoardDrops.request(2, spec(standing + 1, scale), kept)
  }

  /** The median of a few settles of [request]'s board, logged under [label], in milliseconds. */
  private fun timed(
    label: String,
    request: () -> BoardRequest,
  ): Long {
    val times =
      List(TIMED_RUNS) {
        val board = request()
        val started = SystemClock.elapsedRealtimeNanos()
        val track: BoardTrack = settler.settle(board)
        val took = (SystemClock.elapsedRealtimeNanos() - started) / NANOS_PER_MS
        Log.i(TAG, "$label: ${board.bodies.size} dice, ${track.steps} steps, $took ms")
        took
      }.sorted()
    return times[times.size / 2]
  }

  private fun scaleFor(dice: Int): Double =
    (TableCapacity.check(List(dice) { d6 }, geometry) as CapacityVerdict.Fits).scale

  private fun spec(
    dice: Int,
    scale: Double,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        List(dice) { index ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = d6)
        },
      geometry = geometry,
      table = table,
      seed = 0L,
      dieScale = scale,
    )

  private companion object {
    const val TAG = "dinfinity.board"
    const val SEVEN = 7
    const val EIGHT = 8
    const val TEN = 10
    const val TWENTY = 20
    const val FORTY = 40
    const val WARM_UP = 3
    const val TIMED_RUNS = 5
    const val NANOS_PER_MS = 1_000_000L

    /** A float's worth of slack on a position read back from the recording. */
    const val FLOAT_MM = 1e-3

    /** Well clear of a die standing anywhere on the other half of the tray. */
    const val FAR_MM = 90.0

    /**
     * How long dropping one die among twenty may take to work out: three
     * frames at 60 Hz, which is about where a tap starts to feel late.
     */
    const val ONE_AMONG_TWENTY_MS = 50L
  }
}
