package de.drehtuer.dinfinity.render.filament

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The bundled tables' pictures on a real GPU (`docs/tables.md`, "Textures").
 *
 * Everything that decides what a table is drawn from — which keys, which
 * material, which coordinates — is tested on a JVM (`DiceMaterialTest`,
 * `TrayMeshTest`). What is left here is what only a GPU can say: the table
 * material compiles on this driver, a felt floor comes out as felt rather than
 * as the flat colour it used to be, plain comes out exactly as flat as it
 * always was, one grey felt comes out as the two colours its looks name, and
 * each textured floor draws, under the real room and after the post pass, as
 * the colour its look names.
 *
 * Not a test of whether felt *looks* like felt. That is the gallery's, and a
 * person's (`tools/gallery.sh`).
 */
@RunWith(AndroidJUnit4::class)
class TableTextureDeviceTest {
  private val geometry = TableGeometry.referenceDevice()

  @Before
  fun ready() {
    FilamentStage.ready()
  }

  @Test
  fun theTableMaterialCompilesOnThisDevice() {
    FilamentEngine(artwork = BundledPictures).use { filament ->
      val felt = DiceMaterial.floorOf(look("felt-green"))
      assertEquals(filament.tableMaterial, filament.materialFor(felt))
      assertNotEquals("the table is drawn with the dice's material", filament.material, filament.tableMaterial)
    }
  }

  @Test
  fun everyBundledPictureDecodesAndUploads() {
    FilamentEngine(artwork = BundledPictures).use { filament ->
      pictureKeys().forEach { (key, map) ->
        assertNotNull("$key did not reach the GPU", filament.tablePicture(key, map))
      }
    }
  }

  @Test
  fun aFeltFloorIsFeltAndNotAFlatColour() {
    val grain = grainOf(look("felt-green"))
    val plain = grainOf(look("plain"))
    Log.i(TAG, "grain: felt $grain, plain $plain")
    assertTrue("the felt floor is as smooth as plain ($grain against $plain)", grain > LEAST_GRAIN)
    assertTrue("the felt floor has no more grain than plain ($grain against $plain)", grain > plain * GRAINIER)
  }

  @Test
  fun anOakFloorIsWoodAndNotAFlatColour() {
    val grain = grainOf(look("oak"))
    Log.i(TAG, "grain: oak $grain")
    assertTrue("the oak floor is a flat colour ($grain)", grain > LEAST_GRAIN)
  }

  @Test
  fun plainIsStillAFlatColour() {
    // Plain is the look that costs least, and it stays as flat as it always
    // was: next-door pixels of its floor differ by the lighting's slow slope
    // and nothing else.
    val grain = grainOf(look("plain"))
    assertTrue("plain has grain ($grain): something drew a picture on it", grain < FLAT)
  }

  @Test
  fun aTableWhosePicturesAreMissingIsDrawnInItsColours() {
    // A package that is gone by the time its table is drawn: no picture
    // comes, and the tray is drawn anyway, flat.
    val orphan = look("felt-green").copy(packageId = "removed-package")
    val grain = grainOf(orphan)
    assertTrue("a missing felt still drew grain ($grain)", grain < FLAT)
  }

  @Test
  fun aFeltIsTheColourItsLookNames() {
    // One grey cloth, two felts: the green one comes out green and the black
    // one dark, because each look's colour is what its felt averages out to
    // (`color_mode = "average"`, `TableTint`). A tint that did not reach the
    // material would draw both the same grey.
    val green = floorColourOf(look("felt-green"))
    val black = floorColourOf(look("felt-black"))
    Log.i(TAG, "felt: green ${green.toList()}, black ${black.toList()}")
    assertTrue("green felt is not green: ${green.toList()}", green[1] > green[0] && green[1] > green[2])
    assertTrue("black felt is not darker than green", black.sum() < green.sum())
  }

