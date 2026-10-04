package de.drehtuer.dinfinity.render.filament

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
 * always was, and one grey felt comes out as the two colours its looks name.
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
  }
}
