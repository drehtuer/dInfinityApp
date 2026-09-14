package de.drehtuer.dinfinity.feature.sets

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.dicesets.install.PackageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    compose.onNodeWithTag(SetDetailTestTags.dieOf("d6")).assertIsDisplayed()
    compose.onNodeWithTag(SetDetailTestTags.dieOf("d6")).assertTextContains("6 faces", substring = true)

    // And tapping the source with nothing wired to it does nothing, rather
    // than taking the screen down.
    compose.onNodeWithTag(SetDetailTestTags.SOURCE).performClick()
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

    compose.onNodeWithTag(SetDetailTestTags.TOGGLE).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.row?.enabled == false }
    compose.onNodeWithTag(SetDetailTestTags.TOGGLE).assertTextContains("Switch on", substring = true)
  }

  @Test
  fun `removing from the details screen leaves it`() {
    write("brass", toml("brass", "Brass"))
    var left = 0
    show("brass", onGone = { left++ })

    compose.onNodeWithTag(SetDetailTestTags.REMOVE).performClick()

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

    compose.onNodeWithTag(SetDetailTestTags.TOGGLE).performClick()
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

  private fun show(
    id: String,
    onSource: (String) -> Unit = {},
    onGone: () -> Unit = {},
    default: String = "",
    onDefault: (String) -> Unit = {},
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

    const val PATIENCE = 5_000L
  }
}
