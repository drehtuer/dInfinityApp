package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drawing a table's picture, with the thread and the GPU taken out
 * (`docs/tables.md`, "Thumbnails").
 *
 * What is left once they are is everything that can be wrong without a device:
 * whether a look is drawn once or once per row, whether a scene is built the
 * way a throw builds one, what becomes of a frame that came back empty, and
 * whether a phone with no working engine is asked again for every table it
 * owns. The one thing not tested here is that Filament draws anything at all,
 * which is what the device suite is for.
 */
class TrayThumbnailsTest {
  private val plan = ThumbnailPlan(widthPx = 52, heightPx = 78)
  private val look = TableLook(id = "oak", name = "Oak")
  private val d20 = ThumbnailDie(StandardDice.d20, setId = "builtin")
  private val stages = mutableListOf<FakeStage>()

  @Test
  fun `a look is drawn on the tray mesh, with the die standing on it`() {
    val drawn = mutableListOf<Snapshot>()

    thumbnails().of("builtin/oak", look, d20, drawn::add)

    assertEquals(1, drawn.size)
    val stage = stages.single()
    assertTrue("the tray was not lit the way the roll screen lights it", stage.litWhenCaptured)
    // Floor, wall and rim, and then the die: the renderer's own scene rather
    // than a second way of building one.
    assertEquals(4, stage.whenCaptured.size)
    assertEquals(plan.widthPx, stage.width)
  }

  @Test
  fun `the camera is the plan's, not the whole tray`() {
    thumbnails().of("builtin/oak", look, d20) {}

    val aimed = stages.single().shots.last()
    val whole = TrayCamera.framingTheTray(plan.geometry, plan.aspectRatio)
    assertEquals(TrayCamera.framingTheTray(plan.geometry, plan.aspectRatio, plan.view), aimed)
    assertTrue("the camera is still framing the whole tray", aimed.distanceMm < whole.distanceMm)
  }

  @Test
  fun `a package with no d20 gets a picture of the table with nothing on it`() {
    thumbnails().of("builtin/oak", look, die = null) {}

    // Floor, wall and rim, and no die.
    assertEquals(3, stages.single().whenCaptured.size)
  }

  @Test
  fun `the same look is drawn once, however many rows ask for it`() {
    val pictures = thumbnails()
    val drawn = mutableListOf<Snapshot>()

    repeat(3) { pictures.of("builtin/oak", look, d20, drawn::add) }

    assertEquals("the look was drawn again", 1, stages.size)
    assertEquals("the rows after the first were left with nothing", 3, drawn.size)
    assertTrue("two rows were given different pictures", drawn.all { it === drawn.first() })
  }

  @Test
  fun `two looks are two pictures`() {
    val pictures = thumbnails()

    pictures.of("builtin/oak", look, d20) {}
    pictures.of("builtin/felt-green", look.copy(id = "felt-green"), d20) {}

    assertEquals(2, stages.size)
  }

  @Test
  fun `the stage is given back as soon as the picture is`() {
    // A swap chain per look held for the life of the app is a leak the JVM
    // cannot see.
    thumbnails().of("builtin/oak", look, d20) {}

    assertTrue("the stage was left open", stages.single().closed)
    assertTrue("the scene was left in it", stages.single().added.isEmpty())
  }

  @Test
  fun `a frame that came back all one colour is not shown`() {
    val drawn = mutableListOf<Snapshot>()

    thumbnails(picture = { blank() }).of("builtin/oak", look, d20, drawn::add)

    assertTrue("a black rectangle was shown as a table", drawn.isEmpty())
  }

  @Test
  fun `and the look is not drawn again for it, on this device`() {
    val pictures = thumbnails(picture = { blank() })

    repeat(3) { pictures.of("builtin/oak", look, d20) {} }

    assertEquals(1, stages.size)
  }

  @Test
  fun `a stage that will not read a frame back at all gives nothing`() {
    val drawn = mutableListOf<Snapshot>()

    thumbnails(picture = { null }).of("builtin/oak", look, d20, drawn::add)

    assertTrue(drawn.isEmpty())
  }

