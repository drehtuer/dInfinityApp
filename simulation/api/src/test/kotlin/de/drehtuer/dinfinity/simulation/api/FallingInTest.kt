package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The die a player has just added, falling onto the board.
 *
 * Three things are being pinned here and only one of them is about how it
 * looks. It has to **end where it would have been stood anyway**, so the board
 * is the same board it always was; it has to **leave the dice already
 * standing alone**; and it has to be **incapable of deciding a face**, which
 * is the rule the whole app is built around and the reason this is arithmetic
 * and not a second physics world (`.claude/CLAUDE.md`).
 */
class FallingInTest {
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `a die comes to rest where it would have been stood`() {
    // The fall changes how a die arrives and nothing about where. A board that
    // moved when the animation was added would be a board that could not be
    // compared with the one before it.
    val board = FallingIn.board(geometry, List(THREE) { RADIUS_MM }, SEED)

    val stood = RestingPlaces.of(geometry, List(THREE) { RADIUS_MM })
    assertEquals(stood, board.map { it.restingAt })
  }

  @Test
  fun `and it comes to rest in the one orientation every die comes to rest in`() {
    // The whole of why this cannot become a way to get a number: the turn a
    // die ends on is decided before it is released, and it is the same turn
    // for every die and every seed.
    val board = FallingIn.board(geometry, List(FOUR) { RADIUS_MM }, SEED)

    board.forEach { drop ->
      val standing = drop.orientationAt(drop.restsAt)
      assertTrue(
        standing.turnsAs(Quaternion.Identity),
        "die ${drop.index} came to rest at $standing rather than square on",
      )
    }
  }

  @Test
  fun `a different seed is a different tumble and the same resting place`() {
    val one = FallingIn.board(geometry, List(TWO) { RADIUS_MM }, SEED)
    val other = FallingIn.board(geometry, List(TWO) { RADIUS_MM }, OTHER_SEED)

    assertEquals(one.map { it.restingAt }, other.map { it.restingAt })
    assertNotEquals(one.map { it.throughRadians }, other.map { it.throughRadians })
  }

  @Test
  fun `the same board is always the same fall`() {
    // A player who taps the same saved roll twice sees the same table, and now
    // the same dice arriving on it the same way.
    assertEquals(
      FallingIn.board(geometry, List(THREE) { RADIUS_MM }, SEED),
      FallingIn.board(geometry, List(THREE) { RADIUS_MM }, SEED),
    )
  }

  @Test
  fun `a die starts above its place and ends on it`() {
    val drop = FallingIn.board(geometry, listOf(RADIUS_MM), SEED).single()

    assertTrue(
      drop.positionAt(0.0).z >= drop.restingAt.z + FallingIn.DROP_HEIGHT_MM,
      "the die was not let go from above the table",
    )
    assertEquals(drop.restingAt, drop.positionAt(drop.restsAt))
  }

  @Test
  fun `it falls straight down`() {
    // The one thing that keeps a falling die from being drawn through a die
    // that is standing: it never leaves the column of floor `ClearSpace` gave
    // it, and no two of those columns overlap.
    val drop = FallingIn.board(geometry, listOf(RADIUS_MM), SEED).single()

    (0..SAMPLES).forEach { step ->
      val at = drop.positionAt(drop.restsAt * step / SAMPLES)
      assertEquals(drop.restingAt.x, at.x, 0.0)
      assertEquals(drop.restingAt.y, at.y, 0.0)
    }
  }

  @Test
  fun `it bounces, and each bounce is lower than the last`() {
    val drop = FallingIn.board(geometry, listOf(RADIUS_MM), SEED).single()
    val firstContact = sqrt(2 * drop.heldAtMm / FallingIn.GRAVITY_MM_PER_SECOND2)

    assertEquals(0.0, FallingIn.heightAt(firstContact, drop.heldAtMm), A_HAIR_MM)
    val apex =
      (0..SAMPLES).maxOf { step ->
        FallingIn.heightAt(firstContact + (drop.fallSeconds - firstContact) * step / SAMPLES, drop.heldAtMm)
      }
    assertTrue(apex > 0.0, "the die hit the felt and stayed there")
    val highestABounceMayReach = drop.heldAtMm * FallingIn.BOUNCE * FallingIn.BOUNCE
    assertTrue(apex <= highestABounceMayReach + A_HAIR_MM, "a bounce of $apex mm is higher than the drop allows")
  }

  @Test
  fun `it never goes below the table or back above where it was let go`() {
    val drop = FallingIn.board(geometry, listOf(RADIUS_MM), SEED).single()

    (0..SAMPLES).forEach { step ->
      val height = FallingIn.heightAt(drop.fallSeconds * step / SAMPLES, drop.heldAtMm)
      assertTrue(height >= 0.0, "the die was $height mm through the felt")
      assertTrue(height <= drop.heldAtMm, "the die bounced back above the hand that let it go")
    }
  }

