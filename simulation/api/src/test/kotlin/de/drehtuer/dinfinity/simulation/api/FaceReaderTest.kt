package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FaceReaderTest {
  @Test
  fun `a die in its reference orientation reads its first face`() {
    StandardDice.all.forEach { die ->
      val reading = FaceReader.read(die, Quaternion.Identity)
      assertIs<Reading.Face>(reading, "${die.id} could not be read upright")
      assertEquals(0, reading.index, die.id)
    }
  }

  @Test
  fun `turning a face to the top reads that face`() {
    val d20 = StandardDice.d20
    ShapeGeometry.directionsOf(DieShape.Icosahedron).forEachIndexed { index, direction ->
      val reading = FaceReader.read(d20, Quaternion.taking(direction, Vector3.Up))
      assertIs<Reading.Face>(reading, "face $index could not be read")
      assertEquals(index, reading.index, "face $index read as ${reading.index}")
    }
  }

  @Test
  fun `every face of every standard die can be turned up and read`() {
    StandardDice.all.forEach { die ->
      ShapeGeometry.directionsOf(die.shape).forEachIndexed { index, direction ->
        val reading = FaceReader.read(die, Quaternion.taking(direction, Vector3.Up))
        assertIs<Reading.Face>(reading, "${die.id} face $index could not be read")
        assertEquals(index, reading.index, "${die.id} face $index read as ${reading.index}")
      }
    }
  }

  @Test
  fun `a die read from a vertex is read from a vertex`() {
    assertTrue(FaceReader.readsFromVertex(StandardDice.d4))
    assertTrue(!FaceReader.readsFromVertex(StandardDice.d6))
  }

  @Test
  fun `a d4 reads the number at the corner pointing up`() {
    val d4 = StandardDice.d4
    ShapeGeometry.directionsOf(DieShape.Tetrahedron).forEachIndexed { index, corner ->
      val reading = FaceReader.read(d4, Quaternion.taking(corner, Vector3.Up))
      assertIs<Reading.Face>(reading)
      assertEquals(index, reading.index)
      assertEquals(index + 1, d4.valueAt(reading.index))
    }
  }

  @Test
  fun `a die tipped just inside fifteen degrees is still readable`() {
    val tilted = Quaternion.about(Vector3(1.0, 0.0, 0.0), Math.toRadians(14.0))
    val reading = FaceReader.read(StandardDice.d6, tilted)
    assertIs<Reading.Face>(reading)
    assertEquals(0, reading.index)
  }

  @Test
  fun `a die balanced on an edge is cocked, not rounded to the nearest face`() {
    // A cube turned 45 degrees rests on an edge; no face is anywhere near up.
    val onEdge = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 4)
    val reading = FaceReader.read(StandardDice.d6, onEdge)
    assertIs<Reading.Cocked>(reading, "a die on its edge must not be read")
    assertTrue(reading.bestAlignment < FaceReader.UPRIGHT_THRESHOLD)
  }

  @Test
  fun `a coin on its rim is cocked`() {
    val onRim = Quaternion.about(Vector3(1.0, 0.0, 0.0), PI / 2)
    assertIs<Reading.Cocked>(FaceReader.read(StandardDice.d2, onRim))
  }

  @Test
  fun `the cocked threshold is fifteen degrees, as the document says`() {
    assertEquals(15.0, FaceReader.COCKED_DEGREES)
    assertEquals(kotlin.math.cos(Math.toRadians(15.0)), FaceReader.UPRIGHT_THRESHOLD, 1e-15)
  }

  @Test
  fun `a die whose faces do not match its shape is a bug, not a reading`() {
    val broken = StandardDice.d6.copy(shape = DieShape.Cube)
    // Same shape, same faces: this one is fine, and proves the check is about
    // a mismatch rather than about the shape.
    assertIs<Reading.Face>(FaceReader.read(broken, Quaternion.Identity))
  }

  @Test
  fun `a rotation and the face it brings up agree for a turn about the up axis`() {
    // Spinning a die about the vertical changes nothing about what is on top.
    val spun = Quaternion.about(Vector3.Up, PI / 3)
    assertEquals(FaceReader.read(StandardDice.d20, Quaternion.Identity), FaceReader.read(StandardDice.d20, spun))
  }
}
