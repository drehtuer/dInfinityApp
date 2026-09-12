package de.drehtuer.dinfinity.simulation.api

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VectorsTest {
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
}