  @Test
  fun `a die that has finished is simply down and stays down`() {
    val drop = FallingIn.board(geometry, listOf(RADIUS_MM), SEED).single()

    assertFalse(drop.moving(drop.restsAt))
    assertEquals(drop.restingAt, drop.positionAt(drop.restsAt + A_LONG_TIME))
    assertTrue(drop.orientationAt(drop.restsAt + A_LONG_TIME).turnsAs(Quaternion.Identity))
  }

  @Test
  fun `it is still turning while it is still falling`() {
    // Otherwise it is a die sliding down a wire rather than a die tumbling.
    val drop = FallingIn.board(geometry, listOf(RADIUS_MM), SEED).single()

    val early = drop.orientationAt(0.0)
    val later = drop.orientationAt(drop.restsAt / 2)
    assertFalse(early.turnsAs(later), "the die did not turn at all on the way down")
    assertTrue(drop.throughRadians > PI, "the die barely turned over on the way down")
  }

  @Test
  fun `and it turns more at the start than at the end`() {
    // Eased out, so the last frames are a die settling rather than a model
    // snapping onto an axis.
    val board = FallingIn.board(geometry, listOf(RADIUS_MM), SEED)
    val whole = board.single().restsAt

    val first = FallingIn.eased(A_TENTH) - FallingIn.eased(0.0)
    val last = FallingIn.eased(1.0) - FallingIn.eased(1.0 - A_TENTH)
    assertTrue(first > last, "the tumble did not slow down; $first then $last over $whole s")
  }

  @Test
  fun `the eased share runs from none of the turn to all of it`() {
    assertEquals(0.0, FallingIn.eased(0.0), 0.0)
    assertEquals(1.0, FallingIn.eased(1.0), 0.0)
  }

  @Test
  fun `a taller drop takes longer`() {
    assertTrue(FallingIn.fallTime(FallingIn.DROP_HEIGHT_MM * 2) > FallingIn.fallTime(FallingIn.DROP_HEIGHT_MM))
  }

  @Test
  fun `a drop is long enough to watch and short enough to be a drop`() {
    // Not a number pinned for its own sake: the whole feature is that the fall
    // is *seen*, and a fall nobody can follow is the placement it replaced.
    val whole = FallingIn.fallTime(FallingIn.DROP_HEIGHT_MM)

    assertTrue(whole > SHORTEST_WORTH_DRAWING, "a fall of $whole s is over before a frame lands")
    assertTrue(whole < LONGEST_BEFORE_IT_DRAGS, "a fall of $whole s is a die being lowered, not dropped")
  }

  @Test
  fun `dice let go together do not all land together`() {
    // A handful arriving on one frame is a thud. They are let go from slightly
    // different heights, so they patter.
    val board = FallingIn.board(geometry, List(SIX) { RADIUS_MM }, SEED)

    assertTrue(board.map { it.restsAt }.distinct().size > 1, "every die landed on the same frame")
  }

  @Test
  fun `a die already standing keeps its place and is not dropped again`() {
    val first = FallingIn.board(geometry, listOf(RADIUS_MM), SEED)
    val keeping = FallingIn.keeping(ONE_DIE, TWO_DICE, first, first.single().restsAt)

    val second = FallingIn.board(geometry, List(TWO) { RADIUS_MM }, SEED, keeping)

    assertEquals(first.single().restingAt, second.first().restingAt, "the standing die was moved")
    assertFalse(second.first().moving(0.0), "the standing die was dropped again")
    assertTrue(second.last().moving(0.0), "the new die did not fall in")
  }

  @Test
  fun `the die that is added lands where it would have been stood all along`() {
    // The board after a fall is the board a fresh layout would have produced,
    // which is what makes "the same dice are always laid out the same way"
    // survive being built one die at a time.
    val one = FallingIn.board(geometry, listOf(RADIUS_MM), SEED)
    val keeping = FallingIn.keeping(ONE_DIE, TWO_DICE, one, one.single().restsAt)
    val grown = FallingIn.board(geometry, List(TWO) { RADIUS_MM }, SEED, keeping)

    assertEquals(RestingPlaces.of(geometry, List(TWO) { RADIUS_MM }), grown.map { it.restingAt })
  }

  @Test
  fun `a die still in the air when the next one is added goes on falling`() {
    val first = FallingIn.board(geometry, listOf(RADIUS_MM), SEED)
    val partWay = first.single().restsAt / 2

    val keeping = FallingIn.keeping(ONE_DIE, TWO_DICE, first, partWay)
    val second = FallingIn.board(geometry, List(TWO) { RADIUS_MM }, SEED, keeping)

    val carried = second.first()
    assertTrue(carried.moving(0.0), "the die that was still in the air was put down early")
    assertEquals(first.single().positionAt(partWay), carried.positionAt(0.0))
  }

