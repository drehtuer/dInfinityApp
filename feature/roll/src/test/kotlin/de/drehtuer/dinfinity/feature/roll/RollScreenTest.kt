package de.drehtuer.dinfinity.feature.roll

import android.view.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import de.drehtuer.dinfinity.core.model.SavedRollSource
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    typeFormula("3d6 +")

    compose.onNodeWithTag(RollTestTags.INVALID).assertExists()
    compose.onNodeWithTag(RollTestTags.THROW).assertIsNotEnabled()
  }

  @Test
  fun `a throw the table cannot hold says how many would fit`() {
    show()

    typeFormula("500d6")

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

    typeFormula("3d6 + 4")
    compose.onNodeWithTag(RollTestTags.THROW).assertIsEnabled()
    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `while the dice are in the air the screen says so and the button is dead`() {
    show(land = false)

    typeFormula("3d6")
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
    typeFormula("3d6")
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

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertTextContains("1d20")
    compose.onNodeWithTag(RollTestTags.THROW).assertIsEnabled()
  }

  @Test
  fun `tapping twice asks for two of them and the badge says so`() {
    show()

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()
    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertTextContains("2d6")
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

    typeFormula("4d6 + 1d20")

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

    typeFormula("3d6")
    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `a new install is welcomed, and the tray is behind it`() {
    compose.setContent {
      RollScreen(presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))), firstLaunch = true)
    }

    compose.onNodeWithTag(RollTestTags.WELCOME).assertExists()
  }

  @Test
  fun `an install that has been welcomed before is not welcomed again`() {
    show()

    compose.onNodeWithTag(RollTestTags.WELCOME).assertDoesNotExist()
  }

  @Test
  fun `the welcome's d20 is thrown for real, and is remembered as seen`() {
    // Not a demonstration and not a canned number: it types `1d20` into the
    // field and presses Roll, which is what the player would have done.
    val seen = mutableListOf<Unit>()
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        firstLaunch = true,
        onWelcomeSeen = { seen += Unit },
      )
    }

    compose.onNodeWithTag(RollTestTags.WELCOME_ROLL).performClick()

    compose.onNodeWithTag(RollTestTags.WELCOME).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertTextContains("1d20")
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
    assertEquals(1, seen.size)
  }

  @Test
  fun `going straight to the tray is remembered too`() {
    // A welcome that comes back is a welcome that was not read the first time.
    val seen = mutableListOf<Unit>()
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        firstLaunch = true,
        onWelcomeSeen = { seen += Unit },
      )
    }

    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    compose.onNodeWithTag(RollTestTags.WELCOME).assertDoesNotExist()
    assertEquals(1, seen.size)
  }

  @Test
  fun `an empty field says what to do rather than nothing`() {
    show()

    compose.onNodeWithTag(RollTestTags.HINT).assertTextEquals("Type a formula, or tap a die below.")
  }

  @Test
  fun `a throw that is ready says the part nobody would guess`() {
    // Shaking is not discoverable. The button is right there and says Roll.
    show()

    typeFormula("1d20")

    compose.onNodeWithTag(RollTestTags.HINT).assertTextEquals("Shake the phone, or press Roll.")
  }

  @Test
  fun `a hint gives way to whatever the screen has to say instead`() {
    show(faces = mapOf(0 to 0, 1 to 0, 2 to 0))
    typeFormula("3d6")

    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.HINT).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `the odds are offered for a throw that has landed, with its total`() {
    val asked = mutableListOf<Pair<String, Long?>>()
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0, 1 to 0, 2 to 0))),
        onSeeTheOdds = { formula, total -> asked += formula to total },
      )
    }
    typeFormula("3d6")
    compose.onNodeWithTag(RollTestTags.THROW).performClick()

    compose.onNodeWithTag(RollTestTags.ODDS).performClick()

    assertEquals(listOf("3d6" to 3L), asked)
  }

  @Test
  fun `the odds are offered for a throw the table refuses, which is when they matter most`() {
    // `500d6` cannot be rolled here. "What would it have been" is then the only
    // answer there is (`docs/probability.md`).
    val asked = mutableListOf<Pair<String, Long?>>()
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        onSeeTheOdds = { formula, total -> asked += formula to total },
      )
    }
    typeFormula("500d6")

    compose.onNodeWithTag(RollTestTags.ODDS).performClick()

    assertEquals(listOf("500d6" to null), asked)
  }

  @Test
  fun `a formula that does not read is not offered odds on itself`() {
    show()

    typeFormula("3d6 +")

    compose.onNodeWithTag(RollTestTags.ODDS).assertDoesNotExist()
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

  @Test
  fun `there is no set chooser until there is a second set to choose`() {
    // A chooser with one entry is furniture, which is the rule every other
    // chooser in the app follows.
    show()

    compose.onNodeWithTag(RollTestTags.SETS).assertDoesNotExist()
  }

  @Test
  fun `with two sets installed the row says which one it is offering`() {
    val presenter = show(catalog = twoSets())

    compose.onNodeWithTag(RollTestTags.SETS).assertExists()
    compose.onNodeWithTag(RollTestTags.setOf(BRASS)).performScrollTo().performClick()

    compose.waitUntil(PATIENCE) { presenter.pickingFrom == BRASS }
    compose.onNodeWithTag(RollTestTags.pickerDie("d20")).performScrollTo().performClick()
    assertEquals("$BRASS:1d20", presenter.text)
  }

  @Test
  fun `a tap on the strip throws that saved roll, and says which one it was`() {
    // The slot is how the roll screen is handed saved rolls without knowing
    // what one is. What it hands back is the formula *and* which roll put it
    // there, so the throw can be recorded as that roll's
    // (`docs/statistics.md`, per saved roll and per group).
    lateinit var presenter: RollPresenter
    compose.setContent {
      presenter = remember { presenter(DirectTray(), LandingRolls(mapOf(0 to 0))) }
      RollScreen(
        presenter = presenter,
        strip = { rollIt ->
          Button(
            onClick = { rollIt("1d20", SavedRollSource(rollId = "fireball", groupId = "thorin")) },
            modifier = Modifier.testTag(STRIP_TAG),
          ) { Text("Fireball") }
        },
      )
    }

    compose.onNodeWithTag(STRIP_TAG).performClick()

    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    assertEquals("1d20", presenter.text)
  }

  @Test
  fun `a typed formula thrown from the slot belongs to no saved roll`() {
    // The other side of the slot: a caller with nothing to attribute passes
    // none, and the screen takes the ordinary path.
    lateinit var presenter: RollPresenter
    compose.setContent {
      presenter = remember { presenter(DirectTray(), LandingRolls(mapOf(0 to 0))) }
      RollScreen(
        presenter = presenter,
        strip = { rollIt ->
          Button(onClick = { rollIt("1d6", null) }, modifier = Modifier.testTag(STRIP_TAG)) { Text("Two") }
        },
      )
    }

    compose.onNodeWithTag(STRIP_TAG).performClick()

    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    assertEquals("1d6", presenter.text)
  }

  private fun twoSets(): DiceCatalog =
    DiceCatalog.of(
      listOf(BuiltinDiceSet.set, BuiltinDiceSet.set.copy(id = BRASS, name = "Brass")),
      BuiltinDiceSet.set.id,
    )

  @Test
  fun `a recomposition around it that changes nothing leaves the screen alone`() {
    // Every parameter of a composable is a branch that says "nothing changed,
    // skip it", and this screen has more parameters than any other. A test
    // that draws once only ever takes one side of each; one that skipped
    // wrongly would come back without its tray or its picker row, which a
    // single pass would never see (`docs/TODO.md`, Coverage).
    var tick by mutableStateOf(0)
    val presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0)))
    compose.setContent {
      Column {
        Text("tick $tick")
        RollScreen(
          presenter = presenter,
          firstLaunch = true,
          whatIsThere = WhatIsThere(sets = 1),
        )
      }
    }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertIsDisplayed()
  }

  @Test
  fun `the welcome's counts reach it from outside, because it cannot know them`() {
    // `feature/roll` does not know what a saved roll or a session is, so the
    // two counts arrive as a `WhatIsThere` the way the strip arrives as a slot
    // (`docs/architecture.md`, "Modules").
    val presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0)))
    compose.setContent {
      RollScreen(presenter = presenter, firstLaunch = true, whatIsThere = WhatIsThere(savedRolls = 5, sessions = 2))
    }

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("5 saved rolls", substring = true)
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("2 sessions", substring = true)
  }

  @Test
  fun `a screen wired to nothing still draws and still works`() {
    // Every one of this screen's callbacks has a default that does nothing, so
    // it can be put on screen by a preview or by a test of something around
    // it. Pressing them is not an error — and the defaults are the branch a
    // test of the wired screen never takes.
    val presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0)))
    compose.setContent { RollScreen(presenter = presenter, firstLaunch = true) }

    compose.onNodeWithTag(RollTestTags.WELCOME_IMPORT).performClick()
    compose.onNodeWithTag(RollTestTags.WELCOME_SETS_ADD).performClick()
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    compose.onNodeWithTag(RollTestTags.WELCOME).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertIsDisplayed()
  }

  @Test
  fun `and its odds button goes nowhere rather than failing`() {
    val presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0)))
    compose.setContent { RollScreen(presenter = presenter) }
    typeFormula("1d20")

    compose.onNodeWithTag(RollTestTags.ODDS).performClick()

    compose.onNodeWithTag(RollTestTags.ODDS).assertIsDisplayed()
  }

  @Test
  fun `the tray shows the formula rather than a field, until it is tapped`() {
    // A field is a thing to fill in; the formula is a thing somebody has
    // written (`design/dInfinity.dc.html`, option 2a).
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
  }

  @Test
  fun `and there is always something to tap, even with nothing typed`() {
    // Without the hint standing in, a fresh install shows a tray, a row of
    // dice and a blank space where the formula goes.
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertTextContains("3d6", substring = true)
  }

  @Test
  fun `tapping it brings the field up`() {
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertIsDisplayed()
  }

  @Test
  fun `Enter throws the dice and puts the keyboard away`() {
    // The other half of 2a: the action key rolls. It closes the editor first,
    // so what the dice land on is not behind a keyboard.
    val presenter = show()
    typeFormula("1d20")

    compose.onNodeWithTag(RollTestTags.FORMULA).performImeAction()

    compose.waitUntil(PATIENCE) { presenter.state is RollState.Settled }
    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertIsDisplayed()
  }

  @Test
  fun `Enter on a formula that does not read throws nothing`() {
    // `throwDice` refuses it, which is the machine's rule rather than the
    // screen's — what is asserted here is that the screen does not pretend
    // otherwise by closing the editor on a formula nobody can roll.
    val presenter = show()
    typeFormula("3d6 +")

    compose.onNodeWithTag(RollTestTags.FORMULA).performImeAction()

    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
    assertTrue("a formula that does not read was thrown", presenter.state is RollState.Invalid)
  }

  @Test
  fun `the line is marked when the formula does not read`() {
    // The badge `9c` asks for. What exactly is wrong is said in the editor,
    // under the squiggle, because that is where somebody can fix it.
    show()
    typeFormula("3d6 +")

    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.INVALID).assertIsDisplayed()
  }

  /**
   * Types a formula the way a player does: tap the line, then type.
   *
   * The field is not on the tray until somebody asks for it
   * (`design/dInfinity.dc.html`, option 2a), so every test that types goes
   * through the tap — which is also the only way the tap stays tested.
   */
  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_LINE).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
  }

  private fun show(
    faces: Map<Int, Int> = mapOf(0 to 0),
    land: Boolean = true,
    catalog: DiceCatalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
  ): RollPresenter {
    val presenter =
      RollPresenter(
        machine =
          RollMachine(
            catalog = catalog,
            geometry = TableGeometry.referenceDevice(),
            look = { TableLook(id = "plain", name = "Plain") },
            simulator =
              object : DiceSimulator {
                override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
              },
            outside = Outside(seeds = { 1L }, clock = { 0L }),
          ),
        driver = if (land) DirectTray() else PendingTray(),
        rolls = LandingRolls(faces),
        toTheScreen = { it() },
      )
    compose.setContent { RollScreen(presenter = presenter) }
    return presenter
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
        look = { TableLook(id = "plain", name = "Plain") },
        simulator =
          object : DiceSimulator {
            override fun run(spec: ThrowSpec) = SimulationOutcome(faces = spec.dice.indices.associateWith { 0 })
          },
        outside = Outside(seeds = { 1L }, clock = { 0L }),
      ),
    driver = tray,
    rolls = rolls,
    toTheScreen = { it() },
  )

  /** Throws the dice where it stands, so a click and its total are one act. */
  private companion object {
    const val CALLER_TAG = "caller:modifier"

    /** A second installed set, which is when the chooser is worth drawing. */
    const val BRASS = "brass"
    const val STRIP_TAG = "test:strip"
    const val PATIENCE = 2_000L
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
