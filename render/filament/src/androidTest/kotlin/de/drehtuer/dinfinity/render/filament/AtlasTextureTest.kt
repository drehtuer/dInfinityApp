package de.drehtuer.dinfinity.render.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.core.model.DieInstance
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

  private fun pixelsOf(artwork: AtlasImage?): ByteArray =
    FilamentEngine(artwork = { artwork }).use { filament ->
      filament
        .stage(surface = null, width = WIDTH, height = HEIGHT, postProcessing = false)
        .use { stage ->
          val renderer = FilamentDiceRenderer(stage)
          renderer.begin(painted(), geometry, look)
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
  private fun painted(): ThrowSpec {
    val die = StandardDice.d6.copy(texturePath = "textures/d6.png")
    return ThrowSpec(
      dice = listOf(DieInstance(index = 0, groupId = 0, setId = "brass", requestedSetId = "brass", die = die)),
      geometry = geometry,
      table = look,
      seed = 1L,
    )
  }

  /**
   * A d6's atlas: the left column of its 3×2 grid drawn in red, the rest clear,
   * so two faces carry artwork and four carry their labels.
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
  }
}
