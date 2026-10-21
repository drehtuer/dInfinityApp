package de.drehtuer.dinfinity.render.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * A die's artwork on a real GPU (`docs/TODO.md`, Step 3).
 *
 * What a JVM has already answered: which atlas a die asks for, that it is
 * decoded once, and that the printed labels are built alongside it. What only a
 * device can answer is the half on the other side of [Stage] — that an
 * `RGBA8` texture uploads, that the material still compiles now that it
 * composites rather than multiplies, that a scene wearing one draws, and that
 * the handles are given back with the engine rather than leaked.
 *
 * Deliberately not a test of whether the artwork *looks* right. Whether a
 * texture reads at arm's length is Step 5.6, and it needs a screen and a
 * person.
 */
@RunWith(AndroidJUnit4::class)
class AtlasTextureTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")

  @Before
  fun ready() {
    FilamentStage.ready()
  }

  @Test
  fun aDieWearingAnAtlasDrawsAFrame() {
    var asked = 0
    FilamentEngine(
      artwork = {
        asked++
        atlas()
      },
    ).use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
        val renderer = FilamentDiceRenderer(stage)
        renderer.begin(painted(), geometry, look)
        renderer.show(frame())
        assertTrue("a textured die would not draw", stage.draw())
      }
      assertEquals("the atlas was decoded more than once", 1, asked)
      assertEquals(1, filament.atlases.uploaded)
    }
  }

  @Test
  fun oneAtlasServesEverySurfaceMadeFromTheEngine() {
    // What a rotation does. The surface goes, the artwork stays: a texture
    // re-uploaded per surface is a native handle nobody gives back.
    var asked = 0
    FilamentEngine(
      artwork = {
        asked++
        atlas()
      },
    ).use { filament ->
      repeat(SURFACES) {
        filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
          val renderer = FilamentDiceRenderer(stage)
          renderer.begin(painted(), geometry, look)
          renderer.show(frame())
          assertTrue(stage.draw())
        }
      }
      assertEquals(1, asked)
      assertEquals(1, filament.atlases.uploaded)
    }
  }

  @Test
  fun anAtlasThatWillNotDecodeLeavesTheDieDrawnAndPrinted() {
    // A die whose artwork is unusable is not a die that fails to draw: it is
    // the die everybody has had all along (`docs/dice-sets.md`, "Textures").
    FilamentEngine(artwork = { null }).use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
        val renderer = FilamentDiceRenderer(stage)
        renderer.begin(painted(), geometry, look)
        renderer.show(frame())
        assertTrue(stage.draw())
      }
      assertEquals(0, filament.atlases.uploaded)
    }
  }

  @Test
  fun theStageSaysWhichFacesTheUploadedAtlasDrawsOn() {
    // The left column of a d6's 3×2 grid is faces 0 and 3: those two carry
    // the drawing and no number, and the other four are printed
    // (`docs/dice-sets.md`, "Labels, and the artwork over them").
    FilamentEngine(artwork = { atlas() }).use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
        assertEquals(setOf(0, 3), stage.drawnCells(AtlasKey.of("brass", "textures/d6.png"), faces = 6))
        val renderer = FilamentDiceRenderer(stage)
        renderer.begin(painted(), geometry, look)
        renderer.show(frame())
        assertTrue(stage.draw())
      }
      assertEquals("asking which faces are drawn uploaded the atlas a second time", 1, filament.atlases.uploaded)
    }
  }

  @Test
  fun aPartlyClearAtlasDrawsSomethingOtherThanTheDieAlone() {
    // The cheapest thing that notices a material which compiled but composites
    // nothing: the same throw drawn with and without artwork must not come
    // back as the same picture. Post-processing off for the reason
    // `FilamentStageTest` gives — the emulator's software backend never blits
    // the post pass into a readable headless swap chain.
    val plain = pixelsOf(artwork = null)
    val painted = pixelsOf(artwork = atlas())

    assertTrue("the artwork changed nothing about the frame", !plain.contentEquals(painted))
  }

  @Test
  fun aDieWearingAnOpaqueAtlasIsTheAtlasColourNotATintOfItsBody() {
    // What the face designer exports: a black die with its faces drawn over in
    // a red ink. On the tray it has to be the red the ink was, which is what a
    // die whose body is that red looks like — numbers printed in the body
    // colour so that neither die shows any. The material used to multiply the
    // artwork by the body (black times anything is black) and upload it as
    // linear (`#C0` read as 75 % rather than 52 %); either is a frame far from
    // the other, and only both gone makes the two the same.
    val ink = 0xFFC01010.toInt()
    val drawn = pixelsOf(artwork = filled(ink), material = DieMaterial(colorArgb = BLACK, numberColorArgb = BLACK))
    val dyed = pixelsOf(artwork = null, material = DieMaterial(colorArgb = ink, numberColorArgb = ink))

    val worst = drawn.indices.maxOf { abs((drawn[it].toInt() and BYTE) - (dyed[it].toInt() and BYTE)) }
    assertTrue("the drawn die is $worst levels off the die of its ink's colour", worst <= SAME_COLOUR)
  }

  private fun pixelsOf(
    artwork: AtlasImage?,
    material: DieMaterial = DieMaterial(),
  ): ByteArray =
    FilamentEngine(artwork = { artwork }).use { filament ->
      filament
        .stage(surface = null, width = WIDTH, height = HEIGHT, postProcessing = false)
        .use { stage ->
          val renderer = FilamentDiceRenderer(stage)
          renderer.begin(painted(material), geometry, look)
          renderer.show(frame())
          val buffer = stage.pixelBuffer()
          assertTrue(stage.draw(buffer))
          ByteArray(buffer.capacity()).also {
            buffer.rewind()
            buffer.get(it)
          }
        }
    }

  /** A throw of one d6 wearing an atlas from a package called `brass`. */
  private fun painted(material: DieMaterial = DieMaterial()): ThrowSpec {
    val die = StandardDice.d6.copy(texturePath = "textures/d6.png", material = material)
    return ThrowSpec(
      dice = listOf(DieInstance(index = 0, groupId = 0, setId = "brass", requestedSetId = "brass", die = die)),
      geometry = geometry,
      table = look,
      seed = 1L,
    )
  }

  /**
   * A d6's atlas: the left column of its 3×2 grid drawn in red, the rest clear,
   * so two faces carry artwork and no labels, and four carry their labels.
   */
  private fun atlas(): AtlasImage {
    val width = CELLS_ACROSS * CELL
    val height = CELLS_DOWN * CELL
    val pixels = ByteArray(width * height * AtlasImage.CHANNELS)
    for (y in 0 until height) {
      for (x in 0 until CELL) {
        val at = (y * width + x) * AtlasImage.CHANNELS
        pixels[at] = 0xC0.toByte()
        pixels[at + 3] = 0xFF.toByte()
      }
    }
    return AtlasImage(width, height, pixels)
  }

  /** A d6's atlas with every pixel of every cell [argb], opaque: every face drawn over. */
  private fun filled(argb: Int): AtlasImage {
    val width = CELLS_ACROSS * CELL
    val height = CELLS_DOWN * CELL
    val pixels = ByteArray(width * height * AtlasImage.CHANNELS)
    for (at in pixels.indices step AtlasImage.CHANNELS) {
      pixels[at] = (argb shr RED_SHIFT).toByte()
      pixels[at + 1] = (argb shr GREEN_SHIFT).toByte()
      pixels[at + 2] = argb.toByte()
      pixels[at + 3] = (argb ushr ALPHA_SHIFT).toByte()
    }
    return AtlasImage(width, height, pixels)
  }

  private fun frame(): RenderFrame =
    RenderFrame.still(
      listOf(BodyTransform(index = 0, position = Vector3(0.0, 0.0, 8.0), orientation = Quaternion.Identity)),
    )

  private companion object {
    const val WIDTH = 240
    const val HEIGHT = 480
    const val SURFACES = 3
    const val CELL = 16
    const val CELLS_ACROSS = 3
    const val CELLS_DOWN = 2
    const val BLACK = 0xFF000000.toInt()
    const val BYTE = 0xFF
    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8
    const val ALPHA_SHIFT = 24

    /**
     * How far apart two frames of the same colour may be, in levels of a
     * byte: the body colour is converted to linear in doubles and the atlas
     * by the GPU from eight bits, and the two roundings can differ by a
     * level or two. The faults this catches are tens of levels off.
     */
    const val SAME_COLOUR = 4
  }
}
