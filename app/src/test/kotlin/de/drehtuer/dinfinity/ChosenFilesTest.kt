package de.drehtuer.dinfinity

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.TablePin
import de.drehtuer.dinfinity.feature.saved.ImportTestTags
import de.drehtuer.dinfinity.feature.sets.SetsTestTags
import de.drehtuer.dinfinity.feature.tables.PhotoOutcome
import de.drehtuer.dinfinity.feature.tables.PickedPhoto
import de.drehtuer.dinfinity.feature.tables.TablePhotos
import de.drehtuer.dinfinity.feature.tables.TablesPresenter
import de.drehtuer.dinfinity.feature.tables.TablesTestTags
import de.drehtuer.dinfinity.navigation.Destination
import de.drehtuer.dinfinity.theme.DInfinityTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/**
 * What happens to a file somebody picked, between the system's picker and the
 * screen that wanted it.
 *
 * The picker is the application's, not the feature module's: a content URI is
 * read through a `ContentResolver`, bounded, and handed on as text or as a
 * copy (`docs/dice-sets.md`; `docs/dice-notation.md`, "Export and import").
 * Robolectric has no picker, so the activity's result registry is replaced by
 * one that answers at once with the file a test chose — or with nothing, which
 * is the picker being dismissed.
 */
@RunWith(RobolectricTestRunner::class)
class ChosenFilesTest {
  @get:Rule
  val compose = createComposeRule()

  private val app = TestApp()
  private val folder: File = Files.createTempDirectory("dinfinity-chosen").toFile()
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private lateinit var navigation: NavHostController

  /** What every picker was asked to show. */
  private val asked = mutableListOf<Any?>()

  @After
  fun close() {
    app.close()
    folder.deleteRecursively()
  }

  @Test
  fun `a collection picked from the phone is read and taken in`() {
    open(Destination.CollectionImport, answer = file("thorin.json", THORIN))

    compose.onNodeWithTag(ImportTestTags.CHOOSE).performClick()

    compose.onNodeWithTag(ImportTestTags.DONE).assertIsDisplayed()
    // Any file at all, because a collection mailed through three apps arrives
    // as text/plain or application/octet-stream as often as not.
    assertEquals(listOf("*/*"), (asked.single() as Array<*>).toList())
  }

  @Test
  fun `seeing what came in climbs off the import screen`() {
    open(Destination.CollectionImport, answer = file("thorin.json", THORIN))
    compose.onNodeWithTag(ImportTestTags.CHOOSE).performClick()

    compose.onNodeWithTag(ImportTestTags.SEE).performScrollTo().performClick()

    compose.waitUntil(PATIENCE) { here() == Destination.SavedRolls }
  }

  @Test
  fun `a picked file that is not a collection is refused with its problems`() {
    open(Destination.CollectionImport, answer = file("notes.txt", "remember the milk"))

    compose.onNodeWithTag(ImportTestTags.CHOOSE).performClick()

    compose.onNodeWithTag(ImportTestTags.UNREADABLE).assertIsDisplayed()
  }

  @Test
  fun `a picked file that cannot be opened says so rather than throwing`() {
    open(Destination.CollectionImport, answer = Uri.fromFile(File(folder, "gone.json")))

    compose.onNodeWithTag(ImportTestTags.CHOOSE).performClick()

    compose.onNodeWithTag(ImportTestTags.UNOPENABLE).assertIsDisplayed()
  }

  @Test
  fun `a dismissed picker is not a failure and says nothing`() {
    open(Destination.CollectionImport, answer = null)

    compose.onNodeWithTag(ImportTestTags.CHOOSE).performClick()

    compose.onNodeWithTag(ImportTestTags.CHOOSE).assertIsDisplayed()
    assertEquals(0, compose.onAllNodesWithTag(ImportTestTags.UNOPENABLE).fetchSemanticsNodes().size)
  }

  @Test
  fun `a picked archive that is not a dice set is refused, and its copy does not stay behind`() {
    // The bytes are a stranger's, copied into a cache nobody empties: the copy
    // goes however the install ends, refused included (`docs/dice-sets.md`).
    open(Destination.DiceSets, answer = file("not-a-set.zip", "these are not the bytes of an archive"))

    compose.onNodeWithTag(SetsTestTags.INSTALL).performClick()

    compose.waitUntil(PATIENCE) {
      compose.onAllNodesWithTag(SetsTestTags.OUTCOME).fetchSemanticsNodes().isNotEmpty()
    }
    val copies = File(context.cacheDir, "chosen-packages").listFiles().orEmpty()
    assertTrue("the chosen archive's copy outlived the install: ${copies.toList()}", copies.isEmpty())
  }

