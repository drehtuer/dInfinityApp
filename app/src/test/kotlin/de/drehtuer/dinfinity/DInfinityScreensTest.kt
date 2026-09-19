package de.drehtuer.dinfinity

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.NotationReference
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.SavedRollGroupRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.feature.designer.DesignerTestTags
import de.drehtuer.dinfinity.feature.roll.FinishedThrow
import de.drehtuer.dinfinity.feature.roll.RollTestTags
import de.drehtuer.dinfinity.feature.roll.ThrowRecorder
import de.drehtuer.dinfinity.feature.saved.EditorTestTags
import de.drehtuer.dinfinity.feature.saved.HomeStripTestTags
import de.drehtuer.dinfinity.feature.saved.ImportTestTags
import de.drehtuer.dinfinity.feature.saved.SavedTestTags
import de.drehtuer.dinfinity.feature.sets.SetDetailTestTags
import de.drehtuer.dinfinity.feature.sets.SetLibrary
import de.drehtuer.dinfinity.feature.sets.SetsTestTags
import de.drehtuer.dinfinity.feature.settings.NotationTestTags
import de.drehtuer.dinfinity.feature.stats.HistoryTestTags
import de.drehtuer.dinfinity.feature.stats.SessionsTestTags
import de.drehtuer.dinfinity.feature.stats.StatsTestTags
import de.drehtuer.dinfinity.navigation.DesignerArgument
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.navigation.GraphArgument
import de.drehtuer.dinfinity.theme.DInfinityTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/**
 * The navigation graph with real screens behind it.
 *
 * `DInfinityAppTest` drives the graph with nothing plugged in, which is right
 * for the graph's own rules. This is the other half: every destination that
 * takes a presenter, built over a real database, so that "the menu reaches
 * every screen" means the screen it reaches *draws* rather than falls back to
 * a placeholder.
 *
 * It is the one place the whole app is assembled, which is also the only place
 * a wiring mistake between two feature modules can show up.
 */
