package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What goes into a board's drop (`docs/physics-and-rendering.md`, "The dice
 * waiting to be thrown").
 *
 * Three things are pinned here. The dice already on the board are **carried
 * over exactly as they are** — where they are, moving as they are — and only
 * the new ones are let go; the new ones are let go **over one spot, one after
 * another**, so the eye can follow them; and a die let go **never starts
 * inside another**, however crowded the spot is by then.
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
    // There is nothing on screen to carry over from — a die still waiting its
    // turn to be let go, say.
    val kept = BoardDrops.keeping(listOf(D6, D6), listOf(D6, D6), present = setOf(0))

    assertEquals(mapOf(0 to 0), kept)
  }

  @Test
  fun `a new die is let go over the one spot, from the drop height`() {
    val radius = ClearSpace.radiusOf(StandardDice.d6, 1.0)

    repeat(SAMPLES) { seed ->
      val at = release(seed).position
      assertTrue(across(at, BoardDrops.DROP_SPOT) <= BoardDrops.SPOT_JITTER_MM + TOLERANCE, "let go off the spot")
      assertEquals(radius + BoardDrops.DROP_HEIGHT_MM, at.z, TOLERANCE)
    }
  }

  @Test
  fun `the spot is the middle of the tray, and the jitter is too small to see`() {
    assertEquals(Vector3.Zero, BoardDrops.DROP_SPOT)
    assertTrue(BoardDrops.SPOT_JITTER_MM > 0.0, "two dice let go over the spot would land centre on centre")
    assertTrue(BoardDrops.SPOT_JITTER_MM <= MOST_JITTER_MM, "a jitter this big moves the spot")
  }

  @Test
  fun `two dice are not let go from exactly the same point`() {
    val points = List(SAMPLES) { seed -> release(seed).position }

    assertEquals(SAMPLES, points.distinct().size)
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
    // The tray has a ceiling. A die as big as the capacity rule ever allows
    // is still let go under it.
    val huge = geometry.shortSideMm / 2 - ClearSpace.CLEARANCE_MM - 1.0
    repeat(SAMPLES) { seed ->
      val placement = BoardDrops.release(geometry, huge, Random(seed))
      assertTrue(placement.position.z + huge < geometry.ceilingHeightMm, "a die started through the lid")
    }
    val ordinary = ClearSpace.radiusOf(StandardDice.d20, 1.0)
    assertTrue(ordinary * 2 + BoardDrops.DROP_HEIGHT_MM < geometry.ceilingHeightMm)
  }

  @Test
  fun `the dice of a handful are let go a named interval apart`() {
    assertEquals(
      BoardDrops.DROP_INTERVAL_SECONDS,
      BoardDrops.DROP_INTERVAL_STEPS * SettleRule.TIMESTEP_SECONDS,
      SettleRule.TIMESTEP_SECONDS / 2,
    )
    assertTrue(BoardDrops.DROP_INTERVAL_SECONDS in QUICKEST_INTERVAL..SLOWEST_INTERVAL)
  }

  @Test
  fun `a handful too big to let go in the longest stream at that pace is let go closer together`() {
    val longest = BoardDrops.LONGEST_STREAM_SECONDS / SettleRule.TIMESTEP_SECONDS
    val interval = BoardDrops.DROP_INTERVAL_STEPS

    assertEquals(interval, BoardDrops.intervalFor(1))
    assertEquals(interval, BoardDrops.intervalFor(FORTY))
    assertTrue(BoardDrops.intervalFor(TableCapacity.MAX_DICE) < interval)
    assertTrue(BoardDrops.intervalFor(TableCapacity.MAX_DICE) * (TableCapacity.MAX_DICE - 1) <= longest)
    assertEquals(1, BoardDrops.intervalFor(Int.MAX_VALUE), "two dice were let go in one step")
  }

  @Test
  fun `a spot with nothing near it is where the die starts`() {
    val meant = release(1)

    val placement = BoardDrops.letGo(geometry, meant, RADIUS, RADIUS, inPlay = listOf(Vector3(FAR_MM, 0.0, RADIUS)))

    assertEquals(meant, placement)
  }

  @Test
  fun `a die let go over one still falling under the spot starts above it, not inside it`() {
    val meant = release(1)
    val falling = meant.position.copy(z = meant.position.z - RADIUS)

    val placement = BoardDrops.letGo(geometry, meant, RADIUS, RADIUS, inPlay = listOf(falling))

    assertTrue((placement.position - falling).length >= 2 * RADIUS + ClearSpace.CLEARANCE_MM, "started inside it")
    assertTrue(placement.position.z > falling.z, "was not lifted over it")
    assertEquals(meant.position.x, placement.position.x)
    assertEquals(meant.position.y, placement.position.y)
    assertEquals(meant.copy(position = placement.position), placement, "more than where it starts changed")
  }

  @Test
  fun `the gap kept is the one the biggest die in play needs`() {
    // A d6 let go over a d20 must clear the d20, not just another d6.
    val meant = release(1)
    val big = RADIUS * 2
    val under = meant.position.copy(z = meant.position.z - RADIUS)

    val placement = BoardDrops.letGo(geometry, meant, RADIUS, big, inPlay = listOf(under))

    assertTrue((placement.position - under).length >= RADIUS + big + ClearSpace.CLEARANCE_MM - TOLERANCE)
  }

  @Test
  fun `a heap at the spot as high as the lid sends the die to the least crowded point instead`() {
    val meant = release(1)
    val apart = 2 * RADIUS + ClearSpace.CLEARANCE_MM
    val column = List(COLUMN_TO_THE_LID) { Vector3(meant.position.x, meant.position.y, RADIUS + it * apart) }

    val placement = BoardDrops.letGo(geometry, meant, RADIUS, RADIUS, inPlay = column)

    assertTrue(across(placement.position, meant.position) > apart, "stayed over the heap")
    assertTrue(placement.position.z + RADIUS < geometry.ceilingHeightMm, "started through the lid")
    column.forEach { assertTrue((placement.position - it).length >= apart - TOLERANCE, "started inside $it") }
  }

  @Test
  fun `a crowded tray sends the die to the least crowded point, above everything already there`() {
    // Dice standing over nearly the whole floor and as many again in the air
    // above them: the die still goes in, and starts inside nothing.
    val apart = 2 * RADIUS + ClearSpace.CLEARANCE_MM
    val full = crowd(RADIUS)
    val taken = full + full.map { it.copy(z = HIGH_MM) }
    val ceiling = geometry.ceilingHeightMm - RADIUS - ClearSpace.CLEARANCE_MM

    val spot = CrowdedFloor.spot(geometry, RADIUS, taken, from = LOW_MM, ceiling = ceiling, apart = apart)

    taken.forEach { assertTrue((spot - it).length >= apart - TOLERANCE, "a die started inside another at $it") }
    assertTrue(spot.z <= ceiling, "a die started through the lid")
  }

  @Test
  fun `a die too big for the floor is sent over the middle of it`() {
    val tooBig = geometry.longSideMm

    val spot = CrowdedFloor.spot(geometry, tooBig, emptyList(), from = LOW_MM, ceiling = HIGH_MM, apart = tooBig)

    assertEquals(Vector3(0.0, 0.0, LOW_MM), spot)
  }

  @Test
  fun `a die too big for the tray is still let go, over the middle`() {
    // Past anything the capacity rule accepts, but a die the player added is
    // never simply missing.
    val tooBig = geometry.longSideMm

    val placement = BoardDrops.release(geometry, tooBig, Random(1))

    assertTrue(across(placement.position, Vector3.Zero) <= BoardDrops.SPOT_JITTER_MM + TOLERANCE)
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
    val spot = CrowdedFloor.spot(geometry, SMALL_MM, emptyList(), from = LOW_MM, ceiling = LOW_MM / 2, apart = LOW_MM)

    assertEquals(Vector3(0.0, 0.0, LOW_MM / 2), spot)
  }

  @Test
  fun `a crowded spot is the point furthest from every die`() {
    val radius = SMALL_MM
    val taken = listOf(Vector3(-100.0, 0.0, radius))

    val spot = CrowdedFloor.spot(geometry, radius, taken, from = LOW_MM, ceiling = HIGH_MM, apart = 2 * radius)

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
  fun `a die carried over starts exactly where and how it was, and is on the board from the start`() {
    // A die mid-air when the next one is added goes on falling; a die that
    // was standing goes on standing. Either way it is what was on screen.
    val standing = Placement(Vector3(30.0, 10.0, 8.0), Quaternion.Identity, Vector3.Zero, Vector3.Zero)
    val inTheAir = Placement(Vector3(-40.0, 5.0, 50.0), Quaternion.Identity, Vector3(0.0, 0.0, -300.0), Vector3.Up)

    val request = BoardDrops.request(number = 2, spec(THREE), kept = mapOf(0 to standing, 1 to inTheAir))

    assertEquals(standing, request.bodies[0].placement)
    assertEquals(inTheAir, request.bodies[1].placement)
    assertNull(request.bodies[0].dropStep)
    assertNull(request.bodies[1].dropStep)
    assertTrue(
      request.bodies[2]
        .placement.position.z > standing.position.z,
      "the new die was not let go from above",
    )
  }

  @Test
  fun `the new dice are let go one after another, the first at once, in index order`() {
    // A saved roll put on the table is several dice added at once, and they
    // leave the spot as a stream rather than all together.
    val standing = Placement(Vector3(30.0, 10.0, 8.0), Quaternion.Identity, Vector3.Zero, Vector3.Zero)

    val bodies = BoardDrops.request(number = 1, spec(FOUR), kept = mapOf(1 to standing)).bodies

    val interval = BoardDrops.DROP_INTERVAL_STEPS
    assertEquals(listOf(0, null, interval, 2 * interval), bodies.map { it.dropStep })
    assertEquals(listOf(0, 0, interval, 2 * interval), bodies.map { it.firstStep })
  }

  @Test
  fun `every die of the fullest board the capacity rule allows is let go, none inside another`() {
    // A die the player added must appear: the shake will count it. The worst
    // case is every die before it still exactly where it was let go — none of
    // them having fallen at all — which stacks them over the spot up to the
    // lid and then sends the rest elsewhere.
    val most = mostThatFit()
    val scale = (TableCapacity.check(List(most) { StandardDice.d6 }, geometry) as CapacityVerdict.Fits).scale
    val radius = ClearSpace.radiusOf(StandardDice.d6, scale)

    val request = BoardDrops.request(number = 1, spec(most, scale), kept = emptyMap())

    assertEquals((0 until most).toList(), request.bodies.map { it.index })
    val steps = request.bodies.mapNotNull { it.dropStep }
    assertEquals(most, steps.toSet().size, "two dice were let go together")
    val started = mutableListOf<Vector3>()
    request.bodies.forEach { body ->
      started += BoardDrops.letGo(geometry, body.placement, radius, radius, started).position
    }
    for (a in started.indices) {
      for (b in a + 1 until started.size) {
        assertTrue((started[a] - started[b]).length >= 2 * radius, "dice $a and $b start inside each other")
      }
      assertTrue(started[a].z + radius < geometry.ceilingHeightMm, "die $a started through the lid")
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

  private fun release(seed: Int): Placement = BoardDrops.release(geometry, RADIUS, Random(seed))

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

  /** How far apart [a] and [b] are across the table, heights aside. */
  private fun across(
    a: Vector3,
    b: Vector3,
  ): Double {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
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

  private companion object {
    val D6: Pair<Die, Double> = StandardDice.d6 to 1.0
    val D20: Pair<Die, Double> = StandardDice.d20 to 1.0
    val RADIUS: Double = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    const val SHRUNK = 0.8
    const val TWO = 2
    const val THREE = 3
    const val FOUR = 4
    const val SEVEN = 7
    const val FORTY = 40
    const val COLUMN = 6
    const val COLUMN_TO_THE_LID = 12
    const val CORNER_CELLS = 3
    const val SAMPLES = 50
    const val MANY = 20_000
    const val SPREAD = 0.02
    const val HIGH_MM = 80.0
    const val LOW_MM = 20.0
    const val SMALL_MM = 5.0
    const val FAR_MM = 80.0
    const val MOST_JITTER_MM = 2.0
    const val QUICKEST_INTERVAL = 0.05
    const val SLOWEST_INTERVAL = 0.2
    const val TOLERANCE = 1e-9
  }
}