  @Test
  fun `a picked archive that cannot be opened is a refusal with a reason`() {
    open(Destination.DiceSets, answer = Uri.fromFile(File(folder, "gone.zip")))

    compose.onNodeWithTag(SetsTestTags.INSTALL).performClick()

    compose.waitUntil(PATIENCE) {
      compose.onAllNodesWithTag(SetsTestTags.OUTCOME_REASON).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun `a dismissed archive picker is not a failure and says nothing`() {
    open(Destination.DiceSets, answer = null)

    compose.onNodeWithTag(SetsTestTags.INSTALL).performClick()

    assertEquals(0, compose.onAllNodesWithTag(SetsTestTags.OUTCOME).fetchSemanticsNodes().size)
  }

  @Test
  fun `a photo picked for a table goes by its file's name, and only pictures are offered`() {
    open(Destination.Tables, answer = file("oak_table.jpg", "not looked at until it is used"), screens = withPhotos())
    useAPhoto()

    compose.onNodeWithTag(TablesTestTags.PHOTO_CHOOSE).performClick()

    compose.onNodeWithTag(TablesTestTags.PHOTO_FILE).assertTextEquals("oak_table.jpg")
    assertEquals(listOf("image/*"), (asked.single() as Array<*>).toList())
  }

  @Test
  fun `a dismissed photo picker leaves the sheet with no photo in it`() {
    open(Destination.Tables, answer = null, screens = withPhotos())
    useAPhoto()
    val before = compose.onNodeWithTag(TablesTestTags.PHOTO_FILE).fetchSemanticsNode().config

    compose.onNodeWithTag(TablesTestTags.PHOTO_CHOOSE).performClick()

    assertEquals(
      before[SemanticsProperties.Text],
      compose.onNodeWithTag(TablesTestTags.PHOTO_FILE).fetchSemanticsNode().config[SemanticsProperties.Text],
    )
  }

  /** The row is the last of a lazy list, so it is not there until the list has come to it. */
  private fun useAPhoto() {
    compose.onNodeWithTag(TablesTestTags.LIST).performScrollToNode(hasTestTag(TablesTestTags.USE_PHOTO))
    compose.onNodeWithTag(TablesTestTags.USE_PHOTO).performClick()
  }

  /** The app's presenters, with a photo library behind the tables screen that nothing here reaches. */
  private fun withPhotos(): Presenters =
    app.presenters().copy(
      tables = {
        TablesPresenter(
          sets = { app.library.catalogue.installed },
          chosen = null,
          onChosen = {},
          scope = app.scope,
          photos = NO_PHOTOS,
        )
      },
    )

  private fun file(
    name: String,
    text: String,
  ): Uri = Uri.fromFile(File(folder, name).apply { writeText(text) })

  /** The app on [destination], with a picker that answers [answer] at once. */
  private fun open(
    destination: Destination,
    answer: Uri?,
    screens: Presenters = app.presenters(),
  ) {
    val registry =
      object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
          requestCode: Int,
          contract: ActivityResultContract<I, O>,
          input: I,
          options: ActivityOptionsCompat?,
        ) {
          asked += input
          dispatchResult(requestCode, answer)
        }
      }
    val owner =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
      }
    compose.setContent {
      navigation = rememberNavController()
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        DInfinityTheme { DInfinityApp(navController = navigation, screens = screens) }
      }
    }
    compose.runOnIdle { navigation.navigate(destination.route) }
    compose.waitForIdle()
  }

  private fun here(): Destination? =
    compose.runOnIdle { navigation.currentBackStackEntry?.destination?.route }?.let(Destination::ofRoute)

  private companion object {
    /** Enough for the screen to offer a photo. These tests stop at the picker. */
    val NO_PHOTOS =
      object : TablePhotos {
        override suspend fun add(
          photo: PickedPhoto,
          name: String,
        ): PhotoOutcome = PhotoOutcome.Refused(emptyList())

        override suspend fun remove(pin: TablePin) = Unit
      }

    /** A collection with one group and one roll in it. */
    val THORIN =
      """
      {
        "format": 1,
        "name": "Thorin",
        "groups": [{ "id": "thorin", "name": "Thorin", "icon": "⚔️", "parent": null }],
        "rolls": [{ "group": "thorin", "name": "Longsword", "icon": "🗡️", "formula": "1d20 + 7" }]
      }
      """.trimIndent()

    /** Long enough for a copy, an install attempt and a database round trip. */
    const val PATIENCE = 5_000L
  }
}
