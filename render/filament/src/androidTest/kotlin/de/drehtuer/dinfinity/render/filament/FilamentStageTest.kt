package de.drehtuer.dinfinity.render.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * The renderer on a real GPU (`docs/TODO.md`, Step 3).
 *
 * Everything a renderer *decides* is tested on a JVM, so what is left for a
 * device is the half a JVM cannot answer: does the native library load, does
 * the material compile on this driver, do the buffers make a scene Filament
 * will accept, and does a frame come back with something in it.
 *
 * Deliberately not a test of whether the dice look right. Nothing here can
 * tell a good picture from a bad one; that is Step 5.6, and it needs a screen
 * and a person. What these can say is that there *is* a picture, which is the
 * thing that stops being true silently.
 */
@RunWith(AndroidJUnit4::class)
class FilamentStageTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")

  @Before
  fun ready() {
    FilamentStage.ready()
  }

  @Test
  fun theMaterialCompilesOnThisDevice() {
    // The one thing compiling at launch buys and costs: it works on whatever
    // driver is actually here, and it is found out here rather than at build
    // time (`docs/architecture.md`, decision 46).
    FilamentStage(WIDTH, HEIGHT).use { stage ->
      assertEquals(WIDTH, stage.width)
    }
  }

  @Test
  fun anEmptyStageDrawsAFrame() {
    FilamentStage(WIDTH, HEIGHT).use { stage ->
      stage.light()
      stage.aim(TrayCamera.framingTheTray(geometry, aspect()))
      assertTrue("Filament would not start a frame at all", stage.draw())
    }
  }

  @Test
  fun aTrayAndItsDiceMakeASceneFilamentAccepts() {
    FilamentStage(WIDTH, HEIGHT).use { stage ->
      val renderer = FilamentDiceRenderer(stage)
      renderer.begin(spec(), geometry, look)
      renderer.show(frame())
      renderer.settled(frame())
      renderer.end()
    }
  }

  @Test
  fun aDrawnFrameIsNotBlank() {
    // The cheapest thing that notices a scene which builds, draws, reports no
    // error and shows nothing — a camera pointing the wrong way, a mesh wound
    // inside out, a material that compiled to black.
    //
    // Post-processing off, and only here. Filament renders the post pass to an
    // offscreen target and blits it, and on the emulator's software backend
    // that blit never reaches a readable headless swap chain: every pixel
    // comes back opaque black while the same scene draws correctly on the
    // Pixel 10a. Reading the frame *before* the post pass asks the question
    // this test is actually for — was anything drawn — on both tiers rather
    // than on one (`docs/build-setup.md`).
    FilamentStage(WIDTH, HEIGHT, postProcessing = false).use { stage ->
      val renderer = FilamentDiceRenderer(stage)
      renderer.begin(spec(), geometry, look)
      val pixels = stage.pixelBuffer()
      renderer.show(frame())
      assertTrue(stage.draw(pixels))

      val bytes =
        ByteArray(pixels.capacity()).also {
          pixels.rewind()
          pixels.get(it)
        }
      val colours =
        bytes
          .toList()
          .chunked(FilamentStage.PIXEL_BYTES)
          .distinct()

      assertTrue(
        "every pixel of the frame is ${colours.firstOrNull()}: nothing was drawn",
        colours.size > 1,
      )
    }
  }

  @Test
  fun oneEngineOutlivesTheSurfacesMadeFromIt() {
    // What a rotation does: the swap chain and the viewport go, the engine and
    // the compiled material stay. Each stage has to draw on its own, and
    // closing one must not take the next one's engine with it
    // (`docs/TODO.md`, Step 4.1).
    FilamentEngine().use { filament ->
      repeat(SURFACES) {
        filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
          stage.light()
          stage.aim(TrayCamera.framingTheTray(geometry, aspect()))
          assertTrue("a stage sharing an engine would not draw", stage.draw())
        }
      }
      // The engine is still usable after every stage made from it has gone.
      filament.stage(surface = null, width = HEIGHT, height = WIDTH).use { turned ->
        assertEquals(HEIGHT, turned.width)
        turned.light()
        turned.aim(TrayCamera.framingTheTray(geometry, HEIGHT.toDouble() / WIDTH))
        assertTrue("the engine did not survive its stages", turned.draw())
      }
    }
  }

  @Test
  fun aSharedEngineDrawsARollTheSameAsAPrivateOne() {
    FilamentEngine().use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
        val renderer = FilamentDiceRenderer(stage)
        renderer.begin(spec(), geometry, look)
        renderer.show(frame())
        assertTrue(stage.draw())
      }
    }
  }

  @Test
  fun aStageCanBeUsedForOneRollAfterAnother() {
    // `clear` is what keeps eighty dice from becoming a hundred and sixty.
    FilamentStage(WIDTH, HEIGHT).use { stage ->
      val renderer = FilamentDiceRenderer(stage)
      repeat(ROLLS) {
        renderer.begin(spec(), geometry, look)
        renderer.show(frame())
        renderer.end()
      }
    }
  }

  private fun aspect(): Double = WIDTH.toDouble() / HEIGHT

  private fun spec(): ThrowSpec =
    ThrowSpec(
      dice =
        listOf(StandardDice.d20, StandardDice.d6, StandardDice.d4).mapIndexed { index, die ->
          DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
        },
      geometry = geometry,
      table = look,
      seed = 1L,
    )

  private fun frame(): RenderFrame =
    RenderFrame.still(
      List(3) { index ->
        BodyTransform(
          index = index,
          position = Vector3(index * 30.0 - 30.0, 0.0, 10.0),
          orientation = Quaternion.Identity,
        )
      },
    )

  private companion object {
    const val WIDTH = 320
    const val HEIGHT = 640
    const val ROLLS = 3

    /** Rotations, near enough: a new surface each, one engine behind them. */
    const val SURFACES = 3
  }
}
