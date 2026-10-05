package de.drehtuer.dinfinity.feature.roll

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.render.filament.Tray
import de.drehtuer.dinfinity.render.headless.Rolls
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  @get:Rule
  val shaking = ShakingHand()

  @Test
  fun `a formula that does not read is shown as an error, not swallowed`() {
    show()

    typeFormula("3d6 +")

    compose.onNodeWithTag(RollTestTags.INVALID).assertExists()
    assertFalse("a formula that does not read was thrown", shake())
  }

  @Test
  fun `a throw the table cannot hold says how many would fit`() {
    show()

    typeFormula("500d6")

    compose.onNodeWithTag(RollTestTags.REFUSED).assertExists()
    assertFalse("a throw the table cannot hold was thrown", shake())
  }

  @Test
  fun `there is nothing to throw until something has been typed`() {
    show()

    assertFalse("an empty field threw something", shake())
  }

  @Test
  fun `while the dice are in the air the screen says so and a second shake throws nothing`() {
    // A second throw would replace the first mid-flight, which is not what a
    // second shake means — those moments go to the dice already in the air
    // (`docs/physics-and-rendering.md`, "Shake input").
    show(land = false)

    typeFormula("3d6")
    shake()

    compose.onNodeWithTag(RollTestTags.ROLLING).assertExists()
    assertFalse("a second shake replaced a roll in the air", shake())
  }

  @Test
  fun `after a total one shake throws the same formula again`() {
    // One shake, one roll. Putting the total away and then throwing was two
    // acts for one, and a shake could never have expressed the first of them
    // anyway.
    show(faces = mapOf(0 to 0, 1 to 0, 2 to 0))
    typeFormula("3d6")
    shake()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()

    assertTrue("a settled roll could not be thrown again", shake())

    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `a long press on a die in the sheet offers to draw on it`() {
    // Quick mode through the whole screen rather than through the breakdown on
    // its own: the sheet the chip is in comes up from the bottom edge now, and
    // a long press on something that is still moving is a drag
    // (`docs/face-designer.md`, "Quick mode"; `PullUpResult`).
    show(faces = mapOf(0 to 0))
    typeFormula("1d20")
    shake()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertIsDisplayed()

    compose.onNodeWithTag(RollTestTags.dieAt(0)).performTouchInput { longClick() }

    compose.onNodeWithTag(RollTestTags.doodleOf(0)).assertIsDisplayed()
  }

  @Test
  fun `a tap on the picker row types the formula for you`() {
    // The row is not a second way to describe a roll: it edits the field, and
    // what comes out is a formula somebody could have typed
    // (`docs/architecture.md`, decision 31).
    show(faces = mapOf(0 to 0))
    openDice()

    // Scrolled to first, because ten dice at a touch target worth pressing do
    // not fit across a phone — which is why the row scrolls.
    compose.onNodeWithTag(RollTestTags.pickerDie("d20")).performScrollTo().performClick()

    theFormula().assertTextContains("1d20")
    assertTrue("the die the row typed could not be thrown", shake())
  }

  @Test
  fun `tapping twice asks for two of them and the badge says so`() {
    show()
    openDice()

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()
    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()

    compose.onNodeWithTag(RollTestTags.pickerCount("d6"), useUnmergedTree = true).assertTextEquals("2")
    theFormula().assertTextContains("2d6")
  }

  @Test
  fun `a long press takes the last one off and empties the field`() {
    show()
    openDice()
    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performClick()

    compose.onNodeWithTag(RollTestTags.pickerDie("d6")).performTouchInput { longClick() }

    compose.onNodeWithTag(RollTestTags.pickerCount("d6"), useUnmergedTree = true).assertDoesNotExist()
    assertFalse("an empty field threw something", shake())
  }

  @Test
  fun `typing puts the badge on the row, so both agree about the same roll`() {
    show()

    typeFormula("4d6 + 1d20")
    // The editor and the dice cannot both be open, so this is also the check
    // that opening one puts the other away.
    openDice()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()

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
  fun `and it says so, rather than leaving a blank where the table was`() {
    // The fault this is the fix for: no surface, no panel, and a total
    // arriving on an empty screen — which is what a renderer that has failed
    // looks like, and what the first session on a phone spent twenty minutes
    // believing it was looking at (`docs/physics-and-rendering.md`).
    compose.setContent { RollScreen(presenter = presenter(UndrawnTray(), LandingRolls(mapOf(0 to 0)))) }

    compose.onNodeWithTag(RollTestTags.POWER_SAVING).assertExists()
  }

  @Test
  fun `a tray that draws needs no notice that it is not drawing`() {
    compose.setContent { RollScreen(presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0)))) }

    compose.onNodeWithTag(RollTestTags.POWER_SAVING).assertDoesNotExist()
  }

  @Test
  fun `while the dice are being read the screen says how far it has got`() {
    // The dice leave the table as they are counted, so the count and the range
    // are what a player follows instead of them (`docs/TODO.md`, Step 5.5).
    val tray = CountingTray(mapOf(0 to 5, 1 to 5))
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }

    typeFormula("4d6")
    shake()

    compose.onNodeWithTag(RollTestTags.COUNTING).assertExists()
  }

  @Test
  fun `a roll nobody has counted anything in still says it is rolling`() {
    val tray = PendingTray()
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }

    typeFormula("4d6")
    shake()

    compose.onNodeWithTag(RollTestTags.ROLLING).assertExists()
    compose.onNodeWithTag(RollTestTags.COUNTING).assertDoesNotExist()
  }

  @Test
  fun `a roll that gave up says so on screen and asks for a shake`() {
    // Rather than reading them off whatever face they were nearest, which is
    // the one thing this app may not do (`docs/physics-and-rendering.md`).
    // There is no button on the plate any more: the dice go back in the air
    // the same way they went into it.
    val tray = StallingTray(unsettled = listOf(1, 2))
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }

    typeFormula("4d6")
    shake()

    compose.onNodeWithTag(RollTestTags.STALLED).assertExists()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
  }

  @Test
  fun `and says over the tray how many of them the shake will throw`() {
    // The plate says it too; the prompt over the tray is the part a screen
    // reader is told about without being asked (decision 84).
    val tray = StallingTray(unsettled = listOf(1, 2))
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }

    typeFormula("4d6")
    shake()

    compose
      .onNodeWithTag(
        RollTestTags.SHAKE_PROMPT_TEXT,
        useUnmergedTree = true,
      ).assertTextEquals("Shake to re-throw 2 dice")
  }

  @Test
  fun `one die that never settled is one die, not one dice`() {
    val tray = StallingTray(unsettled = listOf(1))
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }

    typeFormula("4d6")
    shake()

    compose
      .onNodeWithTag(
        RollTestTags.SHAKE_PROMPT_TEXT,
        useUnmergedTree = true,
      ).assertTextEquals("Shake to re-throw 1 die")
  }

  @Test
  fun `a shake throws the dice that never settled`() {
    val tray = StallingTray(unsettled = listOf(1, 2))
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }
    typeFormula("4d6")
    shake()
    val thrown = tray.throws

    assertTrue("the stalled dice would not go back in the air", shake())

    assertEquals("the shake threw nothing", thrown + 1, tray.throws)
  }

  @Test
  fun `a roll that gave up can be cancelled rather than thrown again`() {
    // The plate offers both, and a player who does not want those dice back
    // needs a way out that is not "type something else"
    // (`docs/physics-and-rendering.md`, "What is drawn over the table").
    val tray = StallingTray(unsettled = listOf(1, 2))
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }
    typeFormula("4d6")
    shake()
    val thrown = tray.throws

    compose.onNodeWithTag(RollTestTags.STALLED_CANCEL).performClick()

    compose.onNodeWithTag(RollTestTags.STALLED).assertDoesNotExist()
    // Cancelled, not re-thrown: the formula is back on the tray ready to go.
    assertEquals("cancelling threw something", thrown, tray.throws)
    compose.onNodeWithTag(RollTestTags.HINT).assertExists()
  }

  @Test
  fun `a formula puts its dice on the board before anybody throws them`() {
    val tray = DirectTray()
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }

    typeFormula("3d6")

    assertEquals("the dice were not put on the table", 3, tray.boards.last().size)
  }

  @Test
  fun `the board is not rebuilt for every keystroke`() {
    // It runs on every one of them, and building the bodies each time is a tray
    // that flickers while somebody types.
    val tray = DirectTray()
    compose.setContent { RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0)))) }

    typeFormula("3d6")
    val afterTyping = tray.boards.size
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(" ")

    assertEquals("the board was redrawn for a keystroke that changed no dice", afterTyping, tray.boards.size)
  }

  @Test
  fun `leaving the screen gives the tray back`() {
    val tray = DirectTray()
    var open by mutableStateOf(true)
    compose.setContent {
      if (open) RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0))))
    }

    assertEquals("a tray was given back before anybody left", 0, tray.closes)
    open = false
    compose.waitForIdle()

    assertEquals(1, tray.closes)
  }

  @Test
  fun `leaving the screen gives a power-saving tray back too`() {
    // The one that was missed. Closing used to live in `DiceTray`, which is on
    // the screen only when there is something to draw — so in this mode nothing
    // closed the tray at all, and a roll the player walked out on ran to the
    // end and was written into the history for a screen nobody was on
    // (`docs/TODO.md`, Step 5.3).
    val tray = UndrawnTray()
    var open by mutableStateOf(true)
    compose.setContent {
      if (open) RollScreen(presenter = presenter(tray, LandingRolls(mapOf(0 to 0))))
    }

    open = false
    compose.waitForIdle()

    assertEquals("a power-saving tray was left running", 1, tray.closes)
  }

  @Test
  fun `power-saving still throws the dice, and the total arrives`() {
    compose.setContent {
      RollScreen(presenter = presenter(UndrawnTray(), LandingRolls(mapOf(0 to 0, 1 to 0, 2 to 0))))
    }

    typeFormula("3d6")
    shake()

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
  fun `the welcome's d20 is put on the table, and the welcome is remembered as seen`() {
    // Not a demonstration and not a canned number: it types `1d20` into the
    // field and gets out of the way, and the throw is the shake the player
    // makes (`docs/physics-and-rendering.md`, "Starting a roll").
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
    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
    theFormula().assertTextContains("1d20")
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    assertEquals(1, seen.size)

    // And it is a real throw when the hand comes: there is no other path.
    shake()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
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
  fun `a shake while the welcome is up is not heard, so nothing lands behind it`() {
    // A formula can reach the tray while the welcome covers it — a saved roll
    // opened on the way back from an import. A shake then used to throw it and
    // draw the breakdown under the welcome's buttons (`docs/architecture.md`,
    // decision 74).
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        firstLaunch = true,
        openWith = "1d20",
      )
    }

    assertFalse("a screen under the welcome is listening for a shake", compose.runOnIdle { shaking.hand.heard })
    assertFalse("a shake under the welcome threw", shake())
    compose.onNodeWithTag(RollTestTags.WELCOME).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.ROLLING).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
  }

  @Test
  fun `pressing past the welcome finds the formula waiting, and the next shake throws it`() {
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        firstLaunch = true,
        openWith = "1d20",
      )
    }
    shake()

    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    assertTrue("the tray is not listening once the welcome has gone", compose.runOnIdle { shaking.hand.heard })
    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
    theFormula().assertTextContains("1d20")
    assertTrue("the first shake after the welcome threw nothing", shake())
    compose.onNodeWithTag(RollTestTags.TOTAL).assertExists()
  }

  @Test
  fun `a welcome pressed past stays gone across a rotation, and the tray still hears the hand`() {
    // Whether it has been pressed past is saved state, and it is what decides
    // whether a recreated screen listens: a rotation that brought the welcome
    // back, or came back deaf, would both read as broken.
    val restore = StateRestorationTester(compose)
    restore.setContent {
      RollScreen(presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))), firstLaunch = true)
    }
    assertFalse(compose.runOnIdle { shaking.hand.heard })
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    restore.emulateSavedInstanceStateRestore()

    compose.onNodeWithTag(RollTestTags.WELCOME).assertDoesNotExist()
    assertTrue("a restored tray is not listening for a shake", compose.runOnIdle { shaking.hand.heard })
  }

  @Test
  fun `an empty tray says nothing at all, and draws nothing over the felt`() {
    // `Type a formula, or open Dice at the top.` stood here and is gone: it
    // pointed at a formula that is no longer on the table, and it was a plate
    // over the felt in the one state where the felt is all there is.
    show()

    compose.onNodeWithTag(RollTestTags.HINT).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.DICE_MENU).assertIsDisplayed()
  }

  @Test
  fun `a throw that is ready says the part nobody would guess`() {
    // Shaking is not discoverable, and now that the button has gone it is the
    // only way in — so the words are the whole of the affordance.
    show()

    typeFormula("1d20")

    compose.onNodeWithTag(RollTestTags.HINT).assertTextEquals("Shake the phone to roll.")
  }

  @Test
  fun `and what the throw is expected to come to, before it is made`() {
    // What the Roll button's label used to be spent on. A player about to
    // shake wants the shape of the throw, not the word "Roll" (`Expectation`).
    show()

    typeFormula("3d6 + 4")

    compose
      .onNodeWithTag(RollTestTags.EXPECTED)
      .assertContentDescriptionEquals("Expected 7 to 22, average avg 14.5")
  }

  @Test
  fun `a hint gives way to whatever the screen has to say instead`() {
    show(faces = mapOf(0 to 0, 1 to 0, 2 to 0))
    typeFormula("3d6")

    shake()

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
    shake()

    compose.onNodeWithTag(RollTestTags.ODDS).performClick()

    assertEquals(listOf("3d6" to 3L), asked)
  }

  @Test
  fun `the result also offers to save the formula as a roll`() {
    // `:feature:roll` does not know what a saved roll is, so what goes out is
    // the formula and nothing else (`docs/architecture.md`, "Modules").
    val asked = mutableListOf<String>()
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0, 1 to 0, 2 to 0))),
        onSaveAsRoll = { formula -> asked += formula },
      )
    }
    typeFormula("3d6")
    shake()

    compose.onNodeWithTag(RollTestTags.SAVE_AS_ROLL).performClick()

    assertEquals(listOf("3d6"), asked)
  }

  @Test
  fun `neither is offered until a throw has landed, because both are the result's`() {
    // They used to be plates in the column of controls, offered from `Ready`
    // and from a refusal as well. They are the result sheet's now, which is
    // what the device session asked for — and a sheet only exists once the
    // dice have been read (`docs/physics-and-rendering.md`, "What is drawn
    // over the table").
    show()

    typeFormula("3d6")

    compose.onNodeWithTag(RollTestTags.ODDS).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.SAVE_AS_ROLL).assertDoesNotExist()
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
  fun `there is no set chooser until there is a second set to choose`() {
    // A chooser with one entry is furniture, which is the rule every other
    // chooser in the app follows.
    show()
    openDice()

    compose.onNodeWithTag(RollTestTags.SETS).assertDoesNotExist()
  }

  @Test
  fun `with two sets installed the row says which one it is offering`() {
    val presenter = show(catalog = twoSets())
    openDice()

    compose.onNodeWithTag(RollTestTags.SETS).assertExists()
    compose.onNodeWithTag(RollTestTags.setOf(BRASS)).performScrollTo().performClick()

    compose.waitUntil(PATIENCE) { presenter.pickingFrom == BRASS }
    compose.onNodeWithTag(RollTestTags.pickerDie("d20")).performScrollTo().performClick()
    assertEquals("$BRASS:1d20", presenter.text)
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
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
  }

  @Test
  fun `and the result's two buttons go nowhere rather than failing`() {
    val presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0)))
    compose.setContent { RollScreen(presenter = presenter) }
    typeFormula("1d20")
    shake()

    compose.onNodeWithTag(RollTestTags.ODDS).performClick()
    compose.onNodeWithTag(RollTestTags.SAVE_AS_ROLL).performClick()

    compose.onNodeWithTag(RollTestTags.ODDS).assertIsDisplayed()
  }

  @Test
  fun `the tray shows a tab rather than the formula, until it is asked for`() {
    // The second device session asked for the formula to be out of the way
    // entirely: what is left on the felt is the door, not what is behind it
    // (`design/dInfinity.dc.html`, option 2a).
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.FORMULA_DRAWER).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
  }

  @Test
  fun `and there is always a tab to press, even with nothing typed`() {
    show()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertTextContains("Formula", substring = true)
  }

  @Test
  fun `Done puts the keyboard away and throws nothing`() {
    // The key used to throw. It does not: a shake is the only way to start a
    // roll (`docs/architecture.md`, decision 66), so finishing a formula is
    // finishing a formula and the editor goes away so the table can be seen.
    val presenter = show()
    typeFormula("1d20")

    compose.onNodeWithTag(RollTestTags.FORMULA).performImeAction()

    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
    compose.onNodeWithTag(RollTestTags.TOTAL).assertDoesNotExist()
    assertFalse("the keyboard's key threw the dice", presenter.state is RollState.Settled)
    assertEquals("the keyboard's key threw the dice", 0, (presenter.tray as DirectTray).throws)
  }

  @Test
  fun `the table offers a screen reader nothing that throws`() {
    // There used to be a custom action, "Throw the dice". It is gone with the
    // key, and with it the last way to roll without a shake — decided, and
    // accepted for what it costs (`docs/architecture.md`, decision 66). The
    // table keeps what it says, and the one thing it offers to do is clear
    // the controls off itself (decision 83) — which throws nothing.
    val presenter = show()
    typeFormula("1d20")

    val table = compose.onNodeWithTag(RollTestTags.TRAY).fetchSemanticsNode().config
    val actions = table.getOrElse(SemanticsActions.CustomActions) { emptyList() }
    assertEquals("the table carries an action of another kind", listOf("Hide the controls"), actions.map { it.label })
    compose.runOnIdle { actions.single().action() }
    assertEquals("clearing the table threw the dice", 0, (presenter.tray as DirectTray).throws)
    assertTrue("the table says nothing about itself", table.contains(SemanticsProperties.ContentDescription))
  }

  @Test
  fun `a shake with no screen up throws nothing`() {
    // The hand only reaches a screen that is resumed and listening.
    assertFalse("a shake reached a screen that is not there", compose.runOnIdle { shaking.hand.shake() })
  }

  @Test
  fun `the tab is marked when the formula does not read`() {
    // The badge `9c` asks for, and the one thing the shut tab still says
    // about a formula it does not print. What exactly is wrong is said in the
    // drawer, under the squiggle, because that is where somebody can fix it.
    show()
    typeFormula("3d6 +")

    compose.onNodeWithTag(RollTestTags.INVALID).assertIsDisplayed()

    // And shut again, the tab is still there to say something is behind it —
    // what it says to a screen reader is [FormulaDrawerTest]'s.
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()

    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).assertIsDisplayed()
    compose.onNodeWithTag(RollTestTags.FORMULA).assertDoesNotExist()
  }

  /**
   * Pulls the dice menu down, the way a player does.
   *
   * The dice are put away until somebody asks for them
   * (`docs/physics-and-rendering.md`, "What is drawn over the table"), so
   * every test that taps a die goes through the head — which is also the only
   * way the head stays tested.
   */
  private fun openDice() {
    compose.onNodeWithTag(RollTestTags.DICE_MENU).performClick()
  }

  /**
   * Types a formula the way a player does: bring the drawer in, then type.
   *
   * The formula is not on the tray at all until somebody asks for it
   * (`design/dInfinity.dc.html`, option 2a), so every test that types goes
   * through the tab — which is also the only way the tab stays tested.
   */
  private fun typeFormula(text: String) {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    compose.onNodeWithTag(RollTestTags.FORMULA).performTextInput(text)
  }

  /**
   * The field, which is now the only place the formula is written at all.
   *
   * It brings the drawer in first: the tray no longer prints the formula, so
   * "what does the field say" is a question that starts with a press.
   */
  private fun theFormula(): SemanticsNodeInteraction {
    compose.onNodeWithTag(RollTestTags.FORMULA_TAB).performClick()
    return compose.onNodeWithTag(RollTestTags.FORMULA)
  }

  /**
   * Shakes the phone: the only way to throw.
   *
   * Robolectric has no accelerometer, so the hand is a [TestHand] standing
   * where the sensors would be — it reaches the presenter through the same
   * `onStarted → RollPresenter.roll` the sensors do, and hands back the same
   * answer, which is what says whether anything was thrown ([ShakeInput]).
   */
  private fun shake(): Boolean {
    val threw = compose.runOnUiThread { shaking.hand.shake() }
    compose.waitForIdle()
    return threw
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
            outside = Outside(seeds = { 1L }, clock = { 0L }),
          ),
        driver = if (land) DirectTray() else PendingTray(),
        rolls = LandingRolls(faces),
        toTheScreen = { it() },
      )
    compose.setContent { RollScreen(presenter = presenter) }
    return presenter
  }

  @Test
  fun `the way back to the designer is on the tray only when there is one`() {
    // The banner itself is `BackToDesignerTest`'s; what this asks is that the
    // screen draws it at all, and that every other way onto the tray has no
    // banner on it (`docs/face-designer.md`, "The way back").
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        onBackToDesigner = {},
      )
    }

    compose.onNodeWithTag(RollTestTags.BACK_TO_DESIGNER).assertIsDisplayed()
  }

  @Test
  fun `and every other way onto the tray has none`() {
    show()

    compose.onNodeWithTag(RollTestTags.BACK_TO_DESIGNER).assertDoesNotExist()
  }

  private fun presenter(
    tray: Tray,
    rolls: Rolls,
  ) = rollPresenter(tray, rolls)

  private fun showWith(modifier: Modifier) {
    compose.setContent {
      RollScreen(
        presenter = presenter(DirectTray(), LandingRolls(mapOf(0 to 0))),
        modifier = modifier,
      )
    }
  }

  /** Throws the dice where it stands, so a click and its total are one act. */
  private companion object {
    const val CALLER_TAG = "caller:modifier"

    /** A second installed set, which is when the chooser is worth drawing. */
    const val BRASS = "brass"
    const val PATIENCE = 2_000L
  }
}
