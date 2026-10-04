package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tables show the dice, how strongly, and from where
 * (`docs/physics-and-rendering.md`, "The dice in a glossy table").
 */
class ReflectionTest {
  @Test
  fun `only the glass of the bundled looks reflects the dice`() {
    val reflecting = StandardDice.tables.filter { Reflection.of(it) != null }.map { it.id }
    assertEquals(listOf("dark-glass"), reflecting)
  }

  @Test
  fun `dark glass shows two-thirds of a polished reflection`() {
    val glass = StandardDice.tables.first { it.id == "dark-glass" }
    assertEquals(2.0 / 3.0, Reflection.of(glass)!!.strength, TOLERANCE)
  }

  @Test
  fun `the strength falls in a straight line from a polish to the glossiest matte`() {
    assertEquals(1.0, Reflection.strengthOf(0.0), TOLERANCE)
    assertEquals(0.5, Reflection.strengthOf(Reflection.GLOSSIEST_MATTE / 2), TOLERANCE)
    assertEquals(0.0, Reflection.strengthOf(Reflection.GLOSSIEST_MATTE), TOLERANCE)
    assertEquals(0.0, Reflection.strengthOf(1.0), TOLERANCE)
    // Below nought is not a polish past perfect.
    assertEquals(1.0, Reflection.strengthOf(-1.0), TOLERANCE)
  }

  @Test
  fun `a look outside the limits is read as the look it is drawn as`() {
    // Below nought is drawn as a polish, and past one as matte.
    assertEquals(1.0, Reflection.of(look(roughness = -0.5))!!.strength, TOLERANCE)
    assertNull(Reflection.of(look(roughness = 5.0)))
    assertNull(Reflection.of(look(roughness = Reflection.GLOSSIEST_MATTE)))
    assertNotNull(Reflection.of(look(roughness = 0.0)))
  }

  @Test
  fun `the picture is a quarter of the screen across and up, rounded up`() {
    assertEquals(270 to 606, Reflection.sizeOf(1080, 2424))
    assertEquals(81 to 1, Reflection.sizeOf(321, 1))
    assertEquals(1 to 1, Reflection.sizeOf(0, 0))
  }

  @Test
  fun `the camera under the floor is the real one turned over in the floor`() {
    val shot = TrayCamera.framingTheTray(TableGeometry.referenceDevice(), ASPECT)
    val under = Reflection.mirrored(shot)

    assertEquals(shot.position.x, under.position.x, TOLERANCE)
    assertEquals(shot.position.y, under.position.y, TOLERANCE)
    assertEquals(-shot.position.z, under.position.z, TOLERANCE)
    assertEquals(-shot.target.z, under.target.z, TOLERANCE)
    assertEquals(shot.verticalFieldOfViewDegrees, under.verticalFieldOfViewDegrees, TOLERANCE)
    assertTrue("the camera under the floor looks up", under.forward.z > 0.0)
    // Still a camera: up is a unit square to where it looks.
    assertEquals(0.0, under.up.dot(under.forward), TOLERANCE)
    assertEquals(1.0, under.up.length, TOLERANCE)
  }

  @Test
  fun `a point on the floor is where it was, and is seen along the reflected ray`() {
    // What makes the picture line up with the floor: a floor point seen by the
    // real camera is seen by the one under the floor in the same place, and
    // the ray to any point above it from below is the real ray, reflected.
    val shot = TrayCamera.framingTheTray(TableGeometry.referenceDevice(), ASPECT, tiltDegrees = 22.0)
    val under = Reflection.mirrored(shot)
    val floor = Vector3(40.0, -10.0, Reflection.FLOOR_MM)
    val real = (floor - shot.position).normalised()
    val fromBelow = (floor - under.position).normalised()

    assertEquals(real.x, fromBelow.x, TOLERANCE)
    assertEquals(real.y, fromBelow.y, TOLERANCE)
    assertEquals(-real.z, fromBelow.z, TOLERANCE)
  }

  private fun look(roughness: Double): TableLook = TableLook(id = "t", name = "T", roughness = roughness)

  private companion object {
    const val TOLERANCE = 1e-9
    const val ASPECT = 9.0 / 20.0
  }
}
