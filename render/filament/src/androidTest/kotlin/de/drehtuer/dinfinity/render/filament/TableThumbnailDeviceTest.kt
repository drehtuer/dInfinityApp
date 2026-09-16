package de.drehtuer.dinfinity.render.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A table's thumbnail on a real GPU (`docs/tables.md`, "Thumbnails").
 *
 * Everything a thumbnail *decides* — how big it is, what tray it is a tray of,
 * where the camera stands, which face of the die is up, which looks are kept —
 * is tested on a JVM (`ThumbnailPlanTest`, `ThumbnailCacheTest`,
 * `TrayThumbnailsTest`). What is left for a device is the one question a JVM
 * cannot be asked: **is there a picture**. A scene that builds, draws, reports
 * no error and hands back a rectangle of one colour is the failure this whole
 * feature has, and it is invisible everywhere else.
 *
 * Deliberately not a test of whether the table *looks* right. Nothing here can
 * tell a good picture from a bad one; that is Step 5.6 and it needs a screen
 * and a person.
 */
@RunWith(AndroidJUnit4::class)
class TableThumbnailDeviceTest {
  private val plan = ThumbnailPlan(widthPx = WIDTH, heightPx = HEIGHT)
  private val look = TableLook(id = "felt-green", name = "Green felt")
  private val d20 = ThumbnailDie(StandardDice.d20, setId = "builtin")

  @Before
  fun ready() {
    FilamentStage.ready()
  }

  @Test
  fun aThumbnailIsAPictureRatherThanARectangleOfOneColour() {
    // Post-processing off, as in `FilamentStageTest`, and for the same reason:
    // Filament renders the post pass to an offscreen target and blits it, and
    // on the emulator's software backend that blit never reaches a readable
    // headless swap chain. Reading the frame *before* it asks the question
    // this test is for — was a tray drawn with a die on it — on both tiers
    // rather than on one (`docs/build-setup.md`).
    FilamentEngine().use { filament ->
      val drawn = mutableListOf<Snapshot>()
      thumbnails(filament, postProcessing = false).of("builtin/felt-green", look, d20, drawn::add)

      val picture = drawn.singleOrNull()
      assertNotNull("no frame came back off the GPU at all", picture)
      assertEquals(WIDTH, picture!!.width)
      assertEquals(HEIGHT, picture.height)
      assertTrue("every pixel of the thumbnail is the same colour: nothing was drawn", !picture.uniform)
      assertTrue(
        "the thumbnail is two colours, which is a swatch rather than a lit tray",
        picture.pixels
          .toList()
          .chunked(Snapshot.CHANNELS)
          .distinct()
          .size > COLOURS,
      )
    }
  }

  @Test
  fun aTableWithNoDieOnItIsStillAPicture() {
    // A package with no twenty-sided die gets the tray by itself, which has to
    // be a picture of something rather than an empty frame.
    FilamentEngine().use { filament ->
      val drawn = mutableListOf<Snapshot>()
      thumbnails(filament, postProcessing = false).of("builtin/plain", look, die = null, onDrawn = drawn::add)

      assertTrue("the empty tray drew nothing", drawn.single().pixels.isNotEmpty())
    }
  }

  @Test
  fun theShippingPathIsEitherAPictureOrNothingAtAll() {
    // What a player actually gets, end to end: the application's roll thread,
    // its engine, and post-processing on, because that is Filament's tone
    // mapping and a tray drawn without it is a white rectangle rather than a
    // table. On a phone this is a picture. On a backend that will not read a
    // post-processed frame back it is nothing at all — and *nothing* is the
    // answer the table picker can live with, because the swatch is still
    // there. What must never come back is a rectangle of one colour dressed up
    // as a table.
    RollThread().use { host ->
      val drawn = mutableListOf<Snapshot>()
      val pictures = TrayThumbnails.on(host, plan)

      pictures.of("builtin/felt-green", look, d20, drawn::add)
      // Flushes the queue: the picture is drawn on that thread, not this one.
      host.await {}

      assertTrue("the engine was never made on the thread that owns it", host.engineMade)
      drawn.singleOrNull()?.let { picture ->
        assertTrue("a blank frame was handed over as a table", !picture.uniform)
        assertEquals(WIDTH, picture.width)
        assertEquals(HEIGHT, picture.height)
      }
    }
  }

  @Test
  fun everySwapChainAThumbnailMakesIsGivenBackBeforeTheNextOne() {
    // Every look in a package, one after another, on one engine. A stage left
    // open is a leak the garbage collector cannot see, and the way it shows up
    // is the twentieth thumbnail rather than the first.
    FilamentEngine().use { filament ->
      val pictures = thumbnails(filament, postProcessing = false)
      val drawn = mutableListOf<Snapshot>()

      repeat(LOOKS) { index ->
        pictures.of("builtin/look-$index", look.copy(id = "look-$index"), d20, drawn::add)
      }

      assertEquals(LOOKS, drawn.size)
    }
  }

  @Test
  fun theSameLookIsDrawnOnceHoweverOftenAScreenAsksForIt() {
    FilamentEngine().use { filament ->
      val opened = mutableListOf<Stage>()
      val pictures =
        TrayThumbnails(
          plan = plan,
          post = { it() },
          stages = {
            filament.stage(null, plan.widthPx, plan.heightPx, postProcessing = false).also { opened += it }
          },
        )

      repeat(ROWS) { pictures.of("builtin/felt-green", look, d20) {} }

      assertEquals("the look was drawn again for a row that had already asked", 1, opened.size)
      assertNull("a device that can draw should not have refused", pictures.refusedBecause)
    }
  }

  /** Thumbnails drawn straight away, on this thread, with this engine. */
  private fun thumbnails(
    filament: FilamentEngine,
    postProcessing: Boolean,
  ): TrayThumbnails =
    TrayThumbnails(
      plan = plan,
      post = { it() },
      stages = { filament.stage(null, plan.widthPx, plan.heightPx, postProcessing = postProcessing) },
    )

  private companion object {
    /** A thumbnail at about the size a dense phone asks for. */
    const val WIDTH = 132
    const val HEIGHT = 192

    /**
     * More than a swatch's worth of colours.
     *
     * A lit tray with a die on it has a shaded floor, shaded walls, a shadow
     * and the die's own faces, so it is hundreds of colours; two would be the
     * swatch this replaced, drawn by accident.
     */
    const val COLOURS = 2

    /** As many looks as a package might ship, in one go. */
    const val LOOKS = 8

    /** Rows of a list, all wanting the same table. */
    const val ROWS = 5
  }
}
