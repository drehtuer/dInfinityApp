package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.glyphs.SignedDistanceField
import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * `sample-sets/marble`'s pictures are what [MarbleArtwork] draws, and the
 * set file asks for the dice they were drawn for.
 *
 * The PNGs are committed so that the package can be downloaded and installed
 * as it stands (`sample-sets/README.md`); this is what stops them drifting
 * from the generator, or from the printing their numbers are copied from. To
 * write them again:
 *
 * ```sh
 * DINFINITY_WRITE_EXAMPLES=1 ./gradlew :render:filament:testDebugUnitTest \
 *   --tests '*MarbleArtworkTest*' --rerun
 * python3 sample-sets/build-archives.py
 * ```
 */
class MarbleArtworkTest {
  private val folder = File(repositoryRoot(), "sample-sets/marble")

  @Test
  fun `the committed pictures are what the generator draws`() {
    DICE.forEach { die ->
      val file = File(folder, requireNotNull(die.texturePath))
      assertTrue("${file.path} is missing — run with $WRITE=1", file.isFile)
      val committed = ImageIO.read(file)
      val drawn = MarbleArtwork.atlasOf(die)
      assertEquals("${die.id}'s atlas width", drawn.width, committed.width)
      assertEquals("${die.id}'s atlas height", drawn.height, committed.height)
      val worst =
        drawn.pixels.indices.maxOf { at ->
          channelGap(drawn.pixels[at], committed.getRGB(at % drawn.width, at / drawn.width))
        }
      // A channel or two of slack: the sines are the JVM's, and a last-place
      // difference between two of them is not a different picture.
      assertTrue("${die.id}'s atlas is out of date (a channel is $worst away) — run with $WRITE=1", worst <= SLACK)
    }
  }

  @Test
  fun `the set file asks for exactly the dice the pictures were drawn for`() {
    val result = DiceSetValidator.validate(PackageFiles.of(folder))
    assertTrue("sample-sets/marble was rejected: $result", result is ValidationResult.Valid)
    val set = (result as ValidationResult.Valid).set
    assertEquals(DICE.map(::shapeOf), set.dice.map(::shapeOf))
  }

  @Test
  fun `every face is artwork, so the app prints nothing over the marble`() {
    DICE.forEach { die ->
      val atlas = MarbleArtwork.atlasOf(die)
      val drawn = imageOf(atlas).drawnCells(die.shape.faceCount)
      assertEquals("${die.id} has a face the app would print on", (0 until die.shape.faceCount).toSet(), drawn)
    }
  }

  @Test
  fun `the numbers are cut where the app would print them`() {
    // Inside every printed glyph the picture is the ink, so a face's number is
    // there to read — and it is the app's own layout, a d4's corners included.
    DICE.forEach { die ->
      val field = requireNotNull(DieNumbers.fieldOf(die, cellPixels = MarbleArtwork.CELL_PIXELS))
      val atlas = MarbleArtwork.atlasOf(die)
      val solid = field.pixels.indices.filter { (field.pixels[it].toInt() and BYTE) >= DEEP_INSIDE }
      assertTrue("${die.id} has no number in it", solid.isNotEmpty())
      solid.forEach { at -> assertEquals("${die.id} at pixel $at", INK, atlas.pixels[at] and RGB) }
    }
  }

  @Test
  fun `a spare cell of the grid stays clear`() {
    // A d10 is ten faces in a 4x3 grid: the two cells left over are nobody's.
    val d10 = DICE.first { it.id == "d10" }
    val atlas = MarbleArtwork.atlasOf(d10)
    val grid = ShapeAtlas.gridFor(d10.shape)
    val corner = (grid.rows * MarbleArtwork.CELL_PIXELS - 1) * atlas.width + atlas.width - 1
    assertEquals(0, atlas.pixels[corner])
  }

  @Test
  fun `a pixel is ink inside the edge and stone outside it`() {
    assertEquals(1.0, MarbleArtwork.inkOf(255), 0.0)
    assertEquals(0.0, MarbleArtwork.inkOf(0), 0.0)
    assertEquals(0.5, MarbleArtwork.inkOf(SignedDistanceField.EDGE), 1e-9)
  }

