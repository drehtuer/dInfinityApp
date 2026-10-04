package de.drehtuer.dinfinity.render.filament

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.jolt.JoltDiceSimulator
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

/**
 * Pictures of real throws, for a person to look at.
 *
 * Nothing here asserts what a die *looks* like — that is a judgement, and it
 * belongs to somebody holding the phone (`.claude/CLAUDE.md`). What this does
 * is make the judgement repeatable: the same seeded throws on the same tables,
 * settled by the real physics and drawn through the real renderer onto a
 * surface the size of the screen, written out as PNGs. Run it before and after
 * a rendering change and the two sets are the comparison
 * (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
 *
 * **Opt-in, like the harness.** It draws every step of every throw, which is
 * minutes rather than seconds, so the ordinary device suite skips it; pass
 * `-e gallery 1` (`tools/gallery.sh`) to run it.
 */
@RunWith(AndroidJUnit4::class)
class RenderGalleryTest {
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun drawTheGallery() {
    assumeTrue(
      "no gallery was asked for; pass -e gallery 1 (tools/gallery.sh)",
      InstrumentationRegistry.getArguments().getString(ARGUMENT) == "1",
    )
    FilamentStage.ready()
    val out = outDirectory()
    FilamentEngine().use { filament ->
      scenes().forEach { scene ->
        filament.stage(surface = null, width = WIDTH, height = HEIGHT).use { stage ->
          val renderer = FilamentDiceRenderer(stage)
          JoltDiceSimulator().start(scene.spec, renderer, listening = false).use { it.runToEnd() }
          val picture = requireNotNull(stage.capture()) { "Filament skipped the frame of ${scene.name}" }
          write(picture, File(out, "${scene.name}.png"))
        }
      }
    }
  }

  private class Scene(
    val name: String,
    val spec: ThrowSpec,
  )

  /**
   * Every built-in shape on each of three tables that light differently —
   * matte felt, a satin wood, a glossy glass — and the same throw twice more
   * on felt, once in metal and once see-through, which are the two materials
   * a rendering change is likeliest to move.
   */
  private fun scenes(): List<Scene> {
    val set = BuiltinDiceSet.set
    val shapes = set.dice.filter { it.id in SHAPES }.sortedBy { SHAPES.indexOf(it.id) }
    val tables = TABLES.mapNotNull { set.table(it) }
    val onTables = tables.map { look -> Scene(look.id, throwOf(shapes + shapes, look)) }
    val felt = tables.first()
    val metal = shapes.map { it.copy(material = it.material.copy(metallic = 1.0, roughness = METAL_ROUGHNESS)) }
    val clear =
      shapes.map {
        it.copy(material = it.material.copy(translucency = CLEAR, colorArgb = AMBER, numberColorArgb = WHITE))
      }
    return onTables +
      Scene("${felt.id}-metal", throwOf(metal + metal, felt)) +
      Scene("${felt.id}-translucent", throwOf(clear + clear, felt))
  }

  private fun throwOf(
    dice: List<Die>,
    look: TableLook,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        dice.mapIndexed { index, die ->
          DieInstance(
            index = index,
            groupId = 0,
            setId = BuiltinDiceSet.set.id,
            requestedSetId = BuiltinDiceSet.set.id,
            die = die,
          )
        },
      geometry = geometry,
      table = look,
      seed = SEED,
    )

  private fun outDirectory(): File {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val root = requireNotNull(context.getExternalFilesDir(null)) { "no external files directory to write into" }
    return File(root, DIRECTORY).apply {
      deleteRecursively()
      mkdirs()
    }
  }

  /** The frame as it came off the GPU — rows from the top, RGBA — as a PNG. */
  private fun write(
    picture: Snapshot,
    file: File,
  ) {
    val bitmap = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(picture.pixels))
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    bitmap.recycle()
  }

  private companion object {
    const val ARGUMENT = "gallery"
    const val DIRECTORY = "gallery"

    /** The Pixel 10a's screen, upright. */
    const val WIDTH = 1080
    const val HEIGHT = 2424

    const val SEED = 20261004L
    const val PNG_QUALITY = 100
    const val METAL_ROUGHNESS = 0.3
    const val CLEAR = 0.6
    const val AMBER = 0xFFD9822B.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    val SHAPES = listOf("d4", "d6", "d8", "d10", "d12", "d20")
    val TABLES = listOf("felt-green", "oak", "dark-glass")
  }
}
