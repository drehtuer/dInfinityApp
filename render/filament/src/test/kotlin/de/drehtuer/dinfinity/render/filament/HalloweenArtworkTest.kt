package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * `sample-sets/halloween`'s pumpkin atlas is what [HalloweenArtwork] draws,
 * and the set file reads the faces it was drawn for: a pumpkin is worth one,
 * a blank nought, and every pumpkin sits opposite a blank.
 *
 * To write the picture again:
 *
 * ```sh
 * DINFINITY_WRITE_EXAMPLES=1 ./gradlew :render:filament:testDebugUnitTest \
 *   --tests '*HalloweenArtworkTest*' --rerun
 * python3 sample-sets/build-archives.py
 * ```
 */
class HalloweenArtworkTest {
  @Test
  fun `the committed picture is what the generator draws`() {
    val file = File(folder(), PICTURE)
    assertTrue("${file.path} is missing — run with $WRITE=1", file.isFile)
    val committed = ImageIO.read(file)
    val drawn = HalloweenArtwork.atlas()
    assertEquals(drawn.width, committed.width)
    assertEquals(drawn.height, committed.height)
    drawn.pixels.indices.forEach { at ->
      val pixel = committed.getRGB(at % drawn.width, at / drawn.width)
      // ImageIO hands a clear pixel back as clear whatever colour it was saved
      // with, so a clear pixel is compared by its alpha alone.
      val same = if (drawn.pixels[at] == 0) pixel ushr ALPHA_SHIFT == 0 else pixel == drawn.pixels[at]
      assertTrue("the atlas is out of date at pixel $at — run with $WRITE=1", same)
    }
  }

  @Test
  fun `the pumpkin faces are drawn and the blank ones are left clear`() {
    val drawn = imageOf(HalloweenArtwork.atlas()).drawnCells(DieShape.Cube.faceCount)
    assertEquals(HalloweenArtwork.PUMPKIN_FACES.toSet(), drawn)
  }

  @Test
  fun `every pumpkin sits opposite a blank face`() {
    // The cube's faces come in opposite pairs, +x and −x first
    // (`simulation/api`'s ShapeGeometry): 0 and 1, 2 and 3, 4 and 5.
    HalloweenArtwork.PUMPKIN_FACES.forEach { face ->
      val opposite = face xor 1
      assertFalse("face $face's opposite is a pumpkin too", opposite in HalloweenArtwork.PUMPKIN_FACES)
    }
  }

  @Test
  fun `the ink stays on the square face, clear of its edges`() {
    // A cube's face covers the middle of its cell; ink past it would be drawn
    // on the rounded edge, or not at all.
    val atlas = HalloweenArtwork.atlas()
    val cell = HalloweenArtwork.CELL_PIXELS
    val margin = (cell * (1 - 1 / Math.sqrt(2.0)) / 2).toInt() + EDGE_PIXELS
    atlas.pixels.indices
      .filter { atlas.pixels[it] != 0 }
      .forEach { at ->
        val x = at % atlas.width % cell
        val y = at / atlas.width % cell
        assertTrue("ink at ($x, $y) of a cell is off the face", x in margin until cell - margin)
        assertTrue("ink at ($x, $y) of a cell is off the face", y in margin until cell - margin)
      }
  }

  @Test
  fun `each face has two eyes above a mouth, and nothing in its corners`() {
    listOf(HalloweenArtwork::grin, HalloweenArtwork::fangs, HalloweenArtwork::scared).forEach { face ->
      assertTrue("no left eye", inked(face, -0.8..-0.15, -0.8..-0.2))
      assertTrue("no right eye", inked(face, 0.15..0.8, -0.8..-0.2))
      assertTrue("no mouth", inked(face, -0.3..0.3, 0.2..0.8))
      assertFalse("ink in a corner", inked(face, -1.0..-0.9, -1.0..-0.9))
      assertFalse("ink in a corner", inked(face, 0.9..1.0, 0.9..1.0))
    }
  }

  @Test
  fun `the grin has a nose between its eyes and the snarl does not`() {
    assertTrue(HalloweenArtwork.grin(0.0, -0.1))
    assertFalse(inked(HalloweenArtwork::fangs, -0.1..0.1, -0.5..-0.2))
  }

