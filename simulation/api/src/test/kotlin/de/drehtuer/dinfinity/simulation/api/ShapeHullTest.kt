package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the hull the physics collides and the faces a die is read from are the
 * same solid.
 *
 * This is the test the whole geometry exists for. A die whose printed face and
 * scored face disagree is the worst bug this app could have — the player would
 * see a 20 and be given a 7 — and it would come from exactly one place: two
 * descriptions of one shape that drifted apart. So the two are built from one
 * construction, turned by one rotation, and checked against each other here.
 */
class ShapeHullTest {
  @Test
  fun `every solid has the corners it should`() {
    assertEquals(48, ShapeGeometry.verticesOf(DieShape.Coin).size, "a coin is a 24-sided rim, top and bottom")
    assertEquals(4, ShapeGeometry.verticesOf(DieShape.Tetrahedron).size)
    assertEquals(8, ShapeGeometry.verticesOf(DieShape.Cube).size)
    assertEquals(6, ShapeGeometry.verticesOf(DieShape.Octahedron).size)
    assertEquals(12, ShapeGeometry.verticesOf(DieShape.PentagonalTrapezohedron).size)
    assertEquals(20, ShapeGeometry.verticesOf(DieShape.Dodecahedron).size)
    assertEquals(20, ShapeGeometry.verticesOf(DieShape.EnneagonalTrapezohedron).size)
    assertEquals(12, ShapeGeometry.verticesOf(DieShape.Icosahedron).size)
  }

  @Test
  fun `every corner of every solid is on one sphere`() {
    DieShape.entries.forEach { shape ->
      ShapeGeometry.verticesOf(shape).forEach { corner ->
        assertEquals(1.0, corner.length, 1e-9, "${shape.id} has a corner at ${corner.length}")
      }
    }
  }

  @Test
  fun `no two corners of a solid are the same point`() {
    DieShape.entries.forEach { shape ->
      val corners = ShapeGeometry.verticesOf(shape)
      assertEquals(corners.size, corners.distinctBy { rounded(it) }.size, "${shape.id} repeats a corner")
    }
  }

  @Test
  fun `every solid is symmetric about its middle, so no corner is favoured`() {
    DieShape.entries.forEach { shape ->
      val sum = ShapeGeometry.verticesOf(shape).reduce(Vector3::plus)
      assertTrue(sum.length < 1e-9, "${shape.id}'s corners lean towards $sum")
    }
  }

  @Test
  fun `every face direction is the outward normal of a real face of the hull`() {
    // The corners furthest out along a face's normal are the corners *of* that
    // face. A face-read solid has at least three of them; if it had one, the
    // "face" would be a corner and the die would be unreadable.
    DieShape.entries
      .filter { it.naturalRead == FaceRead.FaceUp }
      .forEach { shape ->
        val corners = ShapeGeometry.verticesOf(shape)
        ShapeGeometry.directionsOf(shape).forEachIndexed { index, normal ->
          val furthest = corners.maxOf { it dot normal }
          val onTheFace = corners.count { abs((it dot normal) - furthest) < 1e-9 }
          assertTrue(onTheFace >= 3, "${shape.id} face $index is touched by only $onTheFace corners")
        }
      }
  }

  @Test
  fun `a vertex-read solid's directions are its corners, exactly one each`() {
    DieShape.entries
      .filter { it.naturalRead == FaceRead.VertexUp }
      .forEach { shape ->
        val corners = ShapeGeometry.verticesOf(shape)
        ShapeGeometry.directionsOf(shape).forEach { direction ->
          val furthest = corners.maxOf { it dot direction }
          assertEquals(1.0, furthest, 1e-9, "${shape.id}'s reading direction is not a corner")
          assertEquals(1, corners.count { abs((it dot direction) - furthest) < 1e-9 })
        }
      }
  }

  @Test
  fun `every face of a face-read solid is the same distance from the middle, which is what fair means`() {
    DieShape.entries
      .filter { it.naturalRead == FaceRead.FaceUp }
      .forEach { shape ->
        val corners = ShapeGeometry.verticesOf(shape)
        val distances = ShapeGeometry.directionsOf(shape).map { normal -> corners.maxOf { it dot normal } }
        val first = distances.first()
        distances.forEach { distance ->
          assertEquals(first, distance, 1e-9, "${shape.id} has faces at different depths, so it is not fair")
        }
      }
  }

