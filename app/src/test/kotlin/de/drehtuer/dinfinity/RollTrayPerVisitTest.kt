package de.drehtuer.dinfinity

import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.feature.roll.Outside
import de.drehtuer.dinfinity.feature.roll.RollMachine
import de.drehtuer.dinfinity.feature.roll.RollPresenter
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.theme.DInfinityTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * That one visit to the roll screen gets one tray.
 *
 * The screen hands its `Surface` to the tray it has when the surface is
 * *created*. Nothing hands it to a later one, so a tray built after that point
 * has nowhere to draw — and the dice roll, and settle, and are recorded, on a
 * tray nobody can see. That is what a player met on a cold launch: a black
 * rectangle with a total underneath it.
 *
 * The cause was the presenter being remembered against the lambda that builds
 * it rather than against the visit. That lambda is rebuilt whenever `settings`
 * changes, and on a cold launch it always changes — the defaults stand in
 * until the preferences file has been read, which is the one thing every
 * launch does.
 *
 * So the test changes a preference that has nothing to do with rolling and
 * counts the trays. The count is the whole assertion: a second tray is the
 * bug, whatever it is drawn on.
 */
@RunWith(RobolectricTestRunner::class)
class RollTrayPerVisitTest {
  @get:Rule
  val compose = createComposeRule()

  private val app = TestApp()

  @After
  fun close() = app.close()

  @Test
  fun `a preference arriving after launch does not build a second tray`() {
    var built = 0
    var settings by mutableStateOf(AppSettings())
    compose.setContent {
      DInfinityTheme {
        DInfinityApp(
          settings = settings,
          screens =
            app.presenters().copy(
              roll = {
                built++
                presenter()
              },
            ),
        )
      }
    }
    compose.waitForIdle()
    assertEquals("the screen opened without a tray", 1, built)

    // The preferences file has been read, and it does not say what the
    // defaults said. This is a cold launch, every time.
    settings = settings.copy(accentColor = AccentColor.ModernistRed)
    compose.waitForIdle()

    assertEquals("a preference arriving built a second tray, and the surface stayed with the first", 1, built)
  }

  @Test
  fun `several preferences arriving still do not build a second tray`() {
    // A real launch reads more than one. Each of them used to be its own tray.
    var built = 0
    var settings by mutableStateOf(AppSettings())
    compose.setContent {
      DInfinityTheme {
        DInfinityApp(
          settings = settings,
          screens =
            app.presenters().copy(
              roll = {
                built++
                presenter()
              },
            ),
        )
      }
    }
    compose.waitForIdle()

    settings = settings.copy(accentColor = AccentColor.ModernistRed)
    compose.waitForIdle()
    settings = settings.copy(shakeToRoll = false)
    compose.waitForIdle()
    settings = settings.copy(welcomeSeen = true)
    compose.waitForIdle()

    assertEquals("one visit to the screen built $built trays", 1, built)
  }

  private fun presenter(): RollPresenter =
    RollPresenter(
      machine =
        RollMachine(
          catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
          geometry = TableGeometry.referenceDevice(),
          look = { TableLook(id = "plain", name = "Plain") },
          outside = Outside(seeds = { 1L }, clock = { 0L }),
        ),
      driver = SilentTray(),
      rolls = Rolls { _, _ -> error("this test never throws anything") },
      toTheScreen = { it() },
    )

  /** A tray that is asked for nothing and answers nothing. The test counts trays; it does not use one. */
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

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) = Unit

    override fun shake(sample: ShakeSample) = Unit

    override fun look(view: TrayView) = Unit

    override fun clear() = Unit

    override fun close() = Unit
  }
}
