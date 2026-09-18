package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The tray lets its surface go when the screen does, and takes it back when the
 * screen comes back (`docs/TODO.md`, Step 4.1).
 *
 * **On a device because there is no surface anywhere else.** `AndroidExternalSurface`
 * hands over a real `Surface` from a real window, and under Robolectric it
 * hands over nothing at all — a test there would compose this happily and never
 * once call [Tray.surfaceAvailable], which is the whole of what is being
 * asserted. That is also why the bug reached a phone before anybody saw it.
 *
 * The lifecycle is this test's rather than the activity's. Locking a phone is
 * not something instrumentation can do to itself and have the test survive to
 * report, and what the lock screen *does* to a tray is deliver `ON_STOP` — so
 * the stop is delivered directly and the tray is asked what it did about it.
 */
@RunWith(AndroidJUnit4::class)
class DiceTrayLifecycleTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun aScreenThatStopsGivesTheSurfaceBackAndTakesItAgainOnTheWayIn() {
    val tray = Recording()
    val screen = Screen()
    show(tray, screen)

    // Somewhere to draw, before anything else happens.
    //
    // The one wait in this class that a cold process has to satisfy: the first
    // surface of the first test is behind the app being started, Compose being
    // set up and a `SurfaceView` being given a buffer, and on a phone that has
    // just had the suite installed that took longer than five seconds once in
    // a whole-tier run — while passing in under one on every warm run since.
    // So it is given a cold-start budget rather than the warm one the waits
    // below use. It still fails if the surface never arrives, which is what it
    // is for; it stops failing because the phone was busy.
    compose.waitUntil(COLD_TIMEOUT) { tray.events.isNotEmpty() }
    assertEquals(listOf(AVAILABLE), tray.events.toList())

    // The lock screen.
    screen.moveTo(Lifecycle.State.CREATED)
    compose.waitUntil(TIMEOUT) { tray.events.size > 1 }
    assertEquals(
      "the tray held its surface through the screen going off",
      listOf(AVAILABLE, LOST),
      tray.events.toList(),
    )

    // And unlocking it.
    screen.moveTo(Lifecycle.State.RESUMED)
    compose.waitUntil(TIMEOUT) { tray.events.size > 2 }
    assertEquals(
      "nothing handed the surface back when the screen returned",
      listOf(AVAILABLE, LOST, AVAILABLE),
      tray.events.toList(),
    )
  }

  @Test
  fun theSurfaceThatComesBackIsOneThatCanStillBeDrawnTo() {
    // The failure this guards against is worse than a black tray: a `Surface`
    // is not to be touched once it has been withdrawn, and handing a stale one
    // to Filament is a crash in the driver rather than a missing picture.
    val tray = Recording()
    val screen = Screen()
    show(tray, screen)
    compose.waitUntil(TIMEOUT) { tray.surfaces.isNotEmpty() }

    screen.moveTo(Lifecycle.State.CREATED)
    compose.waitUntil(TIMEOUT) { tray.events.size > 1 }
    screen.moveTo(Lifecycle.State.RESUMED)
    compose.waitUntil(TIMEOUT) { tray.events.size > 2 }

    assertTrue("a surface nobody may touch was handed over", tray.surfaces.all(Surface::isValid))
  }

  @Test
  fun aTrayThatIsNeverStoppedIsNeverInterrupted() {
    // The other half of the bar: the fix must not make an ordinary visit give
    // its surface up and take it back for no reason, which would be a black
    // frame in the middle of a roll nobody asked for.
    val tray = Recording()
    val screen = Screen()
    show(tray, screen)
    compose.waitUntil(TIMEOUT) { tray.events.isNotEmpty() }

    compose.mainClock.advanceTimeBy(A_WHILE)
    compose.waitForIdle()

    assertEquals("a tray nobody stopped was interrupted anyway", listOf(AVAILABLE), tray.events.toList())
  }

  private fun show(
    tray: Tray,
    screen: Screen,
  ) {
    compose.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides screen) {
        DiceTray(
          driver = tray,
          geometry = TableGeometry.referenceDevice(),
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    screen.moveTo(Lifecycle.State.RESUMED)
  }

  /**
   * A lifecycle this test drives by hand.
   *
   * A `LifecycleRegistry` insists on the main thread, which is where the
   * activity's own events arrive too, so the moves go through Compose's.
   */
  private inner class Screen : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    fun moveTo(state: Lifecycle.State) {
      compose.runOnUiThread { registry.currentState = state }
      compose.waitForIdle()
    }
  }

  /** A tray that draws nothing and remembers what it was told. */
  private class Recording : Tray {
    val events = CopyOnWriteArrayList<String>()
    val surfaces = CopyOnWriteArrayList<Surface>()

    override fun surfaceAvailable(
      surface: Surface,
      width: Int,
      height: Int,
    ) {
      surfaces += surface
      events += AVAILABLE
    }

    override fun surfaceLost() {
      events += LOST
    }

    override fun roll(
      start: (Renderer) -> WatchedRoll,
      onCounted: (Map<Int, Int>) -> Unit,
      onStalled: (List<Int>) -> Unit,
      onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
    ) = Unit

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) = Unit

    override fun shake(sample: ShakeSample) = Unit

    override fun look(view: TrayView) = Unit

    override fun clear() = Unit

    override fun close() = Unit
  }

  private companion object {
    const val AVAILABLE = "available"
    const val LOST = "lost"

    /** Long enough for a surface to be made on a phone that is busy. */
    const val TIMEOUT = 5_000L

    /** And what the first surface of a cold process is given. */
    const val COLD_TIMEOUT = 20_000L

    /** Long enough that a tray with a habit of churning would have churned. */
    const val A_WHILE = 1_000L
  }
}
