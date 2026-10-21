package de.drehtuer.dinfinity.feature.roll

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.drehtuer.dinfinity.simulation.api.ContactPoint
import de.drehtuer.dinfinity.simulation.api.DieDiagnostic
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.Struck
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The debug overlay's plan of the tray, as pixels
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Which tint a die gets is `TrayPlanTest`'s, and the counts are said in words
 * (`DebugOverlayTest`). What neither can see is the colour each tint is drawn
 * in — and that is the fault the overlay already had once: the palette has one
 * red, the theme maps `error` onto it, and "at rest" and "standing on another
 * die" both came out in it, so the one state that should shout was the one
 * that did not. Captured rather than asserted on semantics, because a colour is
 * the one thing the semantics tree cannot show.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrayPlanInkTest {
  @get:Rule
  val compose = createComposeRule()

  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `a die at rest is filled in the ink and one standing on another in the accent`() {
    val still = die(0, x = FAR_END)
    val stacked = die(1, x = NEAR_END, stacked = true)

    val plan = draw(RollDiagnostics(dice = listOf(still, stacked)))

    assertEquals("a die at rest is not the ink", INK, plan.middleOf(still))
    assertEquals("a stacked die is not the accent", ACCENT, plan.middleOf(stacked))
  }

  @Test
  fun `a contact with another die is a dot in the accent, and one with the floor is not`() {
    // The die-on-die contact is the one somebody turned the overlay on to
    // find; a floor contact is every die landing, and is drawn in the muted
    // ink so the other stands out against it.
    val onADie = contact(x = FAR_END, struck = Struck.Die)
    val onTheFloor = contact(x = NEAR_END, struck = Struck.Floor)

    val plan = draw(RollDiagnostics(contacts = listOf(onADie, onTheFloor)))

    assertEquals(ACCENT, plan.at(onADie))
    assertNotEquals("a floor contact was drawn as if it were trouble", ACCENT, plan.at(onTheFloor))
  }

  private fun draw(diagnostics: RollDiagnostics): PixelMap {
    compose.setContent {
      MaterialTheme(colorScheme = lightColorScheme(onSurface = INK, error = ACCENT)) {
        DebugOverlay(diagnostics = diagnostics, geometry = geometry)
      }
    }
    return compose.onNodeWithTag(DebugTestTags.PLAN, useUnmergedTree = true).captureToImage().toPixelMap()
  }

  /** The pixel in the middle of [die]'s box, where its fill is. */
  private fun PixelMap.middleOf(die: DieDiagnostic): Color {
    val mark = TrayPlan.markOf(die, geometry)
    return this[
      ((mark.left + mark.width / 2) * width).roundToInt(),
      ((mark.top + mark.height / 2) * height).roundToInt(),
    ]
  }

  /** The pixel at the centre of [contact]'s dot. */
  private fun PixelMap.at(contact: ContactPoint): Color {
    val mark = TrayPlan.markOf(contact, geometry)
    return this[(mark.across * width).roundToInt(), (mark.along * height).roundToInt()]
  }

  private fun die(
    index: Int,
    x: Double,
    stacked: Boolean = false,
  ): DieDiagnostic =
    DieDiagnostic(
      index = index,
      position = Vector3(x, 0.0, 8.0),
      acrossMm = 16.0,
      stillForSteps = SettleRule.REST_STEPS,
      atRest = true,
      supportedByDie = stacked,
    )

  private fun contact(
    x: Double,
    struck: Struck,
  ): ContactPoint =
    ContactPoint(
      stepIndex = 0,
      dieIndex = 0,
      position = Vector3(x, 0.0, 8.0),
      struck = struck,
      strength = 1.0,
    )

  private companion object {
    /** Far enough up and down the tray that two dice there do not touch. */
    const val FAR_END = 40.0
    const val NEAR_END = -40.0

    val INK = Color(0xFF14213D)
    val ACCENT = Color(0xFFD62828)
  }
}
