package de.drehtuer.dinfinity.feature.sets

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageFetcher
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.dicesets.install.PackageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/**
 * When "Check for updates" can be pressed (`design/dInfinity.dc.html`, option
 * `9h`; `docs/dice-sets.md`, "Updates").
 *
 * Apart from [SetsScreenTest], which is at the size detekt allows, and which
 * is about what a check finds. This is about the button while something else
 * is going on.
 */
@RunWith(RobolectricTestRunner::class)
class SetsCheckButtonTest {
  @get:Rule
  val compose = createComposeRule()

  private val temporary: File = Files.createTempDirectory("dinfinity-sets-check").toFile()
  private val root = File(temporary, "dicesets")
  private lateinit var database: DInfinityDatabase
  private val scope = CoroutineScope(Dispatchers.Unconfined)

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    val folder = File(root, "brass").apply { mkdirs() }
    File(folder, DiceSetValidator.DICE_SET_FILE).writeText(
      """
      format = 1

      [set]
      id = "brass"
      name = "Brass"
      version = "1.0.0"

      [[die]]
      id = "d6"
      shape = "cube"
      faces = [1, 2, 3, 4, 5, 6]
      """.trimIndent(),
    )
    File(folder, PackageMeta.FILE_NAME).writeText(
      PackageMeta(source = "https://codeberg.org/ada/brass", commit = "aaaa").asJson(),
    )
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
    temporary.deleteRecursively()
  }

  @Test
  fun `while the forges are being asked the button says so and cannot be pressed again`() {
    val presenter = show(latestCommit = { _, _ -> awaitCancellation() })
    compose.onNodeWithTag(SetsTestTags.CHECK).assertIsEnabled()

    compose.onNodeWithTag(SetsTestTags.CHECK).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.checking }
    compose.onNodeWithText("Checking…").assertIsDisplayed()
    compose.onNodeWithTag(SetsTestTags.CHECK).assertIsNotEnabled()
  }

  @Test
  fun `the check is shut while a download is running`() {
    // What it found would be about a list the download is about to change.
    val presenter =
      show(download = { _, far ->
        far(PackageFetcher.Progress(bytes = 512, total = 2048))
        awaitCancellation()
      })

    presenter.installFrom("https://example.test/runes.zip")

    compose.waitUntil(PATIENCE) { presenter.state.installing }
    compose.onNodeWithTag(SetsTestTags.CHECK).assertIsNotEnabled()
    compose.onNodeWithText("Check for updates").assertIsDisplayed()
  }

  private fun show(
    download: suspend (String, (PackageFetcher.Progress) -> Unit) -> FetchedPackage =
      { _, _ -> FetchedPackage.Failed("no downloader in this test") },
    latestCommit: suspend (String, String?) -> LatestCommit = { _, _ -> LatestCommit.Unknown },
  ): SetsPresenter {
    val library =
      SetLibrary(
        bundled = DiceSet(DiceSet.BUILTIN_ID, "Standard", "1.0.0", dice = listOf(d6)),
        installed = InstalledSets(root),
        registry = InstalledSetRepository(database),
        io = Dispatchers.Unconfined,
        installer = PackageInstaller(root),
        defaultSetId = { DiceSet.BUILTIN_ID },
      )
    val presenter = SetsPresenter(library, scope, download, latestCommit)
    compose.setContent { SetsScreen(presenter, onOpen = {}, onInstall = {}) }
    compose.waitUntil(PATIENCE) { presenter.state.checkable }
    return presenter
  }

  private val d6 = Die.standard(id = "d6", shape = DieShape.Cube)

  private companion object {
    const val PATIENCE = 5_000L
  }
}