  @Test
  fun `teeth are pointed, nought between two and one at a point`() {
    assertEquals(0.0, HalloweenArtwork.teeth(-1.0, halfWidth = 1.0, count = 4), 1e-12)
    assertEquals(1.0, HalloweenArtwork.teeth(-0.75, halfWidth = 1.0, count = 4), 1e-12)
    assertEquals(0.0, HalloweenArtwork.teeth(-0.5, halfWidth = 1.0, count = 4), 1e-12)
    assertEquals(0.5, HalloweenArtwork.teeth(-0.875, halfWidth = 1.0, count = 4), 1e-12)
  }

  @Test
  fun `a pixel wholly inside is full ink, and one wholly outside is nothing`() {
    val cell = HalloweenArtwork.CELL_PIXELS
    assertEquals(1.0, HalloweenArtwork.coverage({ _, _ -> true }, 0, 0), 0.0)
    assertEquals(0.0, HalloweenArtwork.coverage({ _, _ -> false }, cell / 2, cell / 2), 0.0)
    assertEquals(1.0, HalloweenArtwork.coverage({ u, _ -> u < 0.0 }, cell / 2 - 1, 0), 0.0)
    assertEquals(0.0, HalloweenArtwork.coverage({ _, v -> v < 0.0 }, 0, cell / 2), 0.0)
    // A boundary along a row of sample points splits the pixel exactly.
    val split = HalloweenArtwork.coverage({ _, v -> v < 0.0 }, 0, cell / 2 - 1)
    assertEquals(1.0, split, 0.0)
  }

  @Test
  fun `the set file reads a pumpkin as one and a blank as nothing, with the picture on the pumpkin die`() {
    val result = DiceSetValidator.validate(PackageFiles.of(folder()))
    assertTrue("sample-sets/halloween was rejected: $result", result is ValidationResult.Valid)
    val pumpkin = requireNotNull((result as ValidationResult.Valid).set.die("pumpkin"))
    assertEquals(DieShape.Cube, pumpkin.shape)
    assertEquals(PICTURE, pumpkin.texturePath)
    pumpkin.faces.forEach { face ->
      val isPumpkin = face.index in HalloweenArtwork.PUMPKIN_FACES
      assertEquals("face ${face.index}'s value", if (isPumpkin) 1 else 0, face.value)
      if (!isPumpkin) assertEquals("a blank face prints nothing", "", face.label)
    }
  }

  /** Whether [face] puts ink anywhere in a box of face units, tried on a fine grid. */
  private fun inked(
    face: (Double, Double) -> Boolean,
    across: ClosedFloatingPointRange<Double>,
    down: ClosedFloatingPointRange<Double>,
  ): Boolean =
    (0..GRID).any { i ->
      (0..GRID).any { j ->
        face(
          across.start + (across.endInclusive - across.start) * i / GRID,
          down.start + (down.endInclusive - down.start) * j / GRID,
        )
      }
    }

  private companion object {
    const val GRID = 40

    @JvmStatic
    @BeforeClass
    fun writeWhenAsked() {
      if (System.getenv(WRITE) != "1") return
      val atlas = HalloweenArtwork.atlas()
      val image = BufferedImage(atlas.width, atlas.height, BufferedImage.TYPE_INT_ARGB)
      image.setRGB(0, 0, atlas.width, atlas.height, atlas.pixels, 0, atlas.width)
      val file = File(folder(), PICTURE)
      file.parentFile?.mkdirs()
      check(ImageIO.write(image, "png", file)) { "no PNG writer" }
    }

    fun imageOf(atlas: MarbleArtwork.Atlas): AtlasImage {
      val bytes = ByteArray(atlas.pixels.size * AtlasImage.CHANNELS)
      atlas.pixels.forEachIndexed { at, argb ->
        bytes[at * 4] = (argb ushr 16).toByte()
        bytes[at * 4 + 1] = (argb ushr 8).toByte()
        bytes[at * 4 + 2] = argb.toByte()
        bytes[at * 4 + 3] = (argb ushr ALPHA_SHIFT).toByte()
      }
      return AtlasImage(atlas.width, atlas.height, bytes)
    }

    fun folder(): File = File(repositoryRoot(), "sample-sets/halloween")

    fun repositoryRoot(): File =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "sample-sets").isDirectory }
        ?: error("no repository root above ${File("").absolutePath}")

    const val WRITE = "DINFINITY_WRITE_EXAMPLES"
    const val PICTURE = "textures/pumpkin.png"
    const val ALPHA_SHIFT = 24

    /** The antialiased pixel at an ink's edge, and the rounded edge's start. */
    const val EDGE_PIXELS = 4
  }
}
