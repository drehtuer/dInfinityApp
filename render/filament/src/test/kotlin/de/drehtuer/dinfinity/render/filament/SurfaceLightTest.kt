package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * What the lamps add to a table's colour, and the colour that is handed to the
 * material so a felt draws as the colour its look names (`docs/tables.md`,
 * "What the floors draw as").
 */
class SurfaceLightTest {
  @Test
  fun `a mirror gives back everything a white room sends it, and a rough lobe loses some between its facets`() {
    // The directional albedo of a white GGX reflector looked at straight on:
    // all of it for a mirror, less and less as the lobe widens, which is
    // what Filament's energy compensation puts back.
    val mirror = SurfaceLight.albedoLookingDown(alpha = SurfaceLight.LEAST_ROUGHNESS * SurfaceLight.LEAST_ROUGHNESS)
    val felt = SurfaceLight.albedoLookingDown(alpha = 0.81)
    val oiled = SurfaceLight.albedoLookingDown(alpha = 0.5625)

    assertEquals(1.0, mirror.white, 1e-3)
    assertEquals(0.42, felt.white, 0.01)
    assertEquals(0.63, oiled.white, 0.01)
    assertTrue("a rougher lobe gives back more", oiled.white > felt.white)
  }

  @Test
  fun `looking straight on, Fresnel adds next to nothing`() {
    // Schlick's term grows with the angle between the view and the facet, and
    // straight down every facet that reflects anything is within 45 degrees.
    val felt = SurfaceLight.albedoLookingDown(alpha = 0.81)

    assertTrue("grazing ${felt.grazing}", felt.grazing in 0.0..1e-3)
  }

  @Test
  fun `a green felt's sheen is a little over one per cent of white, and grey`() {
    // The Pixel 10a drew the textured felt at (44, 94, 65) for #1f5e3a: its
    // colour at about 0.9 of the white level, plus 0.014 of white in every
    // channel. This is that 0.014, from the lamps and the material alone.
    val light = SurfaceLight.facingUp(roughness = 0.9)

    listOf(light.sheen.red, light.sheen.green, light.sheen.blue).forEach {
      assertEquals(0.0142, it, 0.0015)
    }
    assertTrue("the sheen is not grey: ${light.sheen}", abs(light.sheen.red - light.sheen.blue) < 0.001)
  }

  @Test
  fun `a surface shows its colour at the white level, less what its reflection took`() {
    val light = SurfaceLight.facingUp(roughness = 0.9)

    assertTrue("diffuse ${light.diffuse}", light.diffuse < TrayLighting.WHITE_LEVEL)
    assertEquals(TrayLighting.WHITE_LEVEL, light.diffuse, 0.02)
  }

  @Test
  fun `the glossier the surface, the more of the key it sends up`() {
    val felt = SurfaceLight.facingUp(roughness = 0.9)
    val oak = SurfaceLight.facingUp(roughness = 0.75)

    assertTrue("oak ${oak.sheen.green}, felt ${felt.sheen.green}", oak.sheen.green > felt.sheen.green)
    assertTrue(SurfaceLight.keyLookingDown(0.5625) > SurfaceLight.keyLookingDown(0.81))
  }

  @Test
  fun `a look smoother than a phone draws is drawn as smooth as it can be`() {
    assertEquals(SurfaceLight.facingUp(SurfaceLight.LEAST_ROUGHNESS), SurfaceLight.facingUp(0.0))
  }

  @Test
  fun `the key's reflection is GGX's, term by term`() {
    // A lamp straight overhead, seen straight down: the half vector is the
    // normal, and every term has its textbook value there.
    val alpha = 0.81
    assertEquals(1.0 / (Math.PI * alpha * alpha), SurfaceLight.distribution(1.0, alpha), 1e-12)
    assertEquals(0.25, SurfaceLight.correlatedVisibility(1.0, alpha), 1e-12)
    assertEquals(0.25, SurfaceLight.fastVisibility(1.0, alpha), 1e-12)
  }

  @Test
  fun `a colour every channel of which is above the sheen draws as itself`() {
    val light = SurfaceLight.facingUp(roughness = 0.9)
    val green = Colour.of(0xFF2F6E4A.toInt())

    val drawn = light.drawn(light.baseFor(green))

    assertEquals(green.red, drawn.red, 1e-12)
    assertEquals(green.green, drawn.green, 1e-12)
    assertEquals(green.blue, drawn.blue, 1e-12)
    assertEquals(green.alpha, drawn.alpha, 1e-12)
  }

  @Test
  fun `the bundled green felt draws as its colour, its red all sheen`() {
    // #1f's red is 0.0137 in light, a hair under the felt's sheen: no colour
    // makes it, so the felt is lifted by that hair and its red is the sheen.
    val light = SurfaceLight.facingUp(roughness = 0.9)
    val felt = Colour.of(0xFF1F5E3A.toInt())

    val base = light.baseFor(felt)
    val drawn = light.drawn(base)

    assertEquals(0.0, base.red, 1e-12)
    listOf(felt.red to drawn.red, felt.green to drawn.green, felt.blue to drawn.blue).forEach { (wrote, drew) ->
      assertEquals(levelOf(wrote), levelOf(drew), 2.0)
    }
  }

  @Test
  fun `a colour darker than the sheen is lifted by grey, and keeps its hue`() {
    // Oak's walls: a dark brown whose blue no oiled surface can be drawn as.
    // Clamping each channel would keep red and green and lose the blue:
    // maroon. Lifting all three by the same grey keeps brown brown.
    val light = SurfaceLight.facingUp(roughness = 0.75)
    val brown = Colour.of(0xFF3F2913.toInt())

    val base = light.baseFor(brown)
    val drawn = light.drawn(base)

    assertEquals(0.0, minOf(base.red, base.green, base.blue), 1e-12)
    assertEquals(brown.red - brown.green, drawn.red - drawn.green, 1e-12)
    assertEquals(brown.green - brown.blue, drawn.green - drawn.blue, 1e-12)
  }

  @Test
  fun `black felt draws as the grey of its sheen, the darkest a felt can be`() {
    val light = SurfaceLight.facingUp(roughness = 0.9)

    val base = light.baseFor(Colour.of(0xFF1A1A1A.toInt()))
    val drawn = light.drawn(base)
    val brightest = maxOf(light.sheen.red, light.sheen.green, light.sheen.blue)

    // The room is not quite grey, so the felt is the grey of its brightest
    // channel, and is as dark there as it can be.
    assertEquals(0.0, minOf(base.red, base.green, base.blue), 1e-12)
    listOf(drawn.red, drawn.green, drawn.blue).forEach { assertEquals(brightest, it, 1e-12) }
  }

  @Test
  fun `without lamps a colour is handed on as it is`() {
    val felt = Colour.of(0xFF1F5E3A.toInt())

    assertEquals(felt, SurfaceLight.MATTE.baseFor(felt))
    assertEquals(felt, SurfaceLight.MATTE.drawn(felt))
  }

  /** [linear] in levels of 255 once encoded as sRGB. */
  private fun levelOf(linear: Double): Double =
    255 * if (linear <= 0.0031308) linear * 12.92 else 1.055 * linear.pow(1 / 2.4) - 0.055
}
