package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.TableLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a surface is drawn *with*, as opposed to what shape it is.
 *
 * The shader is a question for a GPU. Which numbers go into it is arithmetic,
 * and one of those numbers is the one thing here that is easy to get wrong
 * without ever noticing: a colour handed to a renderer in the wrong space
 * makes everything washed out in the midtones in a way nobody can point at and
 * everybody sees.
 */
class DiceMaterialTest {
  @Test
  fun `black and white survive the trip into light`() {
    // The two ends of the curve are the two values that must not move.
    assertEquals(0.0, Colour.of(BLACK).red, TOLERANCE)
    assertEquals(1.0, Colour.of(WHITE).red, TOLERANCE)
    assertEquals(1.0, Colour.of(WHITE).blue, TOLERANCE)
  }

  @Test
  fun `mid grey is not half way, which is the whole point of converting`() {
    // sRGB's 50 % grey is about 21 % of the light. Handing a renderer 0.5
    // would make every midtone too bright, everywhere, for ever.
    val grey = Colour.of(0xFF808080.toInt())

    assertEquals(0.2158, grey.red, ROUGH)
    assertEquals(grey.red, grey.green, TOLERANCE)
    assertEquals(grey.red, grey.blue, TOLERANCE)
  }

  @Test
  fun `a very dark colour takes the straight part of the curve`() {
    // Below the knee sRGB is a line, not a power. A single step off black has
    // to come out of the line rather than out of the curve.
    val nearlyBlack = Colour.of(0xFF0A0A0A.toInt())

    assertEquals((10.0 / 255.0) / 12.92, nearlyBlack.red, TOLERANCE)
  }

  @Test
  fun `the channels do not get shuffled`() {
    val red = Colour.of(0xFFFF0000.toInt())

    assertEquals(1.0, red.red, TOLERANCE)
    assertEquals(0.0, red.green, TOLERANCE)
    assertEquals(0.0, red.blue, TOLERANCE)
    assertEquals(1.0, red.alpha, TOLERANCE)
  }

  @Test
  fun `alpha is coverage, not light, so it is not converted`() {
    // Half-transparent means half-transparent. Putting it through the curve
    // would make everything translucent more solid than it was asked to be.
    assertEquals(0.5019, Colour.of(0x80FFFFFF.toInt()).alpha, ROUGH)
  }

  @Test
  fun `the floor and the walls take their own halves of the table look`() {
    val look =
      TableLook(
        id = "oak",
        name = "Oak",
        floorColorArgb = 0xFF1F5E3A.toInt(),
        wallColorArgb = 0xFF5A3A1E.toInt(),
        floorTexturePath = "tables/felt.png",
        roughness = 0.7,
        metallic = 0.1,
      )

    val floor = DiceMaterial.floorOf(look)
    val wall = DiceMaterial.wallOf(look)

    assertEquals(Colour.of(look.floorColorArgb), floor.colour)
    assertEquals(Colour.of(look.wallColorArgb), wall.colour)
    assertEquals(look.roughness, floor.roughness, TOLERANCE)
    assertEquals(look.metallic, wall.metallic, TOLERANCE)
    assertTrue("a floor with a texture should sample it", floor.textured)
    assertFalse("a wall with no texture of its own takes its colour", wall.textured)
  }

  @Test
  fun `a die with no atlas is drawn in its own colour`() {
    val plain = DiceMaterial.dieOf(DieMaterial(), texturePath = null)
    val painted = DiceMaterial.dieOf(DieMaterial(), texturePath = "textures/d20.png")

    assertFalse(plain.textured)
    assertTrue(painted.textured)
    assertEquals("the same die, drawn two ways", plain.colour, painted.colour)
  }

  @Test
  fun `a die with no atlas prints its labels, in its own ink`() {
    val ink = DieMaterial(numberColorArgb = 0xFF102030.toInt())
    val field = NumberField(width = 2, height = 2, pixels = ByteArray(4))
    val printed = DiceMaterial.dieOf(ink, texturePath = null, numbers = field)

    assertTrue(printed.numbered)
    assertEquals(Colour.of(0xFF102030.toInt()), printed.ink)
    assertFalse("a die given no field prints nothing", DiceMaterial.dieOf(ink, texturePath = null).numbered)
  }

  @Test
  fun `the material says what it does with the numbers it is given`() {
    // Not a test of the shader — that needs a GPU — but of the promise that
    // every parameter this file computes is one the source actually reads.
    listOf(
      "baseColor",
      "roughness",
      "metallic",
      "textured",
      "atlas",
      "numbered",
      "inkColor",
      "glyphs",
      "opacity",
      "clearCoat",
      "clearCoatRoughness",
    ).forEach {
      assertTrue("the material never reads $it", DiceMaterial.SOURCE.contains(it))
    }
  }

  @Test
  fun `a solid die is drawn, and a translucent one is blended`() {
    val solid = DiceMaterial.dieOf(DieMaterial(), texturePath = null)
    assertEquals(1.0, solid.opacity, TOLERANCE)
    assertFalse("a solid die needs no blending", solid.blended)

    val glass = DiceMaterial.dieOf(DieMaterial(translucency = 0.35), texturePath = null)
    assertEquals(0.65, glass.opacity, TOLERANCE)
    assertTrue("a die you can see into has to be blended", glass.blended)
  }

  @Test
  fun `a die is lacquered and a table is not`() {
    assertEquals(DiceMaterial.DIE_COAT, DiceMaterial.dieOf(DieMaterial(), texturePath = null).clearCoat, TOLERANCE)
    assertEquals(0.0, DiceMaterial.floorOf(PLAIN).clearCoat, TOLERANCE)
    assertEquals(0.0, DiceMaterial.wallOf(PLAIN).clearCoat, TOLERANCE)
  }

  @Test
  fun `the tray is never blended, however a die is drawn`() {
    assertFalse(DiceMaterial.floorOf(PLAIN).blended)
    assertFalse(DiceMaterial.wallOf(PLAIN).blended)
  }

  @Test
  fun `what is printed on a die stays opaque, and the shader is where that happens`() {
    // The mix is `opacity` where the face is bare and one where it is printed
    // on, which is the line that keeps a numeral readable on a clear die. It
    // cannot be asserted without a GPU; that it is *there* can be.
    assertTrue(DiceMaterial.SOURCE.contains("mix(materialParams.opacity, 1.0, printed)"))
  }

  private companion object {
    /** Any table at all: nothing below asks it for anything but its surfaces. */
    val PLAIN =
      TableLook(
        id = "plain",
        name = "Plain",
        floorColorArgb = 0xFF1F5E3A.toInt(),
        wallColorArgb = 0xFF5A3A1E.toInt(),
      )

    const val TOLERANCE = 1e-9
    const val ROUGH = 1e-4
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
  }
}
