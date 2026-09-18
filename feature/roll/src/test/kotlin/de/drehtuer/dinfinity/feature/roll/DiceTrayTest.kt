package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The only gesture the tray has, driven by fingers rather than reasoned about.
 *
 * Two faults the phone found and no test could have caught, because nothing
 * anywhere drove this gesture: **one finger panned the camera**, which is the
 * finger reserved for picking a die up, and **the view went stale** — the
 * gesture kept a copy of its own, the renderer put the camera back at the
 * whole tray on every throw, and the first touch afterwards snapped it to
 * wherever the player had last left it.
 *
 * What a view *means* is `TrayViewTest`'s and is asked on a JVM. What is asked
 * here is only which fingers reach it and which view they start from.
 */
@RunWith(RobolectricTestRunner::class)
class DiceTrayTest {
  @get:Rule
  val compose = createComposeRule()

  /** What the player has asked to look at, in order. */
  private val seen = mutableListOf<TrayView>()

  /** Where the camera is — the caller's, exactly as [RollPresenter] holds it. */
  private val view = mutableStateOf(TrayView.Whole)

  @Test
  fun `one finger does not move the camera`() {
    // It belongs to picking a die up, and to the tap that deliberately does
    // not roll (`docs/physics-and-rendering.md`).
    tray(from = TrayView(zoom = CLOSE_IN))

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      down(0, center)
      moveTo(0, center + Offset(0f, -DRAG_PX))
      moveTo(0, center + Offset(0f, -DRAG_PX * 2))
      up(0)
    }

    assertEquals("one finger moved the camera: $seen", emptyList<TrayView>(), seen)
  }

  @Test
  fun `two fingers move the camera`() {
    tray(from = TrayView(zoom = CLOSE_IN))

    dragWithTwoFingers()

    assertTrue("two fingers moved nothing", seen.isNotEmpty())
    // Dragging up the screen looks further up the tray, which is `+x`.
    assertTrue("the view did not move up the tray: ${seen.last()}", seen.last().panAlongMm > 0.0)
  }

  @Test
  fun `fingers moving apart look closer`() {
    tray()

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      down(0, center + Offset(-SPREAD_PX, 0f))
      down(1, center + Offset(SPREAD_PX, 0f))
      updatePointerTo(0, center + Offset(-SPREAD_PX * WIDER, 0f))
      updatePointerTo(1, center + Offset(SPREAD_PX * WIDER, 0f))
      move()
      up(0)
      up(1)
    }

    assertTrue("a pinch outwards did not look closer: $seen", seen.isNotEmpty())
    assertTrue("a pinch outwards did not look closer: $seen", seen.last().zoom > 1.0)
  }

  @Test
  fun `the touch after a throw starts from the whole tray, not from where the player was`() {
    // The stale-view fault. A throw puts the camera back at the whole table —
    // the renderer does it to its copy, the presenter to the one the gesture
    // reads — and a gesture holding a copy of its own would carry on from the
    // corner the last roll was read in, snapping the camera the moment a
    // finger landed.
    tray(from = TrayView(zoom = CLOSE_IN))
    dragWithTwoFingers()
    assertTrue("nothing moved before the throw", seen.isNotEmpty())

    // The throw.
    compose.runOnIdle { view.value = TrayView.Whole }
    seen.clear()
    dragWithTwoFingers()

    assertTrue("the camera resumed from the stale zoom: $seen", seen.all { it.zoom < PART_WAY_IN })
    // And at the whole tray there is nowhere to pan to, so the drag moves it
    // nowhere rather than dragging on from where the last one stopped.
    assertEquals("the camera panned from a stale place", 0.0, seen.last().panAlongMm, NEARLY_NOTHING_MM)
  }

  private fun dragWithTwoFingers() {
    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      down(0, center + Offset(-SPREAD_PX, 0f))
      down(1, center + Offset(SPREAD_PX, 0f))
      updatePointerBy(0, Offset(0f, -DRAG_PX))
      updatePointerBy(1, Offset(0f, -DRAG_PX))
      move()
      up(0)
      up(1)
    }
  }

  /** A tray on screen, with the view hoisted the way the roll screen hoists it. */
  private fun tray(from: TrayView = TrayView.Whole) {
    view.value = from
    compose.setContent {
      DiceTray(
        driver = SilentTray(),
        geometry = TableGeometry.referenceDevice(),
        modifier = Modifier.requiredSize(WIDE.dp, HIGH.dp),
        view = view.value,
        onLook = {
          view.value = it
          seen += it
        },
      )
    }
    compose.waitForIdle()
  }

  /** A tray that draws nothing: the surface is not what is being asked about. */
  private class SilentTray : Tray {
    override fun surfaceAvailable(
      surface: Surface,
      width: Int,
      height: Int,
    ) = Unit

    override fun surfaceLost() = Unit

    override fun roll(
      start: (Renderer) -> WatchedRoll,
      onCounted: (Map<Int, Int>) -> Unit,
      onStalled: (List<Int>) -> Unit,
      onSettled: (SimulationOutcome, List<ShakeSample>) -> Unit,
    ) = Unit

    override fun waiting(spec: ThrowSpec) = Unit

    override fun shake(sample: ShakeSample) = Unit

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) = Unit

    override fun look(view: TrayView) = Unit

    override fun clear() = Unit

    override fun close() = Unit
  }

  private companion object {
    const val WIDE = 360
    const val HIGH = 720
    const val DRAG_PX = 80f
    const val SPREAD_PX = 40f
    const val WIDER = 3f
    const val CLOSE_IN = 2.0
    const val PART_WAY_IN = 1.5
    const val NEARLY_NOTHING_MM = 0.5
  }
}
