package de.drehtuer.dinfinity.render.filament

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File

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
  fun bothMaterialsCompileOnThisDevice() {
    // Two of them: refraction is baked into a material when it is compiled,
    // so a die a set called translucent is drawn with a second material that
    // shares the first one's surface. If screen-space refraction, the resin's
    // parameters or the clear coat over them were something this driver's
    // compiler refused, it is here that it would say so
    // (`docs/physics-and-rendering.md`, "A die you can see into").
    FilamentEngine().use { filament ->
      val solid = DiceMaterial.dieOf(StandardDice.d20.material, texturePath = null)
      val clear = DiceMaterial.dieOf(StandardDice.d20.material.copy(translucency = HALF_CLEAR), texturePath = null)
      assertEquals(filament.material, filament.materialFor(solid))
      assertEquals(filament.resinMaterial, filament.materialFor(clear))
      assertTrue("both materials are the same one", filament.material != filament.resinMaterial)
    }
  }

  @Test
  fun aTranslucentDieIsDrawnDifferentlyFromASolidOne() {
    // The cheapest thing that notices a translucency that reaches the file
    // format, the model and the material parameters and then does nothing at
    // all — which is what every step of it up to here could be, and what a
    // JVM test cannot tell apart from working.
    val solid = drawnWith(translucency = 0.0)
    val clear = drawnWith(translucency = MOSTLY_CLEAR)
    assertTrue("a die at 80 % translucent drew exactly what a solid one did", !solid.contentEquals(clear))
  }

  @Test
  fun aResinDieShowsTheFeltBehindIt() {
    // What resin is *for*: the floor under a clear die shows through it. A
    // solid die's own pixels are the same whatever the felt is, so the pixels
    // that agree between a red floor and a blue one under a solid die are its
    // body (and the walls, which no die changes). Under a clear die in the
    // same place, some of exactly those pixels have to follow the floor —
    // which is a refraction that ran, a resin that compiled, and a refracting
    // die that was drawn at all, in one question.
    val red = look.copy(floorColorArgb = RED_FLOOR)
    val blue = look.copy(floorColorArgb = BLUE_FLOOR)
    val solidRed = drawnWith(translucency = 0.0, table = red)
    val solidBlue = drawnWith(translucency = 0.0, table = blue)
    val clearRed = drawnWith(translucency = 1.0, table = red, roughness = 0.0)
    val clearBlue = drawnWith(translucency = 1.0, table = blue, roughness = 0.0)

    val body = pixelsAgreeing(solidRed, solidBlue)
    val seenThrough = body.count { pixel -> !samePixel(clearRed, clearBlue, pixel) }
    Log.i(TAG, "resin: $seenThrough of ${body.size} body-or-wall pixels follow the floor")
    assertTrue(
      "no pixel of a clear die changed with the felt under it ($seenThrough of ${body.size})",
      seenThrough >= LEAST_SEEN_THROUGH,
    )
  }

  @Test
  fun aGlassyDieTakesTheColourOfTheFloorUnderIt() {
    // The question the one above cannot answer: not whether *any* pixel of a
    // clear die follows the floor, but whether the die is mostly floor. A
    // near-white glass die tints nothing, so through it a red floor has to
    // look red and a blue one blue across most of its body; a refraction
    // sampling an empty picture, or a tint that darkens what it sees to
    // nothing, leaves the body grey whichever floor is under it.
    //
    // The body is found with an opaque die, not guessed: the pixels a solid
    // die changes from the empty tray (which takes in its shadow too) and that
    // do not change with the floor (which leaves out the shadow, the floor and
    // the walls).
    val red = look.copy(floorColorArgb = RED_FLOOR)
    val blue = look.copy(floorColorArgb = BLUE_FLOOR)
    val emptyRed = drawnWith(translucency = 0.0, table = red, position = OUT_OF_SIGHT)
    val solidRed = drawnWith(translucency = 0.0, table = red, colour = NEAR_WHITE)
    val solidBlue = drawnWith(translucency = 0.0, table = blue, colour = NEAR_WHITE)
    val glassRed = drawnWith(translucency = 1.0, table = red, roughness = GLASSY, colour = NEAR_WHITE)
    val glassBlue = drawnWith(translucency = 1.0, table = blue, roughness = GLASSY, colour = NEAR_WHITE)

    val body = pixelsAgreeing(solidRed, solidBlue).filter { !samePixel(solidRed, emptyRed, it) }
    val floorColoured =
      body.count { pixel ->
        leans(glassRed, pixel, towardsRed = true) && leans(glassBlue, pixel, towardsRed = false)
      }
    val share = floorColoured.toDouble() / body.size.coerceAtLeast(1)
    val percent = (share * PERCENT).toInt()
    Log.i(TAG, "glass: $floorColoured of ${body.size} body pixels ($percent %) take the floor's hue")
    assertTrue("the die's body was not found (${body.size} pixels)", body.size >= LEAST_BODY)
    assertTrue(
      "only $floorColoured of ${body.size} pixels of a glass die take the floor's colour",
      share >= LEAST_FLOOR_SHARE,
    )
  }

  /** Whether [pixel] of [frame] is clearly redder than it is blue, or the other way. */
  private fun leans(
    frame: ByteArray,
    pixel: Int,
    towardsRed: Boolean,
  ): Boolean {
    val from = pixel * FilamentStage.PIXEL_BYTES
    val r = frame[from].toInt() and BYTE
    val b = frame[from + 2].toInt() and BYTE
    return if (towardsRed) r - b >= HUE_MARGIN else b - r >= HUE_MARGIN
  }

  /** Every pixel [a] and [b] have the same, by index. */
  private fun pixelsAgreeing(
    a: ByteArray,
    b: ByteArray,
  ): List<Int> = (0 until a.size / FilamentStage.PIXEL_BYTES).filter { samePixel(a, b, it) }

  private fun samePixel(
    a: ByteArray,
    b: ByteArray,
    pixel: Int,
  ): Boolean {
    val from = pixel * FilamentStage.PIXEL_BYTES
    return (from until from + FilamentStage.PIXEL_BYTES).all { a[it] == b[it] }
  }

  /** One frame of one die of this translucency, on [table], as bytes. */
  private fun drawnWith(
    translucency: Double,
    table: TableLook = look,
    roughness: Double = StandardDice.d20.material.roughness,
    colour: Int = StandardDice.d20.material.colorArgb,
    position: Vector3 = Vector3(0.0, 0.0, 10.0),
  ): ByteArray {
    // Post-processing off, for the reason `aDrawnFrameIsNotBlank` gives.
    FilamentStage(WIDTH, HEIGHT, postProcessing = false).use { stage ->
      val material =
        StandardDice.d20.material.copy(translucency = translucency, roughness = roughness, colorArgb = colour)
      val die = StandardDice.d20.copy(material = material)
      val renderer = FilamentDiceRenderer(stage)
      renderer.begin(
        ThrowSpec(
          dice = listOf(DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)),
          geometry = geometry,
          table = table,
          seed = 1L,
        ),
        geometry,
        table,
      )
      renderer.show(
        RenderFrame.still(
          listOf(BodyTransform(index = 0, position = position, orientation = Quaternion.Identity)),
        ),
      )
      val pixels = stage.pixelBuffer()
      assertTrue(stage.draw(pixels))
      return ByteArray(pixels.capacity()).also {
        pixels.rewind()
        pixels.get(it)
      }
    }
  }

  @Test
  fun aDieLeftOutUntilItIsLetGoIsDrawnOnceItIs() {
    // The dice of a handful dropped onto the board are built together and let
    // go one after another: a die waiting its turn is taken out of the scene,
    // and `put` has to bring it back for real (`Stage.put`).
    FilamentStage(WIDTH, HEIGHT, postProcessing = false).use { stage ->
      val renderer = FilamentDiceRenderer(stage)
      renderer.begin(spec(), geometry, look)
      val pixels = stage.pixelBuffer()

      fun drawn(frame: RenderFrame): ByteArray {
        renderer.show(frame)
        // Reading the last frame out left the buffer at its end; a second
        // read-back into it would overflow.
        pixels.clear()
        assertTrue(stage.draw(pixels))
        return ByteArray(pixels.capacity()).also {
          pixels.rewind()
          pixels.get(it)
        }
      }

      val all = frame()

      val oneOnly = drawn(RenderFrame.still(all.current.take(1)))
      val letGo = drawn(all)

      assertTrue("a die put back into the scene was not drawn", !oneOnly.contentEquals(letGo))
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
  fun aMaterialKeptOnDiskDrawsOnTheNextLaunch() {
    // The second engine reads both materials back instead of compiling them
    // (`MaterialCache`); the packet has to be one this driver accepts.
    val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "materials-test")
    dir.deleteRecursively()
    try {
      val compiling = System.nanoTime()
      FilamentEngine(materials = MaterialCache(dir)).use { it.resinMaterial }
      val compiled = System.nanoTime() - compiling
      assertEquals(2, dir.listFiles { file -> file.name.endsWith(".filamat") }?.size)

      val reading = System.nanoTime()
      FilamentEngine(materials = MaterialCache(dir)).use { filament ->
        filament.resinMaterial
        val read = System.nanoTime() - reading
        Log.i(
          "dinfinity.startup",
          "materials compiled in ${compiled / NANOS_PER_MS} ms, read in ${read / NANOS_PER_MS} ms",
        )
        filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
          val renderer = FilamentDiceRenderer(stage)
          renderer.begin(spec(), geometry, look)
          renderer.show(frame())
          assertTrue("a material read from disk would not draw", stage.draw())
        }
      }
    } finally {
      dir.deleteRecursively()
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

    /** Clear enough that a resin die cannot come out as the solid one. */
    const val MOSTLY_CLEAR = 0.8

    /** And enough to pick the resin material at all. */
    const val HALF_CLEAR = 0.5

    /** Two felts nothing could mistake for each other. */
    const val RED_FLOOR = 0xFFC02020.toInt()
    const val BLUE_FLOOR = 0xFF2030C0.toInt()

    /**
     * How many of a clear die's pixels have to follow the felt. A d20 at this
     * size is a few hundred pixels; a few dozen is a refraction that ran, and
     * nought is one that did not.
     */
    const val LEAST_SEEN_THROUGH = 30

    /** A glass die that tints nothing, so the floor's own colour is what shows. */
    const val NEAR_WHITE = 0xFFF2F2F2.toInt()

    /** Polished glass: the floor seen through it sharp, not blurred. */
    const val GLASSY = 0.05

    /**
     * Under the floor, where a die is neither seen nor shades anything the
     * key light reaches first: the tray as it is with no die on it.
     */
    val OUT_OF_SIGHT = Vector3(0.0, 0.0, -500.0)

    /**
     * How far apart red and blue have to be, in bytes, for a pixel to be
     * coloured by the floor rather than grey with a cast.
     */
    const val HUE_MARGIN = 16

    /**
     * The share of a glass die's body that has to take the floor's colour.
     * The rest is ink, which transmits nothing, and the lacquer's reflections
     * of the room, which are the room's colour whatever the floor is.
     */
    const val LEAST_FLOOR_SHARE = 0.3

    /** A d20 at this size covers hundreds of pixels; fewer is a die not found. */
    const val LEAST_BODY = 100

    const val BYTE = 0xFF
    const val PERCENT = 100

    /** Rotations, near enough: a new surface each, one engine behind them. */
    const val SURFACES = 3
    const val NANOS_PER_MS = 1_000_000L
    const val TAG = "dinfinity.startup"
  }
}
