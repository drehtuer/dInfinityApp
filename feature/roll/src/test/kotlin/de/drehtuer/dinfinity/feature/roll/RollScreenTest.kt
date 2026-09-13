package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.Tray
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

  private class DirectTray : Tray {
    val shaken = mutableListOf<ShakeSample>()

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

    override fun clear() = Unit

    override fun close() = Unit
  }

  /** A tray that takes the throw and leaves the dice in the air. */
  private class PendingTray : Tray {
    val shaken = mutableListOf<ShakeSample>()

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
