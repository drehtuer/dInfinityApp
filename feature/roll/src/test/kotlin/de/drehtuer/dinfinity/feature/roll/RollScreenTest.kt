package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.filament.TrayView
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.HeadlessRenderer
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.render.headless.WatchedRoll
import de.drehtuer.dinfinity.simulation.api.DiceSimulator
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The roll screen, drawn (`design/dInfinity.dc.html`, options 1a–1j).
 *
 * What it can check is that the state reaches the screen: that a formula which
 * does not read is shown as an error rather than swallowed, that a refusal is
 * a sentence with numbers in it, that the button is dead until there is
 * something to throw, and that a total appears when the dice land. What it
 * cannot check is the tray, which needs a GPU — that is the device suite, and
 * ultimately Step 5.6 with a person.
 */
@RunWith(RobolectricTestRunner::class)
class RollScreenTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `a formula that does not read is shown as an error, not swallowed`() {
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6 +")

    compose.onNodeWithTag(RollTestTags.INVALID).assertExists()
    compose.onNodeWithTag(RollTestTags.THROW).assertIsNotEnabled()
  }

  @Test
  fun `a throw the table cannot hold says how many would fit`() {
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("500d6")

    compose.onNodeWithTag(RollTestTags.REFUSED).assertExists()
    compose.onNodeWithTag(RollTestTags.THROW).assertIsNotEnabled()
  }

  @Test
  fun `there is nothing to throw until something has been typed`() {
    show()

    compose.onNodeWithTag(RollTestTags.THROW).assertIsNotEnabled()
  }

  @Test
  fun `a formula that reads can be thrown, and the total arrives`() {
    show(faces = mapOf(0 to 5, 1 to 5, 2 to 5))

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6 + 4")
    compose.onNodeWithTag(RollTestTags.THROW).assertIsEnabled()
    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `while the dice are in the air the screen says so and the button is dead`() {
    show(land = false)

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6")
    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.ROLLING).assertExists()
    compose.onNodeWithTag(RollTestTags.THROW).assertIsNotEnabled()
  }

  @Test
  fun `after a total one press throws the same formula again`() {
    // One press, one roll. Putting the total away and then throwing was two
    // presses for one act, and a shake could never have expressed the first of
    // them anyway.
    show(faces = mapOf(0 to 0, 1 to 0, 2 to 0))
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6")
    compose.onNodeWithTag(RollTestTags.THROW).performClick()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()

    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
    compose.onNodeWithTag(RollTestTags.THROW).assertIsEnabled()
  }

  @Test
  fun `a tap on the picker row types the formula for you`() {
    // The row is not a second way to describe a roll: it edits the field, and
    // what comes out is a formula somebody could have typed
    // (`docs/architecture.md`, decision 31).
    show(faces = mapOf(0 to 0))

    // Scrolled to first, because ten dice at a touch target worth pressing do
    // not fit across a phone — which is why the row scrolls.
    compose.onNodeWithTag(RollTestTags.pickerDie("d20")).performScrollTo().performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertTextContains("1d20")
    compose.onNodeWithTag(RollTestTags.THROW).assertIsEnabled()
  }

  @Test
  fun `tapping twice asks for two of them and the badge says so`() {
    show()

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()
    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertTextContains("2d6")
    compose.onNodeWithTag(RollTestTags.pickerCount("d6"), useUnmergedTree = true).assertTextEquals("2")
  }

  @Test
  fun `a long press takes the last one off and empties the field`() {
    show()
    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performTouchInput { longClick() }

    compose.onNodeWithTag(RollTestTags.pickerCount("d6"), useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.THROW).assertIsNotEnabled()
  }

  @Test
  fun `typing puts the badge on the row, so both agree about the same roll`() {
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("4d6 + 1d20")

    compose.onNodeWithTag(RollTestTags.pickerCount("d6"), useUnmergedTree = true).assertTextEquals("4")
    compose.onNodeWithTag(RollTestTags.pickerCount("d20"), useUnmergedTree = true).assertTextEquals("1")
  }

  @Test
  fun `power-saving mode puts no tray on the screen at all`() {
    // Not a tray that draws nothing: no surface. A surface is a buffer the
    // compositor keeps, and what power-saving claims is that none of it exists
    // (`docs/architecture.md`, decision 38).
    compose.setContent { RollScreen(presenter = presenter(UndrawnTray(), LandingRolls(mapOf(0 to 0)))) }

    compose.onNodeWithTag(RollTestTags.SCREEN).assertExists()
    compose.onNodeWithTag(RollTestTags.TRAY).assertDoesNotExist()
  }

  @Test
  fun `power-saving still throws the dice, and the total arrives`() {
    compose.setContent {
      RollScreen(presenter = presenter(UndrawnTray(), LandingRolls(mapOf(0 to 0, 1 to 0, 2 to 0))))
    }

    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput("3d6")
    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `the screen honours a modifier its caller gives it`() {
    // Every other test lets the default stand, so without this the screen has
    // never once been drawn the way the navigation graph will draw it.
    showWith(Modifier.testTag(CALLER_TAG))

    compose.onNodeWithTag(CALLER_TAG).assertExists()
    compose.onNodeWithTag(RollTestTags.TRAY).assertExists()
  }

  @Test
  fun `the tray is on screen from the start, before anything is thrown`() {
    show()

    compose.onNodeWithTag(RollTestTags.SCREEN).assertExists()
    compose.onNodeWithTag(RollTestTags.TRAY).assertExists()
  }

  private fun show(
    faces: Map<Int, Int> = mapOf(0 to 0),
    land: Boolean = true,
  ) {
    val presenter =
      RollPresenter(
        machine =
          RollMachine(
            catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
            geometry = TableGeometry.referenceDevice(),
            table = TableLook(id = "plain", name = "Plain"),
            simulator =
              object : DiceSimulator {
                override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
              },
            seeds = { 1L },
            clock = { 0L },
          ),
        driver = if (land) DirectTray() else PendingTray(),
        rolls = LandingRolls(faces),
        toTheScreen = { it() },
      )
    compose.setContent { RollScreen(presenter = presenter) }
  }

  private fun showWith(modifier: Modifier) {
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        modifier = modifier,
      )
    }
  }

  private fun presenter(
    tray: Tray,
    rolls: Rolls,
  ) = RollPresenter(
    machine =
      RollMachine(
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        geometry = TableGeometry.referenceDevice(),
        table = TableLook(id = "plain", name = "Plain"),
        simulator =
          object : DiceSimulator {
            override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
          },
        seeds = { 1L },
        clock = { 0L },
      ),
    driver = tray,
    rolls = rolls,
    toTheScreen = { it() },
  )

  /** Throws the dice where it stands, so a click and its total are one act. */
  private companion object {
    const val CALLER_TAG = "caller:modifier"
  }

  /** A tray that throws the dice where it stands and says it draws nothing. */
  private class UndrawnTray : DirectTray() {
    override val draws: Boolean = false
  }

  private open class DirectTray : Tray {
    val shaken = mutableListOf<ShakeSample>()

    /** Every table this tray has been told about, in order. */
    val tabled = mutableListOf<Pair<TableGeometry, TableLook>>()

    /** Every view the player has asked for, in order. */
    val looked = mutableListOf<TrayView>()

    override fun surfaceAvailable(
      surface: Surface,
      width: Int,
      height: Int,
    ) = Unit

    override fun surfaceLost() = Unit

    override fun roll(
      start: (Renderer) -> WatchedRoll,
      onSettled: (SimulationOutcome) -> Unit,
    ) {
      val live = start(HeadlessRenderer())
      while (live.running) live.advance(SettleRule.TIMESTEP_SECONDS)
      live.outcome?.let(onSettled)
      live.close()
    }

    override fun shake(sample: ShakeSample) {
      shaken += sample
    }

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) {
      tabled += geometry to look
    }

    override fun look(view: TrayView) {
      looked += view
    }

    override fun clear() = Unit

    override fun close() = Unit
  }

  /** A tray that takes the throw and leaves the dice in the air. */
  private class PendingTray : Tray {
    val shaken = mutableListOf<ShakeSample>()

    /** Every table this tray has been told about, in order. */
    val tabled = mutableListOf<Pair<TableGeometry, TableLook>>()

    /** Every view the player has asked for, in order. */
    val looked = mutableListOf<TrayView>()

    override fun surfaceAvailable(
      surface: Surface,
      width: Int,
      height: Int,
    ) = Unit

    override fun surfaceLost() = Unit

    override fun roll(
      start: (Renderer) -> WatchedRoll,
      onSettled: (SimulationOutcome) -> Unit,
    ) {
      start(HeadlessRenderer())
    }

    override fun shake(sample: ShakeSample) {
      shaken += sample
    }

    override fun table(
      geometry: TableGeometry,
      look: TableLook,
    ) {
      tabled += geometry to look
    }

    override fun look(view: TrayView) {
      looked += view
    }

    override fun clear() = Unit

    override fun close() = Unit
  }

  /** A roll that lands on the given faces at the first frame. */
  private class LandingRolls(
    private val faces: Map<Int, Int>,
  ) : Rolls {
    override fun start(
      spec: ThrowSpec,
      watcher: Renderer,
    ): WatchedRoll =
      object : WatchedRoll {
        private var landed = false

        override val running: Boolean get() = !landed

        override val outcome: SimulationOutcome? get() = if (landed) SimulationOutcome(faces = faces) else null

        override fun advance(elapsedSeconds: Double): RenderFrame {
          landed = true
          return RenderFrame.still(
            spec.dice.indices.map { BodyTransform(it, Vector3(0.0, 0.0, 8.0), Quaternion.Identity) },
          )
        }

        override fun shake(sample: ShakeSample) = Unit

        override fun close() = Unit
      }
  }
}
