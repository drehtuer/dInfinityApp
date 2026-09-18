package de.drehtuer.dinfinity.feature.sets

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.designer.BitmapAtlas
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.MineSets
import de.drehtuer.dinfinity.designer.PhotoStore
import de.drehtuer.dinfinity.designer.PhysicalStore
import de.drehtuer.dinfinity.designer.SetLicense
import de.drehtuer.dinfinity.designer.Stroke
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.dicesets.install.PackageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * One set on screen (`design/dInfinity.dc.html`, options `6a` and `6b`).
 *
 * The pair of cases is what this is for: a set that loads shows its dice, and
 * a set that does not shows the report **in the same place**, because they are
 * two answers to one question.
 */
@RunWith(RobolectricTestRunner::class)
// The die pictures are a `Canvas`, and a test that reads a pixel back needs a
// real rasteriser rather than Robolectric's stub one.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetDetailScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private val temporary: File = Files.createTempDirectory("dinfinity-detail-screen").toFile()
  private val root = File(temporary, "dicesets")
  private lateinit var database: DInfinityDatabase
  private lateinit var registry: InstalledSetRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)

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
    registry = InstalledSetRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
    temporary.deleteRecursively()
  }

  @Test
  fun `a set that loads shows its name and its dice`() {
    write("brass", toml("brass", "Brass and Bone"))
    File(root, "brass/${PackageMeta.FILE_NAME}").writeText(
      PackageMeta(source = "https://example.invalid/brass.zip").asJson(),
    )

    // Built without a source handler, because a screen with nothing wired to
    // it still has to draw: that is what the menu reaches on a cold start.
    val presenter =
      SetDetailPresenter(
        id = "brass",
        library =
          SetLibrary(
            bundled = bundledSet(),
            installed = InstalledSets(root),
            registry = registry,
            io = Dispatchers.Unconfined,
            installer = PackageInstaller(root),
            defaultSetId = { DiceSet.BUILTIN_ID },
          ),
        scope = scope,
        onGone = {},
        defaultSetId = { DiceSet.BUILTIN_ID },
        onDefault = {},
      )
    compose.setContent { SetDetailScreen(presenter) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }

    compose.onNodeWithTag(SetDetailTestTags.NAME).assertTextContains("Brass and Bone")
    at(SetDetailTestTags.dieOf("d6")).assertIsDisplayed()
    at(SetDetailTestTags.dieOf("d6")).assertTextContains("6 faces", substring = true)

    // And tapping the source with nothing wired to it does nothing, rather
    // than taking the screen down.
    at(SetDetailTestTags.SOURCE).performClick()
    compose.waitForIdle()
    compose.onNodeWithTag(SetDetailTestTags.NAME).assertIsDisplayed()
  }

  @Test
  fun `the author and the licence are shown`() {
    write("brass", toml("brass", "Brass", extra = ABOUT))

    show("brass")

    compose.onNodeWithText("A. Smith").assertIsDisplayed()
    compose.onNodeWithText("CC-BY-4.0").assertIsDisplayed()
    compose.onNodeWithText("Turned brass and bone.").assertIsDisplayed()
  }

  @Test
  fun `the dice are headed by a kicker that says how many there are`() {
    // The prototype heads the grid "Dice · N · rendered from the set"
    // (`design/dInfinityPhone.dc.html`, the Dice set screen). The count lives
    // in the heading because that is the one place it is said.
    write("brass", toml("brass", "Brass"))

    show("brass")

    atText("Dice · 1 · rendered from the set").assertIsDisplayed()
  }

  @Test
  fun `a die is drawn in the colour its own set gives it`() {
    // The kicker over this grid says the dice are rendered from the set, and
    // a row of identical grey outlines would not be. What makes each picture
    // this die rather than a die is `[die.material] color`, which is what the
    // prototype fills its shapes with too.
    write("brass", coloured("brass", "Brass", "#ff0000"))

    show("brass")

    // The whole row, scanned: where exactly the 32 dp picture lands depends on
    // the row's padding and on how tall its two lines of text are, and none of
    // that is what this is about. What is: the set's colour is on the screen,
    // which it would not be if the shape were filled with `surface` the way it
    // was before.
    val row = at(SetDetailTestTags.dieOf("d6")).captureToImage().toPixelMap()
    val painted =
      (0 until row.width).any { x ->
        (0 until row.height).any { y -> row[x, y] == Color.Red }
      }
    assertTrue("the die was not drawn in the colour its set gives it", painted)
  }

  @Test
  fun `the report is headed by a kicker that names it`() {
    write("runes", "format = 1\n\n[set]\nid = \"runes\"\n")

    show("runes")

    compose.onNodeWithText("Validation report").assertIsDisplayed()
  }

  @Test
  fun `a package that will not load shows the report where the dice would be`() {
    // Design 6b. The dice grid is gone because there are no dice; the report
    // stands in its place because that is the answer to the same question.
    write("runes", "format = 1\n\n[set]\nid = \"runes\"\n")

    show("runes")

    compose.onAllNodesWithTag(SetDetailTestTags.PROBLEM).onFirst().assertIsDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.dieOf("d6")).assertIsNotDisplayed()
  }

  @Test
  fun `a report line says the file and what is wrong with it`() {
    // `diceset.toml:…: error: …`, because the person who can fix it is the
    // author and file:line is what they need.
    write("runes", "format = 1\n\n[set]\nid = \"runes\"\n")

    show("runes")

    val first = compose.onAllNodesWithTag(SetDetailTestTags.PROBLEM).onFirst()
    first.assertTextContains(DiceSetValidator.DICE_SET_FILE, substring = true)
    first.assertTextContains("error", substring = true)
  }

  @Test
  fun `a source that is a link can be tapped`() {
    write("brass", toml("brass", "Brass"))
    File(root, "brass/${PackageMeta.FILE_NAME}").writeText(
      PackageMeta(source = "https://example.invalid/brass.zip", commit = "deadbeef").asJson(),
    )
    val opened = mutableListOf<String>()

    show("brass", onSource = opened::add)
    compose.onNodeWithTag(SetDetailTestTags.SOURCE).performClick()

    compose.waitForIdle()
    assertEquals(listOf("https://example.invalid/brass.zip"), opened)
  }

  @Test
  fun `the commit is shown beside the source`() {
    write("brass", toml("brass", "Brass"))
    File(root, "brass/${PackageMeta.FILE_NAME}").writeText(
      PackageMeta(source = "https://example.invalid/brass.zip", commit = "deadbeef").asJson(),
    )

    show("brass")

    compose
      .onNodeWithTag(SetDetailTestTags.SOURCE)
      .assertTextContains("deadbeef", substring = true)
  }

  @Test
  fun `a source that is not a link is not offered as one`() {
    // A set installed from a file, or a folder the app never installed. Making
    // it tappable would promise something it cannot do.
    write("brass", toml("brass", "Brass"))
    File(root, "brass/${PackageMeta.FILE_NAME}").writeText(PackageMeta(source = "brass.zip").asJson())
    val opened = mutableListOf<String>()

    show("brass", onSource = opened::add)
    compose.onNodeWithTag(SetDetailTestTags.SOURCE).performClick()

    compose.waitForIdle()
    assertEquals("a name was offered as a link", emptyList<String>(), opened)
  }

  @Test
  fun `switching off from the details screen changes the button`() {
    write("brass", toml("brass", "Brass"))
    val presenter = show("brass")

    at(SetDetailTestTags.TOGGLE).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.row?.enabled == false }
    at(SetDetailTestTags.TOGGLE).assertTextContains("Switch on", substring = true)
  }

  @Test
  fun `removing from the details screen leaves it`() {
    write("brass", toml("brass", "Brass"))
    var left = 0
    show("brass", onGone = { left++ })

    at(SetDetailTestTags.REMOVE).performClick()

    compose.waitUntil(PATIENCE) { left > 0 }
    assertEquals(false, File(root, "brass").exists())
  }

  @Test
  fun `a set can be made the one a plain d20 comes from`() {
    write("brass", toml("brass", "Brass"))
    val chosen = mutableListOf<String>()
    val presenter = show("brass", onDefault = chosen::add)

    compose.onNodeWithTag(SetDetailTestTags.MAKE_DEFAULT).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.isDefault }
    assertEquals(listOf("brass"), chosen)
    // It says so at once rather than waiting for the setting to come back
    // round: a button that stays unchanged for a frame reads as one that did
    // not work.
    compose.onNodeWithTag(SetDetailTestTags.IS_DEFAULT).assertIsDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.MAKE_DEFAULT).assertIsNotDisplayed()
  }

  @Test
  fun `the set that is already the default is not offered the job again`() {
    write("brass", toml("brass", "Brass"))

    show("brass", default = "brass")

    compose.onNodeWithTag(SetDetailTestTags.IS_DEFAULT).assertIsDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.MAKE_DEFAULT).assertIsNotDisplayed()
  }

  @Test
  fun `a set that will not load is not offered the job at all`() {
    // Naming it would point every plain d20 at a set that is then fallen
    // straight past.
    write("runes", "format = 1\n\n[set]\nid = \"runes\"\n")

    show("runes")

    compose.onNodeWithTag(SetDetailTestTags.MAKE_DEFAULT).assertIsNotDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.IS_DEFAULT).assertIsNotDisplayed()
  }

  @Test
  fun `a set that is switched off is not offered the job either`() {
    write("brass", toml("brass", "Brass"))
    val presenter = show("brass")

    at(SetDetailTestTags.TOGGLE).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.row?.enabled == false }

    compose.onNodeWithTag(SetDetailTestTags.MAKE_DEFAULT).assertIsNotDisplayed()
  }

  @Test
  fun `the bundled set is shown without anything to do to it`() {
    show(DiceSet.BUILTIN_ID)

    compose.onNodeWithTag(SetDetailTestTags.NAME).assertTextContains("Standard")
    compose.onNodeWithTag(SetDetailTestTags.TOGGLE).assertIsNotDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.REMOVE).assertIsNotDisplayed()
  }

  @Test
  fun `a set that is not installed says so instead of showing an empty page`() {
    show("gone")

    compose.onNodeWithTag(SetDetailTestTags.MISSING).assertIsDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.LIST).assertIsNotDisplayed()
  }

  @Test
  fun `the physical block says the three things a dice shop says`() {
    // Design of 2026-09-17: a label, a unit and a value per row
    // (`design/dInfinityPhone.dc.html`, the Dice set details screen).
    write("brass", toml("brass", "Brass", extra = "\n[defaults]\nsize_mm = 20\ndensity = 2.4\ntranslucency = 20"))

    show("brass")

    at(SetDetailTestTags.PHYSICAL).assertIsDisplayed()
    at(SetDetailTestTags.valueOf(SetDetailTestTags.WEIGHT)).assertTextContains("3.7 g")
    at(SetDetailTestTags.valueOf(SetDetailTestTags.TRANSLUCENCY)).assertTextContains("20 %")
    at(SetDetailTestTags.valueOf(SetDetailTestTags.SIZE)).assertTextContains("125 %")
    compose.onNodeWithText("25 % over average", substring = true).assertIsDisplayed()
  }

  @Test
  fun `somebody else's set says where its numbers came from instead of offering steppers`() {
    write("brass", toml("brass", "Brass"))

    show("brass")

    at(SetDetailTestTags.PHYSICAL_FIXED).assertIsDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.stepOf(SetDetailTestTags.WEIGHT, up = true)).assertDoesNotExist()
    compose.onNodeWithTag(SetDetailTestTags.stepOf(SetDetailTestTags.SIZE, up = false)).assertDoesNotExist()
  }

  @Test
  fun `My dice carries the steppers, and a tap moves the figure`() {
    val presenter = show(MINE, personal = mine())
    at(SetDetailTestTags.valueOf(SetDetailTestTags.WEIGHT)).assertTextContains("0.9 g")

    repeat(2) { at(SetDetailTestTags.stepOf(SetDetailTestTags.WEIGHT, up = true)).performClick() }
    compose.waitUntil(PATIENCE) { presenter.state.declared?.density != 1.2 }

    // 0.95 g and two tenths of a gram: the value on the screen is the one the
    // taps left behind, not the one the last reading of the folder found.
    at(SetDetailTestTags.valueOf(SetDetailTestTags.WEIGHT)).assertTextContains("1.1 g")
    compose.onNodeWithTag(SetDetailTestTags.PHYSICAL_FIXED).assertDoesNotExist()
  }

  @Test
  fun `a tap on the size stepper is five per cent of an average die`() {
    val presenter = show(MINE, personal = mine())

    at(SetDetailTestTags.stepOf(SetDetailTestTags.SIZE, up = true)).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.declared?.sizeMm != 16.0 }

    at(SetDetailTestTags.valueOf(SetDetailTestTags.SIZE)).assertTextContains("105 %")
    compose.onNodeWithText("5 % over average", substring = true).assertIsDisplayed()
  }

  @Test
  fun `a package that came from somewhere else is offered no export`() {
    write("brass", toml("brass", "Brass and Bone"))

    show("brass")

    // Every other set on the list already has a copy wherever it came from.
    compose.onNodeWithTag(SetDetailTestTags.EXPORT).assertDoesNotExist()
  }

  @Test
  fun `My dice offers the export, and it is shut until a licence is picked`() {
    write(MINE, toml(MINE, "My dice"))

    show(MINE)

    at(SetDetailTestTags.EXPORT).assertIsDisplayed()
    // The gate (design `8c`): the chooser says nothing has been chosen, and
    // the button will not go.
    at(SetDetailTestTags.LICENSE_CHOOSER).assertTextContains("choose one", substring = true)
    at(SetDetailTestTags.EXPORT_DO).assertIsNotEnabled()
  }

  @Test
  fun `picking a licence from the menu opens the button and says which`() {
    write(MINE, toml(MINE, "My dice"))
    val presenter = show(MINE)

    at(SetDetailTestTags.LICENSE_CHOOSER).performClick()
    compose.onNodeWithTag(SetDetailTestTags.licenseOf(SetLicense.Attribution)).performClick()
    compose.waitForIdle()

    assertEquals(SetLicense.Attribution, presenter.state.license)
    at(SetDetailTestTags.LICENSE_CHOOSER).assertTextContains("CC BY 4.0")
    at(SetDetailTestTags.EXPORT_DO).assertIsEnabled()
  }

  @Test
  fun `a licence already in the package is what the chooser opens on`() {
    // Somebody chose it last time, and the folder remembered.
    write(MINE, toml(MINE, "My dice", extra = """license = "MIT""""))

    show(MINE)

    at(SetDetailTestTags.LICENSE_CHOOSER).assertTextContains("MIT")
    at(SetDetailTestTags.EXPORT_DO).assertIsEnabled()
  }

  /**
   * "My dice" with the records it is built from behind it, which is what makes
   * its three physical numbers this phone's to change.
   */
  private fun mine(): MineSets {
    val drafts = DraftStore(File(temporary, "drafts"))
    drafts.save(
      Draft(die = Die.standard(id = "d6", shape = DieShape.Cube)).onFace(0) {
        it.draw(Stroke(dots = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)), colorArgb = INK, width = 0.05f))
      },
    )
    return MineSets(
      drafts = drafts,
      root = root,
      painter = BitmapAtlas(),
      dice = { listOf(Die.standard(id = "d6", shape = DieShape.Cube)) },
      photos = PhotoStore(File(temporary, "table-photos")),
      physical = PhysicalStore(File(temporary, PhysicalStore.FILE_NAME)),
    )
  }

  /**
   * The row under [tag], scrolled into view first.
   *
   * The details screen is a list and is taller than a phone: the Physical
   * block alone is three rows, so the dice, the export and the buttons under
   * them are below the fold. A finger scrolls to them and so does a test.
   */
  private fun at(tag: String): SemanticsNodeInteraction {
    compose.onNodeWithTag(SetDetailTestTags.LIST).performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
    return compose.onNodeWithTag(tag)
  }

  /** The same, for a line of text rather than a tag. */
  private fun atText(text: String): SemanticsNodeInteraction {
    compose.onNodeWithTag(SetDetailTestTags.LIST).performScrollToNode(hasText(text))
    compose.waitForIdle()
    return compose.onNodeWithText(text)
  }

  // Six ways the screen can be wired and one set to show on it. Every one of
  // them is a different test, and a holder for them would be a type that
  // exists to make a counter smaller.
  @Suppress("LongParameterList")
  private fun show(
    id: String,
    onSource: (String) -> Unit = {},
    onGone: () -> Unit = {},
    default: String = "",
    onDefault: (String) -> Unit = {},
    personal: MineSets? = null,
  ): SetDetailPresenter {
    val presenter =
      SetDetailPresenter(
        id = id,
        library =
          SetLibrary(
            bundled = bundledSet(),
            installed = InstalledSets(root),
            registry = registry,
            io = Dispatchers.Unconfined,
            installer = PackageInstaller(root),
            defaultSetId = { DiceSet.BUILTIN_ID },
            personal = personal,
          ),
        scope = scope,
        onGone = onGone,
        defaultSetId = { default },
        onDefault = onDefault,
      )
    compose.setContent { SetDetailScreen(presenter, onSource = onSource) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  private fun write(
    id: String,
    toml: String,
  ) {
    val folder = File(root, id).apply { mkdirs() }
    File(folder, DiceSetValidator.DICE_SET_FILE).writeText(toml)
  }

  /** A package whose one die names its own colour. */
  private fun coloured(
    id: String,
    name: String,
    colour: String,
  ) = toml(id, name) + "\ncolor = \"$colour\"\n"

  private fun toml(
    id: String,
    name: String,
    extra: String = "",
  ) = """
    format = 1

    [set]
    id = "$id"
    name = "$name"
    version = "1.0.0"
    $extra

    [[die]]
    id = "d6"
    shape = "cube"
    faces = [1, 2, 3, 4, 5, 6]
    """.trimIndent()

  private fun bundledSet() =
    DiceSet(
      id = DiceSet.BUILTIN_ID,
      name = "Standard",
      version = "1.0.0",
      dice = listOf(Die(id = "d6", shape = DieShape.Cube, faces = (1..6).map { Face(it - 1, it, it.toString()) })),
    )

  private companion object {
    val ABOUT =
      """
      author = "A. Smith"
      license = "CC-BY-4.0"
      description = "Turned brass and bone."
      """.trimIndent()

    /** The personal package's id, which is the only thing the screen keys off. */
    const val MINE = "mine"

    /** The ink a drawn face is drawn in, so that "My dice" has a die in it. */
    const val INK = 0xFF202020.toInt()

    const val PATIENCE = 5_000L
  }
}
