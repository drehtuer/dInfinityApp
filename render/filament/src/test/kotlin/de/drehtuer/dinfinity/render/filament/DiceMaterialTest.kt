package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.TableColorMode
import de.drehtuer.dinfinity.core.model.TableLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        packageId = "brass",
      )

    val floor = DiceMaterial.floorOf(look)
    val wall = DiceMaterial.wallOf(look)

    assertEquals(Colour.of(look.floorColorArgb), floor.colour)
    assertEquals(Colour.of(look.wallColorArgb), wall.colour)
    assertEquals(look.roughness, floor.roughness, TOLERANCE)
    assertEquals(look.metallic, wall.metallic, TOLERANCE)
    assertEquals("brass::tables/felt.png", floor.maps?.albedo)
    assertNull("a wall with no picture of its own takes its colour", wall.maps)
    // A table's pictures are its maps; the die's atlas slot stays empty.
    assertFalse(floor.textured)
  }

  @Test
  fun `a table's pictures are keyed by the package it came from, every map of them`() {
    val felt = DiceMaterial.floorOf(FELT)
    val oak = DiceMaterial.wallOf(FELT)

    assertEquals(
      DiceMaterial.SurfaceMaps(
        albedo = "builtin::tables/felt-albedo.webp",
        normal = "builtin::tables/felt-normal.webp",
        roughness = "builtin::tables/felt-roughness.webp",
      ),
      felt.maps,
    )
    assertEquals("builtin::tables/oak-albedo.webp", oak.maps?.albedo)
    assertNull("no wall roughness map was named", oak.maps?.roughness)
  }

  @Test
  fun `a look that came from no package has no pictures to draw`() {
    // A path on its own names no file: two packages may both ship it.
    val adrift = FELT.copy(packageId = null)

    assertNull(DiceMaterial.floorOf(adrift).maps)
    assertNull(DiceMaterial.wallOf(adrift).maps)
    assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(DiceMaterial.floorOf(adrift)))
  }

  @Test
  fun `one map on its own is enough to draw a surface from pictures`() {
    val bumps = DiceMaterial.SurfaceMaps.of("brass", albedo = null, normal = "n.png", roughness = null)
    val sheen = DiceMaterial.SurfaceMaps.of("brass", albedo = null, normal = null, roughness = "r.png")

    assertEquals(DiceMaterial.SurfaceMaps(albedo = null, normal = "brass::n.png", roughness = null), bumps)
    assertEquals("brass::r.png", sheen?.roughness)
    assertEquals("brass::n.png", bumps?.normal)
    assertNull(DiceMaterial.SurfaceMaps.of("brass", albedo = null, normal = null, roughness = null))
  }

  @Test
  fun `the colour travels to the surface as the look wrote it, with how it meets the picture`() {
    // The shader multiplies the picture by the colour it is handed; a look in
    // `color_mode = "average"` has that colour scaled by the picture's mean
    // first, which is the stage's to do once the picture is in (`TableTint`).
    val floor = DiceMaterial.floorOf(FELT)
    val photo = DiceMaterial.floorOf(FELT.copy(colorMode = TableColorMode.Multiply))

    assertEquals(Colour.of(FELT.floorColorArgb), floor.colour)
    assertTrue(floor.averaged)
    assertTrue(DiceMaterial.wallOf(FELT).averaged)
    assertFalse(photo.averaged)
    assertFalse("a die has no picture to average", DiceMaterial.dieOf(DieMaterial(), texturePath = null).averaged)
    assertTrue(DiceMaterial.TABLE_SOURCE.contains("materialParams.baseColor.rgb * picture"))
  }

  @Test
  fun `a textured table is drawn with the table material, and a die never is`() {
    assertEquals(DiceMaterial.Variant.TABLE, DiceMaterial.variantOf(DiceMaterial.floorOf(FELT)))
    assertEquals(DiceMaterial.Variant.TABLE, DiceMaterial.variantOf(DiceMaterial.wallOf(FELT)))
    val glass = DiceMaterial.dieOf(DieMaterial(translucency = 0.5), texturePath = "brass::d6.png")
    assertEquals(DiceMaterial.Variant.RESIN, DiceMaterial.variantOf(glass))
    assertNull(glass.maps)
  }

  @Test
  fun `the table material reads its normal map the way OpenGL writes one`() {
    // Green is up the picture and `v` grows down it, so green is turned over.
    assertTrue(DiceMaterial.TABLE_SOURCE.contains("bump.y = -bump.y;"))
    assertTrue(DiceMaterial.TABLE_SOURCE.contains("texture(materialParams_roughnessMap, uv).r"))
    // And the normal is set before the material is prepared, which is the
    // only place Filament reads it from.
    assertTrue(
      DiceMaterial.TABLE_SOURCE.indexOf("material.normal") < DiceMaterial.TABLE_SOURCE.indexOf("prepareMaterial"),
    )
  }

  @Test
  fun `the table material moves its roughness map by the look's shift and keeps it in range`() {
    // `TableTint.roughnessShift` is what makes a map average out at the
    // look's roughness; a source that read the map bare would draw oak as
    // glossy as its photograph again.
    assertTrue(
      DiceMaterial.TABLE_SOURCE.contains(
        "clamp(texture(materialParams_roughnessMap, uv).r + materialParams.roughnessShift, 0.0, 1.0)",
      ),
    )
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
      "clearCoat",
      "clearCoatRoughness",
      "glow",
    ).forEach {
      val source = DiceMaterial.SOURCE
      assertTrue("the material never reads $it", "materialParams.$it" in source || "materialParams_$it" in source)
      assertTrue("the resin never reads $it", DiceMaterial.RESIN_SOURCE.contains(it))
    }
    listOf("transmission", "scatter", "ior", "thickness", "tint").forEach {
      assertTrue("the resin never reads $it", DiceMaterial.RESIN_SOURCE.contains("materialParams.$it"))
      // The opaque material declares none of these, and Filament refuses a
      // parameter a material does not declare.
      assertFalse("the opaque material reads $it", DiceMaterial.SOURCE.contains("materialParams.$it"))
    }
  }

  @Test
  fun `the opaque material writes a whole pixel and nothing about light passing through`() {
    // Every opaque surface wrote a coverage of one before resin existed; this
    // is that same picture with the multiply by one gone.
    assertTrue(DiceMaterial.SOURCE.contains("material.baseColor = vec4(colour, 1.0);"))
    assertFalse(DiceMaterial.SOURCE.contains("material.transmission"))
  }

  @Test
  fun `a die's artwork is laid over its body in its own colour, not tinted by it`() {
    // `base.rgb * art.rgb` drew yellow pips on a black die black: the face
    // designer's ink never reached the tray. Every die material shares the
    // surface, so each must composite the artwork as it was drawn. Whether
    // the pixels agree is `AtlasTextureTest`'s, on a device.
    listOf(DiceMaterial.SOURCE, DiceMaterial.RESIN_SOURCE).forEach { source ->
      assertTrue(source.contains("colour = mix(body, art.rgb, art.a);"))
      assertFalse("the artwork is tinted by the body", source.contains("* art.rgb"))
    }
  }

  @Test
  fun `a solid die is opaque, and a translucent one is resin`() {
    val solid = DiceMaterial.dieOf(DieMaterial(), texturePath = null)
    assertNull("a solid die has no resin", solid.resin)
    assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(solid))

    val glass = DiceMaterial.dieOf(DieMaterial(translucency = 0.35), texturePath = null)
    assertEquals(Resin.transmissionOf(0.35), glass.resin!!.transmission, TOLERANCE)
    assertEquals(DiceMaterial.Variant.RESIN, DiceMaterial.variantOf(glass))
  }

  @Test
  fun `each variant compiles its own source under its own name`() {
    assertEquals(DiceMaterial.SOURCE, DiceMaterial.Variant.OPAQUE.source)
    assertEquals(DiceMaterial.RESIN_SOURCE, DiceMaterial.Variant.RESIN.source)
    assertEquals(DiceMaterial.TABLE_SOURCE, DiceMaterial.Variant.TABLE.source)
    assertEquals(
      DiceMaterial.Variant.entries.size,
      DiceMaterial.Variant.entries
        .map { it.key }
        .toSet()
        .size,
    )
    assertNotEquals(DiceMaterial.Variant.OPAQUE.key, DiceMaterial.Variant.RESIN.key)
  }

  @Test
  fun `a variant's cache key covers the builder settings that are not in its source`() {
    // A packet compiled before specular anti-aliasing was switched on must
    // miss the cache, not be read back without it.
    DiceMaterial.Variant.entries.forEach { variant ->
      assertTrue(variant.fingerprint.startsWith(variant.source))
      // Whatever the variant is compiled with is what its key says.
      assertEquals(
        variant.name,
        variant.specularAntiAliasing,
        variant.fingerprint.contains("${DiceMaterial.SPECULAR_AA_VARIANCE} ${DiceMaterial.SPECULAR_AA_THRESHOLD}"),
      )
      assertNotEquals(
        MaterialCache.keyOf(variant.source, backend = "OPENGL", variant = variant.key),
        MaterialCache.keyOf(variant.fingerprint, backend = "OPENGL", variant = variant.key),
      )
    }
  }

  @Test
  fun `every surface with a bend in it spreads its glint, and the picture-drawn tray does not`() {
    // The dice have rounded edges, and a floor drawn in its colour alone
    // shares their surface; the tray's mesh is flat where the pictures are, and Filament's filter
    // reads the mesh's normal, not the normal map's.
    assertTrue(DiceMaterial.Variant.OPAQUE.specularAntiAliasing)
    assertTrue(DiceMaterial.Variant.RESIN.specularAntiAliasing)
    assertFalse(DiceMaterial.Variant.TABLE.specularAntiAliasing)
    val table = DiceMaterial.Variant.TABLE.fingerprint
    assertTrue(table.endsWith("specularAntiAliasing off"))
  }

  @Test
  fun `a rounded edge's glint is spread by Filament's own default amount`() {
    assertEquals(0.15f, DiceMaterial.SPECULAR_AA_VARIANCE)
    assertEquals(0.2f, DiceMaterial.SPECULAR_AA_THRESHOLD)
  }

  @Test
  fun `resin is bent like acrylic and as thick as most of the die`() {
    val resin = Resin.of(DieMaterial(translucency = 0.6, sizeMm = 20.0), scale = 0.5)!!
    assertEquals(Resin.IOR, resin.ior, TOLERANCE)
    assertEquals(20.0 * 0.5 * Resin.THICKNESS_OF_SIZE, resin.thicknessMm, TOLERANCE)
  }

  @Test
  fun `a die scaled to nothing bends nothing, and asks for nothing impossible`() {
    // Thickness is only how far what is seen through a die is displaced, so
    // none at all is a die that tints and does not bend — not a division by
    // nought, which is what the absorption it replaced had to guard against.
    val resin = Resin.of(DieMaterial(translucency = 0.6), scale = 0.0)!!
    assertEquals(0.0, resin.thicknessMm, TOLERANCE)
  }

  @Test
  fun `translucency lets the felt through sooner than a straight line would`() {
    // The ends stay put: nought is solid and one is glass.
    assertEquals(0.0, Resin.transmissionOf(0.0), TOLERANCE)
    assertEquals(1.0, Resin.transmissionOf(1.0), TOLERANCE)
    // Between them the body keeps the square of what is not translucent.
    assertEquals(0.84, Resin.transmissionOf(0.6), TOLERANCE)
    assertEquals(0.36, Resin.transmissionOf(0.2), TOLERANCE)
    // And a value outside the scale is held to it rather than passed on.
    assertEquals(1.0, Resin.transmissionOf(3.0), TOLERANCE)
    assertEquals(0.0, Resin.transmissionOf(-1.0), TOLERANCE)
  }

  @Test
  fun `a milky die scatters and a glassy one does not`() {
    val milky = Resin.of(DieMaterial(translucency = 0.1, roughness = 0.0), scale = 1.0)!!
    val glassy = Resin.of(DieMaterial(translucency = 1.0, roughness = 0.0), scale = 1.0)!!
    assertEquals(Resin.MILKY_SCATTER * 0.9, milky.scatter, TOLERANCE)
    assertEquals(0.0, glassy.scatter, TOLERANCE)
  }

  @Test
  fun `a rough die is frosted however clear it is`() {
    val frosted = Resin.of(DieMaterial(translucency = 1.0, roughness = 0.8), scale = 1.0)!!
    assertEquals(0.8, frosted.scatter, TOLERANCE)
  }

  @Test
  fun `resin reads the die through its limits`() {
    // What reaches the renderer was clamped when the set was installed; a
    // value edited on disk since is clamped again rather than handed to a
    // shader.
    val wild = Resin.of(DieMaterial(translucency = 7.0, sizeMm = 1_000.0), scale = 1.0)!!
    assertEquals(1.0, wild.transmission, TOLERANCE)
    assertEquals(DieMaterial.SizeMmRange.endInclusive * Resin.THICKNESS_OF_SIZE, wild.thicknessMm, TOLERANCE)
    assertNull(Resin.of(DieMaterial(translucency = Double.NaN), scale = 1.0))
  }

  @Test
  fun `the resin tints what is seen through it by one pass of its colour`() {
    // A die's colour is what it looks like over a pale table, which is two
    // passes through it; what one pass leaves is the square root.
    val amber = Resin.tintOf(Colour(red = 1.0, green = 0.25, blue = 0.0, alpha = 0.4))
    assertEquals(Colour(red = 1.0, green = 0.5, blue = 0.0, alpha = 1.0), amber)
    // Lighter than the colour, never darker: green felt under amber is olive.
    val die = Colour.of(AMBER)
    val tint = Resin.tintOf(die)
    assertTrue(tint.green > die.green)
    assertTrue(tint.blue > die.blue)
    // And it is what reaches the renderer for a die of that colour.
    assertEquals(tint, Resin.of(DieMaterial(translucency = 1.0, colorArgb = AMBER), scale = 1.0)!!.tint)
    // A colour out of range is held to it rather than becoming NaN.
    assertEquals(0.0, Resin.tintOf(Colour(red = -0.5, green = 2.0, blue = 0.0, alpha = 1.0)).red, TOLERANCE)
    assertEquals(1.0, Resin.tintOf(Colour(red = -0.5, green = 2.0, blue = 0.0, alpha = 1.0)).green, TOLERANCE)
  }

  @Test
  fun `a die that does not glow gives off nothing, and neither does the tray`() {
    assertEquals(Glow.NONE, DiceMaterial.dieOf(DieMaterial(), texturePath = null).glow)
    assertEquals(Glow.NONE, DiceMaterial.floorOf(PLAIN).glow)
    assertEquals(Glow.NONE, DiceMaterial.wallOf(PLAIN).glow)
  }

  @Test
  fun `a glowing die gives off its glow colour, scaled by how much it glows`() {
    val green = 0xFF7CFFB0.toInt()
    val glow = DiceMaterial.dieOf(DieMaterial(glow = 0.5, glowColorArgb = green), texturePath = null).glow
    val linear = Colour.of(green)
    assertEquals(linear.red * 0.5 * Glow.FULL, glow.red, TOLERANCE)
    assertEquals(linear.green * 0.5 * Glow.FULL, glow.green, TOLERANCE)
    assertEquals(linear.blue * 0.5 * Glow.FULL, glow.blue, TOLERANCE)
  }

  @Test
  fun `a die with no glow colour glows in its body colour`() {
    val amber = 0xFFE0A030.toInt()
    val glow = Glow.of(DieMaterial(colorArgb = amber, glow = 1.0))
    assertEquals(Colour.of(amber).red * Glow.FULL, glow.red, TOLERANCE)
    assertEquals(Colour.of(amber).blue * Glow.FULL, glow.blue, TOLERANCE)
  }

  @Test
  fun `a glow is read through its limits`() {
    val white = 0xFFFFFFFF.toInt()
    assertEquals(Glow.FULL, Glow.of(DieMaterial(colorArgb = white, glow = 9.0)).green, TOLERANCE)
    assertEquals(Glow.NONE, Glow.of(DieMaterial(colorArgb = white, glow = Double.NaN)))
  }

  @Test
  fun `a glowing die asks for the halo, and a plain die or the tray does not`() {
    assertTrue(Glow.glows(DiceMaterial.dieOf(DieMaterial(glow = 0.7), texturePath = null)))
    assertFalse(Glow.glows(DiceMaterial.dieOf(DieMaterial(), texturePath = null)))
    assertFalse(Glow.glows(DiceMaterial.floorOf(PLAIN)))
    assertFalse(Glow.glows(DiceMaterial.wallOf(PLAIN)))
  }

  @Test
  fun `the halo's blurs add up to the glow, so its strength alone says how bright it is`() {
    assertEquals(1.0, Glow.HALO_WEIGHTS.sum(), TOLERANCE)
    assertTrue(Glow.HALO_WEIGHTS.all { it > 0.0 })
    assertTrue(Glow.HALO_STRENGTH > 0.0)
  }

  @Test
  fun `the glow mask writes a surface's glow and nothing else, and nothing printed glows`() {
    val mask = Glow.MASK_SOURCE
    assertTrue(mask.contains("material.baseColor = vec4(materialParams.glow * (1.0 - printed), 1.0);"))
    listOf("glyphs", "atlas").forEach { assertTrue(it, mask.contains("materialParams_$it")) }
    listOf("numbered", "textured").forEach { assertTrue(it, mask.contains("materialParams.$it")) }
    // No lighting, no lacquer: a mask, not a surface.
    assertFalse(mask.contains("clearCoat"))
    assertFalse(mask.contains("emissive"))
    assertEquals(Glow.MASK_SOURCE, DiceMaterial.Variant.GLOW.source)
  }

  @Test
  fun `the tone pass lays every blur of the mask over the light, before it is bent`() {
    val tone = ToneCurve.SOURCE
    Glow.HALO_WEIGHTS.forEachIndexed { level, weight ->
      assertTrue(
        "level ${level + 1}",
        tone.contains("textureLod(materialParams_glow, uv, ${level + 1}.0).rgb * $weight"),
      )
    }
    assertFalse("the full-size mask is the die, not its halo", tone.contains("materialParams_glow, uv, 0.0"))
    assertTrue(tone.indexOf("* materialParams.halo") < tone.indexOf("toneShown(light)"))
  }

  @Test
  fun `the glow mask has a level for every blur, and no more than halving makes`() {
    assertEquals(Glow.HALO_WEIGHTS.size + 1, GlowPass.levelsFor(540, 1212))
    assertEquals(1, GlowPass.levelsFor(1, 1))
    assertEquals(3, GlowPass.levelsFor(4, 2))
  }

  @Test
  fun `a glow does not choose the material, so a solid glowing die stays opaque`() {
    val solid = DiceMaterial.dieOf(DieMaterial(glow = 1.0), texturePath = null)
    assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(solid))
    val resin = DiceMaterial.dieOf(DieMaterial(glow = 1.0, translucency = 0.3), texturePath = null)
    assertEquals(DiceMaterial.Variant.RESIN, DiceMaterial.variantOf(resin))
  }

  @Test
  fun `both die materials glow, in the screen's units, and nothing printed glows`() {
    listOf(DiceMaterial.SOURCE, DiceMaterial.RESIN_SOURCE).forEach { source ->
      assertTrue(source.contains("material.emissive = vec4(materialParams.glow * (1.0 - printed), 0.0);"))
    }
    assertFalse(DiceMaterial.TABLE_SOURCE.contains("emissive"))
  }

  @Test
  fun `a die is lacquered and a table is not`() {
    assertEquals(DiceMaterial.DIE_COAT, DiceMaterial.dieOf(DieMaterial(), texturePath = null).clearCoat, TOLERANCE)
    assertEquals(0.0, DiceMaterial.floorOf(PLAIN).clearCoat, TOLERANCE)
    assertEquals(0.0, DiceMaterial.wallOf(PLAIN).clearCoat, TOLERANCE)
  }

  @Test
  fun `a floor in its colour alone has the lamps' sheen taken out of it, and one drawn from pictures does not`() {
    // The built-in plain table's grey, brighter than the sheen in every channel.
    val grey = PLAIN.copy(floorColorArgb = 0xFF6E6E6E.toInt(), roughness = 0.8)
    val light = SurfaceLight.facingUp(grey.roughness)
    val floor = DiceMaterial.floorOf(grey).colour
    assertEquals(light.baseFor(Colour.of(grey.floorColorArgb)), floor)
    // What it is handed draws as what the look wrote.
    val drawn = light.drawn(floor)
    val written = Colour.of(grey.floorColorArgb)
    assertEquals(written.red, drawn.red, TOLERANCE)
    assertEquals(written.blue, drawn.blue, TOLERANCE)
    assertEquals(Colour.of(FELT.floorColorArgb), DiceMaterial.floorOf(FELT).colour)
  }

  @Test
  fun `a metal die is not lacquered, and a die partly metal is lacquered in proportion`() {
    assertEquals(0.0, DiceMaterial.dieOf(DieMaterial(metallic = 1.0), texturePath = null).clearCoat, TOLERANCE)
    assertEquals(DiceMaterial.DIE_COAT * 0.75, DiceMaterial.coatOver(0.25), TOLERANCE)
    // A metalness out of range is held to it rather than giving a negative coat or more than a full one.
    assertEquals(0.0, DiceMaterial.coatOver(2.0), TOLERANCE)
    assertEquals(DiceMaterial.DIE_COAT, DiceMaterial.coatOver(-1.0), TOLERANCE)
  }

  @Test
  fun `every surface sets its coat, so none is left with Filament's mirror coat`() {
    // A coated material starts each pixel with clearCoat 1 at roughness 0; a
    // surface that skipped setting it was drawn under a mirror (decision 106).
    listOf(DiceMaterial.SOURCE, DiceMaterial.RESIN_SOURCE).forEach { source ->
      assertTrue(source.contains("material.clearCoat = materialParams.clearCoat;"))
      assertFalse(source.contains("if (materialParams.clearCoat"))
    }
  }

  @Test
  fun `the tray is never resin, however a die is drawn`() {
    assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(DiceMaterial.floorOf(PLAIN)))
    assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(DiceMaterial.wallOf(PLAIN)))
  }

  @Test
  fun `a glossy floor in its colour alone is the opaque surface, however smooth`() {
    // Dark glass, and any look as smooth: the floor is the plain surface at
    // the look's roughness, walls and rim alike (`docs/architecture.md`,
    // decision 104, which withdrew the reflection of the dice in it).
    listOf(0.9, 0.75, 0.1, 0.0).forEach { roughness ->
      val glossy = PLAIN.copy(roughness = roughness, metallic = 0.1)
      val floor = DiceMaterial.floorOf(glossy)
      assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(floor))
      assertEquals(roughness, floor.roughness, TOLERANCE)
      assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(DiceMaterial.wallOf(glossy)))
    }
  }

  @Test
  fun `a glossy floor drawn from pictures is drawn from them`() {
    val polished = DiceMaterial.floorOf(FELT.copy(roughness = 0.1))
    assertEquals("builtin::tables/felt-albedo.webp", polished.maps?.albedo)
    assertEquals(DiceMaterial.Variant.TABLE, DiceMaterial.variantOf(polished))
    // A glossy look that came from no package has no pictures to draw.
    val adrift = FELT.copy(roughness = 0.1, packageId = null)
    assertEquals(DiceMaterial.Variant.OPAQUE, DiceMaterial.variantOf(DiceMaterial.floorOf(adrift)))
  }

  @Test
  fun `what is printed on a die stays opaque, and the shader is where that happens`() {
    // Light passes only through the *bare* body: where ink or artwork is, the
    // transmission is nought and the roughness the author's, which is the line
    // that keeps a numeral readable on a clear die. It cannot be asserted
    // without a GPU; that it is *there* can be.
    assertTrue(DiceMaterial.RESIN_SOURCE.contains("float bare = 1.0 - printed;"))
    assertTrue(DiceMaterial.RESIN_SOURCE.contains("float through = materialParams.transmission * bare;"))
    assertTrue(DiceMaterial.RESIN_SOURCE.contains("material.transmission = through;"))
    // And the tint is the base colour's, moved towards one pass through the
    // resin only where light passes: Filament multiplies what it sees through
    // a surface by the base colour itself, so nothing else may tint it again.
    assertTrue(
      DiceMaterial.RESIN_SOURCE.contains("material.baseColor.rgb = mix(colour, materialParams.tint, through);"),
    )
    assertFalse(DiceMaterial.RESIN_SOURCE.contains("material.absorption"))
    assertTrue(
      DiceMaterial.RESIN_SOURCE.contains("mix(materialParams.roughness, materialParams.scatter, bare)"),
    )
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

    /** The bundled green felt, as the validator hands it over. */
    val FELT =
      TableLook(
        id = "felt-green",
        name = "Green felt",
        floorTexturePath = "tables/felt-albedo.webp",
        floorNormalPath = "tables/felt-normal.webp",
        floorRoughnessPath = "tables/felt-roughness.webp",
        floorTileMm = 80.0,
        wallTexturePath = "tables/oak-albedo.webp",
        wallTileMm = 300.0,
        floorColorArgb = 0xFF1F5E3A.toInt(),
        colorMode = TableColorMode.Average,
        packageId = "builtin",
      )

    const val TOLERANCE = 1e-9
    const val ROUGH = 1e-4
    const val BLACK = 0xFF000000.toInt()
    const val AMBER = 0xFFD9822B.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
  }
}