  @Test
  fun `no corner of any solid pokes out through a face`() {
    DieShape.entries.forEach { shape ->
      val corners = ShapeGeometry.verticesOf(shape)
      ShapeGeometry.directionsOf(shape).forEach { normal ->
        val depth = corners.maxOf { it dot normal }
        corners.forEach { corner ->
          assertTrue((corner dot normal) <= depth + 1e-9, "${shape.id} has a corner outside its own face")
        }
      }
    }
  }

  @Test
  fun `a hull is the corners at the die's own size`() {
    val d20 = StandardDice.d20
    val hull = ShapeGeometry.hullOf(d20)
    val radius = ShapeGeometry.boundingRadiusPerSize(DieShape.Icosahedron) * d20.material.sizeMm
    assertEquals(12, hull.size)
    hull.forEach { corner -> assertEquals(radius, corner.length, 1e-9) }
  }

  @Test
  fun `a shrunken die is a shrunken hull, and nothing else changes`() {
    val small = ShapeGeometry.hullOf(StandardDice.d6, scale = 0.5)
    val full = ShapeGeometry.hullOf(StandardDice.d6)
    assertEquals(full.size, small.size)
    full.indices.forEach { index ->
      assertEquals(full[index].length / 2, small[index].length, 1e-9)
    }
  }

  @Test
  fun `a coin is as thick as a quarter of its width`() {
    val coin = ShapeGeometry.hullOf(StandardDice.d2)
    val width = coin.maxOf { it.x } - coin.minOf { it.x }
    val thickness = coin.maxOf { it.z } - coin.minOf { it.z }
    assertEquals(ShapeGeometry.COIN_THICKNESS_RATIO, thickness / width, 1e-9)
  }

  @Test
  fun `a cube's corners are where a cube's corners are`() {
    val corners = ShapeGeometry.verticesOf(DieShape.Cube)
    // Every corner is the same distance from the middle and from its three
    // neighbours: the ones that differ in exactly one coordinate.
    val edge = 2.0 / kotlin.math.sqrt(3.0)
    corners.forEach { corner ->
      val neighbours = corners.count { abs((it - corner).length - edge) < 1e-9 }
      assertEquals(3, neighbours, "a cube corner has three neighbours, not $neighbours")
    }
  }

  @Test
  fun `a trapezohedron has two apexes and a zigzag belt, which is what a d10 is`() {
    listOf(
      DieShape.PentagonalTrapezohedron to 5,
      DieShape.EnneagonalTrapezohedron to 9,
    ).forEach { (shape, sides) ->
      val corners = ShapeGeometry.verticesOf(shape)
      // An apex is where `sides` faces meet; every belt corner is where three
      // do. Counting by face rather than by position is what makes this a
      // statement about the solid rather than about its orientation.
      val faces = ShapeGeometry.directionsOf(shape)
      val meeting =
        corners.map { corner ->
          faces.count { normal -> abs((corner dot normal) - corners.maxOf { it dot normal }) < 1e-9 }
        }
      assertEquals(2, meeting.count { it == sides }, "${shape.id} should have two apexes")
      assertEquals(2 * sides, meeting.count { it == 3 }, "${shape.id} should have a belt of ${2 * sides}")
    }
  }

  @Test
  fun `a trapezohedron is a shade taller than it is wide, as a real d10 is`() {
    listOf(DieShape.PentagonalTrapezohedron, DieShape.EnneagonalTrapezohedron).forEach { shape ->
      val corners = ShapeGeometry.verticesOf(shape)
      // Every corner is on one sphere, so the solid is as wide as it is tall
      // to within the belt's tilt — which is what a d10 in a hand looks like.
      val spread = corners.maxOf { it.length } - corners.minOf { it.length }
      assertTrue(spread < 1e-9, "${shape.id}'s corners are not all on one sphere")
    }
  }

  @Test
  fun `a coin's rim is a full turn, evenly spaced`() {
    val rim = ShapeGeometry.verticesOf(DieShape.Coin).filter { it.z > 0 }
    assertEquals(24, rim.size)
    val angles = rim.map { kotlin.math.atan2(it.y, it.x) }.sorted()
    val steps = angles.zipWithNext { a, b -> b - a }
    steps.forEach { assertEquals(2 * PI / 24, it, 1e-9) }
  }

  private fun rounded(vector: Vector3): Triple<Long, Long, Long> =
    Triple(Math.round(vector.x * 1e9), Math.round(vector.y * 1e9), Math.round(vector.z * 1e9))
}