  @Test
  fun `a die taken out of the middle leaves the others standing`() {
    // Long-pressing the d6 of `2d6 + 1d20` must not pick the d20 up and drop
    // it again, so what is kept is matched by what the die *is*.
    val board = FallingIn.board(geometry, List(THREE) { RADIUS_MM }, SEED)
    val was = listOf(D6, D6, D20)
    val now = listOf(D6, D20)

    val keeping = FallingIn.keeping(was, now, board, FallingIn.restsAt(board))

    assertEquals(TWO, keeping.size, "a die that was standing was dropped again")
    assertEquals(board[TWO].restingAt, keeping.getValue(1).restingAt, "the d20 was moved")
  }

  @Test
  fun `a die that has been resized is not kept`() {
    // The capacity rule shrinks the dice to fit, and a board whose dice have
    // changed size is a board whose places have all moved.
    val board = FallingIn.board(geometry, listOf(RADIUS_MM), SEED)

    val keeping = FallingIn.keeping(listOf(D6 to RADIUS_MM), listOf(D6 to BIG_RADIUS_MM), board, 0.0)

    assertTrue(keeping.isEmpty())
  }

  @Test
  fun `a die nobody computed a place for is not kept either`() {
    // The tray ran out of floor for it, so there is nothing to carry over.
    val keeping = FallingIn.keeping(ONE_DIE, ONE_DIE, emptyList(), 0.0)

    assertTrue(keeping.isEmpty())
  }

  @Test
  fun `more dice than there is floor for fall in as far as they go`() {
    val board = FallingIn.board(geometry, List(FAR_TOO_MANY) { RADIUS_MM }, SEED)

    assertTrue(board.size < FAR_TOO_MANY, "the whole tray was filled with more dice than it holds")
    assertTrue(board.isNotEmpty(), "nothing was dropped at all")
    assertEquals(board.map { it.index }, board.map { it.index }.sorted())
  }

  @Test
  fun `a board is falling until its last die is down and not afterwards`() {
    val board = FallingIn.board(geometry, List(FOUR) { RADIUS_MM }, SEED)
    val whole = FallingIn.restsAt(board)

    assertTrue(FallingIn.stillFalling(board, 0.0))
    assertTrue(FallingIn.stillFalling(board, whole - A_MOMENT))
    assertFalse(FallingIn.stillFalling(board, whole))
    assertEquals(0.0, FallingIn.restsAt(emptyList()), 0.0)
  }

  @Test
  fun `the board takes nothing from the streams a throw draws on`() {
    // The rule this feature could most easily have broken. Every number the
    // fall needs comes through `Seeds.WAITING`; if it shared the spawn's
    // purpose, every recorded roll would have moved.
    val spawn = Seeds.stream(SEED, dieIndex = 0, purpose = Seeds.SPAWN).nextDouble()
    val falling = Seeds.stream(SEED, dieIndex = 0, purpose = Seeds.WAITING).nextDouble()

    assertNotEquals(spawn, falling)
    assertNotEquals(Seeds.SPAWN, Seeds.WAITING)
    assertNotEquals(Seeds.RETHROW, Seeds.WAITING)
    assertNotEquals(Seeds.BIAS, Seeds.WAITING)
  }

  /** Whether two quaternions are the same turn, `q` and `-q` being one turn. */
  private fun Quaternion.turnsAs(other: Quaternion): Boolean = abs(this dot other) > NEARLY_ONE

  private companion object {
    const val RADIUS_MM = 8.0
    const val BIG_RADIUS_MM = 12.0
    const val SEED = 0L
    const val OTHER_SEED = 4_242L
    const val TWO = 2
    const val THREE = 3
    const val FOUR = 4
    const val SIX = 6
    const val FAR_TOO_MANY = 400
    const val SAMPLES = 400
    const val A_TENTH = 0.1
    const val A_MOMENT = 1e-6
    const val A_HAIR_MM = 1e-9
    const val A_LONG_TIME = 10.0
    const val NEARLY_ONE = 0.999_999

    /** Under this and the fall is over before a 60 Hz panel has drawn it. */
    const val SHORTEST_WORTH_DRAWING = 0.1

    /** Over this and it is not a drop, it is a die being lowered on a string. */
    const val LONGEST_BEFORE_IT_DRAGS = 0.6

    const val D6 = "d6"
    const val D20 = "d20"
    val ONE_DIE = listOf(D6)
    val TWO_DICE = listOf(D6, D6)
  }
}