  @Test
  fun theFloorsDrawAsTheColoursTheirLooksName() {
    // The calibration end to end, as `StudioLightDeviceTest` does it for a
    // flat felt: the real room and lamp, the post pass on, the empty tray
    // straight down — the Table view a player starts with — and the mean of
    // most of the floor, so a knot or a darker board is averaged with the
    // rest. `color_mode = "average"` says the colour in the file is what the
    // floor averages out to, and here is where that is held to.
    //
    // Every surface reflects the lamps on top of its colour, grey: 1.4 % of
    // white for the felt, 2.3 % for the oiled oak, looking straight down.
    // The colour the material is given has that sheen taken out
    // (`SurfaceLight`) — before it was, green felt drew (44, 94, 65), its red
    // 13 over. A channel darker than the sheen cannot be drawn at all, and is
    // lifted by the least grey that reaches it: black felt's 26 comes out
    // about 32 and oak's blue of 30 about 42. So the oak and the black felt
    // are held to twice the green felt's tolerance, and the oak to staying
    // warm (`docs/tables.md`, "What the floors draw as"). It drew
    // (112, 91, 82) in the gallery's tilted shot while its roughness map stood
    // in for its roughness: pinkish grey.
    //
    // The tilted shot is logged beside it, not held to a number: the key's
    // highlight lands nearer the middle of the floor there, and the gallery
    // is where a person judges it.
    // Every floor is drawn and logged before any is held to its number, so a
    // run that fails on the first still says what the others drew.
    val floors =
      listOf(
        Triple("felt-green", FELT_TOLERANCE, false),
        Triple("felt-black", DARK_TOLERANCE, false),
        Triple("oak", DARK_TOLERANCE, true),
      ).map { (id, tolerance, warm) ->
        val look = look(id)
        val wrote = channelsOf(look.floorColorArgb)
        val down = drawnFloorOf(look, TableView.StraightDown)
        val tilted = drawnFloorOf(look, TableView.Angled)
        Log.i(
          TAG,
          "$id floor ${wrote.joinToString()} drew as (${shown(down)}) straight down, (${shown(tilted)}) tilted",
        )
        DrawnFloor(id, wrote, down, tilted, tolerance, warm)
      }
    floors.forEach { floor ->
      floor.wrote.indices.forEach { channel ->
        assertTrue(
          "${floor.id} drew as (${shown(floor.down)}) straight down for ${floor.wrote.joinToString()}",
          abs(floor.down[channel] - floor.wrote[channel]) <= floor.tolerance,
        )
      }
      if (floor.warm) {
        listOf(floor.down, floor.tilted).forEach { drawn ->
          assertTrue("${floor.id} is not a warm brown: (${shown(drawn)})", drawn[0] > drawn[1] && drawn[1] > drawn[2])
        }
      }
    }
  }

  /** What one look's floor was written as and drew as, and what it is held to. */
  private class DrawnFloor(
    val id: String,
    val wrote: IntArray,
    val down: DoubleArray,
    val tilted: DoubleArray,
    val tolerance: Double,
    val warm: Boolean,
  )

