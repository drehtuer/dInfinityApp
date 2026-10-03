package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What goes into a board's drop (`docs/physics-and-rendering.md`, "The dice
 * waiting to be thrown").
 *
 * Two things are pinned here. The dice already on the board are **carried
 * over exactly as they are** — where they are, moving as they are — and only
 * the new ones are let go; and the new ones are let go **from above, clear of
 * the others, and differently each time**, which is the whole of what the
 * player asked for over dice laid out in a grid.
 */
class BoardDropsTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun `a die already on the board is kept and the added one is new`() {
    val kept = BoardDrops.keeping(listOf(D6), listOf(D6, D6))

    assertEquals(mapOf(0 to 0), kept)
  }

  @Test
  fun `a die taken out of the middle leaves the others where they were`() {
    // Long-pressing the d6 of `2d6 + 1d20` must not pick the d20 up and drop
    // it again, so what is kept is matched by what the die *is*.
    val kept = BoardDrops.keeping(listOf(D6, D6, D20), listOf(D6, D20))

    assertEquals(mapOf(0 to 0, 1 to 2), kept)
  }

  @Test
  fun `a die that has been resized is not kept`() {
    // The capacity rule shrinks the dice to fit, and a board whose dice have
    // changed size is dropped afresh.
    val kept = BoardDrops.keeping(listOf(StandardDice.d6 to 1.0), listOf(StandardDice.d6 to SHRUNK))

    assertTrue(kept.isEmpty())
  }

  @Test
  fun `a die the drop on screen does not hold is not kept either`() {
    // There is nothing on screen to carry over from.
    val kept = BoardDrops.keeping(listOf(D6, D6), listOf(D6, D6), present = setOf(0))

    assertEquals(mapOf(0 to 0), kept)
  }

  @Test
  fun `a new die is let go above the table and inside its walls`() {
    val placement = release(seed = 1)

    val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    assertTrue(placement.position.z >= radius + BoardDrops.DROP_HEIGHT_MM, "let go from too low")
    assertTrue(
      placement.position.z <= radius + BoardDrops.DROP_HEIGHT_MM + BoardDrops.HEIGHT_JITTER_MM,
      "let go from too high",
    )
    assertTrue(abs(placement.position.x) <= geometry.longSideMm / 2 - radius, "let go over a long wall")
    assertTrue(abs(placement.position.y) <= geometry.shortSideMm / 2 - radius, "let go over a short wall")
  }

  @Test
  fun `it drifts, falls and spins within the named bounds`() {
    repeat(SAMPLES) { seed ->
      val placement = release(seed)
      val velocity = placement.linearVelocity
      val slide = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
      assertTrue(slide in BoardDrops.LEAST_SLIDE_MM_PER_SECOND..BoardDrops.MOST_SLIDE_MM_PER_SECOND, "slide $slide")
      assertTrue(-velocity.z in 0.0..BoardDrops.MOST_DOWNWARD_MM_PER_SECOND, "a die was tossed upwards")
      val spin = placement.angularVelocity.length
      assertTrue(
        spin in BoardDrops.LEAST_SPIN_RADIANS_PER_SECOND..BoardDrops.MOST_SPIN_RADIANS_PER_SECOND + TOLERANCE,
        "spin $spin",
      )
      assertEquals(1.0, placement.rotation.let { it dot it }, TOLERANCE, "the turn was not a unit turn")
    }
  }

  @Test
  fun `no die is let go so high it starts in the lid`() {
    // The tray has a ceiling. A die as big as the capacity rule ever allows,
    // let go from as high as the jitter ever reaches, is still under it.
    val huge = geometry.shortSideMm / 2 - ClearSpace.CLEARANCE_MM - 1.0
    repeat(SAMPLES) { seed ->
      val placement = BoardDrops.release(geometry, huge, emptyList(), Random(seed))
      assertTrue(placement.position.z + huge < geometry.ceilingHeightMm, "a die started through the lid")
    }
    val ordinary = ClearSpace.radiusOf(StandardDice.d20, 1.0)
    val highest = ordinary * 2 + BoardDrops.DROP_HEIGHT_MM + BoardDrops.HEIGHT_JITTER_MM
    assertTrue(highest < geometry.ceilingHeightMm)
  }

  @Test
  fun `a die is never let go onto one already standing`() {
    val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    val taken = listOf(Vector3(0.0, 0.0, radius), Vector3(-60.0, 20.0, radius), Vector3(70.0, -15.0, radius))

    repeat(SAMPLES) { seed ->
      val spot = BoardDrops.release(geometry, radius, taken, Random(seed)).position
      taken.forEach { other ->
        val dx = spot.x - other.x
        val dy = spot.y - other.y
        assertTrue(sqrt(dx * dx + dy * dy) >= 2 * radius + ClearSpace.CLEARANCE_MM - TOLERANCE, "dropped onto a die")
      }
    }
  }

  @Test
  fun `a crowded board falls back to the clearest spot there is`() {
    // A tray this full has almost nowhere a random spot misses everything, so
    // the drop goes where `ClearSpace` says the room is.
    val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    val crowd = crowd(radius)
    val clearest = assertNotNull(ClearSpace.clearestPoint(geometry, radius, crowd))

    val spot = BoardDrops.release(geometry, radius, crowd, AlwaysTheMiddle).position

    assertEquals(clearest.x, spot.x)
    assertEquals(clearest.y, spot.y)
  }

  @Test
  fun `a die too big for the tray is still let go, over the middle`() {
    // Past anything the capacity rule accepts, but a die the player added is
    // never simply missing.
    val tooBig = geometry.longSideMm

    val placement = BoardDrops.release(geometry, tooBig, emptyList(), Random(1))

    assertEquals(0.0, placement.position.x)
    assertEquals(0.0, placement.position.y)
  }

  @Test
  fun `a die with no clear floor is let go over the least crowded point, above what is already there`() {
    // Random spots pack a floor less tightly than a grid. When nothing is
    // clear, the die still goes in — higher, so it starts inside nothing.
    val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    val apart = 2 * radius + ClearSpace.CLEARANCE_MM
    val full = crowd(radius) + Vector3(geometry.longSideMm / 2 - radius, geometry.shortSideMm / 2 - radius, radius)
    val inTheAir = full.map { it.copy(z = HIGH_MM) }
    val taken = full + inTheAir

    val placement = BoardDrops.release(geometry, radius, taken, Random(SEVEN))

    taken.forEach { other ->
      assertTrue((placement.position - other).length >= apart - TOLERANCE, "a die started inside another at $other")
    }
    assertTrue(placement.position.z + radius < geometry.ceilingHeightMm, "a die started through the lid")
  }

  @Test
  fun `a crowded spot is lifted clear of every die it would start inside, and no higher`() {
    val apart = 2 * SMALL_MM + ClearSpace.CLEARANCE_MM
    // Two dice in the way, and one far enough above them that the gap between
    // is room enough: the die goes into the gap, not over the top.
    val stack = listOf(Vector3(0.0, 0.0, LOW_MM), Vector3(0.0, 0.0, LOW_MM + apart - 1.0), Vector3(1.0, 0.0, HIGH_MM))

    val height = CrowdedFloor.stackedHeight(Vector3.Zero, from = LOW_MM, apart = apart, taken = stack)

    assertEquals(LOW_MM + 2 * apart - 1.0 + CrowdedFloor.LIFT_SLACK_MM, height, TOLERANCE)
    assertTrue(height < HIGH_MM, "lifted over a die it had room beneath, to $height")
    stack.forEach { assertTrue((Vector3(0.0, 0.0, height) - it).length >= apart) }
  }

  @Test
  fun `a die straight over a column of dice is lifted over all of them, and the lifting ends`() {
    // The case that once never ended: dice exactly overhead of each other,
    // where a lift to exactly `z + apart` measured a hair under `apart` in
    // floating point and was repeated for ever.
    val apart = 2 * SMALL_MM + ClearSpace.CLEARANCE_MM
    val column = List(COLUMN) { Vector3(0.0, 0.0, SMALL_MM + it * apart) }

    val height = CrowdedFloor.stackedHeight(Vector3.Zero, from = SMALL_MM, apart = apart, taken = column)

    column.forEach { assertTrue((Vector3(0.0, 0.0, height) - it).length >= apart) }
    assertTrue(height <= column.last().z + apart + COLUMN * CrowdedFloor.LIFT_SLACK_MM, "lifted too far, to $height")
  }

  @Test
  fun `a spot nothing is near is not lifted at all`() {
    assertEquals(LOW_MM, CrowdedFloor.stackedHeight(Vector3.Zero, LOW_MM, apart = 10.0, taken = emptyList()))
  }

  @Test
  fun `a tray where nothing fits under the lid gives the lowest spot, held under it`() {
    val spot = CrowdedFloor.spot(geometry, SMALL_MM, emptyList(), from = LOW_MM, ceiling = LOW_MM / 2)

    assertEquals(Vector3(0.0, 0.0, LOW_MM / 2), spot)
  }

  @Test
  fun `a crowded spot is the point furthest from every die`() {
    val radius = SMALL_MM
    val taken = listOf(Vector3(-100.0, 0.0, radius))

    val spot = CrowdedFloor.spot(geometry, radius, taken, from = LOW_MM, ceiling = HIGH_MM)

    assertTrue(spot.x > 100.0, "went to $spot, not the far end")
    assertEquals(LOW_MM, spot.z)
  }

  @Test
  fun `the same board is the same drop and the next board is a different one`() {
    val first = BoardDrops.request(number = 1, spec(TWO), kept = emptyMap())
    val again = BoardDrops.request(number = 1, spec(TWO), kept = emptyMap())
    val next = BoardDrops.request(number = 2, spec(TWO), kept = emptyMap())

    assertEquals(first, again)
    assertNotEquals(first.bodies.map { it.placement }, next.bodies.map { it.placement })
  }

  @Test
  fun `a request carries the board's number, its tray and every die in index order`() {
    val request = BoardDrops.request(number = SEVEN, spec(THREE), kept = emptyMap())

    assertEquals(SEVEN, request.number)
    assertEquals(geometry, request.geometry)
    assertEquals(table, request.table)
    assertEquals(listOf(0, 1, 2), request.bodies.map { it.index })
    assertTrue(request.bodies.all { it.die == StandardDice.d6 && it.dieScale == 1.0 })
  }

  @Test
  fun `a die carried over starts exactly where and how it was`() {
    // A die mid-air when the next one is added goes on falling; a die that
    // was standing goes on standing. Either way it is what was on screen.
    val standing = Placement(Vector3(30.0, 10.0, 8.0), Quaternion.Identity, Vector3.Zero, Vector3.Zero)
    val inTheAir = Placement(Vector3(-40.0, 5.0, 50.0), Quaternion.Identity, Vector3(0.0, 0.0, -300.0), Vector3.Up)

    val request = BoardDrops.request(number = 2, spec(THREE), kept = mapOf(0 to standing, 1 to inTheAir))

    assertEquals(standing, request.bodies[0].placement)
    assertEquals(inTheAir, request.bodies[1].placement)
    assertTrue(
      request.bodies[2]
        .placement.position.z > standing.position.z,
      "the new die was not let go from above",
    )
  }

  @Test
  fun `the new die keeps clear of the ones carried over and of each other`() {
    val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    val standing = Placement(Vector3(0.0, 0.0, radius), Quaternion.Identity, Vector3.Zero, Vector3.Zero)

    repeat(SAMPLES) { board ->
      val bodies = BoardDrops.request(board, spec(FOUR), kept = mapOf(0 to standing)).bodies
      val spots = bodies.map { it.placement.position }
      for (a in spots.indices) {
        for (b in a + 1 until spots.size) {
          val dx = spots[a].x - spots[b].x
          val dy = spots[a].y - spots[b].y
          assertTrue(sqrt(dx * dx + dy * dy) >= 2 * radius, "two dice of board $board were let go together")
        }
      }
    }
  }

  @Test
  fun `every die of the fullest board the capacity rule allows is let go, none inside another`() {
    // A die the player added must appear: the shake will count it.
    val most = mostThatFit()
    val scale = (TableCapacity.check(List(most) { StandardDice.d6 }, geometry) as CapacityVerdict.Fits).scale
    val radius = ClearSpace.radiusOf(StandardDice.d6, scale)

    val request = BoardDrops.request(number = 1, spec(most, scale), kept = emptyMap())

    assertEquals((0 until most).toList(), request.bodies.map { it.index })
    val spots = request.bodies.map { it.placement.position }
    for (a in spots.indices) {
      for (b in a + 1 until spots.size) {
        assertTrue((spots[a] - spots[b]).length >= 2 * radius, "dice $a and $b start inside each other")
      }
      assertTrue(spots[a].z + radius < geometry.ceilingHeightMm, "die $a started through the lid")
    }
  }

  /** The most d6 the capacity rule lets onto this tray, at whatever scale. */
  private fun mostThatFit(): Int =
    (TableCapacity.MAX_DICE downTo 1).first {
      TableCapacity.check(List(it) { StandardDice.d6 }, geometry) is CapacityVerdict.Fits
    }

  @Test
  fun `a turn drawn at random is a unit turn and no two are alike`() {
    val random = Random(SEVEN)
    val turns = List(SAMPLES) { BoardDrops.anyTurn(random) }

    turns.forEach { assertEquals(1.0, it dot it, TOLERANCE) }
    assertEquals(SAMPLES, turns.distinct().size)
  }

  @Test
  fun `turns drawn at random favour no way up`() {
    // Evenly over every turn: where the die's own up ends up is spread evenly
    // over the sphere, so its height is spread evenly from -1 to 1.
    val random = Random(SEVEN)
    val heights = List(MANY) { BoardDrops.anyTurn(random).rotate(Vector3.Up).z }

    assertEquals(0.0, heights.average(), SPREAD)
    assertEquals(1.0 / 3.0, heights.map { it * it }.average(), SPREAD)
  }

  @Test
  fun `a direction drawn at random is a unit direction`() {
    val random = Random(SEVEN)

    repeat(SAMPLES) { assertEquals(1.0, BoardDrops.anyDirection(random).length, TOLERANCE) }
  }

  private fun release(seed: Int): Placement =
    BoardDrops.release(geometry, ClearSpace.radiusOf(StandardDice.d6, 1.0), emptyList(), Random(seed))

  /** Dice on a grid, a little apart, leaving clear floor only in one corner. */
  private fun crowd(radius: Double): List<Vector3> {
    val step = 2 * radius + 1.0
    val spots = mutableListOf<Vector3>()
    var x = -geometry.longSideMm / 2 + radius
    while (x < geometry.longSideMm / 2) {
      var y = -geometry.shortSideMm / 2 + radius
      while (y < geometry.shortSideMm / 2) {
        val inTheCorner = x > geometry.longSideMm / 2 - CORNER_CELLS * step && y > geometry.shortSideMm / 2 - step * 2
        if (!inTheCorner) spots += Vector3(x, y, radius)
        y += step
      }
      x += step
    }
    return spots
  }

  private fun spec(
    count: Int,
    scale: Double = 1.0,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        List(count) {
          DieInstance(index = it, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = StandardDice.d6)
        },
      geometry = geometry,
      table = table,
      seed = 0L,
      dieScale = scale,
    )

  /** A random source that always answers the middle of whatever is asked. */
  private object AlwaysTheMiddle : Random() {
    override fun nextBits(bitCount: Int): Int = 0

    override fun nextDouble(): Double = 0.5

    override fun nextDouble(
      from: Double,
      until: Double,
    ): Double = (from + until) / 2
  }

  private companion object {
    val D6: Pair<Die, Double> = StandardDice.d6 to 1.0
    val D20: Pair<Die, Double> = StandardDice.d20 to 1.0
    const val SHRUNK = 0.8
    const val TWO = 2
    const val THREE = 3
    const val FOUR = 4
    const val SEVEN = 7
    const val COLUMN = 6
    const val SAMPLES = 50
    const val MANY = 20_000
    const val SPREAD = 0.02
    const val HIGH_MM = 80.0
    const val LOW_MM = 20.0
    const val SMALL_MM = 5.0
    const val CORNER_CELLS = 3
    const val TOLERANCE = 1e-9
  }
}
