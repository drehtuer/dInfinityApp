package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arrays a GPU is handed.
 *
 * Off-by-one lives here, and off-by-one in an index buffer is a triangle drawn
 * through the middle of a die — which on a device looks like a rendering
 * glitch and is really arithmetic. So the packing is checked against the mesh
 * it came from, number by number, on the JVM.
 */
class GpuMeshTest {
  private val die = DieMesh.of(DieShape.Cube)
  private val packed = GpuMesh.of(die.faces)

  @Test
  fun `every corner of every surface is packed, and none is shared`() {
    // Two faces of a die meet at the same point but disagree about which way
    // they face and where they sit in the texture, so each carries its own.
    assertEquals(die.faces.sumOf { it.positions.size }, packed.vertexCount)
    assertEquals(die.faces.sumOf { it.triangles.size } / 3, packed.triangleCount)
  }

  @Test
  fun `a position comes back out as it went in`() {
    var corner = 0
    die.faces.forEach { face ->
      face.positions.forEach { position ->
        assertEquals(position.x.toFloat(), packed.positions[corner * 3], TOLERANCE)
        assertEquals(position.y.toFloat(), packed.positions[corner * 3 + 1], TOLERANCE)
        assertEquals(position.z.toFloat(), packed.positions[corner * 3 + 2], TOLERANCE)
        corner++
      }
    }
  }

  @Test
  fun `a die is packed at the size it is thrown at`() {
    val scaled = GpuMesh.of(die.faces, scale = RADIUS_MM)

    assertEquals(
      "a mesh one unit across has to become a die of a size somewhere",
      packed.positions[0] * RADIUS_MM.toFloat(),
      scaled.positions[0],
      TOLERANCE,
    )
  }

  @Test
  fun `an index points at a corner of the surface it belongs to, never another`() {
    // The one mistake that draws a triangle through the middle of a die: a
    // surface's own indices are relative to itself and have to be shifted.
    var corner = 0
    var index = 0
    die.faces.forEach { face ->
      face.triangles.forEach { own ->
        assertEquals("face ${face.index} points outside itself", corner + own, packed.indices[index])
        assertTrue(packed.indices[index] in corner until corner + face.positions.size)
        index++
      }
      corner += face.positions.size
    }
  }

  @Test
  fun `the tangent frame turns the axes onto the surface's own`() {
    // What Filament reads out of it: x along the texture, z out of the
    // surface. If those two are wrong the die is lit as though it faced
    // somewhere else.
    var corner = 0
    die.faces.forEach { face ->
      val frame = frameAt(corner)
      assertTrue(
        "face ${face.index} is lit as though it faced ${frame.rotate(Vector3(0.0, 0.0, 1.0))}",
        face.normal.approximates(frame.rotate(Vector3(0.0, 0.0, 1.0)), LOOSE),
      )
      assertTrue(
        "face ${face.index}'s texture is lit as running the wrong way",
        face.tangent.approximates(frame.rotate(Vector3(1.0, 0.0, 0.0)), LOOSE),
      )
      corner += face.positions.size
    }
  }

  @Test
  fun `every tangent frame is a rotation, and the half Filament reads`() {
    listOf(packed, GpuMesh.of(TrayMesh.of(TableGeometry.referenceDevice()).surfaces)).forEach { mesh ->
      (0 until mesh.vertexCount).forEach { corner ->
        val frame = mesh.tangentAt(corner)
        assertEquals("a tangent frame that is not a turn", 1.0, frame dot frame, LOOSE)
        assertTrue("a tangent frame Filament would read as reflected", frame.w >= 0.0)
      }
    }
  }

  @Test
  fun `a surface with no texture still has a corner where its texture would be`() {
    // The rim of a coin takes a plain colour, but the buffer is one stride per
    // corner whether anything samples it or not.
    val coin = GpuMesh.of(DieMesh.of(DieShape.Coin).faces)

    assertEquals(coin.vertexCount * GpuMesh.UV_SIZE, coin.uvs.size)
  }

  @Test
  fun `the tray packs the same way a die does`() {
    val tray = TrayMesh.of(TableGeometry.referenceDevice())
    val floor = GpuMesh.of(tray.partsOf(TrayPart.Floor))

    assertEquals(
      tray
        .partsOf(TrayPart.Floor)
        .single()
        .positions.size,
      floor.vertexCount,
    )
    assertEquals(1.0, floor.tangentAt(0).let { it dot it }, LOOSE)
  }

  @Test
  fun `nothing at all packs to nothing, rather than to a crash`() {
    val empty = GpuMesh.of(emptyList())

    assertEquals(0, empty.vertexCount)
    assertEquals(0, empty.triangleCount)
  }

  @Test
  fun `the same mesh packs to the same arrays`() {
    assertEquals(GpuMesh.of(die.faces), GpuMesh.of(die.faces))
    assertEquals(GpuMesh.of(die.faces).hashCode(), GpuMesh.of(die.faces).hashCode())
  }

  @Test
  fun `a frame whose quaternion points backwards is turned round`() {
    // A rotation is two quaternions, q and -q, and Filament reads the half
    // with a positive w. A frame that naturally lands on the other half has
    // to be flipped, or it is read as a reflection and lit inside out.
    val backwards = Quaternion(w = -0.5, x = 0.5, y = 0.5, z = 0.5).normalised()
    val surface =
      surface(
        tangent = backwards.rotate(Vector3(1.0, 0.0, 0.0)),
        normal = backwards.rotate(Vector3(0.0, 0.0, 1.0)),
      )

    val packed = GpuMesh.of(listOf(surface)).tangentAt(0)

    assertTrue("Filament reads only the positive half", packed.w >= 0.0)
    assertTrue(
      "turning it round changed which way the surface faces",
      surface.normal.approximates(packed.rotate(Vector3(0.0, 0.0, 1.0)), LOOSE),
    )
  }

  @Test
  fun `two different meshes are not the same mesh`() {
    val coin = GpuMesh.of(DieMesh.of(DieShape.Coin).faces)

    assertNotEquals(packed, coin)
    assertNotEquals(packed, GpuMesh.of(die.faces, scale = RADIUS_MM))
    assertFalse("a string is not a mesh", packed.equals("not a mesh at all"))
    assertEquals("a mesh is itself", packed, packed)
  }

  /** A single triangle with the frame given, for the packing to chew on. */
  private fun surface(
    tangent: Vector3,
    normal: Vector3,
  ): Surface =
    object : Surface {
      override val positions = listOf(Vector3.Zero, Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0))
      override val normal = normal
      override val tangent = tangent
      override val uvs = emptyList<TextureCoordinate>()
      override val triangles = listOf(0, 1, 2)
    }

  private fun frameAt(corner: Int): Quaternion = packed.tangentAt(corner)

  private fun GpuMesh.tangentAt(corner: Int): Quaternion =
    Quaternion(
      w = tangents[corner * GpuMesh.TANGENT_SIZE + 3].toDouble(),
      x = tangents[corner * GpuMesh.TANGENT_SIZE].toDouble(),
      y = tangents[corner * GpuMesh.TANGENT_SIZE + 1].toDouble(),
      z = tangents[corner * GpuMesh.TANGENT_SIZE + 2].toDouble(),
    )

  private companion object {
    const val TOLERANCE = 1e-7f

    /** Packed as floats, so compared as floats. */
    const val LOOSE = 1e-6

    const val RADIUS_MM = 13.9
  }
}