@RunWith(RobolectricTestRunner::class)
class DInfinityScreensTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var saved: SavedRollRepository
  private lateinit var savedGroups: SavedRollGroupRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)

  /** A `dicesets/` folder of its own, so one test's packages are not another's. */
  private val temporary: File = Files.createTempDirectory("dinfinity-app-sets").toFile()

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        // Queries and invalidation on the calling thread, so a `Flow` from a
        // `@Query` emits when the write happens rather than when a pool thread
        // gets to it. Without it the first wait in a class races Room's own
        // executors, which surfaces as an unrelated test failing now and then.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    saved = SavedRollRepository(database)
    savedGroups = SavedRollGroupRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `the saved-rolls screen draws when it has a presenter`() {
    val navigation = app()

    go(navigation, Destination.SavedRolls)

    compose.onNodeWithTag(SavedTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the editor draws, on a roll that exists`() {
    runBlocking {
      savedGroups.ensureUnfiled("Unfiled")
      saved.save(SavedRoll(id = "fireball", groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "8d6"))
    }
    val navigation = app()

    compose.runOnIdle { navigation.navigate(editorRoute("fireball")) }

    compose.onNodeWithTag(EditorTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the editor draws for a roll that does not exist yet`() {
    val navigation = app()

    compose.runOnIdle { navigation.navigate(editorRoute(null)) }

    compose.onNodeWithTag(EditorTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the import screen draws`() {
    val navigation = app()

    go(navigation, Destination.CollectionImport)

    compose.onNodeWithTag(ImportTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(ImportTestTags.CHOOSE).assertIsDisplayed()
  }

  @Test
  fun `the history screen draws`() {
    val navigation = app()

    go(navigation, Destination.History)

    compose.onNodeWithTag(HistoryTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the sessions screen draws`() {
    val navigation = app()

    go(navigation, Destination.Sessions)

    compose.onNodeWithTag(SessionsTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the dice-set screen draws`() {
    val navigation = app()

    go(navigation, Destination.DiceSets)

    compose.onNodeWithTag(SetsTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `the set details screen draws, and reaches the bundled set with no argument`() {
    // Every destination has to be reachable by its bare route — from a
    // restored back stack, from a test — and the bundled set is the one that
    // is always installed, so it is what an empty argument means.
    val navigation = app()

    go(navigation, Destination.SetDetail)

    compose.onNodeWithTag(SetDetailTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.NAME).assertTextContains(BuiltinDiceSet.set.name, substring = true)
  }

  @Test
  fun `a row on the dice-set screen opens that set`() {
    // The one wiring between two screens that a feature module cannot check on
    // its own: the list hands an id out and the graph turns it into a route.
    val navigation = app()
    go(navigation, Destination.DiceSets)

    compose.onNodeWithTag(SetsTestTags.setOf(BuiltinDiceSet.set.id)).performClick()

    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.SetDetail
    }
    compose.onNodeWithTag(SetDetailTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `an example on the notation screen opens the tray with that formula in it`() {
    // The whole point of the screen being a screen: you find out what a
    // modifier does by rolling it. The example has to arrive in the field, and
    // it has to arrive *unrolled* — the app does not throw dice nobody asked
    // it to (`docs/dice-notation.md`).
    val navigation = app()
    go(navigation, Destination.Notation)
    val advantage = NotationReference.entries.first { it.syntax.startsWith("kh") }

    compose.onNodeWithTag(NotationTestTags.entryOf(advantage.syntax)).performScrollTo().performClick()

    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.Roll
    }
    assertEquals(
      advantage.example,
      compose.runOnIdle {
        navigation.currentBackStackEntry
          ?.arguments
          ?.getString(GraphArgument.FORMULA)
      },
    )
  }

  @Test
  fun `the welcome's other two ways in reach the screens they name`() {
    // The design has three ways in and the app had one. Both of these now have
    // screens to send somebody to (`design/dInfinity.dc.html`, option 9a).
    val navigation = app()

    compose.onNodeWithTag(RollTestTags.WELCOME_IMPORT).performClick()

    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.CollectionImport
    }
  }

  @Test
  fun `and going to fetch something does not dismiss the welcome`() {
    val navigation = app()

    compose.onNodeWithTag(RollTestTags.WELCOME_SETS_ADD).performClick()
    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.DiceSets
    }
    compose.runOnIdle { navigation.popBackStack() }

    compose.onNodeWithTag(RollTestTags.WELCOME).assertIsDisplayed()
  }

  @Test
  fun `the welcome counts what is really there, not what a fresh install has`() {
    // The line used to say "0 saved rolls" whatever was saved, because the
    // sentence had the zero written into it (`docs/TODO.md`, 4.1).
    runBlocking {
      savedGroups.ensureUnfiled("Unfiled")
      saved.save(SavedRoll(id = "fireball", groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "8d6"))
    }

    app()

    compose.waitUntil(PATIENCE) {
      runCatching {
        compose.onNodeWithTag(RollTestTags.WELCOME_SETS).assertTextContains("1 saved roll", substring = true)
      }.isSuccess
    }
  }

  @Test
  fun `Roll it on the designer opens the tray with that die in the field`() {
    // Step 4 of the designer's flow, through the real graph: the button knows
    // which die is being drawn, the wiring knows how that die is spelled, and
    // the tray opens on it — unrolled, like every other way into the tray
    // (`docs/face-designer.md`).
    val navigation = app()
    go(navigation, Destination.FaceDesigner)

    compose.onNodeWithTag(DesignerTestTags.ROLL).performClick()

    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.Roll
    }
    assertEquals(
      "1d6",
      compose.runOnIdle {
        navigation.currentBackStackEntry
          ?.arguments
          ?.getString(GraphArgument.FORMULA)
      },
    )
    // And the die it was drawing goes with the formula, which is what puts
    // the way back on the tray (`feature/roll`'s `BackToDesigner`).
    assertEquals(
      "d6",
      compose.runOnIdle {
        navigation.currentBackStackEntry
          ?.arguments
          ?.getString(DesignerArgument.DIE)
      },
    )
  }

  @Test
  fun `and the tray it opens offers the way back to the designer`() {
    // The device session: "testing a roll from the face designer offers no
    // way back to the face designer". It is a round trip now, and the way
    // back opens the designer on the die being tested rather than on
    // whichever one it last opened (`docs/face-designer.md`, "Flow", step 4).
    val navigation = app()
    go(navigation, Destination.FaceDesigner)
    compose.onNodeWithTag(DesignerTestTags.ROLL).performClick()
    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.Roll
    }
    // The welcome is a full-screen takeover over everything on the tray, so
    // the banner under it is not a banner anybody can press yet.
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    compose.onNodeWithTag(RollTestTags.BACK_TO_DESIGNER).assertIsDisplayed().performClick()

    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.FaceDesigner
    }
    assertEquals(
      "d6",
      compose.runOnIdle {
        navigation.currentBackStackEntry
          ?.arguments
          ?.getString(DesignerArgument.DIE)
      },
    )
    // It climbed rather than retraced: the tray is under the designer, which
    // is the stack opening the designer from the tray would leave ([climbTo]).
    assertEquals(
      Destination.home.pattern,
      compose.runOnIdle { navigation.previousBackStackEntry?.destination?.route },
    )
  }

  @Test
  fun `a tray nobody came to from the designer has no way back to it`() {
    // The banner is about *this* visit. Every other way into the tray — the
    // menu, a saved roll, an example on the notation screen — leaves it off,
    // because there is nothing behind it to go back to.
    val navigation = app()
    go(navigation, Destination.Roll)
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    compose.onAllNodesWithTag(RollTestTags.BACK_TO_DESIGNER).assertCountEquals(0)
  }

  @Test
  fun `the designer opens on the die a route names, which is what quick mode carries`() {
    // "Doodle this die" is the same screen opened on a different die
    // (`docs/face-designer.md`, "Quick mode"), so what the graph has to get
    // right is the argument.
    val navigation = app()

    compose.runOnIdle { navigation.navigate(designerRoute("d20")) }

    compose.onNodeWithTag(DesignerTestTags.SCREEN).assertIsDisplayed()
    // Twenty faces on the strip. A d6 — which is what the menu opens on —
    // has six, so this says which die is being drawn without reading a label.
    compose.onNodeWithTag(DesignerTestTags.faceOf(19)).assertExists()
  }

  @Test
  fun `a long press on a die that landed opens the designer on that die`() {
    // The whole of quick mode, through the real graph: the sheet offers, the
    // roll screen hands over an id, and the wiring turns it into a route.
    runBlocking {
      savedGroups.ensureUnfiled("Unfiled")
      saved.save(SavedRoll(id = "fireball", groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "1d20"))
    }
    val navigation = app()
    go(navigation, Destination.Roll)
    // Past the welcome first. It is a full-screen takeover whose buttons run to
    // the bottom edge, which is where the result sheet comes up now — so a long
    // press on a die in the breakdown would land on `Add somebody else's dice`
    // rather than on the die.
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    // The saved rolls are a pull-up now, parked so the whole table shows
    // (`docs/physics-and-rendering.md`, "Two pull-ups, one bottom edge"), so
    // they are pulled out before anything can be tapped on them.
    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()

    // The tap fills the field; the shake is the throw
    // (`docs/physics-and-rendering.md`, "Starting a roll").
    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).assertIsDisplayed().performClick()
    shake()
    compose.waitUntil(PATIENCE) {
      compose.onAllNodesWithTag(RollTestTags.dieAt(0)).fetchSemanticsNodes().isNotEmpty()
    }
    // And then for the sheet carrying it to stop moving. The result comes up
    // from the bottom edge (`PullUpResult`), so a breakdown that exists is not
    // yet a breakdown standing still — and a long press on a chip that slides
    // out from under the finger is a drag, which cancels it.
    compose.waitForIdle()
    compose.onNodeWithTag(RollTestTags.dieAt(0)).performTouchInput { longClick() }
    compose.onNodeWithTag(RollTestTags.doodleOf(0)).performClick()

    compose.waitUntil(PATIENCE) {
      compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute) ==
        Destination.FaceDesigner
    }
    assertEquals(
      "d20",
      compose.runOnIdle {
        navigation.currentBackStackEntry
          ?.arguments
          ?.getString(DesignerArgument.DIE)
      },
    )
  }

  @Test
  fun `a throw from a formula the strip filled is written down as that saved roll's`() {
    // The chain this is about runs through four modules and two lambdas, and
    // it was broken the whole time: every roll went down with no saved roll
    // and no group against it, so the history's saved-roll filter found
    // nothing and the saved-roll statistics screen could never have had
    // anything on it (`docs/statistics.md`, per saved roll and per group).
    runBlocking {
      savedGroups.ensureUnfiled("Unfiled")
      saved.save(SavedRoll(id = "fireball", groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "1d20"))
    }
    val recorded = mutableListOf<FinishedThrow>()
    val navigation = app(recorder = { thrown -> recorded += thrown })
    go(navigation, Destination.Roll)

    // Past the welcome first, for the reason the quick-mode test goes past it:
    // it is a full-screen takeover whose buttons run to the bottom edge, and
    // the strip now sits lower than it did — the picker and the odds have left
    // the column of controls. One of those buttons also puts a d20 in the
    // field rather than throwing it now, so a tap that lands on it looks
    // exactly like a tap on a saved roll that has lost its name.
    compose.onNodeWithTag(RollTestTags.WELCOME_DISMISS).performClick()

    // Which roll the formula came from survives the wait for a hand: the tap
    // fills the field and the shake that follows is still Fireball's throw.
    // The rolls are pulled out of their pull-up first, the way a player does.
    compose.onNodeWithTag(RollTestTags.SAVED_HANDLE).performClick()
    compose.waitForIdle()
    compose.onNodeWithTag(HomeStripTestTags.tileOf("fireball")).performClick()
    assertTrue("the strip threw the roll rather than filling the field", recorded.isEmpty())
    shake()

    compose.waitUntil(PATIENCE) { recorded.isNotEmpty() }
    assertEquals("fireball", recorded.single().savedRollId)
    assertEquals(SavedRollGroup.UNFILED_ID, recorded.single().groupId)
  }

  @Test
  fun `the statistics screen draws`() {
    val navigation = app()

    go(navigation, Destination.Statistics)

    compose.onNodeWithTag(StatsTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun `every destination in the graph answers to its own route still answers, rather than leaving the graph blank`() {
    // Every destination in the graph has a composable. A gate that failed to
    // match would leave a destination that navigates to nothing, which is the
    // one navigation failure that looks like the app has crashed.
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme { DInfinityApp(navController = navigation) }
    }

    Destination.entries.forEach { destination ->
      compose.runOnIdle { navigation.navigate(destination.route) }
      assertEquals(
        destination,
        compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute),
      )
    }
  }

  private fun go(
    navigation: NavHostController,
    destination: Destination,
  ) {
    compose.runOnIdle { navigation.navigate(destination.route) }
  }

  /**
   * Throws the dice the only way the app offers that is not a hand: the
   * table's custom accessibility action (`RollScreen`).
   *
   * There is no Roll button and Robolectric has no accelerometer, so this is
   * how a test shakes the phone (`docs/architecture.md`, "Accessibility").
   */
  private fun shake() {
    val throwThem =
      compose
        .onNodeWithTag(RollTestTags.TRAY)
        .fetchSemanticsNode()
        .config[SemanticsActions.CustomActions]
        .single()
    compose.runOnUiThread { throwThem.action() }
    compose.waitForIdle()
  }

  /** The disk and the database joined, as the application does it. */
  @Test
  fun `every screen the menu lists draws itself, and none of them is a placeholder`() {
    // This is the test that should have caught the sessions screen. It was
    // finished, tested and unreachable for a whole step, because the activity
    // never passed its presenter and the app drew a placeholder — which looks
    // exactly like a screen nobody has written yet.
    //
    // It asserts the absence of the placeholder rather than the presence of
    // anything in particular, because "what this screen shows" is that
    // screen's own test. What belongs here is only: something real is there.
    val navigation = app()

    val placeholders =
      Destination.inTheMenu.filter { destination ->
        compose.runOnIdle { navigation.navigate(destination.route) }
        compose.waitForIdle()
        compose.onAllNodesWithTag(notBuiltTag(destination)).fetchSemanticsNodes().isNotEmpty()
      }

    // Asserted exactly, in both directions. A screen that starts drawing a
    // placeholder fails here, and so does one that stops — which is the prompt
    // to delete its line below when 4.5 or 4.6 lands.
    assertEquals(
      "the screens drawing a placeholder are not the ones that have not been written",
      NOT_WRITTEN_YET,
      placeholders.map(Destination::route).toSet(),
    )
  }

  @Test
  fun `with no presenters at all every screen is a placeholder, which is a mode rather than a mistake`() {
    // The other half of the rule. Drawing placeholders is legitimate — a
    // Robolectric test of the graph has no GPU and no physics engine — and
    // what it may not be is *partial*, which is why `Presenters` has no
    // optional fields.
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme { DInfinityApp(navController = navigation) }
    }

    compose.runOnIdle { navigation.navigate(Destination.Sessions.route) }
    compose.waitForIdle()

    compose.onNodeWithTag(notBuiltTag(Destination.Sessions)).assertExists()
  }

  private fun setLibrary() =
    SetLibrary(
      bundled = BuiltinDiceSet.set,
      installed = InstalledSets(File(temporary, "dicesets")),
      registry = InstalledSetRepository(database),
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(File(temporary, "dicesets")),
      defaultSetId = { BuiltinDiceSet.set.id },
    )

  /** The app with every screen that takes a presenter actually given one. */
  private fun app(recorder: ThrowRecorder = ThrowRecorder.NONE): NavHostController {
    lateinit var navigation: NavHostController
    compose.setContent {
      navigation = rememberNavController()
      DInfinityTheme {
        DInfinityApp(
          navController = navigation,
          screens = testPresenters(database, scope, setLibrary(), recorder = recorder),
        )
      }
    }
    return navigation
  }

  private companion object {
    /**
     * The screens that genuinely have not been written.
     *
     * **Empty, at last.** Every destination the menu lists now draws its own
     * screen; a placeholder anywhere is a screen that came unplugged. The list
     * grew for a while and then shrank to nothing, which is what it was for.
     */
    val NOT_WRITTEN_YET = emptySet<String>()

    /** Long enough for a folder read and a database round trip, short enough to fail. */
    const val PATIENCE = 5_000L
  }
}
