package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Thumbnails wired to the real roll thread, on a runtime with no Filament in
 * it (`docs/architecture.md`, decision 60).
 *
 * Robolectric gives a looper, so the thread and the posting are real; what it
 * does not give is Filament's native library, so the engine cannot be opened.
 * Which makes this the fallback test rather than a poor imitation of the
 * device suite: **a device where the engine will not open gets no picture, is
 * not asked again, and does not take the screen down with it**. That is
 * exactly what a phone too old for the material would do, and there is no
 * other tier where it can be made to happen on purpose.
 */
@RunWith(RobolectricTestRunner::class)
class TrayThumbnailsOnRollThreadTest {
  private val plan = ThumbnailPlan(widthPx = 44, heightPx = 64)
  private val look = TableLook(id = "oak", name = "Oak")
  private val d20 = ThumbnailDie(StandardDice.d20, setId = "builtin")

  @Test
  fun `the work goes to the thread that owns the engine`() {
    RollThread().use { host ->
      val drawn = mutableListOf<Snapshot>()
      val pictures = TrayThumbnails.on(host, plan)

      pictures.of("builtin/oak", look, d20, drawn::add)
      // Flushes the queue, which is also how the posting is observed at all:
      // the work does not run where it was asked for.
      host.await {}

      assertTrue("a picture came back from a runtime with no GPU in it", drawn.isEmpty())
    }
  }

  @Test
  fun `a runtime with no Filament gives no picture and says why, once`() {
    RollThread().use { host ->
      val pictures = TrayThumbnails.on(host, plan)

      pictures.of("builtin/oak", look, d20) {}
      pictures.of("builtin/felt-green", look.copy(id = "felt-green"), d20) {}
      host.await {}

      assertNotNull("nothing said why there are no thumbnails here", pictures.refusedBecause)
    }
  }

  @Test
  fun `and the screen it was asked from carries on`() {
    // The whole point of the fallback: no exception reaches the caller, and
    // the table picker goes on showing its swatches.
    RollThread().use { host ->
      val pictures = TrayThumbnails.on(host, plan)
      var asks = 0

      repeat(THREE) {
        pictures.of("builtin/oak", look, d20) { asks++ }
      }
      pictures.forget()
      host.await {}

      assertEquals(0, asks)
    }
  }

  private companion object {
    const val THREE = 3
  }
}