  @Test
  fun `the stone is veined rather than one colour`() {
    val shades = (0 until 64).map { MarbleArtwork.stoneAt(it / 16.0, it / 23.0, seed = 7) }.toSet()
    assertTrue("only ${shades.size} shades of stone", shades.size > 16)
  }

  private companion object {
    /**
     * Writes the pictures first when asked to, before any test reads them:
     * the validator checks the set file's textures are there.
     */
    @JvmStatic
    @BeforeClass
    fun writeWhenAsked() {
      if (System.getenv(WRITE) == "1") write()
    }

    fun write() {
      val folder = File(repositoryRoot(), "sample-sets/marble")
      DICE.forEach { die ->
        val atlas = MarbleArtwork.atlasOf(die)
        val image = BufferedImage(atlas.width, atlas.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, atlas.width, atlas.height, atlas.pixels, 0, atlas.width)
        val file = File(folder, requireNotNull(die.texturePath))
        file.parentFile?.mkdirs()
        check(ImageIO.write(image, "png", file)) { "no PNG writer" }
      }
    }

    private fun shapeOf(die: Die) = listOf(die.id, die.shape, die.read, die.faces, die.texturePath)

    private fun channelGap(
      a: Int,
      b: Int,
    ): Int = (0 until Int.SIZE_BITS step Byte.SIZE_BITS).maxOf { abs((a ushr it and BYTE) - (b ushr it and BYTE)) }

    private fun imageOf(atlas: MarbleArtwork.Atlas): AtlasImage {
      val bytes = ByteArray(atlas.pixels.size * AtlasImage.CHANNELS)
      atlas.pixels.forEachIndexed { at, argb ->
        bytes[at * 4] = (argb ushr 16).toByte()
        bytes[at * 4 + 1] = (argb ushr 8).toByte()
        bytes[at * 4 + 2] = argb.toByte()
        bytes[at * 4 + 3] = (argb ushr 24).toByte()
      }
      return AtlasImage(atlas.width, atlas.height, bytes)
    }

    const val WRITE = "DINFINITY_WRITE_EXAMPLES"
    const val SLACK = 2
    const val BYTE = 0xFF
    const val RGB = 0xFFFFFF
    const val INK = 0x2E2C2A

    /** Inside a glyph by more than the antialiased pixel at its edge. */
    const val DEEP_INSIDE = 144

    /** The dice of `sample-sets/marble/diceset.toml`, as the validator reads them. */
    val DICE: List<Die> =
      listOf(
        die("d2", DieShape.Coin, "coin", (1..2).toList()),
        die("d4", DieShape.Tetrahedron, "tetrahedron", (1..4).toList()),
        die("d6", DieShape.Cube, "cube", (1..6).toList()),
        die("d8", DieShape.Octahedron, "octahedron", (1..8).toList()),
        die("d10", DieShape.PentagonalTrapezohedron, "d10", (1..10).toList(), (1..9).map { "$it" } + "0"),
        die(
          "d10-tens",
          DieShape.PentagonalTrapezohedron,
          "d10-tens",
          (0..90 step 10).toList(),
          listOf("00") + (10..90 step 10).map { "$it" },
        ),
        die("d12", DieShape.Dodecahedron, "dodecahedron", (1..12).toList()),
        die("d18", DieShape.EnneagonalTrapezohedron, "enneagonal-trapezohedron", (1..18).toList()),
        die("d20", DieShape.Icosahedron, "icosahedron", (1..20).toList()),
        die("df", DieShape.Cube, "df", listOf(-1, -1, 0, 0, 1, 1), listOf("−", "−", "", "", "+", "+")),
      )

    fun die(
      id: String,
      shape: DieShape,
      picture: String,
      values: List<Int>,
      labels: List<String> = values.map { "$it" },
    ): Die =
      Die(
        id = id,
        shape = shape,
        faces = values.indices.map { Face(index = it, value = values[it], label = labels[it]) },
        texturePath = "textures/$picture.png",
      )

    fun repositoryRoot(): File =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "sample-sets").isDirectory }
        ?: error("no repository root above ${File("").absolutePath}")
  }
}
