package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VectorsTest {
  private companion object {
    /** Right-handed frames: the axes, a quarter turn, and something awkward. */
    val FRAMES =
      listOf(
        Triple(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)),
        Triple(Vector3(0.0, 1.0, 0.0), Vector3(-1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0)),
        Triple(Vector3(0.0, 0.0, 1.0), Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
        Triple(Vector3(0.0, 0.0, -1.0), Vector3(0.0, 1.0, 0.0), Vector3(1.0, 0.0, 0.0)),
        // Turned right about, once around each axis in turn: these are where
        // the component solved for is x, then y, then z rather than w.
        Triple(Vector3(1.0, 0.0, 0.0), Vector3(0.0, -1.0, 0.0), Vector3(0.0, 0.0, -1.0)),
        Triple(Vector3(-1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, -1.0)),
        Triple(Vector3(-1.0, 0.0, 0.0), Vector3(0.0, -1.0, 0.0), Vector3(0.0, 0.0, 1.0)),
      )
  }

  @Test
  fun `a vector knows how long it is and which way it points`() {
    val vector = Vector3(3.0, 4.0, 0.0)
    assertEquals(5.0, vector.length, 1e-12)
    assertEquals(Vector3(0.6, 0.8, 0.0), vector.normalised())
  }

  @Test
  fun `a vector of no length has no direction`() {
    assertFailsWith<IllegalArgumentException> { Vector3.Zero.normalised() }
  }

  @Test
  fun `up is up, and nothing is nothing`() {
    assertEquals(Vector3(0.0, 0.0, 1.0), Vector3.Up)
    assertEquals(Vector3(0.0, 0.0, 0.0), Vector3.Zero)
  }

  @Test
  fun `vectors add, subtract, scale and negate`() {
    val a = Vector3(1.0, 2.0, 3.0)
    val b = Vector3(4.0, 5.0, 6.0)
    assertEquals(Vector3(5.0, 7.0, 9.0), a + b)
    assertEquals(Vector3(-3.0, -3.0, -3.0), a - b)
    assertEquals(Vector3(2.0, 4.0, 6.0), a * 2.0)
    assertEquals(Vector3(-1.0, -2.0, -3.0), -a)
  }

  @Test
  fun `the dot product says how closely two directions agree`() {
    assertEquals(1.0, Vector3.Up dot Vector3.Up, 1e-12)
    assertEquals(-1.0, Vector3.Up dot -Vector3.Up, 1e-12)
    assertEquals(0.0, Vector3.Up dot Vector3(1.0, 0.0, 0.0), 1e-12)
  }

  @Test
  fun `two vectors that are the same to within a hair are the same`() {
    assertTrue(Vector3.Up.approximates(Vector3(0.0, 0.0, 1.0 + 1e-12)))
    assertTrue(!Vector3.Up.approximates(Vector3(0.0, 0.0, 0.9)))
  }

  @Test
  fun `the cross product is perpendicular to both`() {
    val crossed = cross(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0))
    assertEquals(Vector3.Up, crossed)
  }

  @Test
  fun `doing nothing leaves a vector where it was`() {
    val vector = Vector3(1.0, 2.0, 3.0)
    assertTrue(Quaternion.Identity.rotate(vector).approximates(vector))
    assertEquals(Quaternion(1.0, 0.0, 0.0, 0.0), Quaternion.Identity)
  }

  @Test
  fun `a quarter turn about x takes up to minus y`() {
    val turned = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 2).rotate(Vector3.Up)
    assertTrue(turned.approximates(Vector3(0.0, -1.0, 0.0), 1e-12), "$turned")
  }

  @Test
  fun `a rotation taking one direction to another does exactly that`() {
    val from = Vector3(1.0, 2.0, 3.0).normalised()
    val to = Vector3(-3.0, 1.0, 0.5).normalised()
    assertTrue(Quaternion.taking(from, to).rotate(from).approximates(to, 1e-12))
  }

  @Test
  fun `a rotation onto itself is no rotation`() {
    assertEquals(Quaternion.Identity, Quaternion.taking(Vector3.Up, Vector3.Up))
  }

  @Test
  fun `a rotation onto the opposite direction turns right round`() {
    val turned = Quaternion.taking(Vector3.Up, -Vector3.Up).rotate(Vector3.Up)
    assertTrue(turned.approximates(-Vector3.Up, 1e-12), "$turned")
  }

  @Test
  fun `a quaternion normalises, because a solver's output drifts`() {
    val drifted = Quaternion(2.0, 0.0, 0.0, 0.0)
    assertEquals(Quaternion.Identity, drifted.normalised())
    val components = Quaternion(1.0, 2.0, 3.0, 4.0)
    assertEquals(1.0, components.w)
    assertEquals(2.0, components.x)
    assertEquals(3.0, components.y)
    assertEquals(4.0, components.z)
    assertEquals(
      1.0,
      sqrt(components.normalised().let { it.w * it.w + it.x * it.x + it.y * it.y + it.z * it.z }),
      1e-12,
    )
  }

  @Test
  fun `a quaternion of nothing is not a rotation`() {
    assertFailsWith<IllegalArgumentException> { Quaternion(0.0, 0.0, 0.0, 0.0).normalised() }
  }

  @Test
  fun `a rotation preserves length`() {
    val turned = Quaternion.about(Vector3(1.0, 1.0, 1.0), 1.1).rotate(Vector3(3.0, 4.0, 0.0))
    assertEquals(5.0, turned.length, 1e-12)
  }

  @Test
  fun `a turn blended with itself is that turn, at either end`() {
    val from = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 3)
    val to = Quaternion.about(Vector3(0.0, 1.0, 0.0), PI / 2)

    assertSameTurn(from, from.slerp(to, 0.0))
    assertSameTurn(to, from.slerp(to, 1.0))
  }

  @Test
  fun `halfway between no turn and a half turn is a quarter turn`() {
    val axis = Vector3(0.0, 0.0, 1.0)
    val half = Quaternion.about(axis, PI)

    val quarter = Quaternion.Identity.slerp(half, 0.5)

    assertSameTurn(Quaternion.about(axis, PI / 2), quarter)
  }

  @Test
  fun `a blend turns at an even rate, not faster through the middle`() {
    val axis = Vector3(0.0, 1.0, 0.0)
    val end = Quaternion.about(axis, PI * 0.75)
    val step = Vector3(1.0, 0.0, 0.0)

    val angles =
      (0..4)
        .map { Quaternion.Identity.slerp(end, it / 4.0).rotate(step) }
        .zipWithNext { a, b -> Exact.acos((a dot b).coerceIn(-1.0, 1.0)) }

    angles.zipWithNext { a, b ->
      assertEquals(a, b, 1e-9, "a blend that speeds up in the middle is not a rotation")
    }
  }

  @Test
  fun `a blend takes the short way round`() {
    // A rotation is two quaternions, q and -q. Blending towards the wrong one
    // sends a die the long way about for no reason anybody watching could
    // explain.
    val axis = Vector3(0.0, 0.0, 1.0)
    val small = Quaternion.about(axis, PI / 6)

    val direct = Quaternion.Identity.slerp(small, 0.5)
    val theLongWayRound = Quaternion.Identity.slerp(-small, 0.5)

    assertSameTurn(direct, theLongWayRound)
    assertSameTurn(Quaternion.about(axis, PI / 12), direct)
  }

  @Test
  fun `a blend of two turns that are already the same is that turn`() {
    // The arc has no length here, so the spherical form divides by zero and
    // the straight line is the same answer.
    val turn = Quaternion.about(Vector3(0.0, 1.0, 0.0), PI / 4)

    assertSameTurn(turn, turn.slerp(turn, 0.5))
    assertEquals(1.0, turn.slerp(turn, 0.5).let { q -> q dot q }, 1e-12)
  }

  @Test
  fun `a blend is always a rotation, wherever it is taken`() {
    val from = Quaternion.about(Vector3(1.0, 2.0, 3.0), 0.3)
    val to = Quaternion.about(Vector3(-2.0, 1.0, 0.5), 2.7)

    (0..10).forEach { step ->
      val blended = from.slerp(to, step / 10.0)
      assertEquals(1.0, blended dot blended, 1e-12, "a blend of length ${blended dot blended} is not a turn")
    }
  }

  @Test
  fun `a frame of three axes becomes the turn that makes them`() {
    // What a renderer wants of a surface: which way is along the texture,
    // which way across it, which way out — as one quaternion, because that is
    // what a vertex buffer carries.
    FRAMES.forEach { (right, up, forward) ->
      val turn = Quaternion.of(right, up, forward)

      assertVector(right, turn.rotate(Vector3(1.0, 0.0, 0.0)), "right")
      assertVector(up, turn.rotate(Vector3(0.0, 1.0, 0.0)), "up")
      assertVector(forward, turn.rotate(Vector3(0.0, 0.0, 1.0)), "forward")
    }
  }

  @Test
  fun `the axes themselves are no turn at all`() {
    val identity = Quaternion.of(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0))

    assertEquals(1.0, abs(identity dot Quaternion.Identity), 1e-12)
  }

  @Test
  fun `a frame turned right about is still a turn, not a division by zero`() {
    // The half-turn is where the naive form loses all its precision: the
    // component it solves for goes to zero and everything else divides by it.
    val turned = Quaternion.of(Vector3(-1.0, 0.0, 0.0), Vector3(0.0, -1.0, 0.0), Vector3(0.0, 0.0, 1.0))

    assertEquals(1.0, turned dot turned, 1e-12)
    assertVector(Vector3(-1.0, 0.0, 0.0), turned.rotate(Vector3(1.0, 0.0, 0.0)), "right")
    assertVector(Vector3(0.0, 0.0, 1.0), turned.rotate(Vector3(0.0, 0.0, 1.0)), "forward")
  }

  private fun assertVector(
    expected: Vector3,
    actual: Vector3,
    axis: String,
  ) {
    assertTrue(expected.approximates(actual, 1e-9), "$axis came out as $actual, not $expected")
  }

  /** Two quaternions are the same turn when they agree up to their sign. */
  private fun assertSameTurn(
    expected: Quaternion,
    actual: Quaternion,
  ) {
    assertEquals(1.0, abs(expected dot actual), 1e-9, "$actual is not the turn $expected")
  }
}
