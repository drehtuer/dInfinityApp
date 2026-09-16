package de.drehtuer.dinfinity

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.render.filament.CameraShot
import de.drehtuer.dinfinity.render.filament.DiceMaterial
import de.drehtuer.dinfinity.render.filament.GpuMesh
import de.drehtuer.dinfinity.render.filament.Snapshot
import de.drehtuer.dinfinity.render.filament.Stage
import de.drehtuer.dinfinity.render.filament.ThumbnailDie
import de.drehtuer.dinfinity.render.filament.ThumbnailPlan
import de.drehtuer.dinfinity.render.filament.TrayThumbnails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The join between a screen that wants a picture of a table and a renderer
 * that draws one (`docs/tables.md`, "Thumbnails").
 *
 * Robolectric with native graphics, because the one thing this class does that
 * is not plumbing is turn a run of bytes into a bitmap — and a channel order
 * that is wrong there is a table drawn with its reds and blues swapped, which
 * nothing but a picture can catch.
 *
 * The renderer behind it draws onto a stage that is not a GPU, so what is
 * asked here is the join rather than the drawing: the pixels survive the trip,
 * the answer arrives where Compose can use it, and a table that reuses an id
 * is not shown the picture of the one that is gone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderedTableThumbnailsTest {
  private val plan = ThumbnailPlan(widthPx = 2, heightPx = 2)
  private val look = TableLook(id = "oak", name = "Oak")
  private val pin = TablePin("builtin", "oak")
  private val die = ThumbnailDie(Die.standard("d20", DieShape.Icosahedron), setId = "builtin")
  private var drawings = 0

  @Test
  fun `the pixels the renderer drew are the pixels Compose is given`() {
    val drawn = mutableListOf<ImageBitmap>()

    thumbnails().of(pin, look, drawn::add)

    val picture = drawn.single().toPixelMap()
    assertEquals(2, picture.width)
    assertEquals(2, picture.height)
    // Red, green, blue and white, in that order, and not some rotation of it.
    assertEquals(1.0f, picture[0, 0].red, TOLERANCE)
    assertEquals(0.0f, picture[0, 0].blue, TOLERANCE)
    assertEquals(1.0f, picture[1, 0].green, TOLERANCE)
    assertEquals(1.0f, picture[0, 1].blue, TOLERANCE)
    assertEquals(1.0f, picture[0, 0].alpha, TOLERANCE)
  }

  @Test
  fun `the picture arrives where Compose can be told about it`() {
    val posted = mutableListOf<Runnable>()
    val drawn = mutableListOf<ImageBitmap>()

    thumbnails(toMain = posted::add).of(pin, look, drawn::add)

    assertTrue("the screen was written to from the drawing thread", drawn.isEmpty())
    posted.single().run()
    assertEquals(1, drawn.size)
  }

  @Test
  fun `a frame nothing arrived in is not shown as a table`() {
    val drawn = mutableListOf<ImageBitmap>()

    thumbnails(frame = { blank() }).of(pin, look, drawn::add)

    assertTrue(drawn.isEmpty())
  }

  @Test
  fun `a package with no d20 still gets a picture of its table`() {
    val drawn = mutableListOf<ImageBitmap>()

    thumbnails(die = null).of(pin, look, drawn::add)

    assertEquals(1, drawn.size)
  }

  @Test
  fun `the same look is drawn once, however many rows ask`() {
    val pictures = thumbnails()

    repeat(3) { pictures.of(pin, look) {} }

    assertEquals(1, drawings)
  }

  @Test
  fun `a table that reuses an id is not shown the old table's picture`() {
    // A photograph removed and another made: `photo-oak` is free again, and a
    // picture filed under the pin alone would be of the table that has gone
    // (`docs/tables.md`, "Your own photo").
    val pictures = thumbnails()

    pictures.of(pin, look) {}
    pictures.of(pin, look.copy(floorColorArgb = 0x00FF00FF.toInt())) {}

    assertEquals(2, drawings)
  }

  @Test
  fun `a look that has not changed is the same look, so it is drawn once`() {
    val pictures = thumbnails()

    pictures.of(pin, look) {}
    pictures.of(pin, look.copy()) {}

    assertEquals(1, drawings)
  }

  /**
   * Thumbnails over a renderer whose stage hands back [frame] without touching
   * a GPU.
   *
   * What is being tested is this module's half of the seam;
   * `TrayThumbnailsTest` drives the renderer's half, and the device suite the
   * half that needs a driver.
   */
  private fun thumbnails(
    frame: () -> Snapshot = ::painted,
    die: ThumbnailDie? = this.die,
    toMain: (Runnable) -> Unit = Runnable::run,
  ): RenderedTableThumbnails =
    RenderedTableThumbnails(
      pictures =
        TrayThumbnails(
          plan = plan,
          post = { it() },
          stages = {
            drawings++
            PaintedStage(plan.widthPx, plan.heightPx, frame())
          },
        ),
      die = { die },
      toMain = toMain,
    )

  /** A red, a green, a blue and a white pixel: a frame with something in it. */
  private fun painted(): Snapshot = Snapshot(width = 2, height = 2, pixels = RED + GREEN + BLUE + WHITE)

  /** And one with nothing in it. */
  private fun blank(): Snapshot = Snapshot(2, 2, ByteArray(2 * 2 * Snapshot.CHANNELS))

  private companion object {
    const val TOLERANCE = 0.01f

    /** One opaque pixel each, red, green, blue and alpha a byte at a time. */
    val RED = byteArrayOf(-1, 0, 0, -1)
    val GREEN = byteArrayOf(0, -1, 0, -1)
    val BLUE = byteArrayOf(0, 0, -1, -1)
    val WHITE = byteArrayOf(-1, -1, -1, -1)
  }
}

/** A stage that accepts a whole scene, draws nothing and hands back [frame]. */
private class PaintedStage(
  override val width: Int,
  override val height: Int,
  private val frame: Snapshot,
) : Stage {
  private var entities = 0

  override fun light() = Unit

  override fun add(
    mesh: GpuMesh,
    parameters: DiceMaterial.Parameters,
  ): Int = ++entities

  override fun place(
    entity: Int,
    matrix: FloatArray,
  ) = Unit

  override fun take(entity: Int) = Unit

  override fun aim(shot: CameraShot) = Unit

  override fun draw(): Boolean = true

  override fun capture(): Snapshot = frame

  override fun clear() = Unit

  override fun close() = Unit
}