  @Test
  fun `a device where the engine will not open is asked once and never again`() {
    val opened = mutableListOf<String>()
    val pictures =
      TrayThumbnails(
        plan = plan,
        post = { it() },
        stages = {
          opened += "tried"
          error("no Filament on this device")
        },
      )
    val drawn = mutableListOf<Snapshot>()

    pictures.of("builtin/oak", look, d20, drawn::add)
    pictures.of("builtin/felt-green", look.copy(id = "felt-green"), d20, drawn::add)

    assertTrue(drawn.isEmpty())
    assertEquals("a second look asked a broken engine again", 1, opened.size)
    assertEquals("no Filament on this device", pictures.refusedBecause)
  }

  @Test
  fun `a device with no native library is the same answer`() {
    val pictures =
      TrayThumbnails(plan = plan, post = { it() }, stages = { throw UnsatisfiedLinkError("libfilament-jni.so") })

    pictures.of("builtin/oak", look, d20) {}

    assertEquals("libfilament-jni.so", pictures.refusedBecause)
  }

  @Test
  fun `nothing is drawn before this returns, so a screen may ask from a composition`() {
    val queued = mutableListOf<() -> Unit>()
    val pictures = TrayThumbnails(plan = plan, post = queued::add, stages = { stage() })

    pictures.of("builtin/oak", look, d20) {}

    assertEquals("the picture was drawn where it was asked for", 0, stages.size)
    queued.single().invoke()
    assertEquals(1, stages.size)
  }

  @Test
  fun `forgetting everything makes the next ask draw again`() {
    val pictures = thumbnails()
    pictures.of("builtin/oak", look, d20) {}

    pictures.forget()
    pictures.of("builtin/oak", look, d20) {}

    assertEquals(2, stages.size)
  }

  @Test
  fun `a cache handed in is the one that is used`() {
    val kept = ThumbnailCache(capacity = 1)
    val pictures = TrayThumbnails(plan = plan, post = { it() }, cache = kept, stages = { stage() })

    pictures.of("builtin/oak", look, d20) {}

    assertEquals(listOf("builtin/oak"), kept.keys)
    assertNotNull(kept.of("builtin/oak"))
  }

  @Test
  fun `a look pushed out of the cache is drawn again when it comes back`() {
    val pictures = TrayThumbnails(plan = plan, post = { it() }, cache = ThumbnailCache(1), stages = { stage() })

    pictures.of("a", look, d20) {}
    pictures.of("b", look, d20) {}
    pictures.of("a", look, d20) {}

    assertEquals(3, stages.size)
  }

  @Test
  fun `a device that has refused once keeps saying why`() {
    val pictures = thumbnails()

    assertNull("nothing has gone wrong yet", pictures.refusedBecause)
    pictures.of("builtin/oak", look, d20) {}
    assertNull(pictures.refusedBecause)
  }

  @Test
  fun `the picture that comes back is the one the stage drew`() {
    val painted = picture()
    val drawn = mutableListOf<Snapshot>()

    thumbnails(picture = { painted }).of("builtin/oak", look, d20, drawn::add)

    assertSame(painted, drawn.single())
  }

  /** Thumbnails drawn straight away, onto a stage that remembers everything. */
  private fun thumbnails(picture: () -> Snapshot? = ::picture): TrayThumbnails =
    TrayThumbnails(plan = plan, post = { it() }, stages = { stage(picture()) })

  private fun stage(picture: Snapshot? = picture()): FakeStage =
    FakeStage(width = plan.widthPx, height = plan.heightPx).also {
      it.picture = picture
      stages += it
    }

  /** A frame with something in it: two pixels that are not the same colour. */
  private fun picture(): Snapshot = Snapshot(width = 2, height = 1, pixels = byteArrayOf(0, 0, 0, -1, 1, 2, 3, -1))

  /** And one with nothing in it. */
  private fun blank(): Snapshot = Snapshot(width = 2, height = 1, pixels = ByteArray(2 * Snapshot.CHANNELS))
}
