package de.drehtuer.dinfinity.feature.sets

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/**
 * The dice-set list on screen (`design/dInfinity.dc.html`, option `5a`).
 *
 * What a row *says* is the thing worth testing here: a set that has been
 * switched off and a set that will not load are both unusable, and the screen
 * has to tell them apart, because one is fixed with a tap and the other with an
 * update.
 */
@RunWith(RobolectricTestRunner::class)
class SetsScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private val temporary: File = Files.createTempDirectory("dinfinity-sets-screen").toFile()
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
  fun `the bundled set is listed and says so`() {
    show()

    compose.onNodeWithTag(SetsTestTags.setOf("builtin")).assertIsDisplayed()
    compose
      .onNodeWithTag(SetsTestTags.setOf("builtin"))
      .assertTextContains("Built in", substring = true)
  }

  @Test
  fun `tapping a row with nothing wired to it does nothing`() {
    // The screen the menu reaches on a cold start has no handler attached yet.
    // A tap then has to be harmless rather than fatal.
    write("brass", toml("brass", "Brass"))
    val presenter = show()

    compose.onNodeWithTag(SetsTestTags.setOf("brass")).performClick()

    compose.waitForIdle()
    compose.onNodeWithTag(SetsTestTags.setOf("brass")).assertIsDisplayed()
    assertEquals(null, presenter.state.acting)
  }

  @Test
  fun `the install button asks, and says so while one is running`() {
    // Choosing the file is the application's business, so the screen only
    // asks. While an install is running it must not ask again: two extractions
    // racing for one folder is the one thing the installer cannot guard.
    var asked = 0
    val presenter = show(onInstall = { asked++ })

    compose.onNodeWithTag(SetsTestTags.INSTALL).performClick()
    compose.waitForIdle()

    assertEquals(1, asked)
  }

  @Test
  fun `a screen with nothing wired to it still draws, and taps are harmless`() {
    // What the menu reaches on a cold start: no handlers attached yet. Both
    // gestures have to be harmless rather than fatal.
    write("brass", toml("brass", "Brass"))
    val presenter = SetsPresenter(library(), scope)
    compose.setContent { SetsScreen(presenter) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }

    compose.onNodeWithTag(SetsTestTags.setOf("brass")).performClick()
    compose.onNodeWithTag(SetsTestTags.INSTALL).performClick()

    compose.waitForIdle()
    compose.onNodeWithTag(SetsTestTags.setOf("brass")).assertIsDisplayed()
  }

  @Test
  fun `a refusal lists every error and can be put away`() {
    val presenter = show()
    presenter.refused("that file is not a dice set")
    compose.waitForIdle()

    compose.onNodeWithTag(SetsTestTags.OUTCOME).assertIsDisplayed()
    compose
      .onNodeWithTag(SetsTestTags.OUTCOME_REASON)
      .assertTextContains("that file is not a dice set", substring = true)

    compose.onNodeWithTag(SetsTestTags.OUTCOME_CLOSE).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.outcome == null }
  }

  @Test
  fun `with nothing installed the screen says so instead of showing a bare list`() {
    show()

    compose.onNodeWithTag(SetsTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun `an installed set shows its name and how many dice it has`() {
    write("brass", toml("brass", "Brass and Bone"))
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }

    compose
      .onNodeWithTag(SetsTestTags.setOf("brass"))
      .assertTextContains("Brass and Bone", substring = true)
    compose.onNodeWithTag(SetsTestTags.setOf("brass")).assertTextContains("1 die", substring = true)
  }

  @Test
  fun `a package that will not load says how many problems, not that it is off`() {
    write("runes", "format = 1\n\n[set]\nid = \"runes\"\n")
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "runes" } }

    val row = compose.onNodeWithTag(SetsTestTags.setOf("runes"))
    row.assertTextContains("problem", substring = true)
  }

  @Test
  fun `a set switched off says so`() {
    write("brass", toml("brass", "Brass"))
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }

    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    compose.waitUntil(PATIENCE) {
      presenter.state.sets
        .single { it.id == "brass" }
        .enabled
        .not()
    }

    compose
      .onNodeWithTag(SetsTestTags.setOf("brass"))
      .assertTextContains("Switched off", substring = true)
  }

  @Test
  fun `a long press on a row opens the sheet`() {
    // The gesture the whole screen turns on. Tap and long press share a node,
    // so the one that opens the sheet has to be the one that does not open the
    // details screen.
    write("brass", toml("brass", "Brass"))
    val opened = mutableListOf<String>()
    val presenter = show(onOpen = { opened += it.id })

    compose.onNodeWithTag(SetsTestTags.setOf("brass")).performTouchInput { longClick() }

    compose.waitUntil(PATIENCE) { presenter.state.acting != null }
    assertEquals("brass", presenter.state.acting?.id)
    assertEquals("a long press also opened the details screen", emptyList<String>(), opened)
    compose.onNodeWithTag(SetsTestTags.SHEET).assertIsDisplayed()
  }

  @Test
  fun `a long press on the bundled set opens nothing`() {
    // There is nothing the sheet could offer for it, so it does not appear
    // rather than appearing with everything greyed out.
    val presenter = show()

    compose.onNodeWithTag(SetsTestTags.setOf("builtin")).performTouchInput { longClick() }

    compose.waitForIdle()
    assertEquals(null, presenter.state.acting)
  }

  @Test
  fun `the sheet switches a set off and closes`() {
    write("brass", toml("brass", "Brass"))
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }
    presenter.act(presenter.state.sets.single { it.id == "brass" })
    compose.waitForIdle()

    compose.onNodeWithTag(SetsTestTags.TOGGLE).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.acting == null }
    compose.waitUntil(PATIENCE) {
      presenter.state.sets
        .single { it.id == "brass" }
        .enabled
        .not()
    }
  }

  @Test
  fun `the sheet on a set that is already off offers to switch it back on`() {
    // The other half of the toggle, and the half a player reaches for after
    // changing their mind. The sheet has to read differently or it looks like
    // the same button doing the same thing twice.
    write("brass", toml("brass", "Brass"))
    val presenter = show()
    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    compose.waitUntil(PATIENCE) {
      !presenter.state.sets
        .single { it.id == "brass" }
        .enabled
    }

    presenter.act(presenter.state.sets.single { it.id == "brass" })
    compose.waitForIdle()

    compose.onNodeWithTag(SetsTestTags.TOGGLE).assertTextContains("Switch on", substring = true)
    compose.onNodeWithTag(SetsTestTags.TOGGLE).performClick()

    compose.waitUntil(PATIENCE) {
      presenter.state.sets
        .single { it.id == "brass" }
        .enabled
    }
    compose
      .onNodeWithTag(SetsTestTags.setOf("brass"))
      .assertTextContains("1 die", substring = true)
  }

  @Test
  fun `a set installed while the screen is open appears when it is read again`() {
    // There is no watching: the folder is read when the screen opens and after
    // anything that changes it. So the test for "it appears" is the test for
    // "refresh actually re-reads".
    val presenter = show()
    compose.onNodeWithTag(SetsTestTags.EMPTY).assertIsDisplayed()

    write("brass", toml("brass", "Brass"))
    presenter.refresh()

    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }
    compose.onNodeWithTag(SetsTestTags.setOf("brass")).assertIsDisplayed()
  }

  @Test
  fun `the sheet removes a set`() {
    write("brass", toml("brass", "Brass"))
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }
    presenter.act(presenter.state.sets.single { it.id == "brass" })
    compose.waitForIdle()

    compose.onNodeWithTag(SetsTestTags.REMOVE).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.sets.none { it.id == "brass" } }
    assertEquals(false, File(root, "brass").exists())
  }

  @Test
  fun `cancelling the sheet changes nothing`() {
    write("brass", toml("brass", "Brass"))
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }
    presenter.act(presenter.state.sets.single { it.id == "brass" })
    compose.waitForIdle()

    compose.onNodeWithTag(SetsTestTags.CANCEL).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.acting == null }
    compose.onNodeWithTag(SetsTestTags.setOf("brass")).assertIsDisplayed()
    assertEquals(true, File(root, "brass").isDirectory)
  }

  @Test
  fun `tapping a row asks to open it rather than acting on it`() {
    // Tap and long press are different questions: one goes to the details
    // screen, the other asks what to do with the set.
    write("brass", toml("brass", "Brass"))
    val opened = mutableListOf<String>()
    val presenter = show(onOpen = { opened += it.id })
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }

    compose.onNodeWithTag(SetsTestTags.setOf("brass")).performClick()

    compose.waitForIdle()
    assertEquals(listOf("brass"), opened)
    assertEquals(null, presenter.state.acting)
  }

  @Test
  fun `a download in flight shows how far it has got, and a way to stop it`() {
    // `design/dInfinity.dc.html`, option `9i`. Neither is drawn when nothing
    // is coming down the wire.
    val presenter = show(download = { _, far -> held(far) })
    compose.onNodeWithTag(SetsTestTags.PROGRESS).assertDoesNotExist()

    presenter.installFrom("https://example.test/brass.zip")

    compose.waitUntil(PATIENCE) { presenter.state.progress != null }
    compose.onNodeWithTag(SetsTestTags.PROGRESS).assertIsDisplayed()
    compose.onNodeWithTag(SetsTestTags.STOP).assertIsDisplayed()
  }

  @Test
  fun `stopping it says nothing, because the person who stopped it knows`() {
    // A screen that answered a Cancel button with an error message would be a
    // screen arguing with somebody.
    val presenter = show(download = { _, far -> held(far) })
    presenter.installFrom("https://example.test/brass.zip")
    compose.waitUntil(PATIENCE) { presenter.state.progress != null }

    compose.onNodeWithTag(SetsTestTags.STOP).performClick()

    compose.waitUntil(PATIENCE) { !presenter.state.installing }
    assertNull("a cancelled download left a message behind", presenter.state.outcome)
    compose.onNodeWithTag(SetsTestTags.PROGRESS).assertDoesNotExist()
  }

  @Test
  fun `an install from a file has no bar, because nothing is coming down a wire`() {
    // A bar that reached full and sat there would say the app had hung at the
    // exact moment it was working hardest.
    val notASet = File(root.apply { mkdirs() }, "from-disk.zip").apply { writeText("not an archive") }
    val presenter = show()

    presenter.install(notASet)

    compose.waitUntil(PATIENCE) { presenter.state.outcome != null }
    compose.onNodeWithTag(SetsTestTags.PROGRESS).assertDoesNotExist()
  }

  /**
   * A download that reports one step and then never finishes.
   *
   * The state a bar exists for, and the only one a Cancel button can be
   * pressed in. It has to actually suspend: a download that returned would
   * take the bar down again before the test could look at it.
   */
  private suspend fun held(onProgress: (PackageFetcher.Progress) -> Unit): FetchedPackage {
    onProgress(PackageFetcher.Progress(bytes = 512, total = 2048))
    awaitCancellation()
  }

  private fun show(
    onOpen: (SetRow) -> Unit = {},
    onInstall: () -> Unit = {},
    download: suspend (String, (PackageFetcher.Progress) -> Unit) -> FetchedPackage =
      { _, _ -> FetchedPackage.Failed("no downloader in this test") },
    latestCommit: suspend (String) -> LatestCommit = { LatestCommit.Unknown },
  ): SetsPresenter {
    val presenter =
      SetsPresenter(library(), scope, download, latestCommit)
    compose.setContent { SetsScreen(presenter, onOpen = onOpen, onInstall = onInstall) }
    // The first reading of the disk is asynchronous, and every one of these
    // tests is about what the screen shows once it has happened.
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  /** The two halves joined, with the disk and the database both real. */
  @Test
  fun `a link is offered beside the file, and the button waits for one`() {
    show()

    compose.onNodeWithTag(SetsTestTags.LINK).assertIsDisplayed()
    compose.onNodeWithTag(SetsTestTags.FETCH).assertIsNotEnabled()
    compose.onNodeWithTag(SetsTestTags.LINK).performTextInput("https://example.test/brass.zip")
    compose.onNodeWithTag(SetsTestTags.FETCH).assertIsEnabled()
  }

  @Test
  fun `a fetched archive goes through the validator like any other`() {
    // There is one validator and no path around it. This archive is not a dice
    // set, so the install is refused exactly as a chosen file would be.
    val notASet = File(root.apply { mkdirs() }, "not-a-set.zip").apply { writeText("this is not an archive") }
    val presenter = show(download = { _, _ -> FetchedPackage.Archive(notASet) })

    presenter.installFrom("  https://example.test/brass.zip  ")

    compose.waitUntil(PATIENCE) { presenter.state.outcome != null }
    assertTrue(
      "an archive that is not one was not refused: ${presenter.state.outcome}",
      presenter.state.outcome is PackageInstaller.Result.Failed,
    )
    assertFalse("the downloaded copy was left in the cache", notASet.exists())
  }

  @Test
  fun `a download that does not arrive is refused the way a bad file is`() {
    val presenter = show(download = { _, _ -> FetchedPackage.Failed("'http://example.test' is not an https link") })

    presenter.installFrom("http://example.test/brass.zip")

    compose.waitUntil(PATIENCE) { presenter.state.outcome != null }
    val outcome = presenter.state.outcome as PackageInstaller.Result.Failed
    assertTrue(outcome.reason, outcome.reason.contains("https"))
    assertFalse("the screen was left saying it was still installing", presenter.state.installing)
  }

  @Test
  fun `a blank link fetches nothing`() {
    val asked = mutableListOf<String>()
    val presenter =
      show(download = { url, _ ->
        asked += url
        FetchedPackage.Failed("no")
      })

    presenter.installFrom("   ")

    assertEquals(emptyList<String>(), asked)
    assertFalse("the screen said it was installing for a blank link", presenter.state.installing)
  }

  @Test
  fun `there is nothing to check when nothing came from a forge`() {
    // A set installed from a file has no forge to ask, and a plain archive has
    // no commits to tell apart.
    show()

    compose.onNodeWithTag(SetsTestTags.CHECK).assertDoesNotExist()
  }

  @Test
  fun `a set whose forge has moved on is badged, and the rest are not`() {
    fromForge("brass", commit = "aaaa")
    fromForge("bone", commit = "bbbb")
    val presenter =
      show(latestCommit = { source -> if ("brass" in source) LatestCommit.At("cccc") else LatestCommit.At("bbbb") })
    compose.waitUntil(PATIENCE) { presenter.state.checkable }

    compose.onNodeWithTag(SetsTestTags.CHECK).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.checked != null }
    assertEquals(setOf("brass"), presenter.state.outdated)
    // The list is below the fold on a test-sized screen once the check button
    // is there, and a `LazyColumn` composes only what it shows. The row also
    // merges its descendants for accessibility, so the badge is a node of its
    // own only in the unmerged tree.
    compose.onNodeWithTag(SetsTestTags.LIST).performScrollToNode(hasTestTag(SetsTestTags.setOf("brass")))
    compose.onNodeWithTag(SetsTestTags.outdatedOf("brass"), useUnmergedTree = true).assertExists()
    compose.onNodeWithTag(SetsTestTags.outdatedOf("bone"), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `everything current says so, because an empty list says nothing`() {
    // A check that found everything up to date and a check that could not
    // reach anything look identical on the list.
    fromForge("brass", commit = "aaaa")
    val presenter = show(latestCommit = { LatestCommit.At("aaaa") })
    compose.waitUntil(PATIENCE) { presenter.state.checkable }

    compose.onNodeWithTag(SetsTestTags.CHECK).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.checked != null }
    assertEquals(UpdateCheck(asked = 1, outdated = 0, unreachable = 0), presenter.state.checked)
    compose.onNodeWithTag(SetsTestTags.CHECKED).assertIsDisplayed()
  }

  @Test
  fun `a forge that cannot be reached is counted, not treated as up to date`() {
    fromForge("brass", commit = "aaaa")
    val presenter = show(latestCommit = { LatestCommit.Unknown })
    compose.waitUntil(PATIENCE) { presenter.state.checkable }

    compose.onNodeWithTag(SetsTestTags.CHECK).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.checked != null }
    assertEquals(UpdateCheck(asked = 1, outdated = 0, unreachable = 1), presenter.state.checked)
    assertEquals("an unreachable forge was read as up to date", emptySet<String>(), presenter.state.outdated)
    // And the screen says so rather than leaving a list with nothing badged,
    // which is what "up to date" looks like too.
    compose.onNodeWithTag(SetsTestTags.CHECKED).assertTextContains("could not be checked", substring = true)
  }

  @Test
  fun `updating a set fetches it again from where it came from`() {
    val notASet = File(root.apply { mkdirs() }, "again.zip").apply { writeText("not an archive") }
    fromForge("brass", commit = "aaaa")
    val asked = mutableListOf<String>()
    val presenter =
      show(download = { url, _ ->
        asked += url
        FetchedPackage.Archive(notASet)
      })
    compose.waitUntil(PATIENCE) { presenter.state.sets.any { it.id == "brass" } }

    presenter.installFrom(
      requireNotNull(
        presenter.state.sets
          .first { it.id == "brass" }
          .meta.source,
      ),
    )

    compose.waitUntil(PATIENCE) { presenter.state.outcome != null }
    assertEquals(listOf("https://codeberg.org/ada/brass"), asked)
  }

  @Test
  fun `a second check while one is running is not a second check`() {
    // Two checks at once would be two sets of answers racing to say what the
    // list shows.
    fromForge("brass", commit = "aaaa")
    val asked = mutableListOf<String>()
    val holding = CompletableDeferred<LatestCommit>()
    val presenter =
      show(latestCommit = { source ->
        asked += source
        holding.await()
      })
    compose.waitUntil(PATIENCE) { presenter.state.checkable }

    compose.onNodeWithTag(SetsTestTags.CHECK).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.checking }
    presenter.checkForUpdates()

    assertEquals("the forge was asked twice", 1, asked.size)
    holding.complete(LatestCommit.At("aaaa"))
    compose.waitUntil(PATIENCE) { presenter.state.checked != null }
  }

  @Test
  fun `a recomposition around the screen that changes nothing leaves it alone`() {
    // The controls above the list are drawn from one state, so an ordinary
    // recomposition has to skip them. One that skipped wrongly would come back
    // without its check button — or without the bar and the Cancel button,
    // which is why this runs with a download in flight — and a single-pass
    // test would never see it.
    fromForge("brass", commit = "aaaa")
    var tick by mutableStateOf(0)
    val presenter = SetsPresenter(library(), scope, { _, far -> held(far) }, { LatestCommit.Unknown })
    compose.setContent {
      Column {
        Text("tick $tick")
        SetsScreen(presenter)
      }
    }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    presenter.installFrom("https://example.test/brass.zip")
    compose.waitUntil(PATIENCE) { presenter.state.progress != null }
    compose.waitUntil(PATIENCE) { presenter.state.checkable }

    compose.runOnIdle { tick++ }

    compose.onNodeWithText("tick 1").assertIsDisplayed()
    compose.onNodeWithTag(SetsTestTags.CHECK).assertIsDisplayed()
    compose.onNodeWithTag(SetsTestTags.LINK).assertIsDisplayed()
    compose.onNodeWithTag(SetsTestTags.PROGRESS).assertIsDisplayed()
    compose.onNodeWithTag(SetsTestTags.STOP).assertIsDisplayed()
  }

  /** A package that records where it came from and which commit arrived. */
  private fun fromForge(
    id: String,
    commit: String,
  ) {
    write(id, toml(id, id.replaceFirstChar(Char::uppercase)))
    File(root, "$id/.meta.json").writeText(
      """{"source":"https://codeberg.org/ada/$id","commit":"$commit","sha256":"deadbeef"}""",
    )
  }

  private fun library() =
    SetLibrary(
      bundled = bundledSet(),
      installed = InstalledSets(root),
      registry = registry,
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(root),
      defaultSetId = { DiceSet.BUILTIN_ID },
    )

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
  ) = """
    format = 1

    [set]
    id = "$id"
    name = "$name"
    version = "1.0.0"

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
    const val PATIENCE = 5_000L
  }
}
