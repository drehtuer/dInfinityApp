package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.simulation.api.TableGeometry
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

  /** Every tap the tray reported, as fractions across and down it and its shape. */
  private val tapped = mutableListOf<Triple<Double, Double, Double>>()

  /** How many double taps the tray reported. */
  private var doubled = 0

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
    // **The felt comes with the fingers.** Tray `+x` is screen-up, so a target
    // moved along `+x` puts a further-up part of the table in the middle and
    // the felt appears to slide *down*. Fingers going up must therefore take
    // the target the other way — which is the sign the second device session
    // reported as inverted, and this is the assertion that was agreeing with
    // it (`docs/physics-and-rendering.md`, "Rendering").
    assertTrue("the table did not come up with the fingers: ${seen.last()}", seen.last().panAlongMm < 0.0)
  }

  @Test
  fun `and they take it the way they went, not the other way`() {
    // The whole of the bug in one test: the two directions have to move the
    // table opposite ways. A sign error passes every "it moved" assertion.
    tray(from = TrayView(zoom = CLOSE_IN))

    dragWithTwoFingers()
    val afterUp = seen.last().panAlongMm

    dragWithTwoFingers(by = DRAG_PX)
    val afterDown = seen.last().panAlongMm

    assertTrue("fingers going up did not take the table up: $afterUp", afterUp < 0.0)
    assertTrue("fingers going back down did not bring it back: $afterUp then $afterDown", afterDown > afterUp)
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

  @Test
  fun `one finger that comes down and lifts again is a tap, at the point it touched`() {
    // A finger on a die (decision 76). Reported as fractions of the tray and
    // its shape, which is what `TrayPick` reads a finger with.
    tray()

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput { click(Offset(width / 4f, height * 3 / 4f)) }

    val (across, down, ratio) = tapped.single()
    assertEquals(QUARTER, across, FRACTION)
    assertEquals(THREE_QUARTERS, down, FRACTION)
    assertEquals(WIDE.toDouble() / HIGH, ratio, FRACTION)
    assertEquals("a tap moved the camera", emptyList<TrayView>(), seen)
  }

  @Test
  fun `a finger that wanders, one held down and two fingers are not taps`() {
    tray()

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      down(0, center)
      moveTo(0, center + Offset(0f, -DRAG_PX))
      up(0)
    }
    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      down(0, center)
      advanceEventTime(viewConfiguration.longPressTimeoutMillis * 2)
      up(0)
    }
    dragWithTwoFingers()

    assertTrue("something that was not a tap picked a die: $tapped", tapped.isEmpty())
  }

  @Test
  fun `two taps in quick succession are a double tap and neither of them is a tap`() {
    // The first half of a double tap must not pick a die and the second put
    // it back (decision 83).
    tray(doubleTaps = true)

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      click(center)
      advanceEventTime(viewConfiguration.doubleTapMinTimeMillis * 2)
      click(center + Offset(DRAG_PX, DRAG_PX))
    }
    compose.mainClock.advanceTimeBy(PAST_A_DOUBLE_TAP)
    compose.waitForIdle()

    assertEquals(1, doubled)
    assertTrue("half of a double tap picked a die: $tapped", tapped.isEmpty())
  }

  @Test
  fun `with double taps listened for, a single tap is still a tap once the timeout has passed`() {
    tray(doubleTaps = true)

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput { click(Offset(width / 4f, height * 3 / 4f)) }
    compose.mainClock.advanceTimeBy(PAST_A_DOUBLE_TAP)
    compose.waitForIdle()

    val (across, down, _) = tapped.single()
    assertEquals(QUARTER, across, FRACTION)
    assertEquals(THREE_QUARTERS, down, FRACTION)
    assertEquals(0, doubled)
  }

  @Test
  fun `a tap followed by a pinch is a tap and a pinch, not a double tap`() {
    tray(from = TrayView(zoom = CLOSE_IN), doubleTaps = true)

    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      click(center)
      advanceEventTime(viewConfiguration.doubleTapMinTimeMillis * 2)
      down(0, center + Offset(-SPREAD_PX, 0f))
      down(1, center + Offset(SPREAD_PX, 0f))
      updatePointerBy(0, Offset(0f, -DRAG_PX))
      updatePointerBy(1, Offset(0f, -DRAG_PX))
      move()
      up(0)
      up(1)
    }
    compose.waitForIdle()

    assertEquals(0, doubled)
    assertEquals("the tap before the pinch was lost", 1, tapped.size)
    assertTrue("the pinch after a tap did not move the camera", seen.isNotEmpty())
  }

  @Test
  fun `the tray carries no action it is not handed`() {
    tray()

    assertTrue(
      compose
        .onNodeWithTag(RollTestTags.TRAY)
        .fetchSemanticsNode()
        .config
        .getOrElse(SemanticsActions.CustomActions) { emptyList() }
        .isEmpty(),
    )
  }

  @Test
  fun `and carries the ones it is handed`() {
    var asked = 0
    compose.setContent {
      DiceTray(
        driver = SilentTray(),
        geometry = TableGeometry.referenceDevice(),
        modifier = Modifier.requiredSize(WIDE.dp, HIGH.dp),
        actions =
          listOf(
            CustomAccessibilityAction("Hide the controls") {
              asked++
              true
            },
          ),
      )
    }
    val action =
      compose
        .onNodeWithTag(RollTestTags.TRAY)
        .fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .single()
    compose.runOnIdle { action.action() }

    assertEquals("Hide the controls", action.label)
    assertEquals(1, asked)
  }

  /** Two fingers dragged [by] pixels down the screen; negative goes up. */
  private fun dragWithTwoFingers(by: Float = -DRAG_PX) {
    compose.onNodeWithTag(RollTestTags.TRAY).performTouchInput {
      down(0, center + Offset(-SPREAD_PX, 0f))
      down(1, center + Offset(SPREAD_PX, 0f))
      updatePointerBy(0, Offset(0f, by))
      updatePointerBy(1, Offset(0f, by))
      move()
      up(0)
      up(1)
    }
  }

  /** A tray on screen, with the view hoisted the way the roll screen hoists it. */
  private fun tray(
    from: TrayView = TrayView.Whole,
    doubleTaps: Boolean = false,
  ) {
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
        onTap = { across, down, ratio -> tapped += Triple(across, down, ratio) },
        onDoubleTap = if (doubleTaps) ({ doubled++ }) else null,
      )
    }
    compose.waitForIdle()
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
    const val QUARTER = 0.25
    const val THREE_QUARTERS = 0.75
    const val FRACTION = 0.01

    /** Twice the platform's double-tap timeout, which is what a lone tap waits out. */
    val PAST_A_DOUBLE_TAP: Long = android.view.ViewConfiguration.getDoubleTapTimeout() * 2L
  }
}