  /**
   * The mean red, green and blue [look]'s empty floor draws as from [view],
   * after the post pass, in levels of 255: a grid over the floor
   * [FLOOR_INSET_MM] clear of the walls, each point a small square around
   * where it lands.
   */
  private fun drawnFloorOf(
    look: TableLook,
    view: TableView,
  ): DoubleArray =
    FilamentEngine(artwork = BundledPictures).use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
        FilamentDiceRenderer(stage, view).table(geometry, look)
        val picture = requireNotNull(stage.capture()) { "Filament skipped the frame" }
        assumeFalse("this backend does not read a post-processed frame back", picture.uniform)
        val shot =
          TrayCamera.framingTheTray(
            geometry,
            WIDTH.toDouble() / HEIGHT,
            tiltDegrees = TrayCamera.tiltDegreesOf(view),
          )
        val sums = DoubleArray(RGB)
        var count = 0
        floorGrid().forEach { point ->
          val (x, y) = requireNotNull(TrayCamera.pixelOf(shot, point, WIDTH, HEIGHT))
          for (row in y.toInt() - REACH..y.toInt() + REACH) {
            for (column in x.toInt() - REACH..x.toInt() + REACH) {
              val at = (row * WIDTH + column) * Snapshot.CHANNELS
              repeat(RGB) { sums[it] += (picture.pixels[at + it].toInt() and BYTE).toDouble() }
              count++
            }
          }
        }
        DoubleArray(RGB) { sums[it] / count }
      }
    }

  /** Points over the floor, [FLOOR_INSET_MM] clear of every wall. */
  private fun floorGrid(): List<Vector3> {
    val halfLong = geometry.longSideMm / 2 - FLOOR_INSET_MM
    val halfShort = geometry.shortSideMm / 2 - FLOOR_INSET_MM
    return (0..GRID).flatMap { i ->
      (0..GRID).map { j ->
        Vector3(-halfLong + 2 * halfLong * i / GRID, -halfShort + 2 * halfShort * j / GRID, 0.0)
      }
    }
  }

  private fun channelsOf(argb: Int): IntArray =
    intArrayOf((argb shr RED_SHIFT) and BYTE, (argb shr GREEN_SHIFT) and BYTE, argb and BYTE)

  private fun shown(colour: DoubleArray): String = colour.joinToString { "%.1f".format(it) }

  /**
   * How grainy [look]'s floor is: the mean difference in brightness between
   * a pixel and the one beside it, over the middle of the floor, in levels of
   * 255. A lit flat colour changes slowly and scores near nought; a picture
   * scores what its grain is.
   */
  private fun grainOf(look: TableLook): Double {
    val bytes = frameOf(look)
    var sum = 0.0
    var count = 0
    for (y in middle(HEIGHT)) {
      for (x in middle(WIDTH).first until middle(WIDTH).last) {
        sum += abs(brightness(bytes, x, y) - brightness(bytes, x + 1, y))
        count++
      }
    }
    return sum / count
  }

  /** The average red, green and blue of the middle of [look]'s floor, in levels of 255. */
  private fun floorColourOf(look: TableLook): DoubleArray {
    val bytes = frameOf(look)
    val sums = DoubleArray(RGB)
    var count = 0
    for (y in middle(HEIGHT)) {
      for (x in middle(WIDTH)) {
        val at = (y * WIDTH + x) * FilamentStage.PIXEL_BYTES
        repeat(RGB) { sums[it] += (bytes[at + it].toInt() and BYTE).toDouble() }
        count++
      }
    }
    return DoubleArray(RGB) { sums[it] / count }
  }

  private fun middle(size: Int): IntRange = (size * MIDDLE_FROM).toInt() until (size * MIDDLE_TO).toInt()

  /** [look]'s empty table, drawn once and read back, before the post pass. */
  private fun frameOf(look: TableLook): ByteArray =
    FilamentEngine(artwork = BundledPictures).use { filament ->
      filament.stage(surface = null, width = WIDTH, height = HEIGHT, postProcessing = false).use { stage ->
        FilamentDiceRenderer(stage).table(geometry, look)
        val pixels = stage.pixelBuffer()
        assertTrue("Filament skipped the frame", stage.draw(pixels))
        ByteArray(pixels.capacity()).also {
          pixels.rewind()
          pixels.get(it)
        }
      }
    }

  private fun brightness(
    bytes: ByteArray,
    x: Int,
    y: Int,
  ): Double {
    val at = (y * WIDTH + x) * FilamentStage.PIXEL_BYTES
    return (0 until RGB).sumOf { bytes[at + it].toInt() and BYTE }.toDouble() / RGB
  }

  private fun look(id: String): TableLook = requireNotNull(BuiltinDiceSet.set.table(id)) { "no table $id" }

  /** Every picture the bundled tables name, with the map it is used as. */
  private fun pictureKeys(): Set<Pair<String, SurfaceMap>> =
    BuiltinDiceSet.set.tables
      .flatMap { look ->
        listOf(DiceMaterial.floorOf(look).maps, DiceMaterial.wallOf(look).maps).flatMap { maps ->
          listOfNotNull(
            maps?.albedo?.let { it to SurfaceMap.ALBEDO },
            maps?.normal?.let { it to SurfaceMap.NORMAL },
            maps?.roughness?.let { it to SurfaceMap.ROUGHNESS },
          )
        }
      }.toSet()

  private companion object {
    const val TAG = "TableTextureDeviceTest"
    const val WIDTH = 540
    const val HEIGHT = 1212

    /** The middle of the frame, which is floor and nothing else. */
    const val MIDDLE_FROM = 0.35
    const val MIDDLE_TO = 0.65

    /** Grain worth calling grain, in levels of 255 between neighbours. */
    const val LEAST_GRAIN = 1.0

    /** And how much grainier than plain the felt has to be. */
    const val GRAINIER = 3.0

    /** What a flat lit colour stays under. */
    const val FLAT = 0.5

    const val RGB = 3
    const val BYTE = 0xFF
    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8

    /** The flat felt's own tolerance (`StudioLightDeviceTest`), in levels of 255 a channel. */
    const val FELT_TOLERANCE = 10.0

    /** Oak's and black felt's: twice the felt's, for a channel darker than the sheen (see the test). */
    const val DARK_TOLERANCE = 20.0

    /** How far clear of the walls the floor is measured: the corners' rounding and the foot of the wall. */
    const val FLOOR_INSET_MM = 20.0

    /** Squares a side of the floor grid: 13 by 13 points. */
    const val GRID = 12

    /** A nine-pixel square around each point. */
    const val REACH = 4
  }
}
